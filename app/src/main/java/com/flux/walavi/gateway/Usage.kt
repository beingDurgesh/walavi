package com.flux.walavi.gateway

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

data class UsageInfo(
    val promptTokens: Int,
    val completionTokens: Int,
    val credits: Double,
) {
    val totalTokens: Int get() = promptTokens + completionTokens
}

object Usage {
    fun from(root: JsonElement): UsageInfo {
        val usage = findObject(root, "usage") ?: return UsageInfo(0, 0, 0.0)
        val prompt = (usage["prompt_tokens"] as? JsonPrimitive)?.intOrNull ?: 0
        val completion = (usage["completion_tokens"] as? JsonPrimitive)?.intOrNull ?: 0
        val credits = (usage["credit"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
        return UsageInfo(prompt, completion, credits)
    }

    private fun findObject(root: JsonElement, key: String): JsonObject? {
        when (root) {
            is JsonObject -> {
                val direct = root[key]
                if (direct is JsonObject) return direct
                for (value in root.values) findObject(value, key)?.let { return it }
            }
            is JsonArray -> for (value in root) findObject(value, key)?.let { return it }
            else -> Unit
        }
        return null
    }
}

class SseUsage {
    var promptTokens: Int = 0
        private set
    var completionTokens: Int = 0
        private set
    var credits: Double = 0.0
        private set
    var error: String? = null
        private set
    var sawDone: Boolean = false
        private set

    private val buffer = StringBuilder()

    fun feed(bytes: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        buffer.append(String(bytes, offset, length, Charsets.UTF_8))
        while (true) {
            val newline = buffer.indexOf("\n")
            if (newline < 0) {
                if (buffer.length > 128_000) buffer.delete(0, buffer.length - 8_192)
                return
            }
            val line = buffer.substring(0, newline).trimEnd('\r')
            buffer.delete(0, newline + 1)
            handleLine(line)
        }
    }

    fun finish() {
        if (buffer.isEmpty()) return
        val line = buffer.toString().trimEnd('\r')
        buffer.setLength(0)
        handleLine(line)
    }

    private fun handleLine(line: String) {
        if (!line.startsWith("data:")) return
        val payload = line.removePrefix("data:").trim()
        if (payload.isEmpty()) return
        if (payload == "[DONE]") {
            sawDone = true
            return
        }
        val parsed = runCatching { Json.parseToJsonElement(payload) }.getOrNull() ?: return
        val info = Usage.from(parsed)
        if (info.promptTokens > 0) promptTokens = info.promptTokens
        if (info.completionTokens > 0) completionTokens = info.completionTokens
        if (info.credits > 0.0) credits = info.credits
        if (parsed is JsonObject) {
            val errText = parsed["error"]?.toString()
            if (errText != null && errText != "null") {
                error = errText.take(300)
            }
        }
    }
}
