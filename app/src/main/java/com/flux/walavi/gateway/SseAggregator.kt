package com.flux.walavi.gateway

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream

class SseAggregator {

    private val pending = ByteArrayOutputStream()
    private val content = StringBuilder()
    private val reasoning = StringBuilder()
    private val toolCalls = LinkedHashMap<Int, ToolCall>()

    private var id: String? = null
    private var created: Long = 0L
    private var model: String? = null
    private var role: String = "assistant"

    var finishReason: String? = null
        private set
    var promptTokens: Int = 0
        private set
    var completionTokens: Int = 0
        private set
    var credits: Double = 0.0
        private set
    var errorMessage: String? = null
        private set

    fun feed(bytes: ByteArray, offset: Int, length: Int) {
        var start = offset
        val end = offset + length
        for (index in offset until end) {
            if (bytes[index] == '\n'.code.toByte()) {
                pending.write(bytes, start, index - start)
                handleLine(pending.toByteArray().toString(Charsets.UTF_8).trimEnd('\r'))
                pending.reset()
                start = index + 1
            }
        }
        if (start < end) pending.write(bytes, start, end - start)
        if (pending.size() > MAX_PENDING) pending.reset()
    }

    fun hasContent(): Boolean =
        content.isNotEmpty() || reasoning.isNotEmpty() || toolCalls.isNotEmpty()

    fun toJson(fallbackModel: String): String {
        val message = buildJsonObject {
            put("role", role)
            put("content", content.toString())
            if (reasoning.isNotEmpty()) put("reasoning_content", reasoning.toString())
            if (toolCalls.isNotEmpty()) {
                put("tool_calls", buildJsonArray {
                    for ((index, call) in toolCalls.toSortedMap()) {
                        add(buildJsonObject {
                            put("id", call.id ?: "call_$index")
                            put("type", call.type ?: "function")
                            put("function", buildJsonObject {
                                put("name", call.name ?: "")
                                put("arguments", call.arguments.toString())
                            })
                        })
                    }
                })
            }
        }
        return buildJsonObject {
            put("id", id ?: "chatcmpl-walavi")
            put("object", "chat.completion")
            put("created", if (created > 0L) created else System.currentTimeMillis() / 1000L)
            put("model", model ?: fallbackModel)
            put("choices", buildJsonArray {
                add(buildJsonObject {
                    put("index", 0)
                    put("message", message)
                    put("finish_reason", finishReason ?: "stop")
                })
            })
            put("usage", buildJsonObject {
                put("prompt_tokens", promptTokens)
                put("completion_tokens", completionTokens)
                put("total_tokens", promptTokens + completionTokens)
            })
        }.toString()
    }

    private fun handleLine(line: String) {
        if (!line.startsWith("data:")) return
        val payload = line.removePrefix("data:").trim()
        if (payload.isEmpty() || payload == "[DONE]") return

        val root = runCatching { Json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return
        str(root, "id")?.let { id = it }
        (root["created"] as? JsonPrimitive)?.longOrNull?.let { created = it }
        str(root, "model")?.let { model = it }

        root["error"]?.let { error ->
            val text = error.toString()
            if (text != "null" && text.isNotBlank()) errorMessage = text.take(400)
        }

        (root["usage"] as? JsonObject)?.let { usage ->
            (usage["prompt_tokens"] as? JsonPrimitive)?.intOrNull?.let { promptTokens = it }
            (usage["completion_tokens"] as? JsonPrimitive)?.intOrNull?.let { completionTokens = it }
            (usage["credit"] as? JsonPrimitive)?.doubleOrNull?.let { credits = it }
        }

        val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return
        val delta = choice["delta"] as? JsonObject
        if (delta != null) {
            str(delta, "content")?.let { content.append(it) }
            str(delta, "reasoning_content")?.let { reasoning.append(it) }
            str(delta, "role")?.let { role = it }
            (delta["tool_calls"] as? JsonArray)?.forEach { element ->
                val call = element as? JsonObject ?: return@forEach
                val index = (call["index"] as? JsonPrimitive)?.intOrNull ?: 0
                val builder = toolCalls.getOrPut(index) { ToolCall() }
                str(call, "id")?.let { builder.id = it }
                str(call, "type")?.let { builder.type = it }
                (call["function"] as? JsonObject)?.let { function ->
                    str(function, "name")?.let { builder.name = it }
                    str(function, "arguments")?.let { builder.arguments.append(it) }
                }
            }
        }
        str(choice, "finish_reason")?.let { finishReason = it }
    }

    private fun str(obj: JsonObject, key: String): String? =
        (obj[key] as? JsonPrimitive)?.contentOrNull

    private class ToolCall {
        var id: String? = null
        var type: String? = null
        var name: String? = null
        val arguments = StringBuilder()
    }

    private companion object {
        const val MAX_PENDING = 1_000_000
    }
}
