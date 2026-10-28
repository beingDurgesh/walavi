package com.flux.walavi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.flux.walavi.MainActivity
import com.flux.walavi.R
import com.flux.walavi.WalaviApp

class GatewayService : Service() {

    private lateinit var app: WalaviApp

    override fun onCreate() {
        super.onCreate()
        app = application as WalaviApp
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            app.controller.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(null))
        val result = app.controller.start()
        if (result.isFailure) {
            val message = result.exceptionOrNull()?.message ?: "Could not start"
            updateNotification("Failed: $message")
            Handler(Looper.getMainLooper()).postDelayed({
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }, 4_000L)
        } else {
            updateNotification(null)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        app.controller.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_gateway_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_gateway_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(error: String?): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, GatewayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val state = app.controller.state.value
        val text = error ?: if (state.running) {
            "${app.controller.lanUrl()} · ${app.store.accounts.value.size} accounts"
        } else {
            "Starting gateway…"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_walavi)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.notification_stop), stop)
            .setOngoing(error == null)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(error: String?) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(error))
    }

    companion object {
        const val ACTION_STOP = "com.flux.walavi.action.STOP"
        private const val CHANNEL_ID = "walavi_gateway"
        private const val NOTIFICATION_ID = 41
    }
}
