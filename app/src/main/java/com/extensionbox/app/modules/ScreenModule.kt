package com.extensionbox.app.modules

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.extensionbox.app.Fmt
import com.extensionbox.app.Prefs
import com.extensionbox.app.R
import com.extensionbox.app.SystemAccess
import java.util.Calendar
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class ScreenModule : Module {
    private var ctx: Context? = null
    private var rcv: BroadcastReceiver? = null
    private var running = false
    private var screenOn = true

    private var onAccMs: Long = 0
    private var offAccMs: Long = 0
    private var periodStart: Long = 0
    private var periodStartLevel: Int = 0

    private var onDrain = 0f
    private var offDrain = 0f

    override fun key(): String = "screen"
    override fun name(): String = "Screen Time"
    override fun emoji(): String = "📱"
    override fun description(): String = "Screen on/off time, drain rates"
    override fun defaultEnabled(): Boolean = true
    override fun alive(): Boolean = running
    override fun priority(): Int = 20

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "scr_interval", 10000) } ?: 10000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        onAccMs = Prefs.getLong(ctx, "scr_on_acc", 0)
        offAccMs = 0
        onDrain = 0f
        offDrain = 0f
        periodStart = SystemClock.elapsedRealtime()
        periodStartLevel = getBatteryLevel()

        rcv = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val now = SystemClock.elapsedRealtime()
                val dt = now - periodStart
                val curLevel = getBatteryLevel()
                val drain = max(0, periodStartLevel - curLevel).toFloat()

                if (Intent.ACTION_SCREEN_OFF == intent.action) {
                    onAccMs += dt
                    onDrain += drain
                    screenOn = false
                } else if (Intent.ACTION_SCREEN_ON == intent.action) {
                    offAccMs += dt
                    offDrain += drain
                    screenOn = true
                }
                periodStart = now
                periodStartLevel = curLevel
                Prefs.setLong(ctx, "scr_on_acc", onAccMs)
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ctx.registerReceiver(rcv, f)
        running = true
    }

    override fun stop() {
        if (rcv != null && ctx != null) {
            try {
                ctx?.unregisterReceiver(rcv!!)
            } catch (ignored: Exception) {
            }
        }
        rcv = null
        running = false
    }

    override fun tick() {
        val c = ctx ?: return
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val lastDay = Prefs.getInt(c, "scr_last_day", -1)
        if (lastDay != -1 && lastDay != today) {
            Prefs.setLong(c, "scr_yesterday_on", onAccMs)
            onAccMs = 0
            offAccMs = 0
            onDrain = 0f
            offDrain = 0f
            periodStart = SystemClock.elapsedRealtime()
            periodStartLevel = getBatteryLevel()
            Prefs.setLong(c, "scr_on_acc", 0)
            Prefs.setBool(c, "scr_alert_fired", false)
        }
        Prefs.setInt(c, "scr_last_day", today)
    }

    private fun getBatteryLevel(): Int {
        return try {
            val bm = ctx?.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            0
        }
    }

    private fun getTotalOn(): Long {
        val now = SystemClock.elapsedRealtime()
        return onAccMs + if (screenOn) (now - periodStart) else 0
    }

    private fun getTotalOff(): Long {
        val now = SystemClock.elapsedRealtime()
        return offAccMs + if (!screenOn) (now - periodStart) else 0
    }

    override fun compact(): String = "On: ${Fmt.duration(getTotalOn())}"

    override fun detail(): String {
        val c = ctx ?: return "📱 No data"
        val on = getTotalOn()
        val off = getTotalOff()
        val sb = StringBuilder()
        sb.append("📱 Screen On: ${Fmt.duration(on)}")

        if (Prefs.getBool(c, "scr_show_drain", true)) {
            val curDrain = if (screenOn) max(0, periodStartLevel - getBatteryLevel()).toFloat() else 0f
            val totalOnDrain = onDrain + curDrain
            val onDrainStr = String.format(Locale.US, "%.1f", totalOnDrain)
            val offDrainStr = String.format(Locale.US, "%.1f", offDrain)
            
            sb.append(" • $onDrainStr%")
            sb.append("\n   Screen Off: ${Fmt.duration(off)} • $offDrainStr%")
            
            if (on > 60000) {
                val rateOn = totalOnDrain / (on / 3600000f)
                val rateStr = String.format(Locale.US, "%.1f", rateOn)
                sb.append("\n   Active: $rateStr%/h")
            }
        } else {
            sb.append("\n   Screen Off: ${Fmt.duration(off)}")
        }

        if (Prefs.getBool(c, "scr_show_yesterday", true)) {
            val yOn = Prefs.getLong(c, "scr_yesterday_on", 0)
            if (yOn > 0) {
                val diff = on - yOn
                val pct = (diff * 100 / yOn).toInt()
                val cmp = if (pct <= 0) "↓${abs(pct)}% 🎉" else "↑$pct%"
                sb.append("\n   Yesterday: ${Fmt.duration(yOn)} ($cmp)")
            }
        }
        return sb.toString()
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val d = LinkedHashMap<String, String>()
        d["screen.on_time"] = Fmt.duration(getTotalOn())
        d["screen.off_time"] = Fmt.duration(getTotalOff())
        return d
    }

    override fun checkAlerts(ctx: Context) {
        val limitMin = Prefs.getInt(ctx, "scr_time_limit", 0)
        if (limitMin <= 0) return
        val fired = Prefs.getBool(ctx, "scr_alert_fired", false)
        val onMs = getTotalOn()
        val limitMs = limitMin * 60000L

        if (onMs >= limitMs && !fired) {
            try {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(2004, NotificationCompat.Builder(ctx, "ebox_alerts")
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle("🔴 Screen Time Limit")
                    .setContentText("Screen on for ${Fmt.duration(onMs)}. Take a break!")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true).build())
            } catch (ignored: Exception) {
            }
            Prefs.setBool(ctx, "scr_alert_fired", true)
        }
    }
}
