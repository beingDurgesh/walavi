package com.flux.walavi

import com.flux.walavi.data.Pricing
import com.flux.walavi.data.RequestLog
import org.junit.Assert.assertEquals
import org.junit.Test

class PricingTest {

    @Test
    fun estimatesCreditsFromModelMultiplier() {
        assertEquals(0.79, Pricing.estimate("glm-5.3", 1_000), 0.0001)
        assertEquals(0.0, Pricing.estimate("deepseek-v4.1-flash", 1_000), 0.0001)
        assertEquals(0.0, Pricing.estimate("unknown-model", 1_000), 0.0001)
        assertEquals(0.0, Pricing.estimate("glm-5.3", 0), 0.0001)
    }

    @Test
    fun prefersUpstreamCreditValue() {
        val log = RequestLog(
            id = "1",
            at = 0L,
            model = "glm-5.3",
            account = "a",
            status = 200,
            ms = 10L,
            promptTokens = 100,
            completionTokens = 50,
            credits = 0.5,
        )
        assertEquals(0.5, Pricing.creditsFor(log), 0.0001)
        assertEquals(0.1185, Pricing.creditsFor(log.copy(credits = 0.0)), 0.0001)
    }

    @Test
    fun formatsCostsAndTokens() {
        assertEquals("0.0", Pricing.format(0.0))
        assertEquals("0.0142", Pricing.format(0.0142))
        assertEquals("79.00", Pricing.format(79.0))
        assertEquals("12,345", Pricing.formatTokens(12_345))
        assertEquals("×0.03", Pricing.creditLabel(0.03))
        assertEquals("—", Pricing.creditLabel(null))
    }
}
