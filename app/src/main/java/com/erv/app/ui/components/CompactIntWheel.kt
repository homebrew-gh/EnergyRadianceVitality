package com.erv.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

private const val COMPACT_WHEEL_ITEM_DP = 40
private const val COMPACT_WHEEL_VISIBLE_ITEMS = 3

/**
 * Compact scroll wheel (same pattern as Hot + Cold duration). [values] should be stable or keyed
 * via [key]; [onCommitted] runs when scrolling settles on an index.
 */
@Composable
fun CompactIntWheel(
    values: List<Int>,
    currentValue: Int,
    onCommitted: (Int) -> Unit,
    formatLabel: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) return

    key(values) {
        val exact = values.indexOf(currentValue)
        val ix =
            if (exact >= 0) exact
            else values.indices.minByOrNull { abs(values[it] - currentValue) } ?: 0
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = ix.coerceIn(0, values.lastIndex)
        )

        val itemHeightPx = with(LocalDensity.current) { COMPACT_WHEEL_ITEM_DP.dp.toPx() }
        val selectedIndex by remember {
            derivedStateOf {
                val offset = listState.firstVisibleItemScrollOffset.toFloat()
                val index = listState.firstVisibleItemIndex + (offset / itemHeightPx).roundToInt()
                index.coerceIn(0, values.lastIndex)
            }
        }

        LaunchedEffect(listState.isScrollInProgress) {
            if (!listState.isScrollInProgress) {
                listState.scrollToItem(selectedIndex)
            }
        }

        LaunchedEffect(selectedIndex, listState.isScrollInProgress) {
            if (!listState.isScrollInProgress) {
                onCommitted(values[selectedIndex])
            }
        }

        Box(
            modifier = modifier
                .height((COMPACT_WHEEL_ITEM_DP * COMPACT_WHEEL_VISIBLE_ITEMS).dp)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    vertical = (COMPACT_WHEEL_ITEM_DP * (COMPACT_WHEEL_VISIBLE_ITEMS / 2)).dp
                )
            ) {
                items(values.size, key = { values[it] }) { index ->
                    val v = values[index]
                    val isSelected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .height(COMPACT_WHEEL_ITEM_DP.dp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatLabel(v),
                            style = if (isSelected) {
                                MaterialTheme.typography.titleLarge
                            } else {
                                MaterialTheme.typography.titleMedium
                            },
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            }
                        )
                    }
                }
            }
        }
    }
}
