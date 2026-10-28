package com.flux.walavi.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.UUID

class WalaviStore(context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    private val stable: SharedPreferences =
        context.applicationContext.getSharedPreferences("walavi_stable", Context.MODE_PRIVATE)

    private val _accounts = MutableStateFlow(loadAccounts())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<WalaviSettings> = _settings.asStateFlow()

    private val _logs = MutableStateFlow(loadLogs())
    val logs: StateFlow<List<RequestLog>> = _logs.asStateFlow()

    private val _usage = MutableStateFlow(loadUsage(_logs.value))
    val usage: StateFlow<LifetimeUsage> = _usage.asStateFlow()

    private fun createPrefs(context: Context): SharedPreferences {
        fun build(): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                "walavi_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
        return try {
            build()
        } catch (_: Throwable) {
            context.deleteSharedPreferences("walavi_secure")
            build()
        }
    }

    private fun loadAccounts(): List<Account> = runCatching {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        json.decodeFromString(ListSerializer(Account.serializer()), raw)
    }.getOrDefault(emptyList())

    private fun loadSettings(): WalaviSettings {
        val loaded = runCatching {
            val raw = prefs.getString(KEY_SETTINGS, null) ?: return@runCatching WalaviSettings()
            json.decodeFromString(WalaviSettings.serializer(), raw)
        }.getOrDefault(WalaviSettings())

        val stableKey = stable.getString(KEY_STABLE_API_KEY, null)?.takeIf { it.isNotBlank() }
        val stablePort = stable.getInt(KEY_STABLE_PORT, 0).takeIf { it in 1024..65535 }
        val stableLan = stable.getString(KEY_STABLE_LAN, null)?.takeIf { it.isNotBlank() }

        val resolved = loaded.copy(
            apiKey = stableKey ?: loaded.apiKey.takeIf { it.isNotBlank() } ?: newApiKey(),
            port = stablePort ?: loaded.port,
            lanAddress = stableLan ?: loaded.lanAddress,
        )
        saveStable(resolved)
        if (resolved != loaded) {
            prefs.edit()
                .putString(KEY_SETTINGS, json.encodeToString(WalaviSettings.serializer(), resolved))
                .commit()
        }
        return resolved
    }

    private fun loadLogs(): List<RequestLog> = runCatching {
        val raw = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        json.decodeFromString(ListSerializer(RequestLog.serializer()), raw)
    }.getOrDefault(emptyList())

    private fun loadUsage(logs: List<RequestLog>): LifetimeUsage {
        val stored = runCatching {
            stable.getString(KEY_USAGE, null)
                ?.let { json.decodeFromString(LifetimeUsage.serializer(), it) }
        }.getOrNull()
        if (stored != null) return stored

        val seeded = logs.fold(LifetimeUsage()) { total, log -> total.plus(log) }
        stable.edit()
            .putString(KEY_USAGE, json.encodeToString(LifetimeUsage.serializer(), seeded))
            .apply()
        return seeded
    }

    private fun persistAccounts(list: List<Account>) {
        prefs.edit()
            .putString(KEY_ACCOUNTS, json.encodeToString(ListSerializer(Account.serializer()), list))
            .apply()
        _accounts.value = list
    }

    private fun persistSettings(value: WalaviSettings) {
        prefs.edit()
            .putString(KEY_SETTINGS, json.encodeToString(WalaviSettings.serializer(), value))
            .commit()
        saveStable(value)
        _settings.value = value
    }

    private fun saveStable(value: WalaviSettings) {
        stable.edit()
            .putString(KEY_STABLE_API_KEY, value.apiKey)
            .putInt(KEY_STABLE_PORT, value.port)
            .putString(KEY_STABLE_LAN, value.lanAddress)
            .commit()
    }

    @Synchronized
    fun upsertAccount(account: Account) {
        val list = _accounts.value.toMutableList()
        val index = list.indexOfFirst { it.id == account.id }
        if (index >= 0) list[index] = account else list += account
        persistAccounts(list)
    }

    @Synchronized
    fun updateAccount(id: String, transform: (Account) -> Account): Account? {
        val list = _accounts.value.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return null
        val updated = transform(list[index])
        list[index] = updated
        persistAccounts(list)
        return updated
    }

    @Synchronized
    fun deleteAccount(id: String) {
        persistAccounts(_accounts.value.filterNot { it.id == id })
    }

    @Synchronized
    fun moveAccount(id: String, delta: Int) {
        val list = _accounts.value.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from == to) return
        val item = list.removeAt(from)
        list.add(to, item)
        persistAccounts(list)
    }

    @Synchronized
    fun clearAccounts() {
        persistAccounts(emptyList())
    }

    @Synchronized
    fun updateSettings(transform: (WalaviSettings) -> WalaviSettings): WalaviSettings {
        val updated = transform(_settings.value)
        persistSettings(if (updated.apiKey.isBlank()) updated.copy(apiKey = newApiKey()) else updated)
        return _settings.value
    }

    @Synchronized
    fun regenerateApiKey(): String {
        val key = newApiKey()
        persistSettings(_settings.value.copy(apiKey = key))
        return key
    }

    @Synchronized
    fun addLog(log: RequestLog) {
        val list = (listOf(log) + _logs.value).take(MAX_LOGS)
        prefs.edit()
            .putString(KEY_LOGS, json.encodeToString(ListSerializer(RequestLog.serializer()), list))
            .apply()
        _logs.value = list

        val usage = _usage.value.plus(log)
        stable.edit()
            .putString(KEY_USAGE, json.encodeToString(LifetimeUsage.serializer(), usage))
            .apply()
        _usage.value = usage
    }

    @Synchronized
    fun clearLogs() {
        prefs.edit().remove(KEY_LOGS).apply()
        _logs.value = emptyList()
    }

    fun newAccountId(): String = UUID.randomUUID().toString()

    private fun newApiKey(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        val encoded = Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        return "sk-walavi-$encoded"
    }

    private companion object {
        const val KEY_ACCOUNTS = "accounts_v1"
        const val KEY_SETTINGS = "settings_v1"
        const val KEY_LOGS = "logs_v1"
        const val KEY_STABLE_API_KEY = "api_key"
        const val KEY_STABLE_PORT = "port"
        const val KEY_STABLE_LAN = "lan_address"
        const val KEY_USAGE = "usage_totals"
        const val MAX_LOGS = 200
    }
}
