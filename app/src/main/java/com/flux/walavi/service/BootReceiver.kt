package com.flux.walavi.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.flux.walavi.WalaviApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? WalaviApp ?: return
        if (!app.store.settings.value.startOnBoot) return
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, GatewayService::class.java),
            )
        }
    }
}
