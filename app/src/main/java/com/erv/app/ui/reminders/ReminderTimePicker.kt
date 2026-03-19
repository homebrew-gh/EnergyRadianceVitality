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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

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

@Composable
private fun WheelPickerColumn(
    label: String,
    values: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val itemHeight = 40.dp
    val visibleRowCount = 5
    val density = LocalDensity.current
    val centerRowIndex = visibleRowCount / 2
    val centerScrollOffsetPx = remember(density, itemHeight) {
        with(density) { (itemHeight * centerRowIndex).roundToPx() }
    }
    val centerOffset = 2
    val clampedSelectedIndex = selectedIndex.coerceIn(0, values.lastIndex.coerceAtLeast(0))
    val totalItems = values.size + centerOffset * 2

    LaunchedEffect(values, clampedSelectedIndex, centerScrollOffsetPx) {
        if (values.isNotEmpty()) {
            listState.scrollToItem(
                index = clampedSelectedIndex + centerOffset,
                scrollOffset = centerScrollOffsetPx
            )
        }
    }

    LaunchedEffect(listState, values) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && values.isNotEmpty()) {
                val viewportCenter = (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
                val centeredIndex = listState.layoutInfo.visibleItemsInfo.minByOrNull { itemInfo ->
                    abs((itemInfo.offset + itemInfo.size / 2) - viewportCenter)
                }?.index ?: return@collect
                onSelectedIndexChange((centeredIndex - centerOffset).coerceIn(0, values.lastIndex))
            }
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
                items(totalItems) { lazyIndex ->
                    val valueIndex = lazyIndex - centerOffset
                    val isSpacer = valueIndex !in values.indices
                    val value = if (isSpacer) "" else values[valueIndex]
                    val isSelected = valueIndex == clampedSelectedIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .clickable(enabled = !isSpacer) { onSelectedIndexChange(valueIndex) },
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
