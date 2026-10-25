package com.stahij.jarvis

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("orb_enabled", false)) return
        if (!Settings.canDrawOverlays(context)) return

        val serviceIntent = Intent(context, OrbService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (_: Exception) {
            // Android/OEM background-start restrictions may require manual launch.
        }
    }
}
