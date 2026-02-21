package com.extensionbox.app.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.extensionbox.app.MonitorService
import com.extensionbox.app.Prefs
import com.extensionbox.app.ui.ModuleRegistry
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toComposePath
import androidx.compose.ui.graphics.drawscope.Fill

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    var isRunning by remember { mutableStateOf(Prefs.isRunning(context)) }
    var activeCount by remember { mutableStateOf(0) }
    
    // Module order state
    val savedOrder = Prefs.getString(context, "dash_card_order", "") ?: ""
    val initialOrder = if (savedOrder.isEmpty()) {
        (0 until ModuleRegistry.count()).map { ModuleRegistry.keyAt(it) }
    } else {
        savedOrder.split(",").filter { it.isNotEmpty() }
    }
    val moduleOrder = remember { mutableStateListOf<String>().apply { addAll(initialOrder) } }

    // Expansion state
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }

    var dashData by remember { mutableStateOf<Map<String, Map<String, String>>>(emptyMap()) }

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
                val dataMap = mutableMapOf<String, Map<String, String>>()
                for (key in moduleOrder) {
                    val data = MonitorService.getModuleData(key)
                    if (data != null && data.isNotEmpty()) {
                        dataMap[key] = data
                    }
                }
                dashData = dataMap
            } else {
                dashData = emptyMap()
            }
            
            delay(2000)
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        moduleOrder.apply {
            add(to.index, removeAt(from.index))
        }
        Prefs.setString(context, "dash_card_order", moduleOrder.joinToString(","))
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "status_header") {
            StatusHeader(isRunning, activeCount, context) { isRunning = it }
        }

        if (!isRunning) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Tap start to begin monitoring system resources.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline,
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
            items(moduleOrder, key = { it }) { key ->
                val data = dashData[key]
                if (data != null) {
                    ReorderableItem(reorderableState, key = key) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                        val scale by animateFloatAsState(if (isDragging) 1.05f else 1.0f)

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    this.scaleX = scale
                                    this.scaleY = scale
                                    this.shadowElevation = elevation.toPx()
                                }
                        ) {
                            DashCard(
                                key = key,
                                data = data,
                                isExpanded = expandedStates[key] ?: false,
                                onExpandToggle = { expandedStates[key] = !(expandedStates[key] ?: false) },
                                modifier = Modifier.reorderableItemModifier(reorderableState, key)
                            )
                        }
                    }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun StatusHeader(isRunning: Boolean, activeCount: Int, context: android.content.Context, onStatusChange: (Boolean) -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_morph")
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progress"
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer 
                            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Box {
            // Morphing background element for expressive feel
            MorphingBackground(
                progress = morphProgress,
                color = if (isRunning) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
            )

            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val statusText = if (isRunning) "Active" else "Stopped"
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
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
                        onStatusChange(!isRunning)
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
    }
}

@Composable
fun MorphingBackground(progress: Float, color: Color) {
    val shapeA = remember {
        RoundedPolygon(
            numVertices = 6,
            rounding = CornerRounding(0.2f)
        )
    }
    val shapeB = remember {
        RoundedPolygon.star(
            numVerticesPerRadius = 6,
            innerRadius = 0.7f,
            rounding = CornerRounding(0.1f)
        )
    }
    val morph = remember { Morph(shapeA, shapeB) }

    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxSize().offset(x = 100.dp, y = (-20).dp)
    ) {
        val path = morph.toComposePath(progress)
        drawPath(path, color, style = Fill)
    }
}

@Composable
fun DashCard(
    key: String, 
    data: Map<String, String>, 
    isExpanded: Boolean, 
    onExpandToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val emoji = ModuleRegistry.emojiFor(key)
    val name = ModuleRegistry.nameFor(key)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
            .clickable { onExpandToggle() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = emoji, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Expansion indicator
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.alpha(0.3f))
                Spacer(modifier = Modifier.height(12.dp))
                
                data.forEach { (rawKey, value) ->
                    val labelText = rawKey.substringAfterLast('.').replaceFirstChar { it.uppercase() }.replace("_", " ")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = labelText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Collapsed view: show only first 2 points in a single line or summary
                val summary = data.values.take(2).joinToString(" • ")
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 12.dp, start = 56.dp)
                )
            }
        }
    }
}

// Extension function for reorderable modifier to clean up the code
fun Modifier.reorderableItemModifier(state: sh.calvin.reorderable.ReorderableLazyListState, key: String): Modifier {
    return this.then(Modifier.pointerInput(Unit) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.onDragStart(key) },
            onDrag = { change, dragAmount -> 
                change.consume()
                state.onDrag(dragAmount) 
            },
            onDragEnd = { state.onDragInterrupted() },
            onDragCancel = { state.onDragInterrupted() }
        )
    })
}
