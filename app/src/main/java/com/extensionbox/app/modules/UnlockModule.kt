package com.extensionbox.app.modules

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import com.extensionbox.app.Prefs
import com.extensionbox.app.R
import com.extensionbox.app.SystemAccess
import java.util.Calendar
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class UnlockModule : Module {

    private var ctx: Context? = null
    private var rcv: BroadcastReceiver? = null
    private var running = false
    private var count = 0
    private var lastUnlockTime: Long = 0

    override fun key(): String = "unlock"
    override fun name(): String = "Unlock Counter"
    override fun emoji(): String = "🔓"
    override fun description(): String = "Daily unlocks, detox tracking"
    override fun defaultEnabled(): Boolean = true
    override fun alive(): Boolean = running
    override fun priority(): Int = 60

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "ulk_interval", 10000) } ?: 10000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        count = Prefs.getInt(ctx, "ulk_today", 0)

        rcv = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (Intent.ACTION_USER_PRESENT == intent.action) {
                    val now = System.currentTimeMillis()
                    val debounceMs = Prefs.getInt(ctx, "ulk_debounce", 5000)
                    if (now - lastUnlockTime >= debounceMs) {
                        count++
                        Prefs.setInt(ctx, "ulk_today", count)
                        lastUnlockTime = now
                    }
                }
            }
        }
        ctx.registerReceiver(rcv, IntentFilter(Intent.ACTION_USER_PRESENT))
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
        count = Prefs.getInt(c, "ulk_today", 0)
        checkDayRollover()
    }

    private fun checkDayRollover() {
        val c = ctx ?: return
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val lastDay = Prefs.getInt(c, "ulk_last_day", -1)

        if (lastDay != -1 && lastDay != today) {
            Prefs.setInt(c, "ulk_yesterday", count)
            Prefs.setInt(c, "ulk_today", 0)
            Prefs.setBool(c, "ulk_alert_fired", false)
            count = 0
        }
        Prefs.setInt(c, "ulk_last_day", today)
    }

    override fun compact(): String = "🔓$count"

    override fun detail(): String {
        val c = ctx ?: return "🔓 No data"
        val yesterday = Prefs.getInt(c, "ulk_yesterday", 0)
        val limit = Prefs.getInt(c, "ulk_daily_limit", 0)

        val sb = StringBuilder()
        sb.append("🔓 Unlocked: $count times today")

        val cal = Calendar.getInstance()
        val hoursSinceMidnight = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
        if (hoursSinceMidnight > 0.1f) {
            val rate = count / hoursSinceMidnight
            sb.append(String.format(Locale.US, " (%.1f/h)", rate))
        }

        if (yesterday > 0) {
            val diff = count - yesterday
            val cmp = if (diff <= 0) "↓${abs(diff)} 🎉" else "↑$diff"
            sb.append("\n   Yesterday: $yesterday ($cmp)")
        }

        if (limit > 0) {
            val rem = max(0, limit - count)
            sb.append("\n   Limit: $limit ($rem remaining)")
        }

        return sb.toString()
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val d = LinkedHashMap<String, String>()
        d["unlock.today"] = count.toString()

        ctx?.let { c ->
            val yesterday = Prefs.getInt(c, "ulk_yesterday", 0)
            d["unlock.yesterday"] = yesterday.toString()
            if (yesterday > 0) {
                val diff = count - yesterday
                d["unlock.vs_yesterday"] = if (diff <= 0) "↓${abs(diff)} 🎉" else "↑$diff"
            }
            val limit = Prefs.getInt(c, "ulk_daily_limit", 0)
            d["unlock.limit"] = if (limit > 0) limit.toString() else "Off"
            if (limit > 0) {
                d["unlock.remaining"] = max(0, limit - count).toString()
            }
        }
        return d
    }

    override fun checkAlerts(ctx: Context) {
        val limit = Prefs.getInt(ctx, "ulk_daily_limit", 0)
        val alertEnabled = Prefs.getBool(ctx, "ulk_limit_alert", true)
        val alertFired = Prefs.getBool(ctx, "ulk_alert_fired", false)

        if (limit > 0 && alertEnabled && count >= limit && !alertFired) {
            try {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val b = NotificationCompat.Builder(ctx, "ebox_alerts")
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle("🔴 Unlock Limit Reached")
                    .setContentText("You've unlocked $count times. Limit: $limit. Take a break!")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                nm.notify(2006, b.build())
            } catch (ignored: Exception) {
            }
            Prefs.setBool(ctx, "ulk_alert_fired", true)
        }
    }
}
