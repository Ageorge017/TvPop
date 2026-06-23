package com.tvpop.config

import android.content.Context
import android.provider.Settings
import java.util.UUID

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    val httpEnabled: Boolean
        get() = prefs.getBoolean(KEY_HTTP_ENABLED, true)

    val mqttEnabled: Boolean
        get() = prefs.getBoolean(KEY_MQTT_ENABLED, false)

    val mqttBrokerUrl: String
        get() = prefs.getString(KEY_MQTT_BROKER_URL, "")?.trim().orEmpty()

    val mqttDeviceId: String
        get() {
            val saved = prefs.getString(KEY_MQTT_DEVICE_ID, "")?.trim().orEmpty()
            if (saved.isNotBlank()) {
                return saved
            }

            val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
                ?.trim()
                .orEmpty()
            val generated = if (androidId.isNotBlank()) {
                androidId
            } else {
                UUID.randomUUID().toString()
            }

            prefs.edit().putString(KEY_MQTT_DEVICE_ID, generated).apply()
            return generated
        }

    val mqttUsername: String
        get() = prefs.getString(KEY_MQTT_USERNAME, "")?.trim().orEmpty()

    val mqttPassword: String
        get() = prefs.getString(KEY_MQTT_PASSWORD, "") ?: ""

    val mqttKeepAliveSeconds: Int
        get() {
            val value = prefs.getInt(KEY_MQTT_KEEPALIVE_SECONDS, DEFAULT_KEEPALIVE_SECONDS)
            return value.coerceIn(MIN_KEEPALIVE_SECONDS, MAX_KEEPALIVE_SECONDS)
        }

    val mqttCleanSession: Boolean
        get() = prefs.getBoolean(KEY_MQTT_CLEAN_SESSION, true)

    val mqttAllTopic: String
        get() = "tvpop/notifications/all"

    val mqttDeviceTopic: String
        get() = "tvpop/notifications/${mqttDeviceId}"

    val mqttStatusTopic: String
        get() = "tvpop/status/${mqttDeviceId}"

    val mqttCancelAllTopic: String
        get() = "tvpop/notifications/cancel/all"

    val mqttCancelDeviceTopic: String
        get() = "tvpop/notifications/cancel/${mqttDeviceId}"

    val overlayDebounceMs: Int
        get() {
            val value = prefs.getInt(KEY_OVERLAY_DEBOUNCE_MS, DEFAULT_OVERLAY_DEBOUNCE_MS)
            return value.coerceIn(MIN_OVERLAY_DEBOUNCE_MS, MAX_OVERLAY_DEBOUNCE_MS)
        }

    fun save(
        httpEnabled: Boolean,
        mqttEnabled: Boolean,
        mqttBrokerUrl: String,
        mqttDeviceId: String,
        mqttUsername: String,
        mqttPassword: String,
        mqttKeepAliveSeconds: Int,
        mqttCleanSession: Boolean,
        overlayDebounceMs: Int
    ) {
        prefs.edit()
            .putBoolean(KEY_HTTP_ENABLED, httpEnabled)
            .putBoolean(KEY_MQTT_ENABLED, mqttEnabled)
            .putString(KEY_MQTT_BROKER_URL, mqttBrokerUrl.trim())
            .putString(KEY_MQTT_DEVICE_ID, mqttDeviceId.trim())
            .putString(KEY_MQTT_USERNAME, mqttUsername.trim())
            .putString(KEY_MQTT_PASSWORD, mqttPassword)
            .putInt(
                KEY_MQTT_KEEPALIVE_SECONDS,
                mqttKeepAliveSeconds.coerceIn(MIN_KEEPALIVE_SECONDS, MAX_KEEPALIVE_SECONDS)
            )
            .putBoolean(KEY_MQTT_CLEAN_SESSION, mqttCleanSession)
            .putInt(
                KEY_OVERLAY_DEBOUNCE_MS,
                overlayDebounceMs.coerceIn(MIN_OVERLAY_DEBOUNCE_MS, MAX_OVERLAY_DEBOUNCE_MS)
            )
            .apply()
    }

    companion object {
        const val HTTP_PORT = 7979

        private const val PREFS_NAME = "tvpop_prefs"
        private const val KEY_HTTP_ENABLED = "http_enabled"
        private const val KEY_MQTT_ENABLED = "mqtt_enabled"
        private const val KEY_MQTT_BROKER_URL = "mqtt_broker_url"
        private const val KEY_MQTT_DEVICE_ID = "mqtt_device_id"
        private const val KEY_MQTT_USERNAME = "mqtt_username"
        private const val KEY_MQTT_PASSWORD = "mqtt_password"
        private const val KEY_MQTT_KEEPALIVE_SECONDS = "mqtt_keepalive_seconds"
        private const val KEY_MQTT_CLEAN_SESSION = "mqtt_clean_session"
        private const val KEY_OVERLAY_DEBOUNCE_MS = "overlay_debounce_ms"

        private const val DEFAULT_KEEPALIVE_SECONDS = 30
        private const val MIN_KEEPALIVE_SECONDS = 10
        private const val MAX_KEEPALIVE_SECONDS = 300

        private const val DEFAULT_OVERLAY_DEBOUNCE_MS = 1000
        private const val MIN_OVERLAY_DEBOUNCE_MS = 0
        private const val MAX_OVERLAY_DEBOUNCE_MS = 2000
    }
}
