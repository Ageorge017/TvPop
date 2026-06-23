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
import com.tvpop.config.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class TvPopService : Service() {
    private val logTag = "TvPop"
    private val channelId = "tvpop_service"
    private val notificationId = 1001

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var preferences: AppPreferences
    private lateinit var overlayManager: OverlayManager
    private var httpServer: HttpServer? = null
    private var mqttTransport: MqttTransport? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForeground()

        preferences = AppPreferences(applicationContext)
        overlayManager = OverlayManager(applicationContext)

        serviceScope.launch {
            if (preferences.httpEnabled) {
                try {
                    httpServer = HttpServer(overlayManager, AppPreferences.HTTP_PORT).also { it.start() }
                    Log.i(logTag, "HTTP server started on port ${AppPreferences.HTTP_PORT}")
                } catch (t: Throwable) {
                    Log.e(logTag, "Failed to start HTTP server", t)
                }
            } else {
                Log.i(logTag, "HTTP server disabled by settings")
            }

            if (preferences.mqttEnabled) {
                try {
                    mqttTransport = MqttTransport(applicationContext, overlayManager, preferences, serviceScope).also {
                        it.start()
                    }
                    Log.i(logTag, "MQTT transport started")
                } catch (t: Throwable) {
                    Log.e(logTag, "Failed to start MQTT transport", t)
                }
            } else {
                Log.i(logTag, "MQTT transport disabled by settings")
                MqttRuntimeStatusStore.setState(
                    state = MqttConnectionState.DISABLED,
                    reconnectAttempts = 0,
                    subscribedTopics = emptyList()
                )
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

        httpServer?.let {
            try {
                it.stop()
            } catch (t: Throwable) {
                Log.e(logTag, "HTTP server stop failed", t)
            }
        }
        httpServer = null

        mqttTransport?.let {
            try {
                runBlocking(Dispatchers.IO) {
                    it.stop()
                }
            } catch (t: Throwable) {
                Log.e(logTag, "MQTT transport stop failed", t)
            }
        }
        mqttTransport = null

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