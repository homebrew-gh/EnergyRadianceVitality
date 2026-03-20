package com.erv.app.ui.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val WHEEL_ITEM_HEIGHT_DP = 40
private const val WHEEL_VISIBLE_ITEMS = 5

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
    val hourValues = remember { (1..12).map { it.toString() } }
    val minuteValues = remember { (0..59).map { it.toString().padStart(2, '0') } }
    val amPmValues = remember { listOf("AM", "PM") }

    var hour by remember { mutableIntStateOf(initialHour.coerceIn(1, 12)) }
    var minute by remember { mutableIntStateOf(initialMinute.coerceIn(0, 59)) }
    var isPm by remember { mutableStateOf(initialIsPm) }

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
                        values = hourValues,
                        initialIndex = hour - 1,
                        onSelectionSettled = { hour = it + 1 },
                        modifier = Modifier.weight(1f)
                    )
                    WheelPickerColumn(
                        label = "Minute",
                        values = minuteValues,
                        initialIndex = minute,
                        onSelectionSettled = { minute = it },
                        modifier = Modifier.weight(1f)
                    )
                    WheelPickerColumn(
                        label = "AM/PM",
                        values = amPmValues,
                        initialIndex = if (isPm) 1 else 0,
                        onSelectionSettled = { isPm = it == 1 },
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

/**
 * Scroll-wheel column that mirrors the working LightTherapy timer pattern:
 * contentPadding for centering, derivedStateOf for index, animateScrollToItem to snap.
 *
 * [initialIndex] is read once; after that the wheel owns its scroll position.
 * [onSelectionSettled] fires only when scrolling stops and the list has snapped.
 */
@Composable
private fun WheelPickerColumn(
    label: String,
    values: List<String>,
    initialIndex: Int,
    onSelectionSettled: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = WHEEL_ITEM_HEIGHT_DP.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    val edgePadding = itemHeight * (WHEEL_VISIBLE_ITEMS / 2)
    val clamped = initialIndex.coerceIn(0, values.lastIndex.coerceAtLeast(0))

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = clamped)

    val selectedIndex by remember {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset.toFloat()
            val index = listState.firstVisibleItemIndex + (offset / itemHeightPx).roundToInt()
            index.coerceIn(0, values.lastIndex.coerceAtLeast(0))
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && values.isNotEmpty()) {
            listState.animateScrollToItem(selectedIndex)
            onSelectionSettled(selectedIndex)
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
                .height(itemHeight * WHEEL_VISIBLE_ITEMS)
        ) {
            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = edgePadding)
            ) {
                items(
                    count = values.size,
                    key = { it }
                ) { index ->
                    val isSelected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = values[index],
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
