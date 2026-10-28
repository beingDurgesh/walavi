package com.flux.walavi

import com.flux.walavi.gateway.SseAggregator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseAggregatorTest {

    @Test
    fun aggregatesContentAcrossChunksAndMultibyteBoundaries() {
        val aggregator = SseAggregator()
        val sse = "data: {\"id\":\"x\",\"created\":1,\"model\":\"m\",\"choices\":[{\"index\":0," +
            "\"delta\":{\"role\":\"assistant\",\"content\":\"héllo\"}}]}\n\n" +
            "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\" world\"},\"finish_reason\":\"stop\"}]," +
            "\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2,\"credit\":0.25}}\n\ndata: [DONE]\n\n"
        val bytes = sse.toByteArray()
        val cut = sse.indexOf("é") + 1
        aggregator.feed(bytes, 0, cut)
        aggregator.feed(bytes, cut, bytes.size - cut)

        assertEquals(3, aggregator.promptTokens)
        assertEquals(2, aggregator.completionTokens)
        assertEquals(0.25, aggregator.credits, 0.0001)
        assertEquals("stop", aggregator.finishReason)
        assertNull(aggregator.errorMessage)

        val root = Json.parseToJsonElement(aggregator.toJson("fallback")).jsonObject
        val choice = root["choices"]!!.jsonArray[0].jsonObject
        val message = choice["message"]!!.jsonObject
        assertEquals("héllo world", message["content"]!!.jsonPrimitive.content)
        assertEquals("assistant", message["role"]!!.jsonPrimitive.content)
        assertEquals("stop", choice["finish_reason"]!!.jsonPrimitive.content)
    }

    @Test
    fun mergesToolCallDeltas() {
        val aggregator = SseAggregator()
        val sse = "data: {\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0," +
            "\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"get_weather\",\"arguments\":\"{\\\"city\\\":\"}}]}}]}\n\n" +
            "data: {\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0," +
            "\"function\":{\"arguments\":\"\\\"Paris\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}\n\ndata: [DONE]\n\n"
        val bytes = sse.toByteArray()
        aggregator.feed(bytes, 0, bytes.size)

        val root = Json.parseToJsonElement(aggregator.toJson("fallback")).jsonObject
        val message = root["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject
        val call = message["tool_calls"]!!.jsonArray[0].jsonObject
        assertEquals("call_1", call["id"]!!.jsonPrimitive.content)
        val function = call["function"]!!.jsonObject
        assertEquals("get_weather", function["name"]!!.jsonPrimitive.content)
        assertEquals("""{"city":"Paris"}""", function["arguments"]!!.jsonPrimitive.content)
    }

    @Test
    fun surfacesUpstreamError() {
        val aggregator = SseAggregator()
        val sse = "data: {\"error\":{\"message\":\"quota exhausted\",\"code\":11101}}\n\ndata: [DONE]\n\n"
        val bytes = sse.toByteArray()
        aggregator.feed(bytes, 0, bytes.size)
        assertEquals(false, aggregator.hasContent())
        assertEquals(true, aggregator.errorMessage!!.contains("quota"))
    }
}
