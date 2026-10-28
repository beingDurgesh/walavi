package com.flux.walavi.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Base64

object Jwt {

    private val json = Json { ignoreUnknownKeys = true }

    fun claims(token: String): JsonObject? {
        val parts = token.split('.')
        if (parts.size < 2) return null
        return try {
            val payload = parts[1]
            val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
            val bytes = Base64.getUrlDecoder().decode(padded)
            json.parseToJsonElement(String(bytes, Charsets.UTF_8)) as? JsonObject
        } catch (_: Exception) {
            null
        }
    }

    fun userId(token: String): String? =
        (claims(token)?.get("sub") as? JsonPrimitive)?.contentOrNull

    fun displayName(token: String): String? =
        (claims(token)?.get("name") as? JsonPrimitive)?.contentOrNull
            ?: (claims(token)?.get("preferred_username") as? JsonPrimitive)?.contentOrNull
            ?: (claims(token)?.get("email") as? JsonPrimitive)?.contentOrNull

    fun expiresAt(token: String): Long =
        (claims(token)?.get("exp") as? JsonPrimitive)?.longOrNull?.times(1000L) ?: 0L
}
