package com.flux.walavi.gateway

object FailurePolicy {
    const val NETWORK_COOLDOWN_MS = 20_000L
    const val AUTH_COOLDOWN_MS = 6 * 60 * 60 * 1000L

    fun isAuth(status: Int): Boolean = status == 401

    fun isRetryable(status: Int): Boolean = status == 403 || status == 429 || status in 500..599

    fun cooldownMs(status: Int, retryAfterSeconds: Long?): Long = when {
        status == 403 -> 5 * 60 * 1000L
        status == 429 -> ((retryAfterSeconds ?: 60L).coerceIn(1L, 300L)) * 1000L
        status in 500..599 -> 30_000L
        else -> 0L
    }
}
