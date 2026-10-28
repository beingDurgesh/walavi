package com.flux.walavi.data

import java.util.Locale

object Pricing {

    private const val TOKENS_PER_CREDIT = 1000.0

    fun estimate(model: String, totalTokens: Int, knownModels: List<String> = emptyList()): Double {
        if (totalTokens <= 0) return 0.0
        val multiplier = ModelCatalog.info(model, knownModels).credits ?: return 0.0
        return totalTokens / TOKENS_PER_CREDIT * multiplier
    }

    fun creditsFor(log: RequestLog, knownModels: List<String> = emptyList()): Double =
        if (log.credits > 0.0) {
            log.credits
        } else {
            estimate(log.model, log.promptTokens + log.completionTokens, knownModels)
        }

    fun format(value: Double): String = when {
        value <= 0.0 -> "0.0"
        value < 1.0 -> String.format(Locale.US, "%.4f", value)
        else -> String.format(Locale.US, "%.2f", value)
    }

    fun formatTokens(value: Long): String = String.format(Locale.US, "%,d", value)

    fun creditLabel(credits: Double?): String =
        if (credits == null) "—" else String.format(Locale.US, "×%.2f", credits)
}
