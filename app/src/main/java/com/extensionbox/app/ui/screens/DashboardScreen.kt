package com.extensionbox.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.extensionbox.app.MonitorService
import com.extensionbox.app.Prefs
import com.extensionbox.app.ui.ModuleRegistry
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    var isRunning by remember { mutableStateOf(Prefs.isRunning(context)) }
    var activeCount by remember { mutableStateOf(0) }
    var dashData by remember { mutableStateOf<List<Pair<String, Map<String, String>>>>(emptyList()) }

    // Update isRunning and data periodically
    LaunchedEffect(Unit) {
        while (true) {
            isRunning = Prefs.isRunning(context)
            
            var count = 0
            for (i in 0 until ModuleRegistry.count()) {
                if (Prefs.isModuleEnabled(context, ModuleRegistry.keyAt(i), ModuleRegistry.defAt(i)))
                    count++
            }
            activeCount = count

            if (isRunning) {
                val dataList = mutableListOf<Pair<String, Map<String, String>>>()
                for (i in 0 until ModuleRegistry.count()) {
                    val key = ModuleRegistry.keyAt(i)
                    val data = MonitorService.getModuleData(key)
                    if (data != null && data.isNotEmpty()) {
                        dataList.add(key to data)
                    }
                }
                dashData = dataList
            } else {
                dashData = emptyList()
            }
            
            delay(2000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Dashboard",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val statusText = if (isRunning) "● Running • $activeCount active" else "○ Stopped"
            val statusColor = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = statusColor
            )

            Button(
                onClick = {
                    if (isRunning) {
                        val intent = Intent(context, MonitorService::class.java).setAction(MonitorService.ACTION_STOP)
                        context.startService(intent)
                    } else {
                        val intent = Intent(context, MonitorService::class.java)
                        ContextCompat.startForegroundService(context, intent)
                    }
                    isRunning = !isRunning
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(if (isRunning) "Stop" else "Start")
            }
        }

        if (!isRunning) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Start monitoring to see live data.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else if (dashData.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Waiting for data...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(dashData) { (key, data) ->
                    DashCard(key, data)
                }
            }
        }
    }
}

@Composable
fun DashCard(key: String, data: Map<String, String>) {
    val emoji = ModuleRegistry.emojiFor(key)
    val name = ModuleRegistry.nameFor(key)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "$emoji  $name",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            data.forEach { (rawKey, value) ->
                val labelText = rawKey.substringAfterLast('.').replaceFirstChar { it.uppercase() }.replace("_", " ")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
