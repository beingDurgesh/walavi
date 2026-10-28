package com.flux.walavi.gateway

import java.net.URLEncoder

object Urls {
    const val DEFAULT_BASE = "https://www.codebuddy.ai"

    fun normalizeBase(raw: String): String {
        var value = raw.trim()
        if (value.isBlank()) value = DEFAULT_BASE
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "https://$value"
        value = value.trimEnd('/')
        value = value.removeSuffix("/v2/chat/completions")
        value = value.removeSuffix("/v2")
        return value.trimEnd('/')
    }

    fun chat(base: String): String = normalizeBase(base) + "/v2/chat/completions"

    fun models(base: String): String = normalizeBase(base) + "/v2/models"

    fun account(base: String): String = normalizeBase(base) + "/v2/plugin/account"

    fun authState(base: String): String =
        normalizeBase(base) + "/v2/plugin/auth/state?platform=CLI"

    fun authToken(base: String, state: String): String =
        normalizeBase(base) + "/v2/plugin/auth/token?state=" + URLEncoder.encode(state, "UTF-8")

    fun authRefresh(base: String): String =
        normalizeBase(base) + "/v2/plugin/auth/token/refresh"
}
