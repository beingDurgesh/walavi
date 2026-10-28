package com.flux.walavi

import com.flux.walavi.data.ChatPayload
import com.flux.walavi.data.Jwt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

class JwtTest {

    private fun token(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray())
        val body = encoder.encodeToString(payload.toByteArray())
        return "$header.$body."
    }

    @Test
    fun readsClaims() {
        val value = token("""{"sub":"user-1","name":"Kira","exp":1893456000}""")
        assertEquals("user-1", Jwt.userId(value))
        assertEquals("Kira", Jwt.displayName(value))
        assertEquals(1_893_456_000_000L, Jwt.expiresAt(value))
    }

    @Test
    fun fallsBackToPreferredUsername() {
        val value = token("""{"preferred_username":"light@example.com"}""")
        assertEquals("light@example.com", Jwt.displayName(value))
    }

    @Test
    fun ignoresOpaqueTokens() {
        assertNull(Jwt.userId("opaque-token"))
        assertEquals(0L, Jwt.expiresAt("opaque-token"))
    }
}

class ChatPayloadTest {

    @Test
    fun prependsSystemAndForcesStreaming() {
        val root = Json.parseToJsonElement(
            """{"model":"old","stream":false,"messages":[{"role":"user","content":"hi"}]}""",
        ).jsonObject
        val normalized = ChatPayload.normalize(root, "new-model")

        assertEquals("new-model", normalized["model"]!!.jsonPrimitive.content)
        assertEquals(true, normalized["stream"]!!.jsonPrimitive.boolean)
        val messages = normalized["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        val options = normalized["stream_options"]!!.jsonObject
        assertEquals(true, options["include_usage"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun keepsExistingSystemMessage() {
        val root = Json.parseToJsonElement(
            """{"messages":[{"role":"system","content":"custom"},{"role":"user","content":"hi"}]}""",
        ).jsonObject
        val messages = ChatPayload.normalize(root, "m")["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        assertEquals("custom", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun preservesOtherFieldsAndStreamOptions() {
        val root = Json.parseToJsonElement(
            """{"temperature":0.2,"stream_options":{"include_usage":false,"x":1},"messages":[{"role":"system","content":"s"}]}""",
        ).jsonObject
        val normalized = ChatPayload.normalize(root, "m")
        assertEquals("0.2", normalized["temperature"]!!.jsonPrimitive.content)
        val options = normalized["stream_options"]!!.jsonObject
        assertEquals(true, options["include_usage"]!!.jsonPrimitive.boolean)
        assertEquals("1", options["x"]!!.jsonPrimitive.content)
    }

    @Test
    fun rejectsMissingOrEmptyMessages() {
        assertThrows(IllegalArgumentException::class.java) {
            ChatPayload.normalize(Json.parseToJsonElement("{}").jsonObject, "m")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChatPayload.normalize(
                Json.parseToJsonElement("""{"messages":[]}""").jsonObject,
                "m",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChatPayload.normalize(
                Json.parseToJsonElement("""{"messages":{}}""").jsonObject,
                "m",
            )
        }
    }
}
