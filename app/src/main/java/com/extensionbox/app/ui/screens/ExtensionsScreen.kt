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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen() {
    val context = LocalContext.current
    
    // Creating state for all module checkboxes
    val moduleStates = remember {
        (0 until ModuleRegistry.count()).map { i ->
            mutableStateOf(Prefs.isModuleEnabled(context, ModuleRegistry.keyAt(i), ModuleRegistry.defAt(i)))
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Extensions",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed((0 until ModuleRegistry.count()).toList()) { index, _ ->
                val key = ModuleRegistry.keyAt(index)
                val emoji = ModuleRegistry.emojiAt(index)
                val name = ModuleRegistry.nameAt(index)
                val desc = ModuleRegistry.descAt(index)
                val state = moduleStates[index]

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    onClick = {
                        val newState = !state.value
                        state.value = newState
                        Prefs.setModuleEnabled(context, key, newState)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Switch(
                            checked = state.value,
                            onCheckedChange = {
                                state.value = it
                                Prefs.setModuleEnabled(context, key, it)
                            }
                        )
                    }
                }
            }
        }
    }
}
