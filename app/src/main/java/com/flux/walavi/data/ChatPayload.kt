package com.flux.walavi.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object ChatPayload {

    const val DEFAULT_SYSTEM_PROMPT = "You are a helpful coding assistant."

    fun normalize(root: JsonObject, model: String): JsonObject {
        val messages = root["messages"] as? JsonArray
            ?: throw IllegalArgumentException("messages must be an array")
        if (messages.isEmpty()) throw IllegalArgumentException("messages must not be empty")

        val firstRole = (messages.firstOrNull() as? JsonObject)
            ?.get("role")?.jsonPrimitive?.contentOrNull
        val normalizedMessages = if (firstRole == "system") {
            messages
        } else {
            buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", DEFAULT_SYSTEM_PROMPT)
                })
                messages.forEach { add(it) }
            }
        }

        val existingOptions = root["stream_options"] as? JsonObject
        val streamOptions = if (existingOptions == null) {
            buildJsonObject { put("include_usage", true) }
        } else {
            JsonObject(existingOptions + ("include_usage" to JsonPrimitive(true)))
        }

        return JsonObject(
            root + mapOf(
                "model" to JsonPrimitive(model),
                "stream" to JsonPrimitive(true),
                "messages" to normalizedMessages,
                "stream_options" to streamOptions,
            ),
        )
    }
}
