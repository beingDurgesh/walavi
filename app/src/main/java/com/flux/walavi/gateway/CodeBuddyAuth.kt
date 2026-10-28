package com.flux.walavi.gateway

import com.flux.walavi.data.ChatPayload
import com.flux.walavi.data.JsonExtract
import com.flux.walavi.data.Jwt
import com.flux.walavi.data.TestResult
import com.flux.walavi.data.TokenBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class CodeBuddyAuth {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    data class LoginSession(val authUrl: String, val state: String)

    data class AccountInfo(val userId: String?, val label: String?)

    sealed interface PollResult {
        data class Success(val token: TokenBundle) : PollResult
        data object Pending : PollResult
        data class Failed(val message: String) : PollResult
    }

    suspend fun startLogin(baseUrl: String): Result<LoginSession> = withContext(Dispatchers.IO) {
        try {
            val request = noAuth(Request.Builder().url(Urls.authState(baseUrl)))
                .post("{}".toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("HTTP ${response.code}: ${text.take(180)}"),
                    )
                }
                val root = runCatching { Json.parseToJsonElement(text) }.getOrNull()
                    ?: return@withContext Result.failure(IOException("Invalid auth response"))
                val authUrl = JsonExtract.findString(root, "authUrl")
                val state = JsonExtract.findString(root, "state")
                if (authUrl.isNullOrBlank() || state.isNullOrBlank()) {
                    return@withContext Result.failure(IOException("Auth response missing authUrl/state"))
                }
                Result.success(LoginSession(authUrl, state))
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun poll(baseUrl: String, state: String): PollResult = withContext(Dispatchers.IO) {
        try {
            val request = noAuth(Request.Builder().url(Urls.authToken(baseUrl, state))).get().build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val root = runCatching { Json.parseToJsonElement(text) }.getOrNull()
                if (root != null) {
                    JsonExtract.extractTokens(root)?.withJwtClaims()
                        ?.let { return@withContext PollResult.Success(it) }
                }
                if (response.isSuccessful) return@withContext PollResult.Pending
                if (text.contains("pending", ignoreCase = true)) return@withContext PollResult.Pending
                PollResult.Failed("HTTP ${response.code}: ${text.take(140)}")
            }
        } catch (error: Exception) {
            PollResult.Failed(error.message ?: "network error")
        }
    }

    suspend fun refresh(baseUrl: String, refreshToken: String?, userId: String? = null): TokenBundle? =
        withContext(Dispatchers.IO) {
            if (refreshToken.isNullOrBlank()) return@withContext null
            try {
                val builder = noAuth(Request.Builder().url(Urls.authRefresh(baseUrl)))
                    .header("X-Refresh-Token", refreshToken)
                val uid = userId?.takeIf { it.isNotBlank() }
                if (uid != null) {
                    builder.header("X-User-Id", URLEncoder.encode(uid, "UTF-8"))
                }
                val request = builder.post("{}".toRequestBody(JSON)).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val text = response.body?.string().orEmpty()
                    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull()
                        ?: return@withContext null
                    JsonExtract.extractTokens(root)?.withJwtClaims()
                }
            } catch (_: Exception) {
                null
            }
        }

    suspend fun fetchAccount(baseUrl: String, accessToken: String): AccountInfo? =
        withContext(Dispatchers.IO) {
            try {
                val normalized = Urls.normalizeBase(baseUrl)
                val builder = Request.Builder()
                    .url(Urls.account(normalized))
                    .header("Authorization", "Bearer $accessToken")
                    .header("Accept", "application/json")
                    .header("User-Agent", "CLI/2.177.0 CodeBuddy/2.177.0")
                runCatching { URI(normalized).authority }.getOrNull()?.let { domain ->
                    builder.header("X-Domain", domain)
                }
                client.newCall(builder.get().build()).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val text = response.body?.string().orEmpty()
                    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull()
                        ?: return@withContext null
                    val userId = JsonExtract.findString(root, "userId")
                        ?: JsonExtract.findString(root, "userName")
                    val label = JsonExtract.findString(root, "userName")
                        ?: JsonExtract.findString(root, "name")
                        ?: JsonExtract.findString(root, "email")
                    if (userId.isNullOrBlank() && label.isNullOrBlank()) {
                        null
                    } else {
                        AccountInfo(userId, label)
                    }
                }
            } catch (_: Exception) {
                null
            }
        }

    suspend fun fetchModels(
        baseUrl: String,
        accessToken: String,
        userId: String? = null,
    ): List<String>? =
        withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder()
                    .url(Urls.models(baseUrl))
                    .header("Authorization", "Bearer $accessToken")
                    .header("Accept", "application/json")
                    .header("User-Agent", "CLI/2.177.0 CodeBuddy/2.177.0")
                val uid = userId?.takeIf { it.isNotBlank() }
                if (uid != null) {
                    builder.header("X-User-Id", URLEncoder.encode(uid, "UTF-8"))
                }
                client.newCall(builder.get().build()).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val text = response.body?.string().orEmpty()
                    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull()
                        ?: return@withContext null
                    JsonExtract.parseModelIds(root).takeIf { it.isNotEmpty() }
                }
            } catch (_: Exception) {
                null
            }
        }

    suspend fun ping(
        baseUrl: String,
        accessToken: String,
        model: String,
        userId: String? = null,
    ): TestResult =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            try {
                val body = buildJsonObject {
                    put("model", model)
                    put("messages", buildJsonArray {
                        add(buildJsonObject {
                            put("role", "system")
                            put("content", ChatPayload.DEFAULT_SYSTEM_PROMPT)
                        })
                        add(buildJsonObject {
                            put("role", "user")
                            put("content", "ping")
                        })
                    })
                    put("max_tokens", 1)
                    put("stream", true)
                }.toString()
                Upstream.executeWithRetry {
                    Upstream.chatCall(baseUrl, accessToken, userId, body, stream = true)
                }.use { response ->
                    val text = response.body?.string().orEmpty()
                    val ms = System.currentTimeMillis() - started
                    val streamError = runCatching {
                        val aggregator = SseAggregator()
                        val bytes = text.toByteArray()
                        aggregator.feed(bytes, 0, bytes.size)
                        aggregator.errorMessage
                    }.getOrNull()
                    val ok = response.isSuccessful && streamError == null
                    val message = when {
                        ok -> "OK"
                        streamError != null -> streamError
                        else -> runCatching {
                            val root = Json.parseToJsonElement(text)
                            JsonExtract.findString(root, "message")
                                ?: JsonExtract.findString(root, "msg")
                                ?: text.take(140)
                        }.getOrDefault(text.take(140))
                    }
                    TestResult(ok, response.code, ms, message)
                }
            } catch (error: Exception) {
                TestResult(false, 0, System.currentTimeMillis() - started, error.message ?: "network error")
            }
        }

    private fun TokenBundle.withJwtClaims(): TokenBundle = copy(
        userId = userId ?: Jwt.userId(accessToken),
        expiresAt = if (expiresAt > 0L) expiresAt else Jwt.expiresAt(accessToken),
    )

    private fun noAuth(builder: Request.Builder): Request.Builder = builder
        .header("X-No-Authorization", "true")
        .header("X-No-User-Id", "true")
        .header("X-No-Enterprise-Id", "true")
        .header("X-No-Department-Info", "true")
        .header("Content-Type", "application/json")

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
