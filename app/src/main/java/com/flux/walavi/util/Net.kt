package com.flux.walavi.util

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

data class LanAddress(val interfaceName: String, val address: String)

object Net {

    private val VIRTUAL_PREFIXES = listOf(
        "rmnet", "ccmni", "pdp", "tun", "tap", "ppp", "dummy", "clat",
        "p2p", "sit", "ip6", "v4-", "lo",
    )

    fun lanAddresses(): List<LanAddress> {
        val result = mutableListOf<LanAddress>()
        try {
            for (iface in NetworkInterface.getNetworkInterfaces() ?: return emptyList()) {
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase(Locale.US)
                if (VIRTUAL_PREFIXES.any { name.startsWith(it) }) continue
                for (address in iface.inetAddresses) {
                    if (address is Inet4Address && address.isSiteLocalAddress) {
                        val host = address.hostAddress ?: continue
                        result += LanAddress(iface.name, host)
                    }
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return result
            .distinctBy { it.address }
            .sortedWith(compareBy({ rank(it.interfaceName) }, { it.interfaceName }, { ipValue(it.address) }))
    }

    fun pickLanAddress(
        saved: String? = null,
        preferredInterface: String? = null,
        addresses: List<LanAddress> = lanAddresses(),
    ): String? {
        val all = addresses.sortedWith(
            compareBy({ rank(it.interfaceName) }, { it.interfaceName }, { ipValue(it.address) }),
        )
        if (!saved.isNullOrBlank() && all.any { it.address == saved }) return saved
        if (!preferredInterface.isNullOrBlank()) {
            all.firstOrNull { it.interfaceName.equals(preferredInterface, ignoreCase = true) }
                ?.let { return it.address }
        }
        return all.firstOrNull()?.address
    }

    private fun rank(name: String): Int {
        val lower = name.lowercase(Locale.US)
        return when {
            lower == "wlan0" -> 0
            lower.startsWith("wlan") -> 1
            lower.startsWith("eth") -> 2
            lower.startsWith("ap") || lower.startsWith("swlan") || lower.startsWith("softap") -> 3
            else -> 4
        }
    }

    private fun ipValue(address: String): Long =
        address.split('.').fold(0L) { acc, part -> (acc shl 8) or (part.toLongOrNull() ?: 0L) }

    fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0L) return "never"
        val delta = (now - timestamp).coerceAtLeast(0L)
        return when {
            delta < 5_000L -> "now"
            delta < 60_000L -> "${delta / 1_000}s ago"
            delta < 3_600_000L -> "${delta / 60_000}m ago"
            delta < 86_400_000L -> "${delta / 3_600_000}h ago"
            else -> "${delta / 86_400_000}d ago"
        }
    }

    fun duration(ms: Long): String = when {
        ms < 1_000L -> "${ms}ms"
        ms < 60_000L -> String.format(Locale.US, "%.1fs", ms / 1000.0)
        else -> "${ms / 60_000}m"
    }

    fun maskToken(token: String): String =
        if (token.length <= 8) "••••••" else "••••" + token.takeLast(6)
}
