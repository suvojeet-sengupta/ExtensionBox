package com.extensionbox.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import com.extensionbox.app.modules.*
import com.extensionbox.app.widgets.ModuleWidgetProvider
import java.util.Calendar
import kotlin.math.abs

class MonitorService : Service() {

    companion object {
        const val ACTION_STOP = "com.extensionbox.STOP"
        private const val MONITOR_CH = "ebox_monitor"
        private const val ALERT_CH = "ebox_alerts"
        private const val NOTIF_ID = 1001
        private const val NIGHT_SUMMARY_ID = 2099

        private var instance: MonitorService? = null
        private val moduleData = HashMap<String, LinkedHashMap<String, String>>()

        fun getInstance(): MonitorService? = instance
        fun getModuleData(key: String): LinkedHashMap<String, String>? = moduleData[key]
    }

    private lateinit var sysAccess: SystemAccess
    private lateinit var modules: List<Module>
    private val lastTickTime = HashMap<String, Long>()
    private lateinit var handler: Handler
    private lateinit var tickRunnable: Runnable
    private var nightSummarySent = false

    fun getFapModule(): FapCounterModule? {
        return if (::modules.isInitialized) modules.filterIsInstance<FapCounterModule>().firstOrNull() else null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannels()

        sysAccess = SystemAccess(this)
        modules = listOf(
            BatteryModule(),
            CpuRamModule(),
            ScreenModule(),
            SleepModule(),
            NetworkModule(),
            DataUsageModule(),
            UnlockModule(),
            StorageModule(),
            ConnectionModule(),
            UptimeModule(),
            StepModule(),
            SpeedTestModule(),
            FapCounterModule()
        )

        startForeground(NOTIF_ID, buildNotification())
        syncModules()

        handler = Handler(Looper.getMainLooper())
        tickRunnable = Runnable {
            doTickCycle()
            scheduleNextTick()
        }
        scheduleNextTick()
        Prefs.setRunning(this, true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAll()
            Prefs.setRunning(this, false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopAll()
        if (::handler.isInitialized) handler.removeCallbacksAndMessages(null)
        Prefs.setRunning(this, false)
        moduleData.clear()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun syncModules() {
        if (!::modules.isInitialized) return
        for (m in modules) {
            val shouldRun = Prefs.isModuleEnabled(this, m.key(), m.defaultEnabled())
            if (shouldRun && !m.alive()) {
                m.start(this, sysAccess)
                lastTickTime[m.key()] = 0L
            } else if (!shouldRun && m.alive()) {
                m.stop()
                moduleData.remove(m.key())
                lastTickTime.remove(m.key())
            }
        }
    }

    private fun doTickCycle() {
        checkRollover()
        syncModules()
        val now = SystemClock.elapsedRealtime()
        var changed = false

        if (::modules.isInitialized) {
            for (m in modules) {
                if (!m.alive()) continue
                val last = lastTickTime[m.key()] ?: 0L
                if (now - last >= m.tickIntervalMs()) {
                    m.tick()
                    m.checkAlerts(this)
                    lastTickTime[m.key()] = now
                    moduleData[m.key()] = m.dataPoints()
                    changed = true
                }
            }
        }
        if (changed) {
            updateNotification()
            try {
                ModuleWidgetProvider.updateAllWidgets(this)
            } catch (ignored: Exception) {
            }
        }
        checkNightSummary()
    }

    private fun checkRollover() {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_YEAR)
        val thisMonth = cal.get(Calendar.MONTH)
        val thisYear = cal.get(Calendar.YEAR)

        val lastDay = Prefs.getInt(this, "rollover_day", -1)
        val lastMonth = Prefs.getInt(this, "rollover_month", -1)
        val lastYear = Prefs.getInt(this, "rollover_year", -1)

        if (lastDay == -1) {
            Prefs.setInt(this, "rollover_day", today)
            Prefs.setInt(this, "rollover_month", thisMonth)
            Prefs.setInt(this, "rollover_year", thisYear)
            return
        }

        if (today != lastDay || thisYear != lastYear) {
            doDayRollover()
            Prefs.setInt(this, "rollover_day", today)
            Prefs.setInt(this, "rollover_year", thisYear)
        }

        if (thisMonth != lastMonth || thisYear != lastYear) {
            doMonthRollover()
            Prefs.setInt(this, "rollover_month", thisMonth)
        }
    }

    private fun doDayRollover() {
        Prefs.setInt(this, "ulk_yesterday", Prefs.getInt(this, "ulk_today", 0))
        Prefs.setLong(this, "stp_yesterday", Prefs.getLong(this, "stp_today", 0))
        Prefs.setLong(this, "scr_yesterday_on", Prefs.getLong(this, "scr_on_acc", 0))
        Prefs.setInt(this, "fap_yesterday", Prefs.getInt(this, "fap_today", 0))

        Prefs.setInt(this, "ulk_today", 0)
        Prefs.setLong(this, "stp_today", 0L)
        Prefs.setLong(this, "dat_daily_total", 0L)
        Prefs.setLong(this, "dat_daily_wifi", 0L)
        Prefs.setLong(this, "dat_daily_mobile", 0L)
        Prefs.setLong(this, "scr_on_acc", 0L)
        Prefs.setInt(this, "fap_today", 0)
    }

    private fun doMonthRollover() {
        Prefs.setLong(this, "dat_monthly_total", 0L)
        Prefs.setLong(this, "dat_monthly_wifi", 0L)
        Prefs.setLong(this, "dat_monthly_mobile", 0L)
    }

    private fun scheduleNextTick() {
        if (!::modules.isInitialized) {
            if (::handler.isInitialized) handler.postDelayed(tickRunnable, 5000L)
            return
        }
        val now = SystemClock.elapsedRealtime()
        var minDelay = Long.MAX_VALUE
        for (m in modules) {
            if (!m.alive()) continue
            val last = lastTickTime[m.key()] ?: 0L
            val delay = (last + m.tickIntervalMs()) - now
            if (delay < minDelay) minDelay = delay
        }
        val delay = when {
            minDelay == Long.MAX_VALUE -> 5000L
            minDelay < 1000L -> 1000L
            minDelay > 60000L -> 60000L
            else -> minDelay
        }
        if (::handler.isInitialized) handler.postDelayed(tickRunnable, delay)
    }

    private fun stopAll() {
        if (::modules.isInitialized) {
            for (m in modules) {
                if (m.alive()) m.stop()
            }
        }
        moduleData.clear()
    }

    private fun createChannels() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val monCh = NotificationChannel(MONITOR_CH, "Extension Box Monitor", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(monCh)
        val alertCh = NotificationChannel(ALERT_CH, "Extension Box Alerts", NotificationManager.IMPORTANCE_HIGH)
        nm.createNotificationChannel(alertCh)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPi = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        
        val stopIntent = Intent(this, MonitorService::class.java).setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val bigText = NotificationCompat.BigTextStyle().bigText(buildExpanded())

        return NotificationCompat.Builder(this, MONITOR_CH)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(buildTitle())
            .setContentText(buildCompact())
            .setStyle(bigText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openPi)
            .addAction(0, "■ Stop", stopPi)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun buildTitle(): String {
        if (!::modules.isInitialized) return "Extension Box"
        val contextAware = Prefs.getBool(this, "notif_context_aware", true)
        val batMod = modules.filterIsInstance<BatteryModule>().firstOrNull() ?: return "Extension Box"
        if (!batMod.alive()) return "Extension Box"

        val lvl = batMod.getLevel()
        return if (contextAware && lvl <= 15) "⚠ Extension Box • $lvl% Low!" else "Extension Box • $lvl%"
    }

    private fun buildCompact(): String {
        if (!::modules.isInitialized) return "Starting..."
        val contextAware = Prefs.getBool(this, "notif_context_aware", true)
        val maxItems = Prefs.getInt(this, "notif_compact_items", 4)

        val alive = getAliveModulesSorted()
        val parts = alive.mapNotNull { m -> m.compact().takeIf { it.isNotEmpty() } }.take(maxItems)

        if (parts.isEmpty()) return "All extensions disabled"

        var base = parts.joinToString(" • ")
        if (base.length > 60 && parts.size > 1) {
            // If too long, try to show fewer items or truncate
            base = parts.take(parts.size - 1).joinToString(" • ") + " ..."
        }

        if (contextAware) {
            val batMod = modules.filterIsInstance<BatteryModule>().firstOrNull()
            if (batMod?.alive() == true && batMod.getLevel() <= 10) {
                base += " • ⚡Charge!"
            }
        }
        return base
    }

    private fun buildExpanded(): String {
        if (!::modules.isInitialized) return "Starting..."
        val showAll = Prefs.getBool(this, "notif_show_all", false)
        val alive = getAliveModulesSorted()
        
        val lines = if (showAll) {
            alive.mapNotNull { m -> m.detail().takeIf { it.isNotEmpty() } }
        } else {
            // Compact style: use compact strings but in a list
            alive.mapNotNull { m -> 
                val c = m.compact()
                if (c.isNotEmpty()) "• ${m.name()}: $c" else null 
            }
        }
        
        return if (lines.isEmpty()) "Enable extensions from the app" else lines.joinToString("\n")
    }

    private fun getAliveModulesSorted(): List<Module> {
        if (!::modules.isInitialized) return emptyList()
        val alive = modules.filter { it.alive() }
        val saved = Prefs.getString(this, "dash_card_order", "") ?: ""
        if (saved.isEmpty()) {
            return alive.sortedBy { it.priority() }
        }

        val orderedKeys = saved.split(",").filter { it.isNotEmpty() }
        return alive.sortedWith { m1, m2 ->
            val idx1 = orderedKeys.indexOf(m1.key())
            val idx2 = orderedKeys.indexOf(m2.key())
            when {
                idx1 != -1 && idx2 != -1 -> idx1.compareTo(idx2)
                idx1 != -1 -> -1
                idx2 != -1 -> 1
                else -> m1.priority().compareTo(m2.priority())
            }
        }
    }

    private fun checkNightSummary() {
        if (!Prefs.getBool(this, "notif_night_summary", true)) return
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        if (hour >= 23 && !nightSummarySent) {
            nightSummarySent = true
            sendNightSummary()
        } else if (hour < 23) {
            nightSummarySent = false
        }
    }

    private fun sendNightSummary() {
        val unlocks = Prefs.getInt(this, "ulk_today", 0)
        val screenMs = Prefs.getLong(this, "scr_on_acc", 0L)
        val steps = Prefs.getLong(this, "stp_today", 0L)
        val faps = Prefs.getInt(this, "fap_today", 0)

        val screenMin = (screenMs / 60000).toInt()
        val screenH = screenMin / 60
        val screenM = screenMin % 60

        val body = StringBuilder()
        body.append("📱 Screen: ${screenH}h ${screenM}m")
        body.append("  •  🔓 $unlocks unlocks")
        if (steps > 0) body.append("  •  👣 $steps steps")
        if (faps > 0) body.append("  •  🍆 $faps")

        val ydUnlocks = Prefs.getInt(this, "ulk_yesterday", 0)
        if (ydUnlocks > 0) {
            val diff = unlocks - ydUnlocks
            val pct = abs(diff * 100 / ydUnlocks)
            if (diff < 0) {
                body.append("\n🎉 $pct% fewer unlocks than yesterday!")
            } else if (diff > 0) {
                body.append("\n📈 $pct% more unlocks than yesterday")
            }
        }

        val bodyStr = body.toString()

        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val n = NotificationCompat.Builder(this, ALERT_CH)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("🌙 Daily Summary")
                .setContentText(bodyStr)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bodyStr))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            nm.notify(NIGHT_SUMMARY_ID, n)
        } catch (ignored: Exception) {
        }
    }

    private fun updateNotification() {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotification())
        } catch (ignored: Exception) {
        }
    }
}
