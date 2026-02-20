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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

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

    val shizukuRequestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            shizukuPermissionGranted = isGranted
            if (isGranted) {
                Toast.makeText(context, "Permission granted. Please restart monitoring service.", Toast.LENGTH_LONG).show()
            }
        }
    )

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
        // Shizuku Section
        if (!shizukuPermissionGranted) {
             val isShizukuInstalled = try {
                 Shizuku.pingBinder()
             } catch (e: Exception) { false }

             if (isShizukuInstalled) {
                 ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Shizuku Permission",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            text = "Shizuku is installed but permission is not granted. Grant it to enable system-level monitoring.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        Button(
                            onClick = { 
                                try {
                                    Shizuku.requestPermission(0) 
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to request permission", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Permission", color = MaterialTheme.colorScheme.onError)
                        }
                    }
                }
             }
        }

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
                var contextAware by remember { mutableStateOf(Prefs.getBool(context, "notif_context_aware", true)) }
                ListItem(
                    headlineContent = { Text("Context Aware Notification") },
                    supportingContent = { Text("Show alerts in notification title") },
                    leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = contextAware,
                            onCheckedChange = {
                                contextAware = it
                                Prefs.setBool(context, "notif_context_aware", it)
                            }
                        )
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
