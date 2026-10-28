package com.flux.walavi.gateway

import android.util.Log
import com.flux.walavi.data.Account
import com.flux.walavi.data.ChatPayload
import com.flux.walavi.data.Jwt
import com.flux.walavi.data.Pricing
import com.flux.walavi.data.ModelCatalog
import com.flux.walavi.data.WalaviSettings
import com.flux.walavi.data.WalaviStore
import com.flux.walavi.data.RequestLog
import com.flux.walavi.data.TokenBundle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Response
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class GatewayServer(
    private val port: Int,
    private val store: WalaviStore,
    private val pool: AccountPool,
    private val auth: CodeBuddyAuth,
    private val onLog: (RequestLog) -> Unit,
    private val onActiveRequests: (Int) -> Unit,
) {
    private val server = MiniHttpServer(port, ::handle, ::debug)
    private val active = AtomicInteger(0)
    private val startedAt = System.currentTimeMillis()
    private val refreshLocks = ConcurrentHashMap<String, Mutex>()

    fun start() = server.start()

    fun stop() = server.stop()

    fun localPort(): Int = server.localPort()

    private fun handle(exchange: HttpExchange) {
        val request = exchange.request
        val path = request.path.trimEnd('/').ifBlank { "/" }

        if (request.method == "OPTIONS") {
            exchange.respond(204)
            return
        }
        if (path == "/health") {
            health(exchange)
            return
        }
        if (!authorized(request)) {
            exchange.json(
                401,
                errorBody("invalid_api_key", "Missing or invalid API key. Copy the key from Walavi → Settings."),
                mapOf("WWW-Authenticate" to "Bearer"),
            )
            return
        }

        when (path) {
            "/v1/models", "/models" -> models(exchange)
            "/v1/chat/completions", "/chat/completions" -> chat(exchange)
            "/v1/responses", "/responses" -> exchange.json(
                501,
                errorBody(
                    "not_implemented",
                    "Walavi exposes the Chat Completions API. Configure the agent to use /v1/chat/completions.",
                ),
            )
            "/v1/walavi/status" -> status(exchange)
            else -> exchange.json(404, errorBody("not_found", "Unknown path $path"))
        }
    }

    private fun authorized(request: HttpRequest): Boolean {
        val settings = store.settings.value
        if (!settings.requireKey) return true
        val header = request.header("authorization")
        val bearer = header
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substring(7)
            ?.trim()
        val provided = bearer ?: request.header("x-api-key")?.trim() ?: return false
        return MessageDigest.isEqual(settings.apiKey.toByteArray(), provided.toByteArray())
    }

    private fun health(exchange: HttpExchange) {
        val accounts = store.accounts.value
        val now = now()
        val ready = accounts.count { it.enabled && !it.needsLogin && it.cooldownUntil <= now }
        val body = buildJsonObject {
            put("status", "ok")
            put("service", "walavi")
            put("version", "1.0.0")
            put("accounts", accounts.size)
            put("ready", ready)
            put("uptime_ms", now - startedAt)
        }
        exchange.json(200, body.toString())
    }

    private fun models(exchange: HttpExchange) {
        val settings = store.settings.value
        val catalog = ModelCatalog.all(settings.knownModels)
        val visible = if (settings.exposeAllModels) {
            catalog
        } else {
            catalog.filter { it.id in settings.selectedModels }
        }
        val body = buildJsonObject {
            put("object", "list")
            put("data", buildJsonArray {
                for (model in visible) {
                    add(buildJsonObject {
                        put("id", model.id)
                        put("object", "model")
                        put("created", 0)
                        put("owned_by", model.provider.lowercase())
                    })
                }
            })
        }
        exchange.json(200, body.toString())
    }

    private fun status(exchange: HttpExchange) {
        val settings = store.settings.value
        val accounts = store.accounts.value
        val body = buildJsonObject {
            put("running", true)
            put("uptime_ms", now() - startedAt)
            put("port", port)
            put("model", settings.defaultModel)
            put("accounts", buildJsonArray {
                for (account in accounts) {
                    add(buildJsonObject {
                        put("id", account.id)
                        put("label", account.label)
                        put("enabled", account.enabled)
                        put("needs_login", account.needsLogin)
                        put("cooldown_until", account.cooldownUntil)
                        put("last_error", account.lastError ?: "")
                        put("success", account.successCount)
                        put("failure", account.failureCount)
                    })
                }
            })
        }
        exchange.json(200, body.toString())
    }

    private fun chat(exchange: HttpExchange) {
        val started = System.currentTimeMillis()
        active.incrementAndGet()
        onActiveRequests(active.get())
        try {
            val root = runCatching { Json.parseToJsonElement(exchange.request.bodyText()) }.getOrNull()
            if (root !is JsonObject) {
                exchange.json(400, errorBody("invalid_json", "Request body must be a JSON object."))
                logRequest("", "none", 400, started, false, error = "invalid json")
                return
            }

            val settings = store.settings.value
            val clientStream = root["stream"]?.jsonPrimitive?.booleanOrNull ?: false
            val requested = root["model"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            val model = if (settings.forceModel || requested == null) settings.defaultModel else requested

            if (!settings.exposeAllModels && !settings.forceModel && model !in settings.selectedModels) {
                exchange.json(
                    400,
                    errorBody("model_not_allowed", "Model '$model' is not exposed by this Walavi. Call GET /v1/models."),
                )
                logRequest(model, "none", 400, started, clientStream, error = "model not allowed")
                return
            }

            val payload = try {
                ChatPayload.normalize(root, model).toString()
            } catch (error: IllegalArgumentException) {
                exchange.json(
                    400,
                    errorBody("invalid_messages", error.message ?: "messages are required"),
                )
                logRequest(model, "none", 400, started, clientStream, error = "invalid messages")
                return
            }
            val candidates = pool.candidates(store.accounts.value)
            if (candidates.isEmpty()) {
                val all = store.accounts.value
                val enabled = all.count { it.enabled && it.accessToken.isNotBlank() }
                val needsLogin = all.count {
                    it.enabled && it.accessToken.isNotBlank() && it.needsLogin
                }
                val cooling = all.count {
                    it.enabled && it.accessToken.isNotBlank() &&
                        !it.needsLogin && it.cooldownUntil > now()
                }
                val detail = when {
                    all.isEmpty() -> "No CodeBuddy account yet. Add one in Walavi → Accounts."
                    enabled == 0 -> "All accounts are disabled. Enable one in Walavi → Accounts."
                    else -> "No ready account: $enabled enabled, $needsLogin need sign-in, " +
                        "$cooling cooling down. Open Walavi → Accounts."
                }
                exchange.json(503, errorBody("no_accounts", detail))
                logRequest(model, "none", 503, started, clientStream, error = "no ready account")
                return
            }

            var lastError = "no attempt"
            var retryable = false
            var lastClientError: Attempt.ClientError? = null
            val retryAccounts = mutableListOf<Account>()

            fun runPass(accounts: List<Account>): Boolean {
                for (account in accounts) {
                    when (val attempt = tryAccount(account, settings, payload, stream = true)) {
                        is Attempt.Forward -> {
                            forward(exchange, attempt, clientStream, started, model)
                            return true
                        }
                        is Attempt.ClientError -> {
                            lastClientError = attempt
                            lastError = attempt.body.take(200)
                        }
                        is Attempt.Retry -> {
                            lastError = attempt.reason
                            retryable = retryable || attempt.retryable
                            if (attempt.retryable) retryAccounts += account
                        }
                    }
                }
                return false
            }

            if (runPass(candidates)) return
            if (retryable && retryAccounts.isNotEmpty()) {
                try {
                    Thread.sleep(400L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                if (runPass(retryAccounts.distinct())) return
            }

            lastClientError?.let { client ->
                exchange.respond(
                    client.status,
                    mapOf(
                        "Content-Type" to "application/json",
                        "x-walavi-account" to ascii(client.account.label),
                        "x-walavi-model" to model,
                    ),
                    client.body.toByteArray(Charsets.UTF_8),
                )
                logRequest(
                    model,
                    client.account.label,
                    client.status,
                    started,
                    clientStream,
                    error = client.body.take(200),
                )
                return
            }

            exchange.json(
                502,
                errorBody("all_accounts_failed", "All accounts failed. Last error: $lastError"),
            )
            logRequest(model, "pool", 502, started, clientStream, error = lastError)
        } finally {
            onActiveRequests(active.decrementAndGet())
        }
    }

    private fun tryAccount(
        account: Account,
        settings: WalaviSettings,
        payload: String,
        stream: Boolean,
    ): Attempt {
        val base = settings.baseUrl
        var current = ensureFresh(account)
            ?: return Attempt.Retry("${account.label}: sign-in expired", retryable = true)
        var response: Response? = null
        try {
            response = Upstream.executeWithRetry {
                Upstream.chatCall(base, current.accessToken, current.userId, payload, stream)
            }

            if (FailurePolicy.isAuth(response.code)) {
                val code = response.code
                response.close()
                response = null
                val refreshed = refreshAccount(current, base)
                if (refreshed == null) {
                    markAuthFailure(current, "sign-in rejected (HTTP $code)")
                    return Attempt.Retry("${current.label}: sign-in rejected", retryable = false)
                }
                current = refreshed
                response = Upstream.executeWithRetry {
                    Upstream.chatCall(base, current.accessToken, current.userId, payload, stream)
                }
            }

            val activeResponse = response ?: return Attempt.Retry("${current.label}: empty response")

            if (activeResponse.isSuccessful) {
                store.updateAccount(current.id) {
                    it.copy(
                        successCount = it.successCount + 1,
                        lastUsedAt = now(),
                        cooldownUntil = 0L,
                        lastError = null,
                        needsLogin = false,
                    )
                }
                return Attempt.Forward(activeResponse, current)
            }

            val status = activeResponse.code
            val retryAfter = activeResponse.header("Retry-After")?.toLongOrNull()
            val errorText = activeResponse.body?.string()?.take(2000).orEmpty()
            activeResponse.close()
            response = null

            if (FailurePolicy.isAuth(status)) {
                markAuthFailure(current, "sign-in rejected (HTTP $status)")
                return Attempt.Retry("${current.label}: sign-in rejected", retryable = false)
            }

            if (FailurePolicy.isRetryable(status)) {
                markCooldown(current, status, retryAfter, "HTTP $status")
                return Attempt.Retry(
                    "${current.label}: HTTP $status",
                    retryable = status in 500..599,
                )
            }

            store.updateAccount(current.id) {
                it.copy(
                    failureCount = it.failureCount + 1,
                    lastUsedAt = now(),
                    lastError = "HTTP $status",
                )
            }
            return Attempt.ClientError(status, errorText.ifBlank { errorBody("upstream_error", "HTTP $status") }, current)
        } catch (error: Exception) {
            runCatching { response?.close() }
            markCooldown(current, 0, null, error.message ?: error.javaClass.simpleName)
            return Attempt.Retry("${current.label}: ${error.message ?: "network error"}")
        }
    }

    private fun forward(
        exchange: HttpExchange,
        attempt: Attempt.Forward,
        clientStream: Boolean,
        started: Long,
        model: String,
    ) {
        val headers = mapOf(
            "x-walavi-account" to ascii(attempt.account.label),
            "x-walavi-model" to model,
        )
        val contentType = attempt.response.header("Content-Type") ?: "application/json"
        val upstreamSse = contentType.contains("text/event-stream", ignoreCase = true)

        when {
            !clientStream && upstreamSse -> forwardAggregated(exchange, attempt, headers, started, model)
            !clientStream -> forwardBuffered(exchange, attempt, headers, started, model)
            !upstreamSse -> forwardBuffered(exchange, attempt, headers, started, model)
            else -> forwardStream(exchange, attempt, headers, started, model)
        }
    }

    private fun forwardBuffered(
        exchange: HttpExchange,
        attempt: Attempt.Forward,
        headers: Map<String, String>,
        started: Long,
        model: String,
    ) {
        val status = attempt.response.code
        val contentType = attempt.response.header("Content-Type") ?: "application/json"
        val body = attempt.response.body?.bytes() ?: ByteArray(0)
        attempt.response.close()
        val text = String(body, Charsets.UTF_8)
        val usage = runCatching {
            Usage.from(Json.parseToJsonElement(text))
        }.getOrDefault(UsageInfo(0, 0, 0.0))
        exchange.respond(
            status,
            headers + mapOf("Content-Type" to contentType),
            body,
        )
        val credits = creditsFor(model, usage.promptTokens, usage.completionTokens, usage.credits)
        logRequest(
            model,
            attempt.account.label,
            status,
            started,
            false,
            usage.promptTokens,
            usage.completionTokens,
            credits = credits,
        )
    }

    private fun forwardAggregated(
        exchange: HttpExchange,
        attempt: Attempt.Forward,
        headers: Map<String, String>,
        started: Long,
        model: String,
    ) {
        val aggregator = SseAggregator()
        var failure: String? = null
        try {
            attempt.response.body?.source()?.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    aggregator.feed(buffer, 0, read)
                }
            }
        } catch (error: Exception) {
            failure = error.message ?: error.javaClass.simpleName
        } finally {
            attempt.response.close()
        }

        if (failure != null) {
            val text = failure.orEmpty()
            exchange.json(502, errorBody("upstream_stream_failed", "Upstream stream failed: ${text.take(200)}"))
            logRequest(model, attempt.account.label, 502, started, false, error = "stream failed: ${text.take(160)}")
            return
        }

        val error = aggregator.errorMessage
        if (!aggregator.hasContent() && !error.isNullOrBlank()) {
            exchange.json(502, errorBody("upstream_error", error))
            logRequest(model, attempt.account.label, 502, started, false, error = error)
            return
        }
        if (!aggregator.hasContent() && aggregator.finishReason == null) {
            exchange.json(502, errorBody("empty_stream", "Upstream returned an empty stream."))
            logRequest(model, attempt.account.label, 502, started, false, error = "empty stream")
            return
        }

        val body = aggregator.toJson(model)
        exchange.respond(
            200,
            headers + mapOf("Content-Type" to "application/json; charset=utf-8"),
            body.toByteArray(Charsets.UTF_8),
        )
        val credits = creditsFor(
            model,
            aggregator.promptTokens,
            aggregator.completionTokens,
            aggregator.credits,
        )
        logRequest(
            model,
            attempt.account.label,
            200,
            started,
            false,
            aggregator.promptTokens,
            aggregator.completionTokens,
            aggregator.errorMessage,
            credits = credits,
        )
    }

    private fun forwardStream(
        exchange: HttpExchange,
        attempt: Attempt.Forward,
        headers: Map<String, String>,
        started: Long,
        model: String,
    ) {
        val usage = SseUsage()
        var failure: String? = null
        try {
            exchange.stream(
                200,
                headers + mapOf(
                    "Content-Type" to "text/event-stream; charset=utf-8",
                    "Cache-Control" to "no-cache",
                    "X-Accel-Buffering" to "no",
                ),
            ) { sink ->
                try {
                    attempt.response.body?.source()?.use { input ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            sink.write(buffer, 0, read)
                            usage.feed(buffer, 0, read)
                        }
                    }
                    usage.finish()
                    if (!usage.sawDone) runCatching { sink.write(DONE_EVENT) }
                } catch (error: Exception) {
                    failure = error.message ?: error.javaClass.simpleName
                    runCatching { sink.write(sseError(failure ?: "upstream stream failed")) }
                    if (!usage.sawDone) runCatching { sink.write(DONE_EVENT) }
                }
            }
        } catch (error: Exception) {
            failure = failure ?: "client disconnected: ${error.message?.take(120)}"
        } finally {
            attempt.response.close()
        }

        val credits = creditsFor(model, usage.promptTokens, usage.completionTokens, usage.credits)
        if (failure != null) {
            val text = failure.orEmpty()
            val clientGone = text.contains("Broken pipe", true) ||
                text.contains("Socket closed", true) ||
                text.contains("reset by peer", true)
            logRequest(
                model,
                attempt.account.label,
                if (clientGone) 499 else 502,
                started,
                true,
                usage.promptTokens,
                usage.completionTokens,
                "stream failed: ${text.take(160)}",
                credits = credits,
            )
            return
        }

        logRequest(
            model,
            attempt.account.label,
            200,
            started,
            true,
            usage.promptTokens,
            usage.completionTokens,
            usage.error,
            credits = credits,
        )
    }

    private fun sseError(message: String): ByteArray = (
        "data: " + buildJsonObject {
            put("error", buildJsonObject {
                put("message", message.take(300))
                put("type", "server_error")
            })
        }.toString() + "\n\n"
        ).toByteArray(Charsets.UTF_8)

    private fun creditsFor(
        model: String,
        promptTokens: Int,
        completionTokens: Int,
        upstreamCredits: Double,
    ): Double = upstreamCredits.takeIf { it > 0.0 }
        ?: Pricing.estimate(model, promptTokens + completionTokens, store.settings.value.knownModels)

    private fun ensureFresh(account: Account): Account? {
        val expiresAt = account.expiresAt
        if (expiresAt <= 0L || expiresAt - now() > FRESH_WINDOW_MS) return account
        if (account.refreshToken.isNullOrBlank()) {
            return if (expiresAt > now()) account else null
        }
        val refreshed = refreshAccount(account, store.settings.value.baseUrl)
        return refreshed ?: if (expiresAt > now()) account else null
    }

    private fun refreshAccount(account: Account, baseUrl: String): Account? = runBlocking {
        val lock = refreshLocks.computeIfAbsent(account.id) { Mutex() }
        lock.withLock {
            val latest = store.accounts.value.firstOrNull { it.id == account.id } ?: account
            if (latest.accessToken != account.accessToken) return@withLock latest
            val tokens = auth.refresh(baseUrl, latest.refreshToken, latest.userId) ?: return@withLock null
            applyTokens(latest, tokens)
        }
    }

    private fun applyTokens(account: Account, tokens: TokenBundle): Account {
        val jwtUserId = Jwt.userId(tokens.accessToken)
        val jwtExpiry = Jwt.expiresAt(tokens.accessToken)
        val updated = account.copy(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken ?: account.refreshToken,
            userId = tokens.userId ?: jwtUserId ?: account.userId,
            expiresAt = tokens.expiresAt.takeIf { it > 0L }
                ?: jwtExpiry.takeIf { it > 0L }
                ?: account.expiresAt,
            needsLogin = false,
            cooldownUntil = 0L,
            lastError = null,
        )
        store.upsertAccount(updated)
        return updated
    }

    private fun markCooldown(account: Account, status: Int, retryAfter: Long?, reason: String) {
        val cooldown = if (status == 0) {
            FailurePolicy.NETWORK_COOLDOWN_MS
        } else {
            FailurePolicy.cooldownMs(status, retryAfter)
        }
        store.updateAccount(account.id) {
            it.copy(
                cooldownUntil = now() + cooldown,
                failureCount = it.failureCount + 1,
                lastUsedAt = now(),
                lastError = reason,
            )
        }
    }

    private fun markAuthFailure(account: Account, reason: String) {
        store.updateAccount(account.id) {
            it.copy(
                needsLogin = true,
                cooldownUntil = now() + FailurePolicy.AUTH_COOLDOWN_MS,
                failureCount = it.failureCount + 1,
                lastUsedAt = now(),
                lastError = reason,
            )
        }
    }

    private fun logRequest(
        model: String,
        account: String,
        status: Int,
        started: Long,
        stream: Boolean,
        promptTokens: Int = 0,
        completionTokens: Int = 0,
        error: String? = null,
        credits: Double = 0.0,
    ) {
        onLog(
            RequestLog(
                id = UUID.randomUUID().toString(),
                at = System.currentTimeMillis(),
                model = model,
                account = account,
                status = status,
                ms = System.currentTimeMillis() - started,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                credits = credits,
                stream = stream,
                error = error,
            ),
        )
    }

    private fun now(): Long = System.currentTimeMillis()

    private fun ascii(value: String): String =
        value.filter { it.code in 32..126 }.take(80).ifBlank { "account" }

    private fun errorBody(type: String, message: String): String = buildJsonObject {
        put("error", buildJsonObject {
            put("message", message)
            put("type", type)
            put("code", type)
        })
    }.toString()

    private fun debug(message: String) {
        Log.w(TAG, message)
    }

    private sealed interface Attempt {
        data class Forward(val response: Response, val account: Account) : Attempt
        data class ClientError(val status: Int, val body: String, val account: Account) : Attempt
        data class Retry(val reason: String, val retryable: Boolean = true) : Attempt
    }

    private companion object {
        const val TAG = "WalaviGateway"
        const val FRESH_WINDOW_MS = 5 * 60 * 1000L
        val DONE_EVENT = "data: [DONE]\n\n".toByteArray(Charsets.UTF_8)
    }
}

private fun HttpExchange.json(
    status: Int,
    body: String,
    headers: Map<String, String> = emptyMap(),
) {
    respondText(status, body, "application/json; charset=utf-8", headers)
}
