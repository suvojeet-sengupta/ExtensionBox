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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.core.content.ContextCompat
import com.extensionbox.app.MonitorService
import com.extensionbox.app.Prefs
import com.extensionbox.app.db.ModuleDataEntity
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
    val historyData by viewModel.historyData.collectAsState()
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
                            history = historyData[key] ?: emptyList(),
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
    history: List<ModuleDataEntity>,
    isExpanded: Boolean, 
    onExpandToggle: () -> Unit,
    reorderableItemScope: ReorderableCollectionItemScope? = null,
    modifier: Modifier = Modifier
) {
    val icon = ModuleRegistry.iconFor(key)
    val name = ModuleRegistry.nameFor(key)

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        onClick = onExpandToggle
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                                    .padding(end = 12.dp)
                                    .draggableHandle(),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!isExpanded) {
                            val summary = data.values.take(2).joinToString(" • ")
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
                
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Display data in a 2-column grid if there are many items
                    val items = data.toList()
                    
                    if (isExpanded && history.isNotEmpty()) {
                        ModuleChart(key, history)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items.chunked(2).forEach { rowItems ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                rowItems.forEachIndexed { index, pair ->
                                    val rawKey = pair.first
                                    val value = pair.second
                                    val labelText = rawKey.substringAfterLast('.').replaceFirstChar { it.uppercase() }.replace("_", " ")
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = labelText,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = value,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModuleChart(moduleKey: String, history: List<ModuleDataEntity>) {
    val points = remember(history) {
        history.mapNotNull { entity ->
            val raw = when (moduleKey) {
                "battery" -> entity.data["battery.level"]?.removeSuffix("%")
                "cpuram" -> entity.data["cpu.usage"]?.removeSuffix("%")
                "network" -> entity.data["net.down_speed"]?.substringBefore(" ")
                "data" -> entity.data["dat.daily_total"]?.substringBefore(" ")
                else -> null
            }
            raw?.toFloatOrNull()
        }
    }

    if (points.size < 2) return

    Column {
        Text(
            text = "Trend (Last 15m)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Sparkline(
            points = points,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun Sparkline(
    points: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val min = points.minOrNull() ?: 0f
    val max = points.maxOrNull() ?: 1f
    val range = if (max - min == 0f) 1f else max - min

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val stepX = width / (points.size - 1)

        val path = Path()
        points.forEachIndexed { index, value ->
            val x = index * stepX
            val y = height - ((value - min) / range * height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
