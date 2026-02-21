package com.extensionbox.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.extensionbox.app.Prefs
import com.extensionbox.app.ui.ModuleRegistry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.filled.Settings

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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed((0 until ModuleRegistry.count()).toList()) { index, _ ->
            val key = ModuleRegistry.keyAt(index)
            val emoji = ModuleRegistry.emojiAt(index)
            val name = ModuleRegistry.nameAt(index)
            val desc = ModuleRegistry.descAt(index)
            val isEnabled = moduleStates[index]
            val isExpanded = expandedStates[key] ?: false

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                onClick = {
                    expandedStates[key] = !isExpanded
                }
            ) {
                Column {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        supportingContent = {
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        },
                        leadingContent = {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (isEnabled.value) MaterialTheme.colorScheme.primaryContainer 
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = emoji,
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isExpanded) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp).padding(end = 8.dp)
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
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                                .fillMaxWidth()
                        ) {
                            HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))
                            ModuleSettings(key, context)
                        }
                    }
                }
            }
        }
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

import androidx.compose.ui.draw.scale
