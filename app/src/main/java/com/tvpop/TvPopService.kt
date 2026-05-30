package com.tvpop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TvPopService : Service() {
    private val logTag = "TvPop"
    private val channelId = "tvpop_service"
    private val notificationId = 1001

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var overlayManager: OverlayManager
    private lateinit var httpServer: HttpServer

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForeground()

        overlayManager = OverlayManager(applicationContext)
        httpServer = HttpServer(overlayManager)

        serviceScope.launch {
            try {
                httpServer.start()
            } catch (t: Throwable) {
                Log.e(logTag, "Failed to start HTTP server", t)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            overlayManager.cancel()
        } catch (t: Throwable) {
            Log.e(logTag, "Overlay shutdown failed", t)
        }

        try {
            httpServer.stop()
        } catch (t: Throwable) {
            Log.e(logTag, "HTTP server stop failed", t)
        }

        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()

        try {
            startForeground(notificationId, notification)
        } catch (t: Throwable) {
            Log.e(logTag, "startForeground failed", t)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
            description = "TvPop foreground service"
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }

        manager.createNotificationChannel(channel)
    }
}