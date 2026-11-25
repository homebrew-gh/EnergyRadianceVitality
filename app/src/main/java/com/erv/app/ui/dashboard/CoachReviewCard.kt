package com.erv.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erv.app.data.CoachReviewSyncPayload
import com.erv.app.data.ageDays
import com.erv.app.data.coachStatusLabel
import com.erv.app.data.isStale
import com.erv.app.ui.components.FormSectionLabel

/** Launch Pad card for the latest coach review made on the web companion. Tap opens the full review. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CoachReviewDashboardCard(
    payload: CoachReviewSyncPayload,
    nowEpochSeconds: Long,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val review = payload.review
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FormSectionLabel("Coach review", color = MaterialTheme.colorScheme.primary)
                Text(
                    reviewAgeText(payload, nowEpochSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(review.headline, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("Consistency", review.status.consistency)
                StatusChip("Progress", review.status.progress)
                StatusChip("Recovery", review.status.recovery)
            }
            if (payload.isStale(nowEpochSeconds)) {
                Text(
                    "This review is over ${com.erv.app.data.COACH_REVIEW_STALE_DAYS} days old. Run a new one on the web companion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, value: String) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                "$label: ${coachStatusLabel(value)}",
                color = statusColor(value),
            )
        },
    )
}

/** Full review, read-only. Editing targets stays on the web companion. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoachReviewSheet(
    payload: CoachReviewSyncPayload,
    nowEpochSeconds: Long,
    onDismiss: () -> Unit,
) {
    val review = payload.review
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Coach review", style = MaterialTheme.typography.titleLarge)
            Text(
                listOfNotNull(
                    payload.periodLabel.takeIf { it.isNotBlank() },
                    reviewAgeText(payload, nowEpochSeconds),
                    "Confidence ${review.confidence}",
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(review.headline, style = MaterialTheme.typography.titleMedium)

            if (review.lifts.isNotEmpty()) {
                FormSectionLabel("Next session")
                review.lifts.forEach { lift ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(lift.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(coachStatusLabel(lift.trend), color = statusColor(lift.trend))
                        }
                        Text(lift.next, style = MaterialTheme.typography.bodyLarge)
                        if (lift.why.isNotBlank()) {
                            Text(
                                lift.why,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }

            if (review.nextWeek.isNotEmpty()) {
                FormSectionLabel("Next week")
                review.nextWeek.forEachIndexed { index, item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "${index + 1}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Column {
                            Text(item.action, style = MaterialTheme.typography.bodyMedium)
                            if (item.why.isNotBlank()) {
                                Text(
                                    item.why,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            review.bodyWeight?.takeIf { it.isNotBlank() }?.let {
                FormSectionLabel("Body weight")
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            if (review.wins.isNotEmpty()) {
                FormSectionLabel("What went well")
                review.wins.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }

            if (review.watchOuts.isNotEmpty()) {
                FormSectionLabel("Watch-outs")
                review.watchOuts.forEach { item ->
                    val evidence = item.evidence.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                    Text("• ${item.text}$evidence", style = MaterialTheme.typography.bodyMedium)
                }
            }

            review.missingData?.takeIf { it.isNotBlank() }?.let {
                Text(
                    "Missing data: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                "Made on the web companion with ${payload.model.ifBlank { "the AI coach" }}. Run a new review there after more sessions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(androidx.compose.ui.Alignment.End)) {
                Text("Close")
            }
        }
    }
}

private fun reviewAgeText(payload: CoachReviewSyncPayload, nowEpochSeconds: Long): String =
    when (val days = payload.ageDays(nowEpochSeconds)) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> "$days days ago"
    }

@Composable
private fun statusColor(value: String): Color = when (value) {
    "up", "improving", "on_track", "ok", "ahead" -> MaterialTheme.colorScheme.primary
    "down", "slipping", "behind" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
