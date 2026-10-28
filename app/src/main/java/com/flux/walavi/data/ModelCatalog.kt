package com.flux.walavi.data

object ModelCatalog {

    private val base: List<ModelInfo> = listOf(
        ModelInfo("default-model", "Auto", "Routing", 1.00),
        ModelInfo("fast-model", "Fast", "Routing", 0.34),
        ModelInfo("balanced-model", "Balanced", "Routing", 0.59),
        ModelInfo("primary-model", "Primary", "Routing", 3.31),
        ModelInfo("deep-model", "Deep", "Routing", 3.33),

        ModelInfo("gpt-6-astra", "GPT-6 Astra", "OpenAI", 6.67),
        ModelInfo("gpt-5.6-sol", "GPT-5.6 Sol", "OpenAI", 3.47),
        ModelInfo("gpt-5.6-terra", "GPT-5.6 Terra", "OpenAI", 1.39),
        ModelInfo("gpt-5.6-luna", "GPT-5.6 Luna", "OpenAI", 0.14),
        ModelInfo("gpt-5.5", "GPT-5.5", "OpenAI", 3.31),
        ModelInfo("gpt-5.4", "GPT-5.4", "OpenAI", 1.65),

        ModelInfo("gemini-3.5-flash", "Gemini 3.5 Flash", "Google", 0.99),

        ModelInfo("deepseek-v4.1-flash", "DeepSeek V4.1 Flash", "DeepSeek", 0.00),
        ModelInfo("deepseek-v4.1-flash-sg", "DeepSeek V4.1 Flash SG", "DeepSeek", 0.03),
        ModelInfo("deepseek-v4-pro", "DeepSeek V4 Pro", "DeepSeek"),

        ModelInfo("glm-5.3", "GLM-5.3", "Zhipu", 0.79),
        ModelInfo("glm-5.3-flash", "GLM-5.3 Flash", "Zhipu", 0.06),
        ModelInfo("glm-5.3-flashx", "GLM-5.3 FlashX", "Zhipu"),
        ModelInfo("glm-5.2", "GLM-5.2", "Zhipu", 0.79),
        ModelInfo("glm-5.1", "GLM-5.1", "Zhipu"),
        ModelInfo("glm-5v-turbo", "GLM-5V Turbo", "Zhipu"),

        ModelInfo("kimi-k3", "Kimi K3", "Moonshot", 1.62),
        ModelInfo("kimi-k3-2", "Kimi K3.2", "Moonshot"),
        ModelInfo("kimi-k2.8-preview", "Kimi K2.8 Preview", "Moonshot", 0.77),
        ModelInfo("kimi-k2.7", "Kimi K2.7", "Moonshot"),
        ModelInfo("kimi-k2.6", "Kimi K2.6", "Moonshot", 0.52),

        ModelInfo("hy4-preview", "Hunyuan 4 Preview", "Tencent", 0.29),
        ModelInfo("hy3", "Hunyuan 3", "Tencent", 0.00),
        ModelInfo("hy3-x", "Hunyuan 3 X", "Tencent"),

        ModelInfo("minimax-m3-pay", "MiniMax M3", "MiniMax"),
        ModelInfo("minimax-m2.7", "MiniMax M2.7", "MiniMax"),
    )

    fun all(extraIds: List<String> = emptyList()): List<ModelInfo> {
        val known = base.associateBy { it.id }.toMutableMap()
        val ordered = base.toMutableList()
        for (id in extraIds) {
            if (id.isBlank() || known.containsKey(id)) continue
            val info = ModelInfo(id, prettify(id), providerFor(id))
            known[id] = info
            ordered += info
        }
        return ordered
    }

    fun info(id: String, extraIds: List<String> = emptyList()): ModelInfo =
        all(extraIds).firstOrNull { it.id == id } ?: ModelInfo(id, prettify(id), providerFor(id))

    fun prettify(id: String): String = id
        .split('-', '_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            when {
                part.length <= 2 -> part.uppercase()
                part.all { it.isDigit() || it == '.' } -> part
                else -> part.replaceFirstChar { it.uppercase() }
            }
        }

    private fun providerFor(id: String): String = when {
        id.startsWith("gpt") -> "OpenAI"
        id.startsWith("gemini") -> "Google"
        id.startsWith("glm") -> "Zhipu"
        id.startsWith("kimi") -> "Moonshot"
        id.startsWith("deepseek") -> "DeepSeek"
        id.startsWith("minimax") -> "MiniMax"
        id.startsWith("hy") -> "Tencent"
        id in ROUTING_ALIASES -> "Routing"
        else -> "Other"
    }

    private val ROUTING_ALIASES = setOf(
        "default-model",
        "fast-model",
        "balanced-model",
        "primary-model",
        "deep-model",
    )
}
