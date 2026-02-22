package com.extensionbox.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (Intent.ACTION_BOOT_COMPLETED == intent?.action) {
            if (Prefs.isRunning(ctx)) {
                val serviceIntent = Intent(ctx, MonitorService::class.java).apply {
                    action = MonitorService.ACTION_RESET
                }
                ContextCompat.startForegroundService(ctx, serviceIntent)
            }
        }
    }
}
