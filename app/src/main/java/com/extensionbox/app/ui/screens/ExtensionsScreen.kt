package com.extensionbox.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.scale
import com.extensionbox.app.Prefs
import com.extensionbox.app.ui.ModuleRegistry
import com.extensionbox.app.ui.components.AppCard
import com.extensionbox.app.MonitorService

@Composable
fun ExtensionsScreen() {
    val context = LocalContext.current
    
    // State for module enabled/disabled
    val moduleStates = remember {
        (0 until ModuleRegistry.count()).map { i ->
            mutableStateOf(Prefs.isModuleEnabled(context, ModuleRegistry.keyAt(i), ModuleRegistry.defAt(i)))
        }
    }

    // State for card expansion
    val expandedStates = remember {
        mutableStateMapOf<String, Boolean>()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed((0 until ModuleRegistry.count()).toList()) { index, _ ->
            val key = ModuleRegistry.keyAt(index)
            val icon = ModuleRegistry.iconAt(index)
            val name = ModuleRegistry.nameAt(index)
            val desc = ModuleRegistry.descAt(index)
            val isEnabled = moduleStates[index]
            val isExpanded = expandedStates[key] ?: false

            AppCard(
                onClick = { expandedStates[key] = !isExpanded },
                containerColor = if (isEnabled.value) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isEnabled.value) MaterialTheme.colorScheme.primaryContainer 
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = if (isEnabled.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isEnabled.value) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isEnabled.value,
                        onCheckedChange = {
                            isEnabled.value = it
                            Prefs.setModuleEnabled(context, key, it)
                        },
                        thumbContent = if (isEnabled.value) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        } else null
                    )
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth()
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Configuration",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        ModuleSettings(key, context)
                    }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun ModuleSettings(key: String, context: android.content.Context) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Refresh Interval (Most modules have this)
        val intervalKey = when(key) {
            "battery" -> "bat_interval"
            "cpu_ram" -> "cpu_interval"
            "screen" -> "scr_interval"
            "sleep" -> "slp_interval"
            "network" -> "net_interval"
            "data" -> "dat_interval"
            "unlock" -> "ulk_interval"
            "storage" -> "sto_interval"
            "connection" -> "con_interval"
            "uptime" -> "upt_interval"
            "steps" -> "stp_interval"
            "speedtest" -> "spd_interval"
            "fap" -> "fap_interval"
            else -> null
        }

        intervalKey?.let { prefKey ->
            var interval by remember { mutableStateOf(Prefs.getInt(context, prefKey, 5000).toFloat()) }
            SettingSlider(
                label = "Update Interval",
                value = interval,
                valueRange = if (key == "storage" || key == "data") 10000f..600000f else 1000f..60000f,
                onValueChange = {
                    interval = it
                    Prefs.setInt(context, prefKey, it.toInt())
                },
                formatter = { 
                    if (it >= 60000f) "${it.toInt() / 60000}m"
                    else "${it.toInt() / 1000}s" 
                }
            )
        }

        // Module Specific Settings
        when(key) {
            "battery" -> {
                var lowAlert by remember { mutableStateOf(Prefs.getBool(context, "bat_low_alert", true)) }
                SettingSwitch(
                    label = "Low Battery Alert",
                    checked = lowAlert,
                    onCheckedChange = {
                        lowAlert = it
                        Prefs.setBool(context, "bat_low_alert", it)
                    }
                )
                if (lowAlert) {
                    var lowThresh by remember { mutableStateOf(Prefs.getInt(context, "bat_low_thresh", 15).toFloat()) }
                    SettingSlider(
                        label = "Low Alert Threshold",
                        value = lowThresh,
                        valueRange = 5f..50f,
                        onValueChange = {
                            lowThresh = it
                            Prefs.setInt(context, "bat_low_thresh", it.toInt())
                        },
                        formatter = { "${it.toInt()}%" }
                    )
                }
            }
            "cpu_ram" -> {
                var ramAlert by remember { mutableStateOf(Prefs.getBool(context, "cpu_ram_alert", false)) }
                SettingSwitch(
                    label = "High RAM Alert",
                    checked = ramAlert,
                    onCheckedChange = {
                        ramAlert = it
                        Prefs.setBool(context, "cpu_ram_alert", it)
                    }
                )
                if (ramAlert) {
                    var ramThresh by remember { mutableStateOf(Prefs.getInt(context, "cpu_ram_thresh", 90).toFloat()) }
                    SettingSlider(
                        label = "RAM Alert Threshold",
                        value = ramThresh,
                        valueRange = 50f..98f,
                        onValueChange = {
                            ramThresh = it
                            Prefs.setInt(context, "cpu_ram_thresh", it.toInt())
                        },
                        formatter = { "${it.toInt()}%" }
                    )
                }
            }
            "screen" -> {
                var showDrain by remember { mutableStateOf(Prefs.getBool(context, "scr_show_drain", true)) }
                SettingSwitch(
                    label = "Show Drain Rates",
                    checked = showDrain,
                    onCheckedChange = {
                        showDrain = it
                        Prefs.setBool(context, "scr_show_drain", it)
                    }
                )
                var timeLimit by remember { mutableStateOf(Prefs.getInt(context, "scr_time_limit", 0).toFloat()) }
                SettingSlider(
                    label = "Daily Screen Time Limit",
                    value = timeLimit,
                    valueRange = 0f..600f,
                    steps = 12,
                    onValueChange = {
                        timeLimit = it
                        Prefs.setInt(context, "scr_time_limit", it.toInt())
                    },
                    formatter = { if (it == 0f) "Disabled" else "${it.toInt()}m" }
                )
            }
            "data" -> {
                var planLimit by remember { mutableStateOf(Prefs.getInt(context, "dat_plan_limit", 0).toFloat()) }
                SettingSlider(
                    label = "Monthly Plan Limit",
                    value = planLimit,
                    valueRange = 0f..10000f,
                    onValueChange = {
                        planLimit = it
                        Prefs.setInt(context, "dat_plan_limit", it.toInt())
                    },
                    formatter = { if (it == 0f) "No Limit" else "${it.toInt()} MB" }
                )
            }
            "unlock" -> {
                var ulLimit by remember { mutableStateOf(Prefs.getInt(context, "ulk_daily_limit", 0).toFloat()) }
                SettingSlider(
                    label = "Daily Unlock Limit",
                    value = ulLimit,
                    valueRange = 0f..200f,
                    onValueChange = {
                        ulLimit = it
                        Prefs.setInt(context, "ulk_daily_limit", it.toInt())
                    },
                    formatter = { if (it == 0f) "No Limit" else "${it.toInt()} times" }
                )
            }
            "storage" -> {
                var stoAlert by remember { mutableStateOf(Prefs.getBool(context, "sto_low_alert", true)) }
                SettingSwitch(
                    label = "Low Storage Alert",
                    checked = stoAlert,
                    onCheckedChange = {
                        stoAlert = it
                        Prefs.setBool(context, "sto_low_alert", it)
                    }
                )
                if (stoAlert) {
                    var stoThresh by remember { mutableStateOf(Prefs.getInt(context, "sto_low_thresh_mb", 1000).toFloat()) }
                    SettingSlider(
                        label = "Low Alert Threshold",
                        value = stoThresh,
                        valueRange = 100f..5000f,
                        onValueChange = {
                            stoThresh = it
                            Prefs.setInt(context, "sto_low_thresh_mb", it.toInt())
                        },
                        formatter = { "${it.toInt()} MB" }
                    )
                }
            }
            "fap" -> {
                var fapLimit by remember { mutableStateOf(Prefs.getInt(context, "fap_daily_limit", 0).toFloat()) }
                SettingSlider(
                    label = "Daily Goal/Limit",
                    value = fapLimit,
                    valueRange = 0f..10f,
                    steps = 10,
                    onValueChange = {
                        fapLimit = it
                        Prefs.setInt(context, "fap_daily_limit", it.toInt())
                    },
                    formatter = { if (it == 0f) "No Limit" else "${it.toInt()} times" }
                )
                
                Button(
                    onClick = {
                        MonitorService.getInstance()?.getFapModule()?.increment()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Log Action")
                }
            }
            "speedtest" -> {
                var autoTest by remember { mutableStateOf(Prefs.getBool(context, "spd_auto_test", true)) }
                SettingSwitch(
                    label = "Auto Test",
                    checked = autoTest,
                    onCheckedChange = {
                        autoTest = it
                        Prefs.setBool(context, "spd_auto_test", it)
                    }
                )
                if (autoTest) {
                    var freq by remember { mutableStateOf(Prefs.getInt(context, "spd_test_freq", 60).toFloat()) }
                    SettingSlider(
                        label = "Test Frequency",
                        value = freq,
                        valueRange = 15f..240f,
                        steps = 15,
                        onValueChange = {
                            freq = it
                            Prefs.setInt(context, "spd_test_freq", it.toInt())
                        },
                        formatter = { "${it.toInt()}m" }
                    )
                }
                var wifiOnly by remember { mutableStateOf(Prefs.getBool(context, "spd_wifi_only", true)) }
                SettingSwitch(
                    label = "WiFi Only",
                    checked = wifiOnly,
                    onCheckedChange = {
                        wifiOnly = it
                        Prefs.setBool(context, "spd_wifi_only", it)
                    }
                )
                var showPing by remember { mutableStateOf(Prefs.getBool(context, "spd_show_ping", true)) }
                SettingSwitch(
                    label = "Show Ping",
                    checked = showPing,
                    onCheckedChange = {
                        showPing = it
                        Prefs.setBool(context, "spd_show_ping", it)
                    }
                )
            }
        }
    }
}

@Composable
fun SettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    formatter: (Float) -> String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = formatter(value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.8f)
        )
    }
}
