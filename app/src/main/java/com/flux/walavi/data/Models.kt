package com.flux.walavi.data

import kotlinx.serialization.Serializable

@Serializable
data class Account(
    val id: String,
    val label: String,
    val accessToken: String,
    val refreshToken: String? = null,
    val userId: String? = null,
    val expiresAt: Long = 0L,
    val enabled: Boolean = true,
    val needsLogin: Boolean = false,
    val createdAt: Long = 0L,
    val lastUsedAt: Long = 0L,
    val successCount: Long = 0L,
    val failureCount: Long = 0L,
    val cooldownUntil: Long = 0L,
    val lastError: String? = null,
)

@Serializable
data class WalaviSettings(
    val port: Int = 8787,
    val baseUrl: String = "https://www.codebuddy.ai",
    val apiKey: String = "",
    val requireKey: Boolean = true,
    val defaultModel: String = "deepseek-v4.1-flash",
    val forceModel: Boolean = false,
    val exposeAllModels: Boolean = true,
    val selectedModels: List<String> = emptyList(),
    val knownModels: List<String> = emptyList(),
    val autoStart: Boolean = false,
    val keepAwake: Boolean = true,
    val startOnBoot: Boolean = false,
    val lanAddress: String = "",
)

@Serializable
data class RequestLog(
    val id: String,
    val at: Long,
    val model: String,
    val account: String,
    val status: Int,
    val ms: Long,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val credits: Double = 0.0,
    val stream: Boolean = false,
    val error: String? = null,
) {
    val totalTokens: Int get() = promptTokens + completionTokens
}

@Serializable
data class LifetimeUsage(
    val requests: Long = 0,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val credits: Double = 0.0,
) {
    val totalTokens: Long get() = promptTokens + completionTokens

    fun plus(log: RequestLog): LifetimeUsage = LifetimeUsage(
        requests = requests + 1,
        promptTokens = promptTokens + log.promptTokens,
        completionTokens = completionTokens + log.completionTokens,
        credits = credits + log.credits,
    )
}

data class TokenBundle(
    val accessToken: String,
    val refreshToken: String? = null,
    val userId: String? = null,
    val expiresAt: Long = 0L,
)

data class ModelInfo(
    val id: String,
    val name: String,
    val provider: String,
    val credits: Double? = null,
)

data class TestResult(
    val ok: Boolean,
    val status: Int,
    val ms: Long,
    val message: String,
)
