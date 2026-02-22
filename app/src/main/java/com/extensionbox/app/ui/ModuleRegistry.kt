package com.extensionbox.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

data class ModuleDef(
    val key: String,
    val icon: ImageVector,
    val emoji: String, // Fallback for widgets (RemoteViews)
    val name: String,
    val description: String,
    val defaultEnabled: Boolean
)

object ModuleRegistry {

    private val MODULES = listOf(
        ModuleDef("battery", Icons.Rounded.BatteryChargingFull, "🔋", "Battery", "Current, power, temperature, health", true),
        ModuleDef("cpu_ram", Icons.Rounded.Memory, "🧠", "CPU & RAM", "CPU usage, memory status", true),
        ModuleDef("screen", Icons.Rounded.Smartphone, "📱", "Screen Time", "Screen on/off time, drain rates", true),
        ModuleDef("sleep", Icons.Rounded.Bedtime, "😴", "Deep Sleep", "CPU sleep vs awake ratio", true),
        ModuleDef("network", Icons.Rounded.NetworkCheck, "📶", "Network Speed", "Real-time download/upload speed", true),
        ModuleDef("data", Icons.Rounded.DataUsage, "📊", "Data Usage", "Daily & monthly, WiFi & mobile", true),
        ModuleDef("unlock", Icons.Rounded.LockOpen, "🔓", "Unlock Counter", "Daily unlocks, detox tracking", true),
        ModuleDef("storage", Icons.Rounded.Storage, "💾", "Storage", "Internal storage usage", false),
        ModuleDef("connection", Icons.Rounded.SettingsInputAntenna, "📡", "Connection Info", "WiFi, cellular, VPN status", false),
        ModuleDef("uptime", Icons.Rounded.History, "🕒", "Uptime", "Device uptime since boot", false),
        ModuleDef("steps", Icons.Rounded.DirectionsWalk, "👣", "Step Counter", "Steps and distance", false),
        ModuleDef("speedtest", Icons.Rounded.Speed, "🏎", "Speed Test", "Periodic download/upload speed test", false),
        ModuleDef("fap", Icons.Rounded.Favorite, "🍆", "Fap Counter", "Self-monitoring counter & streak", false)
    )

    fun keyAt(i: Int): String = MODULES[i].key
    fun iconAt(i: Int): ImageVector = MODULES[i].icon
    fun emojiAt(i: Int): String = MODULES[i].emoji
    fun nameAt(i: Int): String = MODULES[i].name
    fun descAt(i: Int): String = MODULES[i].description
    fun defAt(i: Int): Boolean = MODULES[i].defaultEnabled
    fun count(): Int = MODULES.size

    fun iconFor(key: String): ImageVector {
        return MODULES.find { it.key == key }?.icon ?: Icons.Rounded.Extension
    }

    fun emojiFor(key: String): String {
        return MODULES.find { it.key == key }?.emoji ?: "🧩"
    }

    fun nameFor(key: String): String {
        return MODULES.find { it.key == key }?.name ?: key
    }
}
