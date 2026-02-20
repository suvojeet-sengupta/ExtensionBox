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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.Alignment

@Composable
fun ExtensionsScreen() {
    val context = LocalContext.current
    
    // Creating state for all module checkboxes
    val moduleStates = remember {
        (0 until ModuleRegistry.count()).map { i ->
            mutableStateOf(Prefs.isModuleEnabled(context, ModuleRegistry.keyAt(i), ModuleRegistry.defAt(i)))
        }
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
            val state = moduleStates[index]

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                onClick = {
                    val newState = !state.value
                    state.value = newState
                    Prefs.setModuleEnabled(context, key, newState)
                }
            ) {
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
                            color = MaterialTheme.colorScheme.surfaceVariant,
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
                        Switch(
                            checked = state.value,
                            onCheckedChange = {
                                state.value = it
                                Prefs.setModuleEnabled(context, key, it)
                            },
                            thumbContent = if (state.value) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            } else null
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
            }
        }
    }
}
