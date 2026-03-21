package com.erv.app.ui.cardio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.erv.app.cardio.CardioActivitySnapshot
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioModality
import com.erv.app.cardio.CardioQuickLaunch
import com.erv.app.cardio.CardioQuickTimerMode
import com.erv.app.cardio.CardioSpeedUnit
import com.erv.app.cardio.CardioTreadmillParams
import com.erv.app.cardio.distanceFieldLabelOptional
import com.erv.app.cardio.parseCardioDistanceInputToMeters
import com.erv.app.cardio.resolveSnapshot
import com.erv.app.cardio.supportsOutdoorPaceEstimate
import com.erv.app.cardio.supportsTreadmillModality
import com.erv.app.cardio.displayName
import com.erv.app.cardio.label
import java.util.UUID

@Composable
fun OutdoorRuckPackWeightDialog(
    quickLaunchName: String,
    defaultRuckLoadKg: Double?,
    onDismiss: () -> Unit,
    onStart: (ruckLoadKg: Double?) -> Unit
) {
    var loadLbStr by remember(quickLaunchName) {
        mutableStateOf(
            defaultRuckLoadKg?.takeIf { it > 0 }?.let { kg ->
                String.format(LocaleUS, "%.1f", kg / 0.453592)
            }.orEmpty()
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pack weight — $quickLaunchName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter the load you are carrying for this ruck (optional).",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = loadLbStr,
                    onValueChange = { loadLbStr = it },
                    label = { Text("Pack weight (lb)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val kg = loadLbStr.trim().toDoubleOrNull()?.takeIf { it > 0 }?.times(0.453592)
                    onStart(kg)
                    onDismiss()
                }
            ) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private val LocaleUS = java.util.Locale.US

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardioQuickLaunchEditorDialog(
    existing: CardioQuickLaunch?,
    creating: Boolean,
    state: CardioLibraryState,
    distanceUnit: CardioDistanceUnit,
    onDismiss: () -> Unit,
    onSave: (CardioQuickLaunch) -> Unit
) {
    val seed = existing
    var nameStr by remember(existing?.id) { mutableStateOf(seed?.name ?: "") }
    var useCustom by remember(existing?.id) {
        mutableStateOf(seed?.activity?.customTypeId != null && seed.activity.builtin == null)
    }
    var selectedCustomId by remember(existing?.id) {
        mutableStateOf(seed?.activity?.customTypeId ?: state.customActivityTypes.firstOrNull()?.id)
    }
    var selectedBuiltin by remember(existing?.id) {
        mutableStateOf(seed?.activity?.builtin ?: CardioBuiltinActivity.WALK)
    }
    var modality by remember(existing?.id) {
        mutableStateOf(seed?.modality ?: CardioModality.OUTDOOR)
    }
    var speedStr by remember(existing?.id) { mutableStateOf(seed?.treadmill?.speed?.toString() ?: "3.0") }
    var speedUnit by remember(existing?.id) {
        mutableStateOf(seed?.treadmill?.speedUnit ?: CardioSpeedUnit.MPH)
    }
    var inclineStr by remember(existing?.id) {
        mutableStateOf(seed?.treadmill?.inclinePercent?.toString() ?: "0")
    }
    var treadDistStr by remember(existing?.id) { mutableStateOf("") }
    var loadStr by remember(existing?.id) {
        mutableStateOf(
            seed?.treadmill?.loadKg?.let { kg -> String.format(LocaleUS, "%.1f", kg / 0.453592) }.orEmpty()
        )
    }
    var timerMode by remember(existing?.id) {
        mutableStateOf(seed?.timerMode ?: CardioQuickTimerMode.COUNT_UP)
    }
    var countDownMinStr by remember(existing?.id) {
        mutableStateOf((seed?.countDownMinutes ?: 30).toString())
    }
    var outdoorPaceStr by remember(existing?.id) {
        mutableStateOf(seed?.outdoorPaceSpeed?.toString().orEmpty())
    }
    var outdoorPaceUnit by remember(existing?.id) {
        mutableStateOf(seed?.outdoorPaceSpeedUnit ?: CardioSpeedUnit.MPH)
    }
    var defaultRuckLbStr by remember(existing?.id) {
        mutableStateOf(
            seed?.defaultRuckLoadKg?.takeIf { it > 0 }?.let { kg ->
                String.format(LocaleUS, "%.1f", kg / 0.453592)
            }.orEmpty()
        )
    }

    LaunchedEffect(seed?.id, seed?.treadmill?.distanceMeters) {
        val m = seed?.treadmill?.distanceMeters
        if (m != null && m > 1) {
            treadDistStr = when (distanceUnit) {
                CardioDistanceUnit.MILES -> String.format(LocaleUS, "%.2f", m / 1609.344)
                CardioDistanceUnit.KILOMETERS -> String.format(LocaleUS, "%.2f", m / 1000.0)
            }
        }
    }

    val builtinForModality = if (useCustom) null else selectedBuiltin
    val treadmillApplicable = builtinForModality?.supportsTreadmillModality() == true
    LaunchedEffect(treadmillApplicable, modality) {
        if (!treadmillApplicable && modality == CardioModality.INDOOR_TREADMILL) {
            modality = CardioModality.OUTDOOR
        }
    }

    fun buildSnapshot(): CardioActivitySnapshot? {
        return if (useCustom) {
            val id = selectedCustomId ?: return null
            state.resolveSnapshot(null, id)
        } else {
            state.resolveSnapshot(selectedBuiltin, null)
        }
    }

    fun buildTreadmill(): CardioTreadmillParams? {
        if (modality != CardioModality.INDOOR_TREADMILL) return null
        val speed = speedStr.toDoubleOrNull() ?: return null
        val inc = inclineStr.toDoubleOrNull() ?: 0.0
        val distM = treadDistStr.toDoubleOrNull()
            ?.let { parseCardioDistanceInputToMeters(it, distanceUnit) }
        val lb = if (builtinForModality == CardioBuiltinActivity.RUCK) {
            loadStr.toDoubleOrNull()
        } else null
        return CardioTreadmillParams(
            speed = speed,
            speedUnit = speedUnit,
            inclinePercent = inc,
            distanceMeters = distM,
            loadKg = lb?.takeIf { it > 0 }?.times(0.453592)
        )
    }

    val snap = buildSnapshot()
    val validationError = when {
        nameStr.isBlank() -> "Enter a name"
        snap == null -> "Choose an activity"
        useCustom && selectedCustomId == null -> "Choose a custom activity"
        timerMode == CardioQuickTimerMode.COUNT_DOWN &&
            (countDownMinStr.toIntOrNull()?.let { it > 0 } != true) -> "Enter countdown minutes"
        modality == CardioModality.INDOOR_TREADMILL && buildTreadmill() == null -> "Check treadmill fields"
        else -> null
    }

    val isRuckOutdoor = snap?.builtin == CardioBuiltinActivity.RUCK && modality == CardioModality.OUTDOOR

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "New quick start" else "Edit quick start") },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "One-tap timer: activity, modality, and clock are saved. Multi-leg workouts stay in routines.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = nameStr,
                    onValueChange = { nameStr = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Activity", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !useCustom,
                        onClick = { useCustom = false },
                        label = { Text("Built-in") }
                    )
                    FilterChip(
                        selected = useCustom,
                        onClick = { useCustom = true },
                        label = { Text("Custom") }
                    )
                }
                if (useCustom) {
                    if (state.customActivityTypes.isEmpty()) {
                        Text("Add custom activities on the Activities tab first.", color = MaterialTheme.colorScheme.error)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.customActivityTypes.forEach { t ->
                                FilterChip(
                                    selected = selectedCustomId == t.id,
                                    onClick = { selectedCustomId = t.id },
                                    label = { Text(t.name) }
                                )
                            }
                        }
                    }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CardioBuiltinActivity.entries.forEach { b ->
                            FilterChip(
                                selected = selectedBuiltin == b,
                                onClick = { selectedBuiltin = b },
                                label = { Text(b.displayName()) }
                            )
                        }
                    }
                }

                if (treadmillApplicable) {
                    Text("Where", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = modality == CardioModality.OUTDOOR,
                            onClick = { modality = CardioModality.OUTDOOR },
                            label = { Text(CardioModality.OUTDOOR.label()) }
                        )
                        FilterChip(
                            selected = modality == CardioModality.INDOOR_TREADMILL,
                            onClick = { modality = CardioModality.INDOOR_TREADMILL },
                            label = { Text(CardioModality.INDOOR_TREADMILL.label()) }
                        )
                    }
                }

                if (modality == CardioModality.INDOOR_TREADMILL && treadmillApplicable) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = speedUnit == CardioSpeedUnit.MPH,
                            onClick = { speedUnit = CardioSpeedUnit.MPH },
                            label = { Text("mph") }
                        )
                        FilterChip(
                            selected = speedUnit == CardioSpeedUnit.KMH,
                            onClick = { speedUnit = CardioSpeedUnit.KMH },
                            label = { Text("km/h") }
                        )
                    }
                    OutlinedTextField(
                        value = speedStr,
                        onValueChange = { speedStr = it },
                        label = { Text("Speed") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inclineStr,
                        onValueChange = { inclineStr = it },
                        label = { Text("Incline %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = treadDistStr,
                        onValueChange = { treadDistStr = it },
                        label = { Text(distanceUnit.distanceFieldLabelOptional()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (builtinForModality == CardioBuiltinActivity.RUCK) {
                        OutlinedTextField(
                            value = loadStr,
                            onValueChange = { loadStr = it },
                            label = { Text("Pack weight (lb)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Text("Timer", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = timerMode == CardioQuickTimerMode.COUNT_UP,
                        onClick = { timerMode = CardioQuickTimerMode.COUNT_UP },
                        label = { Text("Count up") }
                    )
                    FilterChip(
                        selected = timerMode == CardioQuickTimerMode.COUNT_DOWN,
                        onClick = { timerMode = CardioQuickTimerMode.COUNT_DOWN },
                        label = { Text("Count down") }
                    )
                }
                if (timerMode == CardioQuickTimerMode.COUNT_DOWN) {
                    OutlinedTextField(
                        value = countDownMinStr,
                        onValueChange = { countDownMinStr = it },
                        label = { Text("Countdown (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (modality == CardioModality.OUTDOOR && snap?.supportsOutdoorPaceEstimate() == true) {
                    Text(
                        "Optional average speed (distance estimate without GPS).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = outdoorPaceUnit == CardioSpeedUnit.MPH,
                            onClick = { outdoorPaceUnit = CardioSpeedUnit.MPH },
                            label = { Text("mph") }
                        )
                        FilterChip(
                            selected = outdoorPaceUnit == CardioSpeedUnit.KMH,
                            onClick = { outdoorPaceUnit = CardioSpeedUnit.KMH },
                            label = { Text("km/h") }
                        )
                    }
                    OutlinedTextField(
                        value = outdoorPaceStr,
                        onValueChange = { outdoorPaceStr = it },
                        label = { Text("Avg speed (optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (isRuckOutdoor) {
                    OutlinedTextField(
                        value = defaultRuckLbStr,
                        onValueChange = { defaultRuckLbStr = it },
                        label = { Text("Default pack weight (lb, optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "You’ll confirm pack weight each time you start an outdoor ruck.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                validationError?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val s = buildSnapshot() ?: return@TextButton
                    val tm = buildTreadmill()
                    val cdMin = countDownMinStr.toIntOrNull()
                    val pace = outdoorPaceStr.toDoubleOrNull()?.takeIf { it > 0 }
                    val pUnit = if (pace != null) outdoorPaceUnit else null
                    val defaultRuckKg = defaultRuckLbStr.trim().toDoubleOrNull()?.takeIf { it > 0 }?.times(0.453592)
                    onSave(
                        CardioQuickLaunch(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = nameStr.trim(),
                            activity = s,
                            modality = modality,
                            treadmill = if (modality == CardioModality.INDOOR_TREADMILL) tm else null,
                            timerMode = timerMode,
                            countDownMinutes = if (timerMode == CardioQuickTimerMode.COUNT_DOWN) cdMin else null,
                            outdoorPaceSpeed = pace,
                            outdoorPaceSpeedUnit = pUnit,
                            defaultRuckLoadKg = if (isRuckOutdoor) defaultRuckKg else null
                        )
                    )
                    onDismiss()
                },
                enabled = validationError == null && snap != null
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
