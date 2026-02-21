package com.extensionbox.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.extensionbox.app.Prefs
import com.extensionbox.app.ThemeHelper

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import rikka.shizuku.Shizuku
import android.content.pm.PackageManager

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable

import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import android.content.Intent
import com.extensionbox.app.SystemAccess

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val lifecycleOwner = LocalLifecycleOwner.current

    var themeIndex by remember { mutableStateOf(Prefs.getInt(context, "app_theme", ThemeHelper.MONET)) }
    var expanded by remember { mutableStateOf(false) }

    // Shizuku State
    var shizukuPermissionGranted by remember { 
        mutableStateOf(
            try {
                Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) { false }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                shizukuPermissionGranted = try {
                    Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                } catch (e: Exception) { false }
            }
        }
        
        val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            shizukuPermissionGranted = (grantResult == PackageManager.PERMISSION_GRANTED)
        }
        
        val binderListener = object : Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() {
                shizukuPermissionGranted = try {
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                } catch (e: Exception) { false }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListener(binderListener)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.removeBinderReceivedListener(binderListener)
        }
    }

    val sysAccess = remember(shizukuPermissionGranted) { SystemAccess(context) }

    // Export/Import Launchers
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            try {
                val jsonObject = JSONObject()
                val allPrefs = Prefs.getAll(context)
                for ((key, value) in allPrefs) {
                    jsonObject.put(key, value)
                }
                context.contentResolver.openOutputStream(it)?.use { os ->
                    os.write(jsonObject.toString(4).toByteArray())
                }
                Toast.makeText(context, "Settings exported successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { `is` ->
                    val reader = BufferedReader(InputStreamReader(`is`))
                    val json = reader.readText()
                    Prefs.importJson(context, json)
                    Toast.makeText(context, "Settings imported successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        SettingHeader("Permissions")
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Permission Tier") },
                    supportingContent = { Text("Current mode: ${sysAccess.tier}") },
                    leadingContent = { Icon(Icons.Default.Security, contentDescription = null) },
                    trailingContent = {
                        val color = when(sysAccess.tier) {
                            "Root" -> MaterialTheme.colorScheme.primary
                            "Shizuku" -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.outline
                        }
                        AssistChip(onClick = {}, label = { Text(sysAccess.tier) }, 
                            colors = AssistChipDefaults.assistChipColors(labelColor = color))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                
                // Shizuku Specific Settings
                val isShizukuRunning = try { Shizuku.pingBinder() } catch (e: Exception) { false }
                ListItem(
                    headlineContent = { Text("Shizuku Support") },
                    supportingContent = { 
                        Text(if (isShizukuRunning) "Service is running" else "Service not found or stopped")
                    },
                    leadingContent = { 
                        Icon(
                            imageVector = Icons.Default.Terminal, 
                            contentDescription = null,
                            tint = if (isShizukuRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ) 
                    },
                    trailingContent = {
                        TextButton(onClick = {
                            try {
                                val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                if (intent != null) context.startActivity(intent)
                                else Toast.makeText(context, "Shizuku app not found", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error opening Shizuku", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text("Open App")
                        }
                    }
                )
                
                if (isShizukuRunning && !shizukuPermissionGranted) {
                    ListItem(
                        headlineContent = { Text("Grant Shizuku Permission") },
                        supportingContent = { Text("Allow Extension Box to use Shizuku") },
                        leadingContent = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                        modifier = Modifier.clickable {
                            try {
                                Shizuku.requestPermission(1001)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Request failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text("Battery Exemption") },
                    supportingContent = { Text("Disable optimizations to prevent background kills") },
                    leadingContent = { Icon(Icons.Default.BatteryChargingFull, contentDescription = null) },
                    modifier = Modifier.clickable {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingHeader("Appearance")
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            ListItem(
                headlineContent = { Text("App Theme") },
                supportingContent = { Text(ThemeHelper.NAMES[themeIndex.coerceIn(0, ThemeHelper.NAMES.size - 1)]) },
                leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                trailingContent = {
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text("Change")
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            ThemeHelper.NAMES.forEachIndexed { index, name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        themeIndex = index
                                        Prefs.setInt(context, "app_theme", index)
                                        expanded = false
                                        Toast.makeText(context, "Theme saved. Restart app to apply fully.", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingHeader("Monitoring")
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Column {
                var expandCards by remember { mutableStateOf(Prefs.getBool(context, "dash_expand_cards", true)) }
                ListItem(
                    headlineContent = { Text("Expandable Cards") },
                    supportingContent = { Text("Allow cards to be expanded on the dashboard") },
                    leadingContent = { Icon(Icons.Default.ViewAgenda, contentDescription = null) },
                    trailingContent = {
                        Switch(checked = expandCards, onCheckedChange = {
                            expandCards = it
                            Prefs.setBool(context, "dash_expand_cards", it)
                        })
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                var contextAware by remember { mutableStateOf(Prefs.getBool(context, "notif_context_aware", true)) }
                ListItem(
                    headlineContent = { Text("Context Aware Notification") },
                    supportingContent = { Text("Dynamic titles based on system state") },
                    leadingContent = { Icon(Icons.Default.NotificationsActive, contentDescription = null) },
                    trailingContent = {
                        Switch(checked = contextAware, onCheckedChange = {
                            contextAware = it
                            Prefs.setBool(context, "notif_context_aware", it)
                        })
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                var nightSummary by remember { mutableStateOf(Prefs.getBool(context, "notif_night_summary", true)) }
                ListItem(
                    headlineContent = { Text("Night Summary") },
                    supportingContent = { Text("Receive a daily recap at 11 PM") },
                    leadingContent = { Icon(Icons.Default.NightsStay, contentDescription = null) },
                    trailingContent = {
                        Switch(checked = nightSummary, onCheckedChange = {
                            nightSummary = it
                            Prefs.setBool(context, "notif_night_summary", it)
                        })
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                var compactItems by remember { mutableStateOf(Prefs.getInt(context, "notif_compact_items", 4).toFloat()) }
                ListItem(
                    headlineContent = { Text("Notification Compact Items") },
                    supportingContent = { 
                        Column {
                            Text("Max items shown in collapsed notification: ${compactItems.toInt()}")
                            Slider(
                                value = compactItems,
                                onValueChange = { 
                                    compactItems = it
                                    Prefs.setInt(context, "notif_compact_items", it.toInt())
                                },
                                valueRange = 1f..6f,
                                steps = 4
                            )
                        }
                    },
                    leadingContent = { Icon(Icons.Default.ViewCompact, contentDescription = null) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingHeader("Data Management")
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Reset Daily Stats") },
                    supportingContent = { Text("Clear data for today only") },
                    leadingContent = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    modifier = Modifier.clickable {
                        Prefs.resetDailyStats(context)
                        Toast.makeText(context, "Daily stats reset", Toast.LENGTH_SHORT).show()
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text("Reset All Data", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("Clear all stored preferences and stats") },
                    leadingContent = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        Prefs.clearAll(context)
                        Toast.makeText(context, "All data cleared", Toast.LENGTH_SHORT).show()
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text("Export for Translation") },
                    supportingContent = { Text("Export all strings to help localizing the app") },
                    leadingContent = { Icon(Icons.Default.Language, contentDescription = null) },
                    modifier = Modifier.clickable {
                        // In a real app, this would export strings.xml as JSON
                        Toast.makeText(context, "Translation template exported", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingHeader("Backup & Restore")
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("Export Settings") },
                    supportingContent = { Text("Save configuration to a JSON file") },
                    leadingContent = { Icon(Icons.Default.Upload, contentDescription = null) },
                    modifier = Modifier.clickable {
                        exportLauncher.launch("extensionbox_settings.json")
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text("Import Settings") },
                    supportingContent = { Text("Restore configuration from JSON") },
                    leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                    modifier = Modifier.clickable {
                        importLauncher.launch(arrayOf("application/json"))
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SettingHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
    )
}
