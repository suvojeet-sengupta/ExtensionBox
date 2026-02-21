package com.extensionbox.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import java.io.BufferedReader
import android.os.Build
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import java.util.Locale
import rikka.shizuku.Shizuku

class SystemAccess(ctx: Context) {

    companion object {
        const val TIER_ROOT = "Root"
        const val TIER_SHIZUKU = "Shizuku"
        const val TIER_NORMAL = "Normal"

        private fun detectRoot(): Boolean {
            return try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                val br = BufferedReader(InputStreamReader(p.inputStream))
                val line = br.readLine()
                br.close()
                val exitCode = p.waitFor()
                exitCode == 0 && line != null && line.contains("uid=0")
            } catch (e: Exception) {
                false
            }
        }

        private fun detectShizuku(ctx: Context): Boolean {
            return try {
                if (Shizuku.pingBinder()) {
                    return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                }
                false
            } catch (e: Exception) {
                false
            }
        }

        private fun readFileDirect(path: String): String? {
            return try {
                val f = File(path)
                if (!f.exists() || !f.canRead()) return null
                val br = BufferedReader(FileReader(f))
                val line = br.readLine()
                br.close()
                if (line != null && line.isNotEmpty()) line.trim() else null
            } catch (e: Exception) {
                null
            }
        }

        private fun readFileRoot(path: String): String? {
            return try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat $path"))
                val br = BufferedReader(InputStreamReader(p.inputStream))
                val line = br.readLine()
                br.close()
                p.waitFor()
                if (line != null && line.isNotEmpty()) line.trim() else null
            } catch (e: Exception) {
                null
            }
        }

        private fun readFileShizuku(path: String): String? {
            return try {
                // Use the 3-parameter signature (String[], envp, dir) which is public in Shizuku 12.1.0
                val p = Shizuku.newProcess(arrayOf("sh", "-c", "cat $path"), null, null)
                val br = BufferedReader(InputStreamReader(p.inputStream))
                val line = br.readLine()
                br.close()
                p.waitFor()
                if (line != null && line.isNotEmpty()) line.trim() else null
            } catch (e: Exception) {
                null
            }
        }
    }

    private val rootAvailable: Boolean = detectRoot()
    
    private fun shizukuAvailableNow(): Boolean {
        if (rootAvailable) return false
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    val tier: String 
        get() = when {
            rootAvailable -> TIER_ROOT
            shizukuAvailableNow() -> TIER_SHIZUKU
            else -> TIER_NORMAL
        }

    fun isEnhanced(): Boolean = rootAvailable || shizukuAvailableNow()

    fun readSysFile(path: String): String? {
        readFileDirect(path)?.let { return it }
        if (rootAvailable) readFileRoot(path)?.let { return it }
        if (shizukuAvailableNow()) readFileShizuku(path)?.let { return it }
        return null
    }

    fun readBatteryCurrentMa(ctx: Context): Int {
        if (isEnhanced()) {
            readSysFile("/sys/class/power_supply/battery/current_now")?.let {
                try {
                    return (it.toLong() / 1000).toInt()
                } catch (ignored: NumberFormatException) {
                }
            }
        }
        return try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000
        } catch (e: Exception) {
            0
        }
    }

    fun readDesignCapacity(ctx: Context): Int {
        if (isEnhanced()) {
            readSysFile("/sys/class/power_supply/battery/charge_full_design")?.let {
                try {
                    val mah = (it.toLong() / 1000).toInt()
                    if (mah in 1..99999) return mah
                } catch (ignored: NumberFormatException) {
                }
            }
        }
        return try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val pp = powerProfileClass.getConstructor(Context::class.java).newInstance(ctx)
            val cap = powerProfileClass.getMethod("getBatteryCapacity").invoke(pp) as Double
            if (cap > 0) cap.toInt() else 4000
        } catch (e: Exception) {
            4000
        }
    }

    fun readActualCapacity(): Int {
        if (!isEnhanced()) return -1
        val valStr = readSysFile("/sys/class/power_supply/battery/charge_full") ?: return -1
        return try {
            val mah = (valStr.toLong() / 1000).toInt()
            if (mah in 1..99999) mah else -1
        } catch (e: NumberFormatException) {
            -1
        }
    }

    fun readCycleCount(): Int {
        if (!isEnhanced()) return -1
        val valStr = readSysFile("/sys/class/power_supply/battery/cycle_count") ?: return -1
        return try {
            val cycles = valStr.toInt()
            if (cycles >= 0) cycles else -1
        } catch (e: NumberFormatException) {
            -1
        }
    }

    fun readBatteryTechnology(): String? {
        if (!isEnhanced()) return null
        return readSysFile("/sys/class/power_supply/battery/technology")
    }

    fun readCpuTemp(): Float {
        val paths = arrayOf(
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/devices/virtual/thermal/thermal_zone1/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/devices/virtual/thermal/thermal_zone2/temp"
        )
        for (path in paths) {
            readSysFile(path)?.let {
                try {
                    var raw = it.toFloat()
                    if (raw > 1000) raw /= 1000f
                    if (raw in 0.1f..149.9f) return raw
                } catch (ignored: NumberFormatException) {
                }
            }
        }
        return Float.NaN
    }

    fun readRealHealthPct(ctx: Context): Int {
        val actualCap = readActualCapacity()
        val designCap = readDesignCapacity(ctx)
        if (actualCap <= 0 || designCap <= 0) return -1
        val pct = actualCap * 100 / designCap
        return if (pct in 1..200) pct else -1
    }

    fun readCpuUsageFallback(): Float {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("top", "-n", "1", "-b"))
            val br = BufferedReader(InputStreamReader(p.inputStream))
            var line: String?
            while (br.readLine().also { line = it } != null) {
                val l = line?.lowercase(Locale.US) ?: ""
                if (l.contains("user") && l.contains("sys") && (l.contains("%") || l.contains("cpu"))) {
                    val u = parseCpuFromTopLine(l)
                    if (u >= 0) {
                        br.close()
                        p.destroy()
                        return u
                    }
                }
            }
            br.close()
            p.destroy()
            -1f
        } catch (ignored: Exception) {
            -1f
        }
    }

    private fun parseCpuFromTopLine(line: String): Float {
        return try {
            if (line.contains("user") && line.contains("%")) {
                var total = 0f
                val parts = line.split(",")
                for (part in parts) {
                    if (part.contains("user") || part.contains("sys") || part.contains("nice") || part.contains("irq")) {
                        val m = Regex("""(\d+(?:\.\d+)?)\s*%""").find(part)
                        if (m != null) total += m.groupValues[1].toFloat()
                    }
                }
                if (total > 0) return total
            }
            if (line.contains("idle")) {
                val words = line.trim().split(Regex("\\s+"))
                var idle = -1f
                var total = -1f
                for (w in words) {
                    if (w.contains("idle")) {
                        val m = Regex("""(\d+(?:\.\d+)?)\s*%""").find(w)
                        if (m != null) idle = m.groupValues[1].toFloat()
                    } else if (w.contains("cpu")) {
                        val m = Regex("""(\d+(?:\.\d+)?)\s*%""").find(w)
                        if (m != null) total = m.groupValues[1].toFloat()
                    }
                }
                if (total > 0 && idle >= 0) {
                    val usage = (total - idle) * 100f / total
                    return usage.coerceIn(0f, 100f)
                }
            }
            -1f
        } catch (ignored: Exception) {
            -1f
        }
    }

    fun getCpuCoreCount(): Int {
        return try {
            Runtime.getRuntime().availableProcessors()
        } catch (e: Exception) {
            1
        }
    }
}
