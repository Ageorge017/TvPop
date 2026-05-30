package com.tvpop

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    private val logTag = "TvPop"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.statusText).text = getString(R.string.main_status_text)

        startTvPopService()

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

        moveTaskToBack(true)
    }

    private fun startTvPopService() {
        val intent = Intent(this, TvPopService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}