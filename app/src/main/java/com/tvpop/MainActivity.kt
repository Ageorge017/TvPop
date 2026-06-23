package com.tvpop

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.tvpop.config.AppPreferences
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    private val logTag = "TvPop"
    private val uiHandler = Handler(Looper.getMainLooper())
    private lateinit var preferences: AppPreferences

    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateProtocolStatus()
            uiHandler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        preferences = AppPreferences(applicationContext)

        findViewById<TextView>(R.id.statusText).text = getString(R.string.main_status_text)
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.refreshButton).setOnClickListener {
            updateProtocolStatus()
        }

        startTvPopService()
        updateProtocolStatus()

        if (!Settings.canDrawOverlays(this)) {
            val overlayIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )

            val appDetailsIntent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )

            try {
                when {
                    overlayIntent.resolveActivity(packageManager) != null -> {
                        startActivity(overlayIntent)
                    }

                    appDetailsIntent.resolveActivity(packageManager) != null -> {
                        startActivity(appDetailsIntent)
                    }

                    else -> {
                        Log.e(logTag, "No settings activity available to request overlay permission")
                    }
                }
            } catch (t: Throwable) {
                Log.e(logTag, "Failed to open settings for overlay permission", t)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateProtocolStatus()
        uiHandler.post(refreshRunnable)
    }

    override fun onPause() {
        uiHandler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    private fun startTvPopService() {
        val intent = Intent(this, TvPopService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updateProtocolStatus() {
        val httpLabel = if (preferences.httpEnabled) {
            getString(R.string.main_enabled)
        } else {
            getString(R.string.main_disabled)
        }
        val mqttLabel = if (preferences.mqttEnabled) {
            getString(R.string.main_enabled)
        } else {
            getString(R.string.main_disabled)
        }

        findViewById<TextView>(R.id.protocolStatusText).text = getString(
            R.string.main_protocol_status_format,
            httpLabel,
            mqttLabel
        )

        val runtime = MqttRuntimeStatusStore.snapshot()
        val stateLabel = when (runtime.state) {
            MqttConnectionState.DISABLED -> getString(R.string.mqtt_state_disabled)
            MqttConnectionState.CONNECTING -> getString(R.string.mqtt_state_connecting)
            MqttConnectionState.CONNECTED -> getString(R.string.mqtt_state_connected)
            MqttConnectionState.RECONNECTING -> getString(R.string.mqtt_state_reconnecting)
            MqttConnectionState.DISCONNECTED -> getString(R.string.mqtt_state_disconnected)
            MqttConnectionState.ERROR -> getString(R.string.mqtt_state_error)
        }

        val runtimeLabel = if (runtime.lastError.isNullOrBlank()) {
            getString(
                R.string.main_mqtt_runtime_format,
                stateLabel,
                runtime.reconnectAttempts
            )
        } else {
            getString(
                R.string.main_mqtt_runtime_error_format,
                stateLabel,
                runtime.reconnectAttempts,
                runtime.lastError
            )
        }
        findViewById<TextView>(R.id.mqttRuntimeStatusText).text = runtimeLabel
    }
}