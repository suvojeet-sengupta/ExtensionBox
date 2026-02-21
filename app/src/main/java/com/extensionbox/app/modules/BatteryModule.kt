package com.extensionbox.app.modules

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.app.NotificationCompat
import com.extensionbox.app.Fmt
import com.extensionbox.app.Prefs
import com.extensionbox.app.R
import com.extensionbox.app.SystemAccess
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.math.abs

class BatteryModule : Module {

    private var ctx: Context? = null
    private var sys: SystemAccess? = null
    private var rcv: BroadcastReceiver? = null
    private var running = false

    private var level = 0
    private var temp = 0
    private var voltage = 0
    private var health = 0
    private var status = 0
    private var plugged = 0
    private var designCap = 4000
    private var currentMa = 0

    // Enhanced tier data
    private var actualCap = -1
    private var cycleCount = -1
    private var realHealthPct = -1
    private var technology: String? = null
    private var cpuTemp = Float.NaN

    override fun key(): String = "battery"
    override fun name(): String = "Battery"
    override fun emoji(): String = "🔋"
    override fun description(): String = "Current, power, temperature, health"
    override fun defaultEnabled(): Boolean = true
    override fun alive(): Boolean = running
    override fun priority(): Int = 10

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "bat_interval", 10000) } ?: 10000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        this.sys = sys
        designCap = sys.readDesignCapacity(ctx)

        rcv = object : BroadcastReceiver() {
            override fun onReceive(context: Context, i: Intent) {
                level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                voltage = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)
                status = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
                plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            }
        }

        ctx.registerReceiver(rcv, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val sticky = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        sticky?.let { i ->
            level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            voltage = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)
            status = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
            plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        }

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
        sys?.let { s ->
            ctx?.let { c ->
                currentMa = s.readBatteryCurrentMa(c)
                actualCap = s.readActualCapacity()
                cycleCount = s.readCycleCount()
                realHealthPct = s.readRealHealthPct(c)
                technology = s.readBatteryTechnology()
                cpuTemp = s.readCpuTemp()

                if (actualCap > 0) {
                    designCap = actualCap
                }
            }
        }
    }

    override fun compact(): String = "$level% ${timeLeft()}"

    override fun detail(): String {
        val ma = abs(currentMa)
        val w = ma * voltage / 1_000_000f
        val t = temp / 10f

        val sb = StringBuilder()
        sb.append("🔋 $level% • ${ma}mA (${String.format(Locale.US, "%.1f", w)}W) • ${Fmt.temp(t)}\n")

        if (sys?.isEnhanced() == true && realHealthPct > 0 && cycleCount >= 0) {
            val design = sys?.readDesignCapacity(ctx!!) ?: 0
            sb.append("   Health: $realHealthPct% (${if (actualCap > 0) actualCap else "—"}/$design mAh) • $cycleCount cycles\n")
        } else {
            sb.append("   Health: ${healthStr()} • ${String.format(Locale.US, "%.2f", voltage / 1000f)}V • ${statusStr()}\n")
        }

        if (sys?.isEnhanced() == true && technology != null) {
            sb.append("   ${String.format(Locale.US, "%.2f", voltage / 1000f)}V • $technology • ${statusStr()}\n")
        }

        sb.append("   ").append(timeLeft())
        if (isCharging()) {
            sb.append(" • ").append(chargeType())
        }
        return sb.toString()
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val d = LinkedHashMap<String, String>()
        val ma = abs(currentMa)
        val w = ma * voltage / 1_000_000f
        val t = temp / 10f

        d["battery.level"] = "$level%"
        d["battery.current"] = "$ma mA"
        d["battery.power"] = String.format(Locale.US, "%.1f W", w)
        d["battery.temp"] = Fmt.temp(t)
        d["battery.voltage"] = String.format(Locale.US, "%.2fV", voltage / 1000f)
        d["battery.health"] = healthStr()
        d["battery.status"] = statusStr()
        d["battery.time_left"] = timeLeft()

        if (isCharging()) {
            d["battery.charge_type"] = chargeType()
        }

        d["battery.design_cap"] = if (sys != null && ctx != null) "${sys?.readDesignCapacity(ctx!!)} mAh" else "$designCap mAh"
        d["battery.technology"] = technology ?: "—"
        d["battery.cycle_count"] = if (cycleCount >= 0) cycleCount.toString() else "—"
        d["battery.real_health_pct"] = if (realHealthPct > 0) "$realHealthPct%" else "—"
        d["battery.actual_cap"] = if (actualCap > 0) "$actualCap mAh" else "—"

        return d
    }

    override fun checkAlerts(ctx: Context) {
        val lowEnabled = Prefs.getBool(ctx, "bat_low_alert", true)
        val lowThresh = Prefs.getInt(ctx, "bat_low_thresh", 15)
        val lowFired = Prefs.getBool(ctx, "bat_low_fired", false)

        if (lowEnabled && level <= lowThresh && !lowFired && !isCharging()) {
            fireAlert(ctx, 2001, "🔴 Battery Low", "Battery at $level%. Charge your phone!")
            Prefs.setBool(ctx, "bat_low_fired", true)
        }
        if (lowFired && level > lowThresh + 5) {
            Prefs.setBool(ctx, "bat_low_fired", false)
        }

        val tempEnabled = Prefs.getBool(ctx, "bat_temp_alert", true)
        val tempThresh = Prefs.getInt(ctx, "bat_temp_thresh", 42)
        val tempFired = Prefs.getBool(ctx, "bat_temp_fired", false)
        val currentTemp = temp / 10f

        if (tempEnabled && currentTemp >= tempThresh.toFloat() && !tempFired) {
            fireAlert(ctx, 2002, "🔴 High Temperature", "Battery at ${Fmt.temp(currentTemp)}. Let your phone cool down!")
            Prefs.setBool(ctx, "bat_temp_fired", true)
        }
        if (tempFired && currentTemp < tempThresh - 3) {
            Prefs.setBool(ctx, "bat_temp_fired", false)
        }
    }

    fun getLevel(): Int = level

    private fun isCharging(): Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING

    private fun timeLeft(): String {
        val ma = abs(currentMa)
        if (ma < 5) return "—"
        val cap = if (actualCap > 0) actualCap else designCap
        return if (isCharging()) {
            val neededMah = (100 - level) / 100f * cap
            val hrs = neededMah / ma
            "⚡Full in ${formatHours(hrs)}"
        } else {
            val remMah = level / 100f * cap
            val hrs = remMah / ma
            "${formatHours(hrs)} left"
        }
    }

    private fun formatHours(hrs: Float): String {
        val m_hrs = if (hrs < 0) 0f else hrs
        val d = (m_hrs / 24).toInt()
        val h = (m_hrs % 24).toInt()
        val m = ((m_hrs * 60) % 60).toInt()
        return when {
            d > 0 -> String.format(Locale.US, "%dd %dh", d, h)
            h > 0 -> String.format(Locale.US, "%dh %dm", h, m)
            else -> String.format(Locale.US, "%dm", m)
        }
    }

    private fun chargeType(): String {
        val ma = abs(currentMa)
        return when {
            ma > 3000 -> "Rapid"
            ma > 1500 -> "Fast"
            ma > 500 -> "Normal"
            else -> "Slow"
        }
    }

    private fun healthStr(): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat!"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OverVolt"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        else -> "Unknown"
    }

    private fun statusStr(): String = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
        else -> "Unknown"
    }

    private fun fireAlert(c: Context, id: Int, title: String, body: String) {
        try {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val b = NotificationCompat.Builder(c, "ebox_alerts")
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
            nm.notify(id, b.build())
        } catch (ignored: Exception) {
        }
    }
}
