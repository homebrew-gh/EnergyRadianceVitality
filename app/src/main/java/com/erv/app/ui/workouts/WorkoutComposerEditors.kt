package com.erv.app.ui.workouts

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erv.app.ui.components.FieldLabel
import com.erv.app.ui.components.FormSectionLabelMedium
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioRoutine
import com.erv.app.cardio.cardioBuiltinActivitiesForUserSelection
import com.erv.app.workouts.WorkoutCardioLogField
import com.erv.app.workouts.WorkoutCardioPrescription

@Composable
fun WorkoutPickCardioActivityDialog(
    excludeActivities: Set<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cardio activity") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                cardioBuiltinActivitiesForUserSelection()
                    .filter { it.name !in excludeActivities }
                    .forEach { activity ->
                        TextButton(
                            onClick = { onPick(activity.name) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(activity.name.replace('_', ' '), modifier = Modifier.fillMaxWidth())
                        }
                    }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
fun CardioPrescriptionEditor(
    prescription: WorkoutCardioPrescription,
    cardioState: CardioLibraryState,
    onUpdate: (WorkoutCardioPrescription) -> Unit,
) {
    val usingRoutine = !prescription.cardioRoutineId.isNullOrBlank()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (cardioState.routines.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = prescription.cardioRoutineId.isNullOrBlank(),
                    onClick = { onUpdate(prescription.copy(cardioRoutineId = null)) },
                    label = { FieldLabel("Inline") },
                )
                cardioState.routines.take(6).forEach { routine ->
                    FilterChip(
                        selected = prescription.cardioRoutineId == routine.id,
                        onClick = {
                            onUpdate(
                                prescription.copy(
                                    cardioRoutineId = routine.id,
                                    mode = "steady",
                                    activity = routine.primaryBuiltinActivityName()
                                        ?: prescription.activity,
                                    targetMinutes = routine.targetDurationMinutes
                                        ?: prescription.targetMinutes,
                                ),
                            )
                        },
                        label = { Text(routine.name, maxLines = 1) },
                    )
                }
            }
            if (usingRoutine) {
                Text(
                    "Launch uses saved routine; inline fields below are ignored at run time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!usingRoutine) {
            val mode = prescription.mode.lowercase()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("steady", "sprint_intervals", "interval_template").forEach { option ->
                    FilterChip(
                        selected = mode == option,
                        onClick = {
                            onUpdate(
                                when (option) {
                                    "sprint_intervals" -> prescription.copy(
                                        mode = option,
                                        rounds = prescription.rounds ?: 10,
                                        workSeconds = prescription.workSeconds ?: 60,
                                        restSeconds = prescription.restSeconds ?: 60,
                                    )
                                    "interval_template" -> prescription.copy(
                                        mode = option,
                                        outerRounds = prescription.outerRounds ?: 3,
                                        legs = prescription.legs.ifEmpty {
                                            listOf(
                                                com.erv.app.workouts.WorkoutCardioIntervalLeg(
                                                    workSeconds = 240,
                                                    restSeconds = 240,
                                                ),
                                            )
                                        },
                                    )
                                    else -> prescription.copy(
                                        mode = option,
                                        targetMinutes = prescription.targetMinutes ?: 10,
                                    )
                                },
                            )
                        },
                        label = {
                            Text(
                                when (option) {
                                    "steady" -> "Steady"
                                    "sprint_intervals" -> "Sprints"
                                    else -> "Template"
                                },
                            )
                        },
                    )
                }
            }
            when (mode) {
                "sprint_intervals" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IntField("Rounds", prescription.rounds) {
                            onUpdate(prescription.copy(rounds = it?.coerceAtLeast(1)))
                        }
                        IntField("Work (s)", prescription.workSeconds) {
                            onUpdate(prescription.copy(workSeconds = it?.coerceAtLeast(1)))
                        }
                        IntField("Rest (s)", prescription.restSeconds) {
                            onUpdate(prescription.copy(restSeconds = it?.coerceAtLeast(0)))
                        }
                    }
                }
                "interval_template" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IntField("Outer rounds", prescription.outerRounds) {
                            onUpdate(prescription.copy(outerRounds = it?.coerceAtLeast(1)))
                        }
                        val leg = prescription.legs.firstOrNull()
                        IntField("Work (s)", leg?.workSeconds) {
                            val base = leg ?: com.erv.app.workouts.WorkoutCardioIntervalLeg(240, 240)
                            onUpdate(prescription.copy(legs = listOf(base.copy(workSeconds = it ?: 240))))
                        }
                        IntField("Rest (s)", leg?.restSeconds) {
                            val base = leg ?: com.erv.app.workouts.WorkoutCardioIntervalLeg(240, 240)
                            onUpdate(prescription.copy(legs = listOf(base.copy(restSeconds = it ?: 0))))
                        }
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IntField("Minutes", prescription.targetMinutes) {
                            onUpdate(prescription.copy(targetMinutes = it?.coerceAtLeast(1)))
                        }
                        IntField("HR (bpm)", prescription.hrTargetBpm) {
                            onUpdate(prescription.copy(hrTargetBpm = it?.coerceAtLeast(0)))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IntField("HR min", prescription.hrTargetMinBpm) {
                            onUpdate(prescription.copy(hrTargetMinBpm = it?.coerceAtLeast(0)))
                        }
                        IntField("HR max", prescription.hrTargetMaxBpm) {
                            onUpdate(prescription.copy(hrTargetMaxBpm = it?.coerceAtLeast(0)))
                        }
                    }
                    OutlinedTextField(
                        value = prescription.hrZoneLabel.orEmpty(),
                        onValueChange = { onUpdate(prescription.copy(hrZoneLabel = it.ifBlank { null })) },
                        label = { FieldLabel("Zone label") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    FormSectionLabelMedium("Log prompts")
                    WorkoutCardioLogField.entries.forEach { field ->
                        val checked = field in prescription.logFields
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { selected ->
                                    val next = if (selected) {
                                        prescription.logFields + field
                                    } else {
                                        prescription.logFields - field
                                    }
                                    onUpdate(prescription.copy(logFields = next.distinct()))
                                },
                            )
                            Text(field.name.lowercase().replaceFirstChar { it.titlecase() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.IntField(
    label: String,
    value: Int?,
    onValue: (Int?) -> Unit,
) {
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { raw ->
            onValue(raw.toIntOrNull())
        },
        label = { FieldLabel(label) },
        modifier = Modifier.weight(1f),
        singleLine = true,
    )
}

@Composable
fun MobilityPrescriptionEditor(
    mobility: com.erv.app.workouts.WorkoutMobilityPrescription,
    onUpdate: (com.erv.app.workouts.WorkoutMobilityPrescription) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = mobility.holdSeconds?.toString().orEmpty(),
            onValueChange = { raw ->
                onUpdate(mobility.copy(holdSeconds = raw.toIntOrNull()?.coerceAtLeast(1)))
            },
            label = { FieldLabel("Hold (s)") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        OutlinedTextField(
            value = mobility.holdSecondsPerSide?.toString().orEmpty(),
            onValueChange = { raw ->
                onUpdate(mobility.copy(holdSecondsPerSide = raw.toIntOrNull()?.coerceAtLeast(0)))
            },
            label = { FieldLabel("Per side (s)") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
    }
}

fun CardioRoutine.primaryBuiltinActivityName(): String? =
    activity.builtin?.name ?: steps.firstOrNull()?.activity?.builtin?.name