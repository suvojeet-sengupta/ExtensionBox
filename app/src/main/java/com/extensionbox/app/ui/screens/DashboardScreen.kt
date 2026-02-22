package com.extensionbox.app.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.extensionbox.app.MonitorService
import com.extensionbox.app.Prefs
import com.extensionbox.app.ui.ModuleRegistry
import com.extensionbox.app.ui.components.AppCard
import kotlinx.coroutines.delay

// Reorderable 3.0.0 imports
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.ReorderableCollectionItemScope

import androidx.lifecycle.viewmodel.compose.viewModel
import com.extensionbox.app.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
    val context = LocalContext.current
    val isRunning by viewModel.isRunning.collectAsState()
    val activeCount by viewModel.activeCount.collectAsState()
    val dashData by viewModel.dashData.collectAsState()
    val visibleModules by viewModel.visibleModules.collectAsState()

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.updateOrder(from.index, to.index)
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "status_header") {
            StatusHero(isRunning, activeCount, context) { /* Status change is handled via intent in StatusHero, ViewModel will pick it up on next poll */ }
        }

        if (!isRunning) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Monitoring is stopped.\nTap start to begin.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (dashData.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            items(visibleModules, key = { it }) { key ->
                val data = dashData[key]
                if (data != null) {
                    ReorderableItem(reorderableState, key = key) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                        val scale by animateFloatAsState(if (isDragging) 1.02f else 1.0f, label = "scale")

                        DashCard(
                            key = key,
                            data = data,
                            isExpanded = viewModel.isExpanded(key),
                            onExpandToggle = { viewModel.toggleExpansion(key) },
                            reorderableItemScope = this,
                            modifier = Modifier
                                .graphicsLayer {
                                    this.scaleX = scale
                                    this.scaleY = scale
                                    this.shadowElevation = elevation.toPx()
                                }
                        )
                    }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

@Composable
fun StatusHero(isRunning: Boolean, activeCount: Int, context: android.content.Context, onStatusChange: (Boolean) -> Unit) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    
    val targetColor = if (isRunning) primaryColor else errorColor
    val containerColor by animateColorAsState(targetValue = targetColor, label = "hero_bg")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Box {
            // Subtle gradient overlay
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.1f), Color.Transparent)
                        )
                    )
            )

            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AnimatedContent(targetState = isRunning, label = "status_text") { running ->
                        Text(
                            text = if (running) "Active" else "Stopped",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isRunning) "$activeCount modules running" else "Service is idle",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                }

                FilledIconButton(
                    onClick = {
                        if (isRunning) {
                            val intent = Intent(context, MonitorService::class.java).setAction(MonitorService.ACTION_STOP)
                            context.startService(intent)
                        } else {
                            val intent = Intent(context, MonitorService::class.java)
                            ContextCompat.startForegroundService(context, intent)
                        }
                        onStatusChange(!isRunning)
                    },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = targetColor
                    )
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DashCard(
    key: String, 
    data: Map<String, String>, 
    isExpanded: Boolean, 
    onExpandToggle: () -> Unit,
    reorderableItemScope: ReorderableCollectionItemScope? = null,
    modifier: Modifier = Modifier
) {
    val icon = ModuleRegistry.iconFor(key)
    val name = ModuleRegistry.nameFor(key)

    AppCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onExpandToggle
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (reorderableItemScope != null) {
                    with(reorderableItemScope) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Reorder",
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .draggableHandle(),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))
                
                data.forEach { (rawKey, value) ->
                    val labelText = rawKey.substringAfterLast('.').replaceFirstChar { it.uppercase() }.replace("_", " ")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = labelText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (!isExpanded) {
            val summary = data.values.take(2).joinToString(" • ")
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 56.dp),
                maxLines = 1
            )
        }
    }
}
