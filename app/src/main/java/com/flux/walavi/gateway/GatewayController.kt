package com.flux.walavi.gateway

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.flux.walavi.WalaviApp
import com.flux.walavi.data.Account
import com.flux.walavi.data.WalaviStore
import com.flux.walavi.data.RequestLog
import com.flux.walavi.data.TestResult
import com.flux.walavi.service.GatewayService
import com.flux.walavi.util.Net
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class GatewayState(
    val running: Boolean = false,
    val port: Int = 8787,
    val startedAt: Long = 0L,
    val activeRequests: Int = 0,
    val totalRequests: Long = 0L,
    val successRequests: Long = 0L,
    val failedRequests: Long = 0L,
    val lastError: String? = null,
    val lanAddress: String = "",
    val lanUrl: String = "",
)

class GatewayController(
    private val app: WalaviApp,
    private val store: WalaviStore,
) {
    private val _state = MutableStateFlow(GatewayState(port = store.settings.value.port))
    val state: StateFlow<GatewayState> = _state.asStateFlow()

    private val pool = AccountPool()
    private val auth = CodeBuddyAuth()
    private var server: GatewayServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    fun startService(context: Context) {
        val intent = Intent(context, GatewayService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopService(context: Context) {
        val intent = Intent(context, GatewayService::class.java).setAction(GatewayService.ACTION_STOP)
        runCatching { context.startService(intent) }
    }

    @Synchronized
    fun start(): Result<Unit> {
        server?.let { return Result.success(Unit) }
        val settings = store.settings.value
        return try {
            val instance = GatewayServer(
                port = settings.port,
                store = store,
                pool = pool,
                auth = auth,
                onLog = ::recordLog,
                onActiveRequests = { count ->
                    _state.update { it.copy(activeRequests = count) }
                },
            )
            instance.start()
            val boundPort = instance.localPort()
            val host = resolveLanHost()
            server = instance
            acquireLocks()
            _state.update {
                it.copy(
                    running = true,
                    port = boundPort,
                    startedAt = System.currentTimeMillis(),
                    lastError = null,
                    lanAddress = host,
                    lanUrl = "http://$host:$boundPort/v1",
                )
            }
            Result.success(Unit)
        } catch (error: Exception) {
            _state.update {
                it.copy(running = false, lastError = error.message ?: "Could not bind port ${settings.port}")
            }
            Result.failure(error)
        }
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
        releaseLocks()
        _state.update { it.copy(running = false, activeRequests = 0) }
    }

    suspend fun testAccount(account: Account): TestResult {
        val settings = store.settings.value
        val result = auth.ping(settings.baseUrl, account.accessToken, settings.defaultModel, account.userId)
        store.updateAccount(account.id) { current ->
            val now = System.currentTimeMillis()
            val cooldown = when {
                result.ok -> 0L
                result.status == 401 -> now + FailurePolicy.AUTH_COOLDOWN_MS
                result.status == 403 || result.status == 429 ->
                    now + FailurePolicy.cooldownMs(result.status, null)
                else -> current.cooldownUntil
            }
            current.copy(
                successCount = current.successCount + if (result.ok) 1 else 0,
                failureCount = current.failureCount + if (result.ok) 0 else 1,
                lastUsedAt = now,
                needsLogin = if (result.ok) false else current.needsLogin || result.status == 401,
                cooldownUntil = cooldown,
                lastError = if (result.ok) null else result.message,
            )
        }
        return result
    }

    suspend fun refreshModels(): Int {
        val settings = store.settings.value
        val account = store.accounts.value.firstOrNull {
            it.enabled && !it.needsLogin && it.accessToken.isNotBlank()
        } ?: return 0
        var token = account.accessToken
        val now = System.currentTimeMillis()
        if (account.expiresAt > 0L && account.expiresAt <= now + 60_000L) {
            auth.refresh(settings.baseUrl, account.refreshToken, account.userId)?.let { refreshed ->
                token = refreshed.accessToken
                store.updateAccount(account.id) {
                    it.copy(
                        accessToken = refreshed.accessToken,
                        refreshToken = refreshed.refreshToken ?: it.refreshToken,
                        expiresAt = if (refreshed.expiresAt > 0) refreshed.expiresAt else it.expiresAt,
                    )
                }
            }
        }
        val ids = auth.fetchModels(settings.baseUrl, token, account.userId) ?: return 0
        val merged = (settings.knownModels + ids).distinct()
        store.updateSettings { it.copy(knownModels = merged) }
        return ids.size
    }

    fun lanHost(): String = resolveLanHost()

    fun lanUrl(): String {
        val state = _state.value
        if (state.lanUrl.isNotBlank()) return state.lanUrl
        return "http://${lanHost()}:${state.port}/v1"
    }

    fun refreshEndpoint() {
        val host = resolveLanHost(force = true)
        val port = _state.value.port
        _state.update {
            it.copy(lanAddress = host, lanUrl = "http://$host:$port/v1")
        }
    }

    private fun resolveLanHost(force: Boolean = false): String {
        val saved = if (force) "" else store.settings.value.lanAddress
        val picked = Net.pickLanAddress(saved, activeWifiInterface())
        if (picked.isNullOrBlank()) return "127.0.0.1"
        if (picked != store.settings.value.lanAddress) {
            store.updateSettings { it.copy(lanAddress = picked) }
        }
        return picked
    }

    private fun activeWifiInterface(): String? = runCatching {
        val manager = app.getSystemService(ConnectivityManager::class.java)
            ?: return@runCatching null
        val network = manager.activeNetwork ?: return@runCatching null
        val capabilities = manager.getNetworkCapabilities(network)
            ?: return@runCatching null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return@runCatching null
        }
        manager.getLinkProperties(network)?.interfaceName
    }.getOrNull()

    private fun recordLog(log: RequestLog) {
        store.addLog(log)
        val success = log.status in 200..299
        _state.update {
            it.copy(
                totalRequests = it.totalRequests + 1,
                successRequests = it.successRequests + if (success) 1 else 0,
                failedRequests = it.failedRequests + if (success) 0 else 1,
            )
        }
    }

    private fun acquireLocks() {
        if (!store.settings.value.keepAwake) return
        runCatching {
            val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "walavi:gateway").apply {
                setReferenceCounted(false)
                acquire()
            }
            val wifi = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "walavi:wifi").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        wifiLock = null
    }
}
