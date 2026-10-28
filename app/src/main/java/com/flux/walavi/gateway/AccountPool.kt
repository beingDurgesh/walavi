package com.flux.walavi.gateway

import com.flux.walavi.data.Account
import java.util.concurrent.atomic.AtomicInteger

class AccountPool {
    private val cursor = AtomicInteger(0)

    fun candidates(accounts: List<Account>, now: Long = System.currentTimeMillis()): List<Account> {
        val usable = accounts.filter { it.enabled && it.accessToken.isNotBlank() }
        if (usable.isEmpty()) return emptyList()

        val ready = usable.filter { !it.needsLogin && it.cooldownUntil <= now }
        val cooling = usable
            .filter { !it.needsLogin && it.cooldownUntil > now }
            .sortedBy { it.cooldownUntil }
        val needsLogin = usable.filter { it.needsLogin }

        if (ready.isEmpty()) return cooling + needsLogin

        val start = Math.floorMod(cursor.getAndIncrement(), ready.size)
        val rotated = ready.drop(start) + ready.take(start)
        return rotated + cooling + needsLogin
    }

    fun reset() {
        cursor.set(0)
    }
}
