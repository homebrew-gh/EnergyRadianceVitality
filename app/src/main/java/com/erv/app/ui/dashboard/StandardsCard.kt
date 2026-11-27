package com.erv.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erv.app.standards.StandardEventResult
import com.erv.app.standards.StandardResult
import com.erv.app.standards.StandardTier
import com.erv.app.standards.formatStandardGap
import com.erv.app.standards.formatStandardTargets
import com.erv.app.standards.formatStandardValue
import com.erv.app.standards.standardMissingLabel
import com.erv.app.standards.standardTierLabel
import com.erv.app.ui.components.FormSectionLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthDay = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

private fun prettyDate(iso: String?): String? =
    iso?.let { runCatching { LocalDate.parse(it).format(monthDay) }.getOrNull() }

@Composable
private fun tierColor(tier: StandardTier?): Color = when (tier) {
    StandardTier.ELITE -> MaterialTheme.colorScheme.primary
    StandardTier.STRONG -> MaterialTheme.colorScheme.tertiary
    StandardTier.BASE -> MaterialTheme.colorScheme.secondary
    null -> MaterialTheme.colorScheme.outline
}

@Composable
private fun TierBadge(tier: StandardTier?) {
    Surface(
        shape = RoundedCornerShape(50),
        color = tierColor(tier).copy(alpha = 0.16f),
    ) {
        Text(
            standardTierLabel(tier),
            style = MaterialTheme.typography.labelMedium,
            color = tierColor(tier),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** One bar per event, as tall as its score (Base, Strong, Elite at a third, two thirds, full). */
@Composable
private fun EventBars(result: StandardResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics { contentDescription = "Score per event" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        result.events.forEach { e ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                val fraction = (e.score / 3.0).toFloat().coerceIn(0f, 1f)
                if (e.value != null && fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction.coerceAtLeast(0.06f))
                            .background(tierColor(e.tier), RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}

private fun tierSummary(result: StandardResult): String {
    val notScored = result.events.size - result.scoredCount
    val belowBase = result.scoredCount - result.tierCounts.values.sum()
    return (
        listOf(StandardTier.ELITE, StandardTier.STRONG, StandardTier.BASE)
            .filter { (result.tierCounts[it] ?: 0) > 0 }
            .map { "${result.tierCounts[it]} ${it.label}" } +
            listOfNotNull(
                "$belowBase Below Base".takeIf { belowBase > 0 },
                "$notScored not scored yet".takeIf { notScored > 0 },
            )
        ).joinToString(" · ")
}

/** The scored event closest to its next tier, as a "next step" line. */
private fun nextStep(result: StandardResult, metric: Boolean, latestBodyWeightKg: Double?): String? {
    val closest = result.events
        .filter { it.value != null && it.nextTier != null && it.gapToNext != null }
        .minByOrNull { e ->
            val target = e.tiers[e.nextTier!!]
            if (target == 0.0) Double.MAX_VALUE else e.gapToNext!! / kotlin.math.abs(target)
        } ?: return null
    val gap = formatStandardGap(closest, metric, latestBodyWeightKg) ?: return null
    return "Closest: ${closest.event.name}, $gap"
}

/** Home card for the main followed standard; tap for every event. */
@Composable
internal fun StandardsDashboardCard(
    main: StandardResult?,
    metric: Boolean,
    latestBodyWeightKg: Double?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FormSectionLabel("Fitness standards", color = MaterialTheme.colorScheme.primary)
            if (main == null) {
                Text(
                    "Follow a standard on the web companion's Progress page to see your tiers here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        main.standard.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    if (main.scoredCount > 0) TierBadge(main.overallTier)
                }
                EventBars(main)
                Text(
                    tierSummary(main),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                nextStep(main, metric, latestBodyWeightKg)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun EventRow(e: StandardEventResult, metric: Boolean, latestBodyWeightKg: Double?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(e.event.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (e.value != null) {
                Text(
                    listOfNotNull(
                        formatStandardValue(e.event, e.value, metric),
                        e.source?.label,
                        prettyDate(e.date),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
                formatStandardGap(e, metric, latestBodyWeightKg)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                e.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                standardMissingLabel(e)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    formatStandardTargets(e, metric),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (e.value != null) TierBadge(e.tier)
    }
}

/** Every followed standard, event by event. Choosing standards and logging results stays on the web. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun StandardsSheet(
    results: List<StandardResult>,
    metric: Boolean,
    latestBodyWeightKg: Double?,
    onHideCard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedId by remember { mutableStateOf(results.firstOrNull()?.standard?.id) }
    val selected = results.firstOrNull { it.standard.id == selectedId } ?: results.firstOrNull()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Fitness Standards", style = MaterialTheme.typography.titleLarge)
            if (results.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    results.forEach { r ->
                        FilterChip(
                            selected = r.standard.id == selected?.standard?.id,
                            onClick = { selectedId = r.standard.id },
                            label = { Text(r.standard.name) },
                        )
                    }
                }
            }
            if (selected == null) {
                Text(
                    "You don't follow a standard yet. Pick one on the web companion's Progress page, and it syncs here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        selected.standard.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    if (selected.scoredCount > 0) TierBadge(selected.overallTier)
                }
                Text(
                    tierSummary(selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Overall is the lowest tier across scored events. Est. means estimated from your lift records or the average pace of a longer session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column {
                    selected.events.forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider()
                        EventRow(e, metric, latestBodyWeightKg)
                    }
                }
            }
            Text(
                "Choose standards, build your own and log test results on the web companion's Progress page.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onHideCard) { Text("Hide Card From Home") }
        }
    }
}

/** Settings row that brings the Home card back after it was hidden. */
@Composable
fun StandardsHomeCardSetting(shown: Boolean, onShownChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text("Fitness standards card on Home", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Shows your main standard's tiers at the top of Home.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = shown, onCheckedChange = onShownChange)
    }
}
