package com.extensionbox.app.ui

object ModuleRegistry {

    private val MODULES = arrayOf(
        arrayOf("battery", "🔋", "Battery", "Current, power, temperature, health", "true"),
        arrayOf("cpu_ram", "🧠", "CPU & RAM", "CPU usage, memory status", "true"),
        arrayOf("screen", "📱", "Screen Time", "Screen on/off time, drain rates", "true"),
        arrayOf("sleep", "😴", "Deep Sleep", "CPU sleep vs awake ratio", "true"),
        arrayOf("network", "📶", "Network Speed", "Real-time download/upload speed", "true"),
        arrayOf("data", "📊", "Data Usage", "Daily & monthly, WiFi & mobile", "true"),
        arrayOf("unlock", "🔓", "Unlock Counter", "Daily unlocks, detox tracking", "true"),
        arrayOf("storage", "💾", "Storage", "Internal storage usage", "false"),
        arrayOf("connection", "📡", "Connection Info", "WiFi, cellular, VPN status", "false"),
        arrayOf("uptime", "🕒", "Uptime", "Device uptime since boot", "false"),
        arrayOf("steps", "👣", "Step Counter", "Steps and distance", "false"),
        arrayOf("speedtest", "🏎", "Speed Test", "Periodic download/upload speed test", "false"),
        arrayOf("fap", "🍆", "Fap Counter", "Self-monitoring counter & streak", "false")
    )

    fun keyAt(i: Int): String = MODULES[i][0]
    fun emojiAt(i: Int): String = MODULES[i][1]
    fun nameAt(i: Int): String = MODULES[i][2]
    fun descAt(i: Int): String = MODULES[i][3]
    fun defAt(i: Int): Boolean = "true" == MODULES[i][4]
    fun count(): Int = MODULES.size

    fun emojiFor(key: String): String {
        for (m in MODULES) if (m[0] == key) return m[1]
        return "?"
    }

    fun nameFor(key: String): String {
        for (m in MODULES) if (m[0] == key) return m[2]
        return key
    }
}
