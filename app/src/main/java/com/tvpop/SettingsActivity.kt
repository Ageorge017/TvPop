package com.tvpop

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.tvpop.config.AppPreferences

class SettingsActivity : FragmentActivity() {
    private lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        preferences = AppPreferences(applicationContext)

        val httpSwitch = findViewById<Switch>(R.id.httpSwitch)
        val mqttSwitch = findViewById<Switch>(R.id.mqttSwitch)
        val brokerUrlInput = findViewById<EditText>(R.id.mqttBrokerUrlInput)
        val deviceIdInput = findViewById<EditText>(R.id.mqttDeviceIdInput)
        val usernameInput = findViewById<EditText>(R.id.mqttUsernameInput)
        val passwordInput = findViewById<EditText>(R.id.mqttPasswordInput)
        val keepAliveInput = findViewById<EditText>(R.id.mqttKeepAliveInput)
        val debounceInput = findViewById<EditText>(R.id.debounceMsInput)
        val cleanSessionSwitch = findViewById<Switch>(R.id.cleanSessionSwitch)
        val topicsText = findViewById<TextView>(R.id.topicsText)
        val saveButton = findViewById<Button>(R.id.saveButton)

        httpSwitch.isChecked = preferences.httpEnabled
        mqttSwitch.isChecked = preferences.mqttEnabled
        brokerUrlInput.setText(preferences.mqttBrokerUrl)
        deviceIdInput.setText(preferences.mqttDeviceId)
        usernameInput.setText(preferences.mqttUsername)
        passwordInput.setText(preferences.mqttPassword)
        keepAliveInput.setText(preferences.mqttKeepAliveSeconds.toString())
        debounceInput.setText(preferences.overlayDebounceMs.toString())
        cleanSessionSwitch.isChecked = preferences.mqttCleanSession
        topicsText.text = getString(
            R.string.settings_topics_format,
            preferences.mqttAllTopic,
            preferences.mqttDeviceTopic,
            preferences.mqttCancelAllTopic,
            preferences.mqttCancelDeviceTopic
        )

        saveButton.setOnClickListener {
            val keepAliveSeconds = keepAliveInput.text.toString().toIntOrNull() ?: 30
            val debounceMs = debounceInput.text.toString().toIntOrNull() ?: 1000
            preferences.save(
                httpEnabled = httpSwitch.isChecked,
                mqttEnabled = mqttSwitch.isChecked,
                mqttBrokerUrl = brokerUrlInput.text.toString(),
                mqttDeviceId = deviceIdInput.text.toString(),
                mqttUsername = usernameInput.text.toString(),
                mqttPassword = passwordInput.text.toString(),
                mqttKeepAliveSeconds = keepAliveSeconds,
                mqttCleanSession = cleanSessionSwitch.isChecked,
                overlayDebounceMs = debounceMs
            )
            restartService()
            finish()
        }
    }

    private fun restartService() {
        val intent = Intent(this, TvPopService::class.java)
        stopService(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
