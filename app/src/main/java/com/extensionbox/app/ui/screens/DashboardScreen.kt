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

import androidx.compose.animation.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items

import androidx.compose.ui.text.style.TextAlign

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

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer 
                                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val statusText = if (isRunning) "Active" else "Stopped"
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer 
                                else MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = if (isRunning) "$activeCount extensions running" else "Service is idle",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                }

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
                        containerColor = if (isRunning) MaterialTheme.colorScheme.error 
                                        else MaterialTheme.colorScheme.primary
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRunning) "Stop" else "Start")
                }
            }
        }

        AnimatedVisibility(
            visible = !isRunning,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "Tap start to begin monitoring system resources.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }

        AnimatedVisibility(
            visible = isRunning && dashData.isEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        if (isRunning && dashData.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = emoji, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            data.forEach { (rawKey, value) ->
                val labelText = rawKey.substringAfterLast('.').replaceFirstChar { it.uppercase() }.replace("_", " ")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
