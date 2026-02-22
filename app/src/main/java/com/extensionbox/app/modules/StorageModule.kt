package com.extensionbox.app.modules

import android.app.NotificationManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.core.app.NotificationCompat
import com.extensionbox.app.Fmt
import com.extensionbox.app.Prefs
import com.extensionbox.app.R
import com.extensionbox.app.SystemAccess
import java.util.LinkedHashMap
import java.util.Locale

class StorageModule : Module {

    private var ctx: Context? = null
    private var running = false
    private var intUsed = 0L
    private var intFree = 0L
    private var intTotal = 0L

    override fun key(): String = "storage"
    override fun name(): String = "Storage"
    override fun emoji(): String = "💾"
    override fun description(): String = "Internal storage usage"
    override fun defaultEnabled(): Boolean = false
    override fun alive(): Boolean = running
    override fun priority(): Int = 85

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "sto_interval", 300000) } ?: 300000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun tick() {
        try {
            val sf = StatFs(Environment.getDataDirectory().path)
            intTotal = sf.totalBytes
            intFree = sf.availableBytes
            intUsed = intTotal - intFree
        } catch (ignored: Exception) {
        }
    }

    override fun compact(): String = "💾${Fmt.bytes(intUsed)}/${Fmt.bytes(intTotal)}"

    override fun detail(): String {
        val pct = if (intTotal > 0) intUsed * 100f / intTotal else 0f
        return "💾 Internal: ${Fmt.bytes(intUsed)} / ${Fmt.bytes(intTotal)} (${String.format(Locale.US, "%.1f%%", pct)})\n" +
               "   Free: ${Fmt.bytes(intFree)}"
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val pct = if (intTotal > 0) intUsed * 100f / intTotal else 0f
        val d = LinkedHashMap<String, String>()
        d["storage.used"] = Fmt.bytes(intUsed)
        d["storage.free"] = Fmt.bytes(intFree)
        d["storage.total"] = Fmt.bytes(intTotal)
        d["storage.pct"] = String.format(Locale.US, "%.1f%%", pct)
        return d
    }

    override fun checkAlerts(ctx: Context) {
        val on = Prefs.getBool(ctx, "sto_low_alert", true)
        val threshMb = Prefs.getInt(ctx, "sto_low_thresh_mb", 1000)
        val fired = Prefs.getBool(ctx, "sto_low_alert_fired", false)
        val threshBytes = threshMb * 1024L * 1024L

        if (on && intFree < threshBytes && intFree > 0 && !fired) {
            try {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(2007, NotificationCompat.Builder(ctx, "ebox_alerts")
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle("🔴 Low Storage")
                    .setContentText("Only " + Fmt.bytes(intFree) + " remaining")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true).build())
            } catch (ignored: Exception) {
            }
            Prefs.setBool(ctx, "sto_low_alert_fired", true)
        }
        if (fired && intFree > threshBytes + 500L * 1024 * 1024) {
            Prefs.setBool(ctx, "sto_low_alert_fired", false)
        }
    }
}
