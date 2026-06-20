@file:OptIn(ExperimentalMaterial3Api::class)

package com.erv.app.hr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.erv.app.ui.components.FieldLabel
import com.erv.app.ui.components.FormSectionLabel
import com.erv.app.data.UserPreferences
import com.erv.app.ui.settings.SettingsCollapsibleHelp

@Composable
fun HeartRateZoneSettingsSection(
    maxBpm: Int?,
    ageYears: Int?,
    restingBpm: Int?,
    zoneMethod: HeartRateZoneMethod,
    onSave: (maxBpm: Int?, ageYears: Int?, restingBpm: Int?, method: HeartRateZoneMethod) -> Unit,
    onOpenProgress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var maxDraft by remember(maxBpm) { mutableStateOf(maxBpm?.toString().orEmpty()) }
    var ageDraft by remember(ageYears) { mutableStateOf(ageYears?.toString().orEmpty()) }
    var restingDraft by remember(restingBpm) { mutableStateOf(restingBpm?.toString().orEmpty()) }
    var methodDraft by remember(zoneMethod) { mutableStateOf(zoneMethod) }

    val previewMax = remember(maxDraft, ageDraft) {
        maxDraft.trim().toIntOrNull()?.takeIf { it in 90..230 }
            ?: ageDraft.trim().toIntOrNull()?.takeIf { it in 10..100 }?.let { estimateMaxHrFromAge(it) }
            ?: 180
    }
    val previewResting = restingDraft.trim().toIntOrNull()?.takeIf { it in 35..100 }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Heart Rate Zones",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsCollapsibleHelp {
                    Text(
                        "Set your max HR, age, or resting HR for accurate Z1–Z5 charts during BLE workouts. " +
                            "Workouts already record heart rate — zones are calculated from those samples.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = maxDraft,
                    onValueChange = { maxDraft = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { FieldLabel("Max HR (bpm)") },
                    supportingText = {
                        Text("Optional. Overrides age estimate (90–230).")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ageDraft,
                    onValueChange = { ageDraft = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { FieldLabel("Age (years)") },
                    supportingText = {
                        val est = ageDraft.trim().toIntOrNull()?.takeIf { it in 10..100 }
                            ?.let { "Estimated max ≈ ${estimateMaxHrFromAge(it)} bpm" }
                        Text(est ?: "Optional. Uses 220 − age when max HR is blank.")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = restingDraft,
                    onValueChange = { restingDraft = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { FieldLabel("Resting HR (bpm)") },
                    supportingText = { Text("Optional. Required for Karvonen (heart-rate reserve) zones.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                FormSectionLabel("Zone formula")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = methodDraft == HeartRateZoneMethod.PERCENT_MAX_HR,
                        onClick = { methodDraft = HeartRateZoneMethod.PERCENT_MAX_HR },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("% max HR") }
                    SegmentedButton(
                        selected = methodDraft == HeartRateZoneMethod.KARVONEN_HRR,
                        onClick = { methodDraft = HeartRateZoneMethod.KARVONEN_HRR },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Karvonen") }
                }
                Text(
                    "Preview at $previewMax bpm max" +
                        (previewResting?.let { ", resting $it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                for (z in 1..5) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Z$z ${heartRateZoneShortName(z)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = zoneColor(z),
                        )
                        Text(
                            formatZoneBpmRange(z, previewMax, previewResting, methodDraft),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onSave(
                                maxDraft.trim().toIntOrNull()?.takeIf { it in 90..230 },
                                ageDraft.trim().toIntOrNull()?.takeIf { it in 10..100 },
                                restingDraft.trim().toIntOrNull()?.takeIf { it in 35..100 },
                                methodDraft,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Save") }
                    OutlinedButton(
                        onClick = {
                            maxDraft = ""
                            ageDraft = ""
                            restingDraft = ""
                            methodDraft = HeartRateZoneMethod.PERCENT_MAX_HR
                            onSave(null, null, null, HeartRateZoneMethod.PERCENT_MAX_HR)
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Clear") }
                }
            }
        }
        onOpenProgress?.let { open ->
            OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth()) {
                Text("View zone progress over time")
            }
        }
    }
}
