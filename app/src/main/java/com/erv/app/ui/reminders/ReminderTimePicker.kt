package com.erv.app.ui.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.scrollToItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeWheelPickerDialog(
    initialHour: Int,
    initialMinute: Int,
    initialIsPm: Boolean,
    title: String = "Set reminder time",
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int, isPm: Boolean) -> Unit
) {
    var hour by remember(initialHour, initialMinute, initialIsPm) { mutableIntStateOf(initialHour.coerceIn(1, 12)) }
    var minute by remember(initialHour, initialMinute, initialIsPm) { mutableIntStateOf(initialMinute.coerceIn(0, 59)) }
    var isPm by remember(initialHour, initialMinute, initialIsPm) { mutableStateOf(initialIsPm) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Scroll the wheels to choose the hour, minute, and AM or PM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WheelPickerColumn(
                        label = "Hour",
                        values = (1..12).map { it.toString() },
                        selectedIndex = hour - 1,
                        onSelectedIndexChange = { hour = it + 1 },
                        modifier = Modifier.weight(1f)
                    )
                    WheelPickerColumn(
                        label = "Minute",
                        values = (0..59).map { it.toString().padStart(2, '0') },
                        selectedIndex = minute,
                        onSelectedIndexChange = { minute = it },
                        modifier = Modifier.weight(1f)
                    )
                    WheelPickerColumn(
                        label = "AM/PM",
                        values = listOf("AM", "PM"),
                        selectedIndex = if (isPm) 1 else 0,
                        onSelectedIndexChange = { isPm = it == 1 },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hour, minute, isPm) }) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private const val CENTER_OFFSET = 2

private fun nearestLazyIndexToViewportCenter(listState: LazyListState): Int? {
    val layout = listState.layoutInfo
    val visible = layout.visibleItemsInfo
    if (visible.isEmpty()) return null
    val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
    return visible.minByOrNull { item ->
        abs((item.offset + item.size / 2) - viewportCenter)
    }?.index
}

private fun isValueRowSnapped(
    listState: LazyListState,
    valueIndex: Int,
    tolerancePx: Int
): Boolean {
    val lazyIndex = valueIndex + CENTER_OFFSET
    val layout = listState.layoutInfo
    val item = layout.visibleItemsInfo.find { it.index == lazyIndex } ?: return false
    val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
    val rowCenter = item.offset + item.size / 2
    return abs(rowCenter - viewportCenter) <= tolerancePx
}

@Composable
private fun WheelPickerColumn(
    label: String,
    values: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 40.dp
    val visibleRowCount = 5
    val centerRowIndex = visibleRowCount / 2
    val density = LocalDensity.current
    val centerScrollOffsetPx = remember(density, itemHeight) {
        with(density) { (itemHeight * centerRowIndex).roundToPx() }
    }
    val clampedSelectedIndex = selectedIndex.coerceIn(0, values.lastIndex.coerceAtLeast(0))
    val totalItems = values.size + CENTER_OFFSET * 2

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = clampedSelectedIndex + CENTER_OFFSET,
        initialFirstVisibleItemScrollOffset = centerScrollOffsetPx
    )
    val scope = rememberCoroutineScope()

    val centerValueIndex by remember(listState, values.size) {
        derivedStateOf {
            val lazyIdx = nearestLazyIndexToViewportCenter(listState) ?: return@derivedStateOf clampedSelectedIndex
            (lazyIdx - CENTER_OFFSET).coerceIn(0, values.lastIndex.coerceAtLeast(0))
        }
    }

    /**
     * Only react when [isScrollInProgress] *changes* to false — not on every scroll offset tick.
     * Watching firstVisibleItemIndex/offset caused scrollToItem ↔ layout feedback loops (wild jumping).
     */
    LaunchedEffect(listState, values.size) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling || values.isEmpty()) return@collect
                delay(80)
                if (listState.isScrollInProgress) return@collect
                if (listState.layoutInfo.visibleItemsInfo.isEmpty()) return@collect

                val lazyNearest = nearestLazyIndexToViewportCenter(listState) ?: return@collect
                val valueIndex = (lazyNearest - CENTER_OFFSET).coerceIn(0, values.lastIndex)

                if (!isValueRowSnapped(listState, valueIndex, tolerancePx = 10)) {
                    listState.scrollToItem(
                        index = valueIndex + CENTER_OFFSET,
                        scrollOffset = centerScrollOffsetPx
                    )
                }
                onSelectedIndexChange(valueIndex)
            }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight * visibleRowCount)
        ) {
            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    count = totalItems,
                    key = { lazyIndex ->
                        val v = lazyIndex - CENTER_OFFSET
                        if (v in values.indices) values[v] + "@$v" else "pad$lazyIndex"
                    }
                ) { lazyIndex ->
                    val valueIndex = lazyIndex - CENTER_OFFSET
                    val isSpacer = valueIndex !in values.indices
                    val value = if (isSpacer) "" else values[valueIndex]
                    val isSelected = !isSpacer && valueIndex == centerValueIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .clickable(enabled = !isSpacer) {
                                onSelectedIndexChange(valueIndex)
                                scope.launch {
                                    listState.scrollToItem(
                                        index = valueIndex + CENTER_OFFSET,
                                        scrollOffset = centerScrollOffsetPx
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = value,
                            style = if (isSelected) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(itemHeight)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            )
        }
    }
}
