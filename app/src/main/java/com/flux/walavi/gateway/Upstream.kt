package com.flux.walavi.gateway

import com.flux.walavi.data.Jwt
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.URI
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

object Upstream {

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    fun executeWithRetry(attempts: Int = 2, build: () -> Call): Response {
        var last: IOException? = null
        for (attempt in 0 until attempts) {
            try {
                return build().execute()
            } catch (error: IOException) {
                last = error
                if (attempt == attempts - 1 || !isTransient(error)) throw error
                try {
                    Thread.sleep(300L * (attempt + 1))
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw error
                }
            }
        }
        throw last ?: IOException("network error")
    }

    private fun isTransient(error: IOException): Boolean {
        val name = error.javaClass.name
        if (name.contains("StreamResetException") || name.contains("ConnectionShutdownException")) {
            return true
        }
        if (error is UnknownHostException || error is SSLException) return false
        return error is ConnectException ||
            error is InterruptedIOException ||
            error is java.io.EOFException ||
            error is java.net.SocketException
    }

    fun chatCall(
        baseUrl: String,
        accessToken: String,
        userId: String?,
        body: String,
        stream: Boolean,
    ): Call {
        val requestId = UUID.randomUUID().toString()
        val conversationId = UUID.randomUUID().toString()
        val conversationRequestId = UUID.randomUUID().toString()
        val effectiveUserId = userId?.takeIf { it.isNotBlank() } ?: Jwt.userId(accessToken)
        val domain = runCatching { URI(Urls.normalizeBase(baseUrl)).host }.getOrNull()

        val builder = Request.Builder()
            .url(Urls.chat(baseUrl))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "CLI/2.177.0 CodeBuddy/2.177.0")
            .header("X-Product", "SaaS")
            .header("X-IDE-Type", "CLI")
            .header("X-IDE-Name", "CLI")
            .header("X-IDE-Version", "2.177.0")
            .header("X-Agent-Intent", "craft")
            .header("X-Agent-Purpose", "conversation")
            .header("X-Agent-Runtime", "other")
            .header("X-Agent-Type", "main")
            .header("X-Private-Data", "false")
            .header("x-codebuddy-request", "1")
            .header("x-requested-with", "XMLHttpRequest")
            .header("X-Conversation-ID", conversationId)
            .header("X-Request-ID", requestId)
            .header("X-Conversation-Message-ID", requestId)
            .header("X-Conversation-Request-ID", conversationRequestId)
            .header("X-Root-Request-ID", conversationRequestId)
        if (!effectiveUserId.isNullOrBlank()) {
            runCatching {
                builder.header("X-User-Id", URLEncoder.encode(effectiveUserId, "UTF-8"))
            }
        }
        if (!domain.isNullOrBlank()) {
            builder.header("X-Domain", domain)
        }
        return client.newCall(builder.post(body.toRequestBody(JSON)).build())
    }

    fun modelsCall(baseUrl: String, accessToken: String): Call {
        val request = Request.Builder()
            .url(Urls.models(baseUrl))
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .header("User-Agent", "CLI/2.177.0 CodeBuddy/2.177.0")
            .get()
            .build()
        return client.newCall(request)
    }

    private val JSON = "application/json; charset=utf-8".toMediaType()
}
