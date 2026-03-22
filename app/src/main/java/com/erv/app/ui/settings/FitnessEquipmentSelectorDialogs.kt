@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.erv.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erv.app.data.BandOwnership
import com.erv.app.data.BandResistanceTier
import com.erv.app.data.BarbellOwnership
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.DumbbellOwnership
import com.erv.app.data.DumbbellOwnershipMode
import com.erv.app.data.EquipmentCatalogKind
import com.erv.app.data.FitnessEquipmentPresets
import com.erv.app.data.KettlebellOwnership
import com.erv.app.data.OwnedEquipmentItem
import com.erv.app.data.PlateOwnership
import com.erv.app.data.PlatePairEntry
import com.erv.app.data.StandardBarbellType
import com.erv.app.data.displayLabel
import com.erv.app.data.displayTitle
import com.erv.app.data.formatWeightValue
import com.erv.app.data.kgToLb
import com.erv.app.data.label
import com.erv.app.data.lbToKg
import com.erv.app.data.WorkoutModality
import java.util.UUID
import kotlin.math.abs

private const val MAX_MANUAL_NAME_LEN = 100

private fun parsePositiveWeightToKg(raw: String, unit: BodyWeightUnit): Double? {
    val v = raw.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (v <= 0) return null
    return when (unit) {
        BodyWeightUnit.KG -> v
        BodyWeightUnit.LB -> lbToKg(v)
    }
}

@Composable
fun ModalityPicker(
    modalities: Set<WorkoutModality>,
    onChange: (Set<WorkoutModality>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Useful for",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        WorkoutModality.entries.forEach { m ->
            val selected = m in modalities
            FilterChip(
                selected = selected,
                onClick = {
                    onChange(if (selected) modalities - m else modalities + m)
                },
                label = { Text(m.displayLabel()) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ManualEquipmentDialog(
    existingId: String?,
    nameDraft: String,
    initialModalities: Set<WorkoutModality>,
    onDismiss: () -> Unit,
    onSave: (String, Set<WorkoutModality>) -> Unit,
) {
    var name by remember(existingId) { mutableStateOf(nameDraft) }
    var modalities by remember(existingId) { mutableStateOf(initialModalities) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingId == null) "Add Other Equipment" else "Edit Equipment") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_MANUAL_NAME_LEN) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ModalityPicker(modalities = modalities, onChange = { modalities = it })
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, modalities) },
                enabled = name.trim().isNotEmpty()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun BarbellEquipmentDialog(
    existingId: String?,
    initial: BarbellOwnership?,
    initialModalities: Set<WorkoutModality>,
    weightUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onSave: (OwnedEquipmentItem) -> Unit,
) {
    var selectedType by remember(existingId) {
        mutableStateOf(initial?.barType ?: StandardBarbellType.OLYMPIC_MENS)
    }
    var customNameText by remember(existingId) {
        mutableStateOf(
            initial?.takeIf { it.barType == StandardBarbellType.CUSTOM }?.customName?.trim().orEmpty()
        )
    }
    var customWeightText by remember(existingId) {
        mutableStateOf(
            initial?.takeIf { it.barType == StandardBarbellType.CUSTOM }?.customWeightKg?.let { kg ->
                when (weightUnit) {
                    BodyWeightUnit.KG -> String.format("%.1f", kg).trimEnd('0').trimEnd('.')
                    BodyWeightUnit.LB -> String.format("%.1f", kgToLb(kg)).trimEnd('0').trimEnd('.')
                }
            }.orEmpty()
        )
    }
    var modalities by remember(existingId) { mutableStateOf(initialModalities) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingId == null) "Add Barbell" else "Edit Barbell") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Pick the bar you use most often. Typical unloaded weights are shown for common bars.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(Modifier.selectableGroup()) {
                    StandardBarbellType.entries.forEach { type ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectedType = type },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedType == type,
                                onClick = { selectedType = type }
                            )
                            Text(
                                type.label(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
                if (selectedType == StandardBarbellType.CUSTOM) {
                    OutlinedTextField(
                        value = customNameText,
                        onValueChange = { customNameText = it },
                        label = { Text("Bar name") },
                        placeholder = { Text("e.g. REP Open Trap, Eleiko, garage bar") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customWeightText,
                        onValueChange = { customWeightText = it },
                        label = {
                            Text(
                                when (weightUnit) {
                                    BodyWeightUnit.KG -> "Bar weight (kg)"
                                    BodyWeightUnit.LB -> "Bar weight (lb)"
                                }
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ModalityPicker(modalities = modalities, onChange = { modalities = it })
            }
        },
        confirmButton = {
            val customKgParsed = parsePositiveWeightToKg(customWeightText, weightUnit)
            val customOk = selectedType != StandardBarbellType.CUSTOM ||
                (customKgParsed != null && customNameText.trim().isNotEmpty())
            Button(
                onClick = {
                    val customKg = if (selectedType == StandardBarbellType.CUSTOM) {
                        parsePositiveWeightToKg(customWeightText, weightUnit)
                    } else null
                    if (selectedType == StandardBarbellType.CUSTOM && (customKg == null || customNameText.trim().isEmpty())) {
                        return@Button
                    }
                    val ownership = BarbellOwnership(
                        barType = selectedType,
                        customWeightKg = if (selectedType == StandardBarbellType.CUSTOM) customKg else null,
                        customName = if (selectedType == StandardBarbellType.CUSTOM) customNameText.trim() else null
                    )
                    val id = existingId ?: UUID.randomUUID().toString()
                    val item = OwnedEquipmentItem(
                        id = id,
                        name = "",
                        modalities = modalities,
                        catalogKind = EquipmentCatalogKind.BARBELL,
                        barbell = ownership
                    ).let { it.copy(name = it.displayTitle(weightUnit)) }
                    onSave(item)
                },
                enabled = customOk
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun kgMatchesDumbbellPreset(kg: Double, presetKg: Double): Boolean = abs(kg - presetKg) < 0.06

private fun removeHeavyDumbbellsFromSelection(selected: Set<Double>, heavyPresets: List<Double>): Set<Double> =
    selected.filter { s -> !heavyPresets.any { h -> kgMatchesDumbbellPreset(s, h) } }.toSet()

@Composable
fun DumbbellEquipmentDialog(
    existingId: String?,
    initial: DumbbellOwnership?,
    initialModalities: Set<WorkoutModality>,
    weightUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onSave: (OwnedEquipmentItem) -> Unit,
) {
    var mode by remember(existingId) {
        mutableStateOf(initial?.mode ?: DumbbellOwnershipMode.FIXED_PAIRS)
    }
    val standardPresetsKg = remember(weightUnit) {
        when (weightUnit) {
            BodyWeightUnit.LB -> FitnessEquipmentPresets.dumbbellPairsLb()
            BodyWeightUnit.KG -> FitnessEquipmentPresets.dumbbellPairsKg()
        }
    }
    val heavyPresetsKg = remember(weightUnit) {
        when (weightUnit) {
            BodyWeightUnit.LB -> FitnessEquipmentPresets.dumbbellPairsLbHeavy()
            BodyWeightUnit.KG -> FitnessEquipmentPresets.dumbbellPairsKgHeavy()
        }
    }
    var selectedPairs by remember(existingId, weightUnit) {
        mutableStateOf(initial?.pairWeightsKg?.toSet() ?: emptySet())
    }
    var showHeavyPairs by remember(existingId, weightUnit, heavyPresetsKg) {
        mutableStateOf(
            initial?.pairWeightsKg?.let { saved ->
                saved.any { s -> heavyPresetsKg.any { h -> kgMatchesDumbbellPreset(s, h) } }
            } ?: false
        )
    }
    var selMin by remember(existingId, weightUnit) {
        mutableStateOf(
            initial?.selectorizedMinKg?.let { kg ->
                when (weightUnit) {
                    BodyWeightUnit.KG -> String.format("%.1f", kg).trimEnd('0').trimEnd('.')
                    BodyWeightUnit.LB -> String.format("%.0f", kgToLb(kg))
                }
            }.orEmpty().ifEmpty {
                when (weightUnit) {
                    BodyWeightUnit.KG -> "5"
                    BodyWeightUnit.LB -> "5"
                }
            }
        )
    }
    var selMax by remember(existingId, weightUnit) {
        mutableStateOf(
            initial?.selectorizedMaxKg?.let { kg ->
                when (weightUnit) {
                    BodyWeightUnit.KG -> String.format("%.1f", kg).trimEnd('0').trimEnd('.')
                    BodyWeightUnit.LB -> String.format("%.0f", kgToLb(kg))
                }
            }.orEmpty().ifEmpty {
                when (weightUnit) {
                    BodyWeightUnit.KG -> "25"
                    BodyWeightUnit.LB -> "50"
                }
            }
        )
    }
    var selInc by remember(existingId, weightUnit) {
        mutableStateOf(
            initial?.selectorizedIncrementKg?.let { kg ->
                when (weightUnit) {
                    BodyWeightUnit.KG -> String.format("%.1f", kg).trimEnd('0').trimEnd('.')
                    BodyWeightUnit.LB -> String.format("%.0f", kgToLb(kg))
                }
            }.orEmpty().ifEmpty {
                when (weightUnit) {
                    BodyWeightUnit.KG -> "2.5"
                    BodyWeightUnit.LB -> "5"
                }
            }
        )
    }
    var modalities by remember(existingId) { mutableStateOf(initialModalities) }

    val canSaveFixed = mode != DumbbellOwnershipMode.FIXED_PAIRS || selectedPairs.isNotEmpty()
    val minKg = parsePositiveWeightToKg(selMin, weightUnit)
    val maxKg = parsePositiveWeightToKg(selMax, weightUnit)
    val incKg = parsePositiveWeightToKg(selInc, weightUnit)
    val canSaveSelectorized = mode != DumbbellOwnershipMode.SELECTORIZED ||
        (minKg != null && maxKg != null && incKg != null && maxKg > minKg && incKg <= (maxKg - minKg))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingId == null) "Add Dumbbells" else "Edit Dumbbells") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Fixed pairs: tap each weight you own (per dumbbell). Selectorized: min, max, and step.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = mode == DumbbellOwnershipMode.FIXED_PAIRS,
                        onClick = { mode = DumbbellOwnershipMode.FIXED_PAIRS },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("Fixed pairs") }
                    SegmentedButton(
                        selected = mode == DumbbellOwnershipMode.SELECTORIZED,
                        onClick = { mode = DumbbellOwnershipMode.SELECTORIZED },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("Selectorized") }
                }
                when (mode) {
                    DumbbellOwnershipMode.FIXED_PAIRS -> {
                        Text(
                            "Quick select",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (weightUnit) {
                                BodyWeightUnit.LB -> {
                                    listOf(
                                        "5–25" to {
                                            selectedPairs = selectedPairs +
                                                (5..25 step 5).map { lbToKg(it.toDouble()) }.toSet()
                                        },
                                        "5–50" to {
                                            selectedPairs = selectedPairs +
                                                (5..50 step 5).map { lbToKg(it.toDouble()) }.toSet()
                                        },
                                        "5–75" to {
                                            selectedPairs = selectedPairs +
                                                (5..75 step 5).map { lbToKg(it.toDouble()) }.toSet()
                                        },
                                        "5–100" to {
                                            selectedPairs = selectedPairs +
                                                (5..100 step 5).map { lbToKg(it.toDouble()) }.toSet()
                                        },
                                    ).forEach { (label, onClick) ->
                                        FilledTonalButton(onClick = onClick) { Text("$label lb") }
                                    }
                                }
                                BodyWeightUnit.KG -> {
                                    listOf(
                                        "2.5–15" to Pair(2.5, 15.0),
                                        "2.5–25" to Pair(2.5, 25.0),
                                        "2.5–35" to Pair(2.5, 35.0),
                                        "2.5–45" to Pair(2.5, 45.0),
                                    ).forEach { (label, range) ->
                                        FilledTonalButton(
                                            onClick = {
                                                val add = standardPresetsKg
                                                    .filter { it >= range.first - 0.01 && it <= range.second + 0.01 }
                                                    .toSet()
                                                selectedPairs = selectedPairs + add
                                            }
                                        ) { Text("$label kg") }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Standard pairs",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            standardPresetsKg.forEach { kg ->
                                val selected = selectedPairs.any { s -> kgMatchesDumbbellPreset(s, kg) }
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedPairs = if (selected) {
                                            selectedPairs.filter { s -> !kgMatchesDumbbellPreset(s, kg) }.toSet()
                                        } else {
                                            selectedPairs + kg
                                        }
                                    },
                                    label = { Text(formatWeightValue(kg, weightUnit)) }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        FilterChip(
                            selected = showHeavyPairs,
                            onClick = {
                                val next = !showHeavyPairs
                                showHeavyPairs = next
                                if (!next) {
                                    selectedPairs = removeHeavyDumbbellsFromSelection(selectedPairs, heavyPresetsKg)
                                }
                            },
                            label = {
                                Text(
                                    when (weightUnit) {
                                        BodyWeightUnit.LB -> "Strongman: heavy pairs (105–200 lb)"
                                        BodyWeightUnit.KG -> "Strongman: heavy pairs (47.5–90 kg)"
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (showHeavyPairs) {
                            Text(
                                "Add each heavy pair you own (per dumbbell).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                heavyPresetsKg.forEach { kg ->
                                    val selected = selectedPairs.any { s -> kgMatchesDumbbellPreset(s, kg) }
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            selectedPairs = if (selected) {
                                                selectedPairs.filter { s -> !kgMatchesDumbbellPreset(s, kg) }.toSet()
                                            } else {
                                                selectedPairs + kg
                                            }
                                        },
                                        label = { Text(formatWeightValue(kg, weightUnit)) }
                                    )
                                }
                            }
                        }
                    }
                    DumbbellOwnershipMode.SELECTORIZED -> {
                        OutlinedTextField(
                            value = selMin,
                            onValueChange = { selMin = it },
                            label = { Text("Min (${if (weightUnit == BodyWeightUnit.KG) "kg" else "lb"})") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = selMax,
                            onValueChange = { selMax = it },
                            label = { Text("Max (${if (weightUnit == BodyWeightUnit.KG) "kg" else "lb"})") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = selInc,
                            onValueChange = { selInc = it },
                            label = { Text("Increment (${if (weightUnit == BodyWeightUnit.KG) "kg" else "lb"})") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                ModalityPicker(modalities = modalities, onChange = { modalities = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ownership = when (mode) {
                        DumbbellOwnershipMode.FIXED_PAIRS -> DumbbellOwnership(
                            mode = mode,
                            pairWeightsKg = selectedPairs.sorted()
                        )
                        DumbbellOwnershipMode.SELECTORIZED -> DumbbellOwnership(
                            mode = mode,
                            selectorizedMinKg = minKg,
                            selectorizedMaxKg = maxKg,
                            selectorizedIncrementKg = incKg
                        )
                    }
                    val id = existingId ?: UUID.randomUUID().toString()
                    val item = OwnedEquipmentItem(
                        id = id,
                        name = "",
                        modalities = modalities,
                        catalogKind = EquipmentCatalogKind.DUMBBELLS,
                        dumbbells = ownership
                    ).let { it.copy(name = it.displayTitle(weightUnit)) }
                    onSave(item)
                },
                enabled = canSaveFixed && canSaveSelectorized
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun KettlebellEquipmentDialog(
    existingId: String?,
    initial: KettlebellOwnership?,
    initialModalities: Set<WorkoutModality>,
    weightUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onSave: (OwnedEquipmentItem) -> Unit,
) {
    val presets = remember { FitnessEquipmentPresets.kettlebellsKg() }
    var selected by remember(existingId) {
        mutableStateOf(initial?.weightsKg?.toSet() ?: emptySet())
    }
    var modalities by remember(existingId) { mutableStateOf(initialModalities) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingId == null) "Add Kettlebells" else "Edit Kettlebells") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Select each kettlebell weight you have. Sizes are shown in kg — most kettlebells are sold in kg even in the US.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { kg ->
                        val on = kg in selected
                        FilterChip(
                            selected = on,
                            onClick = {
                                selected = if (on) selected - kg else selected + kg
                            },
                            label = { Text(formatWeightValue(kg, BodyWeightUnit.KG)) }
                        )
                    }
                }
                ModalityPicker(modalities = modalities, onChange = { modalities = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ownership = KettlebellOwnership(weightsKg = selected.sorted())
                    val id = existingId ?: UUID.randomUUID().toString()
                    val item = OwnedEquipmentItem(
                        id = id,
                        name = "",
                        modalities = modalities,
                        catalogKind = EquipmentCatalogKind.KETTLEBELLS,
                        kettlebells = ownership
                    ).let { it.copy(name = it.displayTitle(weightUnit)) }
                    onSave(item)
                },
                enabled = selected.isNotEmpty()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PlateEquipmentDialog(
    existingId: String?,
    initial: PlateOwnership?,
    initialModalities: Set<WorkoutModality>,
    weightUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onSave: (OwnedEquipmentItem) -> Unit,
) {
    val presets = remember(weightUnit) {
        when (weightUnit) {
            BodyWeightUnit.LB -> FitnessEquipmentPresets.platesLb()
            BodyWeightUnit.KG -> FitnessEquipmentPresets.platesKg()
        }
    }
    var pairCounts by remember(existingId, weightUnit, presets) {
        mutableStateOf(
            presets.associateWith { presetKg ->
                initial?.resolvedPlatePairs()?.firstOrNull { abs(it.weightKg - presetKg) < 0.06 }?.pairCount ?: 0
            }
        )
    }
    var modalities by remember(existingId) { mutableStateOf(initialModalities) }

    fun setPairCount(kg: Double, count: Int) {
        val c = count.coerceIn(0, 99)
        pairCounts = pairCounts + (kg to c)
    }

    val hasAnyPairs = pairCounts.values.any { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingId == null) "Add Plates" else "Edit Plates") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Set how many pairs of each plate size you have. One pair is two plates (one per side of the bar).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { kg ->
                        val count = pairCounts[kg] ?: 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                formatWeightValue(kg, weightUnit),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { setPairCount(kg, count - 1) },
                                enabled = count > 0
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Decrease pairs")
                            }
                            Text(
                                "$count",
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(
                                onClick = { setPairCount(kg, count + 1) },
                                enabled = count < 99
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Increase pairs")
                            }
                            Text(
                                "pairs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(40.dp)
                            )
                        }
                    }
                }
                ModalityPicker(modalities = modalities, onChange = { modalities = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ownership = PlateOwnership(
                        pairs = presets.mapNotNull { w ->
                            val c = pairCounts[w] ?: 0
                            if (c > 0) PlatePairEntry(w, c) else null
                        }.sortedByDescending { it.weightKg },
                        plateWeightsKg = emptyList()
                    )
                    val id = existingId ?: UUID.randomUUID().toString()
                    val item = OwnedEquipmentItem(
                        id = id,
                        name = "",
                        modalities = modalities,
                        catalogKind = EquipmentCatalogKind.PLATES,
                        plates = ownership
                    ).let { it.copy(name = it.displayTitle(weightUnit)) }
                    onSave(item)
                },
                enabled = hasAnyPairs
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun BandEquipmentDialog(
    existingId: String?,
    initial: BandOwnership?,
    initialModalities: Set<WorkoutModality>,
    weightUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onSave: (OwnedEquipmentItem) -> Unit,
) {
    var tiers by remember(existingId) {
        mutableStateOf(initial?.tiers ?: emptySet())
    }
    var miniLoop by remember(existingId) { mutableStateOf(initial?.hasMiniLoopSet == true) }
    var longLoop by remember(existingId) { mutableStateOf(initial?.hasLongLoopBand == true) }
    var pullAssist by remember(existingId) { mutableStateOf(initial?.hasPullUpAssist == true) }
    var tubeHandles by remember(existingId) { mutableStateOf(initial?.hasTubeHandles == true) }
    var modalities by remember(existingId) { mutableStateOf(initialModalities) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingId == null) "Add Bands" else "Edit Bands") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Resistance tiers are approximate; include specialty bands you own.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BandResistanceTier.entries.forEach { tier ->
                    val on = tier in tiers
                    FilterChip(
                        selected = on,
                        onClick = { tiers = if (on) tiers - tier else tiers + tier },
                        label = { Text(tier.label()) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                FilterChip(
                    selected = miniLoop,
                    onClick = { miniLoop = !miniLoop },
                    label = { Text("Mini loop / hip band set") },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = longLoop,
                    onClick = { longLoop = !longLoop },
                    label = { Text("Long loop / pull-down band") },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = pullAssist,
                    onClick = { pullAssist = !pullAssist },
                    label = { Text("Pull-up assist band") },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = tubeHandles,
                    onClick = { tubeHandles = !tubeHandles },
                    label = { Text("Tube bands with handles") },
                    modifier = Modifier.fillMaxWidth()
                )
                ModalityPicker(modalities = modalities, onChange = { modalities = it })
            }
        },
        confirmButton = {
            val hasSomething = tiers.isNotEmpty() || miniLoop || longLoop || pullAssist || tubeHandles
            Button(
                onClick = {
                    val ownership = BandOwnership(
                        tiers = tiers,
                        hasMiniLoopSet = miniLoop,
                        hasLongLoopBand = longLoop,
                        hasPullUpAssist = pullAssist,
                        hasTubeHandles = tubeHandles
                    )
                    val id = existingId ?: UUID.randomUUID().toString()
                    val item = OwnedEquipmentItem(
                        id = id,
                        name = "",
                        modalities = modalities,
                        catalogKind = EquipmentCatalogKind.BANDS,
                        bands = ownership
                    ).let { it.copy(name = it.displayTitle(weightUnit)) }
                    onSave(item)
                },
                enabled = hasSomething
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
