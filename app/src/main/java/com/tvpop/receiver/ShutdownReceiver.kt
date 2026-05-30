package com.tvpop.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tvpop.TvPopService

class ShutdownReceiver : BroadcastReceiver() {
    private val logTag = "TvPop"

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_SHUTDOWN) {
            return
        }

        try {
            context.stopService(Intent(context, TvPopService::class.java))
        } catch (t: Throwable) {
            Log.e(logTag, "Failed to stop service on shutdown", t)
        }
    }
}