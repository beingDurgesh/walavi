package com.flux.walavi.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.flux.walavi.BuildConfig
import com.flux.walavi.WalaviApp
import com.flux.walavi.data.Account
import com.flux.walavi.data.JsonExtract
import com.flux.walavi.data.Jwt
import com.flux.walavi.data.WalaviSettings
import com.flux.walavi.data.TokenBundle
import com.flux.walavi.gateway.CodeBuddyAuth
import com.flux.walavi.gateway.Urls
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

sealed interface LoginState {
    data object Idle : LoginState
    data object Starting : LoginState
    data class Waiting(val authUrl: String) : LoginState
    data class Failed(val message: String) : LoginState
    data class Done(val label: String) : LoginState
}

class AppViewModel(private val app: WalaviApp) : ViewModel() {

    private val store = app.store
    private val auth = CodeBuddyAuth()

    val accounts = store.accounts
    val settings = store.settings
    val logs = store.logs
    val usage = store.usage
    val gateway = app.controller.state

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _login = MutableStateFlow<LoginState>(LoginState.Idle)
    val login: StateFlow<LoginState> = _login.asStateFlow()

    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    private val _refreshingModels = MutableStateFlow(false)
    val refreshingModels: StateFlow<Boolean> = _refreshingModels.asStateFlow()

    private var loginJob: Job? = null
    private var reloginTarget: String? = null
    private var autoStartChecked = false

    fun autoStartIfNeeded(context: Context) {
        if (autoStartChecked) return
        autoStartChecked = true
        if (store.settings.value.autoStart && !app.controller.state.value.running) {
            app.controller.startService(context)
        }
    }

    fun startGateway(context: Context) {
        app.controller.startService(context)
    }

    fun refreshEndpoint() {
        app.controller.refreshEndpoint()
    }

    fun stopGateway(context: Context) {
        app.controller.stopService(context)
    }

    fun addAccountFromToken(label: String, token: String, refreshToken: String) {
        val raw = token.trim().removePrefix("Bearer ").trim()
        if (raw.isBlank()) {
            emit("Paste an access token first")
            return
        }
        val parsed = if (raw.startsWith("{")) {
            runCatching { JsonExtract.extractTokens(Json.parseToJsonElement(raw)) }.getOrNull()
        } else {
            null
        }
        val access = parsed?.accessToken ?: raw
        val refresh = refreshToken.trim().ifBlank { parsed?.refreshToken.orEmpty() }.ifBlank { null }
        val jwtUserId = Jwt.userId(access)
        val jwtLabel = Jwt.displayName(access)
        val jwtExpires = Jwt.expiresAt(access)
        val account = Account(
            id = store.newAccountId(),
            label = label.trim().ifBlank { jwtLabel ?: nextLabel() },
            accessToken = access,
            refreshToken = refresh,
            userId = parsed?.userId ?: jwtUserId,
            expiresAt = parsed?.expiresAt?.takeIf { it > 0L } ?: jwtExpires,
            createdAt = System.currentTimeMillis(),
        )
        store.upsertAccount(account)
        emit("Account added")
        viewModelScope.launch {
            val info = withTimeoutOrNull(10_000L) {
                auth.fetchAccount(store.settings.value.baseUrl, account.accessToken)
            }
            if (info != null) {
                store.updateAccount(account.id) { current ->
                    current.copy(
                        userId = info.userId ?: current.userId,
                        label = if (current.label.startsWith("CodeBuddy ")) {
                            info.label?.takeIf { it.isNotBlank() } ?: current.label
                        } else {
                            current.label
                        },
                    )
                }
            }
            testAccount(account)
        }
    }

    fun startBrowserLogin(targetId: String? = null) {
        loginJob?.cancel()
        reloginTarget = targetId
        loginJob = viewModelScope.launch {
            _login.value = LoginState.Starting
            val base = store.settings.value.baseUrl
            val started = auth.startLogin(base)
            if (started.isFailure) {
                _login.value = LoginState.Failed(
                    started.exceptionOrNull()?.message ?: "Could not start sign-in",
                )
                return@launch
            }
            val session = started.getOrNull() ?: return@launch
            _login.value = LoginState.Waiting(session.authUrl)

            var failures = 0
            val deadline = System.currentTimeMillis() + LOGIN_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                delay(2_000L)
                when (val poll = auth.poll(base, session.state)) {
                    is CodeBuddyAuth.PollResult.Success -> {
                        saveTokens(poll.token)
                        return@launch
                    }
                    is CodeBuddyAuth.PollResult.Failed -> {
                        failures += 1
                        if (failures >= 4) {
                            _login.value = LoginState.Failed(poll.message)
                            return@launch
                        }
                    }
                    CodeBuddyAuth.PollResult.Pending -> Unit
                }
            }
            _login.value = LoginState.Failed("Timed out waiting for browser approval")
        }
    }

    fun cancelLogin() {
        loginJob?.cancel()
        loginJob = null
        reloginTarget = null
        _login.value = LoginState.Idle
    }

    fun resetLogin() {
        loginJob?.cancel()
        loginJob = null
        reloginTarget = null
        _login.value = LoginState.Idle
    }

    fun deleteAccount(account: Account) {
        store.deleteAccount(account.id)
        emit("${account.label} removed")
    }

    fun toggleAccount(account: Account, enabled: Boolean) {
        store.updateAccount(account.id) {
            it.copy(enabled = enabled, cooldownUntil = if (enabled) 0L else it.cooldownUntil)
        }
    }

    fun moveAccount(account: Account, delta: Int) {
        store.moveAccount(account.id, delta)
    }

    fun testAccount(account: Account) {
        if (_busy.value.contains(account.id)) return
        _busy.update { it + account.id }
        viewModelScope.launch {
            val result = app.controller.testAccount(account)
            _busy.update { it - account.id }
            emit(
                if (result.ok) {
                    "${account.label}: OK · ${result.ms} ms"
                } else {
                    "${account.label}: ${if (result.status > 0) "HTTP ${result.status} · " else ""}${result.message}"
                },
            )
        }
    }

    fun refreshModels() {
        if (_refreshingModels.value) return
        _refreshingModels.value = true
        viewModelScope.launch {
            val count = app.controller.refreshModels()
            _refreshingModels.value = false
            emit(
                if (count > 0) {
                    "Discovered $count models"
                } else {
                    "Could not fetch the upstream catalogue; keeping local models"
                },
            )
        }
    }

    fun setDefaultModel(model: String) {
        store.updateSettings { settings ->
            settings.copy(
                defaultModel = model,
                selectedModels = if (model in settings.selectedModels) {
                    settings.selectedModels
                } else {
                    settings.selectedModels + model
                },
            )
        }
    }

    fun clearLogs() {
        store.clearLogs()
    }

    fun setForceModel(force: Boolean) {
        store.updateSettings { it.copy(forceModel = force) }
    }

    fun setExposeAll(expose: Boolean) {
        store.updateSettings { settings ->
            val selected = if (expose) {
                settings.selectedModels
            } else if (settings.selectedModels.isEmpty()) {
                listOf(settings.defaultModel)
            } else {
                settings.selectedModels
            }
            settings.copy(exposeAllModels = expose, selectedModels = selected)
        }
    }

    fun toggleSelectedModel(model: String, selected: Boolean) {
        store.updateSettings { settings ->
            val next = if (selected) {
                (settings.selectedModels + model).distinct()
            } else {
                settings.selectedModels - model
            }
            settings.copy(selectedModels = next)
        }
    }

    fun savePort(value: Int) {
        if (value !in 1024..65535) {
            emit("Port must be between 1024 and 65535")
            return
        }
        store.updateSettings { it.copy(port = value) }
        emit("Port saved. Restart the gateway to apply.")
    }

    fun saveBaseUrl(value: String) {
        val normalized = Urls.normalizeBase(value)
        store.updateSettings { it.copy(baseUrl = normalized) }
        emit("API base URL saved")
    }

    fun setRequireKey(require: Boolean) {
        store.updateSettings { it.copy(requireKey = require) }
    }

    fun regenerateKey() {
        store.regenerateApiKey()
        emit("API key regenerated")
    }

    fun setAutoStart(value: Boolean) {
        store.updateSettings { it.copy(autoStart = value) }
    }

    fun setKeepAwake(value: Boolean) {
        store.updateSettings { it.copy(keepAwake = value) }
    }

    fun setStartOnBoot(value: Boolean) {
        store.updateSettings { it.copy(startOnBoot = value) }
    }

    fun notifyCopied() {
        emit("Copied to clipboard")
    }

    fun notify(text: String) {
        emit(text)
    }

    fun debugInfo(): String = buildString {
        appendLine("Walavi ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Endpoint: ${app.controller.lanUrl()}")
        appendLine("Port: ${store.settings.value.port}")
        appendLine("Accounts: ${store.accounts.value.size}")
        appendLine("Base URL: ${store.settings.value.baseUrl}")
        appendLine("Default model: ${store.settings.value.defaultModel}")
        appendLine("Exposed: ${if (store.settings.value.exposeAllModels) "all" else store.settings.value.selectedModels.joinToString()}")
    }

    private suspend fun saveTokens(tokens: TokenBundle) {
        val target = reloginTarget
        reloginTarget = null
        val now = System.currentTimeMillis()
        val base = store.settings.value.baseUrl
        val jwtUserId = Jwt.userId(tokens.accessToken)
        val jwtLabel = Jwt.displayName(tokens.accessToken)
        val jwtExpires = Jwt.expiresAt(tokens.accessToken)
        val info = if (tokens.userId.isNullOrBlank() && jwtUserId.isNullOrBlank()) {
            withTimeoutOrNull(10_000L) { auth.fetchAccount(base, tokens.accessToken) }
        } else {
            null
        }
        val resolvedUserId = tokens.userId ?: jwtUserId ?: info?.userId
        val resolvedLabel = jwtLabel ?: info?.label?.takeIf { it.isNotBlank() }
        val resolvedExpiry = tokens.expiresAt.takeIf { it > 0L } ?: jwtExpires

        if (target != null) {
            val updated = store.updateAccount(target) {
                it.copy(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken ?: it.refreshToken,
                    userId = resolvedUserId ?: it.userId,
                    expiresAt = if (resolvedExpiry > 0L) resolvedExpiry else it.expiresAt,
                    needsLogin = false,
                    cooldownUntil = 0L,
                    lastError = null,
                    enabled = true,
                )
            }
            _login.value = LoginState.Done(updated?.label ?: "Account")
            emit("Account reconnected")
            return
        }

        val existing = resolvedUserId?.let { userId ->
            store.accounts.value.firstOrNull { it.userId == userId }
        }
        if (existing != null) {
            store.updateAccount(existing.id) {
                it.copy(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken ?: it.refreshToken,
                    userId = resolvedUserId ?: it.userId,
                    expiresAt = if (resolvedExpiry > 0L) resolvedExpiry else it.expiresAt,
                    needsLogin = false,
                    cooldownUntil = 0L,
                    lastError = null,
                    enabled = true,
                )
            }
            _login.value = LoginState.Done(existing.label)
            emit("Account refreshed")
            return
        }

        val label = resolvedLabel ?: resolvedUserId?.takeIf { it.isNotBlank() } ?: nextLabel()
        store.upsertAccount(
            Account(
                id = store.newAccountId(),
                label = label,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                userId = resolvedUserId,
                expiresAt = if (resolvedExpiry > 0L) resolvedExpiry else 0L,
                createdAt = now,
            ),
        )
        _login.value = LoginState.Done(label)
        emit("Account connected")
    }

    private fun nextLabel(): String {
        val used = store.accounts.value.map { it.label }.toSet()
        var index = store.accounts.value.size + 1
        while ("CodeBuddy $index" in used) index += 1
        return "CodeBuddy $index"
    }

    private fun emit(text: String) {
        _messages.tryEmit(text)
    }

    companion object {
        private const val LOGIN_TIMEOUT_MS = 5 * 60 * 1000L

        fun factory(app: WalaviApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(app) }
        }
    }
}
