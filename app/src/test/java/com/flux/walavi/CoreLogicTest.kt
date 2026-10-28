package com.flux.walavi

import com.flux.walavi.data.Account
import com.flux.walavi.data.JsonExtract
import com.flux.walavi.data.LifetimeUsage
import com.flux.walavi.data.RequestLog
import com.flux.walavi.data.ModelCatalog
import com.flux.walavi.gateway.AccountPool
import com.flux.walavi.gateway.FailurePolicy
import com.flux.walavi.gateway.SseUsage
import com.flux.walavi.gateway.Urls
import com.flux.walavi.gateway.Usage
import com.flux.walavi.util.LanAddress
import com.flux.walavi.util.Net
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountPoolTest {

    private fun account(
        id: String,
        cooldown: Long = 0L,
        enabled: Boolean = true,
        needsLogin: Boolean = false,
        token: String = "token-$id",
    ) = Account(
        id = id,
        label = id,
        accessToken = token,
        enabled = enabled,
        needsLogin = needsLogin,
        cooldownUntil = cooldown,
    )

    @Test
    fun rotatesRoundRobin() {
        val pool = AccountPool()
        val accounts = listOf(account("a"), account("b"), account("c"))

        assertEquals(listOf("a", "b", "c"), pool.candidates(accounts, 1_000L).map { it.id })
        assertEquals(listOf("b", "c", "a"), pool.candidates(accounts, 1_000L).map { it.id })
        assertEquals(listOf("c", "a", "b"), pool.candidates(accounts, 1_000L).map { it.id })
        assertEquals(listOf("a", "b", "c"), pool.candidates(accounts, 1_000L).map { it.id })
    }

    @Test
    fun triesCoolingAccountsAfterReadyOnes() {
        val pool = AccountPool()
        val accounts = listOf(
            account("a", cooldown = 2_000L),
            account("b"),
            account("c"),
        )
        assertEquals(listOf("b", "c", "a"), pool.candidates(accounts, 1_000L).map { it.id })
    }

    @Test
    fun keepsDisabledAndEmptyAccountsOut() {
        val pool = AccountPool()
        val accounts = listOf(
            account("disabled", enabled = false),
            account("expired", needsLogin = true),
            account("empty", token = ""),
            account("ready"),
        )
        assertEquals(listOf("ready", "expired"), pool.candidates(accounts, 1_000L).map { it.id })
    }

    @Test
    fun retriesNeedsLoginAsLastResort() {
        val pool = AccountPool()
        val accounts = listOf(
            account("needs-login", needsLogin = true),
            account("cooling", cooldown = 5_000L),
        )
        assertEquals(
            listOf("cooling", "needs-login"),
            pool.candidates(accounts, 1_000L).map { it.id },
        )
    }

    @Test
    fun fallsBackToLeastCoolingWhenAllAreHot() {
        val pool = AccountPool()
        val accounts = listOf(
            account("a", cooldown = 5_000L),
            account("b", cooldown = 3_000L),
            account("c", cooldown = 4_000L),
        )
        assertEquals(listOf("b", "c", "a"), pool.candidates(accounts, 1_000L).map { it.id })
    }

    @Test
    fun returnsEmptyWhenNothingUsable() {
        val pool = AccountPool()
        assertTrue(pool.candidates(emptyList(), 1_000L).isEmpty())
        assertTrue(pool.candidates(listOf(account("a", enabled = false)), 1_000L).isEmpty())
    }
}

class UrlsTest {
    @Test
    fun normalizesChatUrl() {
        val expected = "https://www.codebuddy.ai/v2/chat/completions"
        assertEquals(expected, Urls.chat("https://www.codebuddy.ai"))
        assertEquals(expected, Urls.chat("https://www.codebuddy.ai/"))
        assertEquals(expected, Urls.chat("https://www.codebuddy.ai/v2"))
        assertEquals(expected, Urls.chat("https://www.codebuddy.ai/v2/chat/completions"))
    }

    @Test
    fun addsSchemeWhenMissing() {
        assertEquals("https://copilot.tencent.com/v2/models", Urls.models("copilot.tencent.com"))
    }

    @Test
    fun buildsAuthUrls() {
        assertEquals(
            "https://www.codebuddy.ai/v2/plugin/auth/state?platform=CLI",
            Urls.authState("https://www.codebuddy.ai"),
        )
        assertEquals(
            "https://www.codebuddy.ai/v2/plugin/auth/token?state=abc123",
            Urls.authToken("https://www.codebuddy.ai", "abc123"),
        )
        assertEquals(
            "https://www.codebuddy.ai/v2/plugin/auth/token/refresh",
            Urls.authRefresh("https://www.codebuddy.ai/"),
        )
    }
}

class JsonExtractTest {
    @Test
    fun extractsNestedTokens() {
        val root = Json.parseToJsonElement(
            """{"data":{"data":{"accessToken":"abcdefghijklmnop","refreshToken":"refresh-123","userId":"u1","expiresIn":3600}}}""",
        )
        val tokens = JsonExtract.extractTokens(root, now = 1_000_000L)
        assertNotNull(tokens)
        assertEquals("abcdefghijklmnop", tokens!!.accessToken)
        assertEquals("refresh-123", tokens.refreshToken)
        assertEquals("u1", tokens.userId)
        assertEquals(1_000_000L + 3_600_000L, tokens.expiresAt)
    }

    @Test
    fun rejectsShortTokens() {
        val root = Json.parseToJsonElement("""{"accessToken":"short"}""")
        assertNull(JsonExtract.extractTokens(root))
    }

    @Test
    fun readsAbsoluteExpiryInSeconds() {
        val root = Json.parseToJsonElement(
            """{"accessToken":"abcdefghijklmnop","expiresAt":1893456000}""",
        )
        val tokens = JsonExtract.extractTokens(root, now = 0L)
        assertEquals(1_893_456_000_000L, tokens!!.expiresAt)
    }

    @Test
    fun parsesOnlyModelObjects() {
        val root = Json.parseToJsonElement(
            """{"object":"list","data":[{"id":"glm-5.3","object":"model"},{"id":"gpt-x","owned_by":"x"},{"id":"junk"}]}""",
        )
        assertEquals(listOf("glm-5.3", "gpt-x"), JsonExtract.parseModelIds(root))
    }
}

class UsageTest {
    @Test
    fun readsUsageAcrossSplitChunks() {
        val sse = SseUsage()
        val payload =
            "data: {\"id\":\"x\",\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":7}}\n\ndata: [DONE]\n\n"
        val bytes = payload.toByteArray()
        sse.feed(bytes, 0, 9)
        sse.feed(bytes, 9, 17)
        sse.feed(bytes, 26, bytes.size - 26)
        assertEquals(12, sse.promptTokens)
        assertEquals(7, sse.completionTokens)
        assertNull(sse.error)
        assertTrue(sse.sawDone)
    }

    @Test
    fun capturesErrorFrames() {
        val sse = SseUsage()
        val bytes = "data: {\"error\":{\"message\":\"quota exhausted\"}}\n\n".toByteArray()
        sse.feed(bytes, 0, bytes.size)
        assertNotNull(sse.error)
        assertTrue(sse.error!!.contains("quota"))
        assertFalse(sse.sawDone)
    }

    @Test
    fun readsFinalLineWithoutNewline() {
        val sse = SseUsage()
        val bytes = "data: [DONE]".toByteArray()
        sse.feed(bytes, 0, bytes.size)
        assertFalse(sse.sawDone)
        sse.finish()
        assertTrue(sse.sawDone)
    }

    @Test
    fun readsNestedUsage() {
        val root = Json.parseToJsonElement(
            """{"choices":[],"usage":{"prompt_tokens":3,"completion_tokens":9,"credit":0.42}}""",
        )
        val usage = Usage.from(root)
        assertEquals(3, usage.promptTokens)
        assertEquals(9, usage.completionTokens)
        assertEquals(12, usage.totalTokens)
        assertEquals(0.42, usage.credits, 0.0001)
    }
}

class FailurePolicyTest {
    @Test
    fun classifiesStatuses() {
        assertTrue(FailurePolicy.isAuth(401))
        assertFalse(FailurePolicy.isAuth(403))
        assertFalse(FailurePolicy.isAuth(429))
        assertTrue(FailurePolicy.isRetryable(403))
        assertTrue(FailurePolicy.isRetryable(429))
        assertTrue(FailurePolicy.isRetryable(503))
        assertFalse(FailurePolicy.isRetryable(400))
    }

    @Test
    fun computesCooldowns() {
        assertEquals(5_000L, FailurePolicy.cooldownMs(429, 5L))
        assertEquals(60_000L, FailurePolicy.cooldownMs(429, null))
        assertEquals(300_000L, FailurePolicy.cooldownMs(429, 9_999L))
        assertEquals(300_000L, FailurePolicy.cooldownMs(403, null))
        assertEquals(30_000L, FailurePolicy.cooldownMs(500, null))
        assertEquals(0L, FailurePolicy.cooldownMs(400, null))
    }
}

class ModelCatalogTest {
    @Test
    fun mergesDiscoveredModels() {
        val all = ModelCatalog.all(listOf("custom-model"))
        val custom = all.first { it.id == "custom-model" }
        assertEquals("Custom Model", custom.name)
        assertEquals("Other", custom.provider)
        assertTrue(all.any { it.id == "deepseek-v4.1-flash" })
    }
}

class LanAddressTest {

    private val wifi = LanAddress("wlan0", "192.168.1.20")
    private val hotspot = LanAddress("ap0", "192.168.43.1")
    private val ethernet = LanAddress("eth0", "10.0.0.5")

    @Test
    fun keepsSavedAddressWhileStillAssigned() {
        val picked = Net.pickLanAddress(
            saved = hotspot.address,
            preferredInterface = "wlan0",
            addresses = listOf(wifi, hotspot, ethernet),
        )
        assertEquals(hotspot.address, picked)
    }

    @Test
    fun replacesSavedAddressWhenItIsGone() {
        val picked = Net.pickLanAddress(
            saved = "192.168.1.99",
            preferredInterface = null,
            addresses = listOf(hotspot, wifi, ethernet),
        )
        assertEquals(wifi.address, picked)
    }

    @Test
    fun prefersActiveWifiInterface() {
        val second = LanAddress("wlan1", "192.168.1.21")
        val picked = Net.pickLanAddress(
            saved = null,
            preferredInterface = "wlan1",
            addresses = listOf(wifi, second, ethernet),
        )
        assertEquals(second.address, picked)
    }

    @Test
    fun fallsBackToWifiThenEthernet() {
        assertEquals(
            wifi.address,
            Net.pickLanAddress(addresses = listOf(ethernet, hotspot, wifi)),
        )
        assertEquals(
            ethernet.address,
            Net.pickLanAddress(addresses = listOf(ethernet, hotspot)),
        )
    }

    @Test
    fun returnsNullWithoutAddresses() {
        assertNull(Net.pickLanAddress(addresses = emptyList()))
    }
}

class LifetimeUsageTest {

    private fun log(prompt: Int, completion: Int, credits: Double) = RequestLog(
        id = "id",
        at = 0L,
        model = "m",
        account = "a",
        status = 200,
        ms = 1L,
        promptTokens = prompt,
        completionTokens = completion,
        credits = credits,
    )

    @Test
    fun sumsTokensAndCreditsAcrossRequests() {
        val usage = LifetimeUsage()
            .plus(log(10, 5, 0.5))
            .plus(log(20, 7, 1.25))

        assertEquals(2L, usage.requests)
        assertEquals(30L, usage.promptTokens)
        assertEquals(12L, usage.completionTokens)
        assertEquals(42L, usage.totalTokens)
        assertEquals(1.75, usage.credits, 0.0001)
    }

    @Test
    fun failedRequestsStillCount() {
        val failed = log(0, 0, 0.0).copy(status = 502)
        val usage = LifetimeUsage().plus(failed)
        assertEquals(1L, usage.requests)
        assertEquals(0L, usage.totalTokens)
    }
}
