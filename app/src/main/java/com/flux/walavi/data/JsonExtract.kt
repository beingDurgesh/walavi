package com.flux.walavi.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object JsonExtract {

    fun findString(root: JsonElement, key: String): String? {
        when (root) {
            is JsonObject -> {
                val direct = root[key]
                if (direct is JsonPrimitive && direct.isString) {
                    val value = direct.contentOrNull
                    if (!value.isNullOrBlank()) return value
                }
                for (value in root.values) {
                    findString(value, key)?.let { return it }
                }
            }
            is JsonArray -> {
                for (value in root) {
                    findString(value, key)?.let { return it }
                }
            }
            else -> Unit
        }
        return null
    }

    fun findLong(root: JsonElement, key: String): Long? {
        when (root) {
            is JsonObject -> {
                val direct = root[key]
                if (direct is JsonPrimitive) {
                    direct.contentOrNull?.toLongOrNull()?.let { return it }
                }
                for (value in root.values) {
                    findLong(value, key)?.let { return it }
                }
            }
            is JsonArray -> {
                for (value in root) {
                    findLong(value, key)?.let { return it }
                }
            }
            else -> Unit
        }
        return null
    }

    fun extractTokens(root: JsonElement, now: Long = System.currentTimeMillis()): TokenBundle? {
        val access = findString(root, "accessToken")
            ?: findString(root, "access_token")
            ?: findString(root, "token")
            ?: return null
        if (access.length < 12) return null

        val refresh = findString(root, "refreshToken") ?: findString(root, "refresh_token")
        val userId = findString(root, "userId")
            ?: findString(root, "user_id")
            ?: findString(root, "userName")
            ?: findString(root, "username")

        val absolute = findLong(root, "expiresAt")
            ?: findLong(root, "expireAt")
            ?: findLong(root, "expires_at")
        val relative = findLong(root, "expiresIn") ?: findLong(root, "expires_in")

        val expiresAt = when {
            absolute != null && absolute > 0L -> if (absolute < 10_000_000_000L) absolute * 1000 else absolute
            relative != null && relative > 0L -> now + relative * 1000
            else -> 0L
        }
        return TokenBundle(access, refresh, userId, expiresAt)
    }

    fun parseModelIds(root: JsonElement): List<String> {
        val found = LinkedHashSet<String>()
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    val id = element["id"]?.let { if (it is JsonPrimitive) it.contentOrNull else null }
                    val looksLikeModel = element["object"]?.jsonPrimitive?.contentOrNull == "model" ||
                        element.containsKey("owned_by") ||
                        element.containsKey("display_name")
                    if (!id.isNullOrBlank() && looksLikeModel) found += id
                    element["models"]?.let { walk(it) }
                    element["data"]?.let { walk(it) }
                }
                is JsonArray -> element.forEach { walk(it) }
                else -> Unit
            }
        }
        walk(root)
        return found.toList()
    }
}
