@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.erv.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erv.app.data.BattleRopeHeft
import com.erv.app.data.BattleRopeOwnership
import com.erv.app.data.BenchOwnership
import com.erv.app.data.BenchType
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.CableStationOwnership
import com.erv.app.data.CableStationType
import com.erv.app.data.CardioMachineKind
import com.erv.app.data.CardioMachinesOwnership
import com.erv.app.data.EquipmentCatalogKind
import com.erv.app.data.FitnessEquipmentPresets
import com.erv.app.data.JumpRopeOwnership
import com.erv.app.data.JumpRopeStyle
import com.erv.app.data.MedicineBallOwnership
import com.erv.app.data.MobilityToolsOwnership
import com.erv.app.data.OwnedEquipmentItem
import com.erv.app.data.ParallettesRingsOwnership
import com.erv.app.data.PlyoBoxKind
import com.erv.app.data.PlyoBoxOwnership
import com.erv.app.data.PullUpOwnership
import com.erv.app.data.PullUpStationOption
import com.erv.app.data.SquatRackOwnership
import com.erv.app.data.SquatRackType
import com.erv.app.data.SuspensionAnchorKind
import com.erv.app.data.SuspensionTrainerOwnership
import com.erv.app.data.WorkoutModality
import com.erv.app.data.displayTitle
import com.erv.app.data.formatWeightValue
import com.erv.app.data.label
import java.util.UUID

private fun buildCatalogItem(
    id: String?,
    catalogKind: EquipmentCatalogKind,
    weightUnit: BodyWeightUnit,
    modalities: Set<WorkoutModality>,
    block: (OwnedEquipmentItem) -> OwnedEquipmentItem,
): OwnedEquipmentItem {
    val idResolved = id ?: UUID.randomUUID().toString()
    val base = OwnedEquipmentItem(
        id = idResolved,
        name = "",
        modalities = modalities,
        catalogKind = catalogKind
    )
    val built = block(base)
    return built.copy(name = built.displayTitle(weightUnit))
}

@Composable
fun BenchEquipmentDialog(
    existingId: String?,
    initial: BenchOwnership?,
    initialModalities: Set<WorkoutModality>,
    weightUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onSave: (OwnedEquipmentItem) -> Unit,
) {
    var selected by remember(existingId) { mutableStateOf(initial?.benchType ?: BenchType.FLAT_ONLY) }
    var modalities by remember(existingId) { mutableStateOf(initialModalities) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingId == null) "Add Bench" else "Edit Bench") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Pick the style that matches your bench.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(Modifier.selectableGroup()) {
                    BenchType.entries.forEach { type ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = type },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected == type, onClick = { selected = type })
                            Text(type.label(), modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
                ModalityPicker(modalities = modalities, onChange = { modalities = it })
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    buildCatalogItem(existingId, EquipmentCatalogKind.BENCH, weightUnit, modalities) {
                        it.copy(bench = BenchOwnership(selected))
                    }
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SquatRackEquipmentDialog(
    existingId: String?,
    initial: SquatRackOwnership?,
    initialModalities: Set<WorkoutModality>,
    weightUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onSave: (OwnedEquipmentItem) -> Unit,
) {
    var selected by remember(existingId) { mutableStateOf(initial?.rackType ?: SquatRackType.HALF_RACK) }
    var modalities by remember(existingId) { mutableStateOf(initialModalities) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingId == null) "Add Squat Rack / Cage" else "Edit Squat Rack / Cage") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Pick the rack setup you use most.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(Modifier.selectableGroup()) {
                    SquatRackType.entries.forEach { type ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = type },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected == type, onClick = { selected = type })
                            Text(type.label(), modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
                ModalityPicker(modalities = modalities, onChange = { modalities = it })
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    buildCatalogItem(existingId, EquipmentCatalogKind.SQUAT_RACK, weightUnit, modalities) {
                        it.copy(squatRack = SquatRackOwnership(selected))
                    }
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun PullUpDipEquipmentDialog(
    existingId: String?,
    initial: PullUpOwnership?,
    initialModalities: Set<WorkoutModality>,
    weightUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onSave: (OwnedEquipmentItem) -> Unit,
) {
    var selected by remember(existingId) { mutableStateOf(initial?.options ?: emptySet()) }
    var modalities by remember(existingId) { mutableStateOf(initialModalities) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingId == null) "Add Pull-Up & Dip" else "Edit Pull-Up & Dip") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Select everything you have.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PullUpStationOption.entries.forEach { opt ->
                        val on = opt in selected
                        FilterChip(
                            selected = on,
                            onClick = { selected = if (on) selected - opt else selected + opt },
                            label = { Text(opt.label()) }
                        )
                    }
                }
                ModalityPicker(modalities = modalities, onChange = { modalities = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        buildCatalogItem(existingId, EquipmentCatalogKind.PULL_UP_DIP, weightUnit, modalities) {
                            it.copy(pullUp = PullUpOwnership(selected))
                        }
                    )
                },
                enabled = selected.isNotEmpty()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CardioMachinesEquipmentDialog(
    existingId: String?,
    initial: CardioMachinesOwnership?,
    initialModalities: Set<WorkoutModality>,
    weightUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onSave: (OwnedEquipmentItem) -> Unit,
) {
    var selected by remember(existingId) { mutableStateOf(initial?.machines ?: emptySet()) }
    var modalities by remember(existingId) { mutableStateOf(initialModalities) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingId == null) "Add Cardio Machines" else "Edit Cardio Machines") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Select machines you have at home (or skip if you only use gym cardio).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CardioMachineKind.entries.forEach { k ->
                        val on = k in selected
                        FilterChip(
                            selected = on,
                            onClick = { selected = if (on) selected - k else selected + k },
                            label = { Text(k.label()) }
                        )
                    }
                }
                ModalityPicker(modalities = modalities, onChange = { modalities = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        buildCatalogItem(existingId, EquipmentCatalogKind.CARDIO_MACHINES, weightUnit, modalities) {
                            it.copy(cardioMachines = CardioMachinesOwnership(selected))
                        }
                    )
                },
                enabled = selected.isNotEmpty()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CableStationEquipmentDialog(
    existingId: String?,
    initial: CableStationOwnership?,
    initialModalities: Set<WorkoutModality>,
    weightUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onSave: (OwnedEquipmentItem) -> Unit,
) {
    var selected by remember(existingId) { mutableStateOf(initial?.stationType ?: CableStationType.DUAL_ADJUSTABLE) }
    var modalities by remember(existingId) { mutableStateOf(initialModalities) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingId == null) "Add Cable Station" else "Edit Cable Station") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Pick the style closest to your setup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(Modifier.selectableGroup()) {
                    CableStationType.entries.forEach { type ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = type },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected == type, onClick = { selected = type })
                            Text(type.label(), modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
                ModalityPicker(modalities = modalities, onChange = { modalities = it })
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    buildCatalogItem(existingId, EquipmentCatalogKind.CABLE_STATION, weightUnit, modalities) {
                        it.copy(cableStation = CableStationOwnership(selected))
                    }
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun JumpRopeEquipmentDialog(
    existingId: String?,
    initial: JumpRopeOwnership?,
    initialModalities: Set<WorkoutModality>,
    weightUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onSave: (OwnedEquipmentItem) -> Unit,
) {
    var selected by remember(existingId) { mutableStateOf(initial?.styles ?: emptySet()) }
    var modalities by remember(existingId) { mutableStateOf(initialModalities) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingId == null) "Add Jump Rope" else "Edit Jump Rope") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    JumpRopeStyle.entries.forEach { s ->
                        val on = s in selected
                        FilterChip(
                            selected = on,
                            onClick = { selected = if (on) selected - s else selected + s },
                            label = { Text(s.label()) }
                        )
                    }
                }
                ModalityPicker(modalities = modalities, onChange = { modalities = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        buildCatalogItem(existingId, EquipmentCatalogKind.JUMP_ROPE, weightUnit, modalities) {
                            it.copy(jumpRope = JumpRopeOwnership(selected))
                        }
                    )
                },
                enabled = selected.isNotEmpty()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun MedicineBallEquipmentDialog(
    existingId: String?,
    initial: MedicineBallOwnership?,
    initialModalities: Set<WorkoutModality>,
    weightUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onSave: (OwnedEquipmentItem) -> Unit,
) {
    val presets = remember(weightUnit) {
        when (weightUnit) {
            BodyWeightUnit.LB -> FitnessEquipmentPresets.medicineBallsLb()
            BodyWeightUnit.KG -> FitnessEquipmentPresets.medicineBallsKg()
        }
    }
    var selected by remember(existingId, weightUnit) {
        mutableStateOf(initial?.ballWeightsKg?.toSet() ?: emptySet())
    }
    var modalities by remember(existingId) { mutableStateOf(initialModalities) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingId == null) "Add Medicine Balls" else "Edit Medicine Balls") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Select each ball weight you have (slam or wall balls).",
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
                            onClick = { selected = if (on) selected - kg else selected + kg },
                            label = { Text(formatWeightValue(kg, weightUnit)) }
                        )
                    }
                }
                ModalityPicker(modalities = modalities, onChange = { modalities = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        buildCatalogItem(existingId, EquipmentCatalogKind.MEDICINE_BALL, weightUnit, modalities) {
                            it.copy(medicineBalls = MedicineBallOwnership(ballWeightsKg = selected.sorted()))
                        }
                    )
                },
                enabled = selected.isNotEmpty()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SuspensionTrainerEquipmentDialog(
    existingId: String?,
    initial: SuspensionTrainerOwnership?,
    initialModalities: Set<WorkoutModality>,
    weightUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onSave: (OwnedEquipmentItem) -> Unit,
) {
    var selected by remember(existingId) { mutableStateOf(initial?.anchors ?: emptySet()) }
    var modalities by remember(existingId) { mutableStateOf(initialModalities) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingId == null) "Add Suspension Trainer" else "Edit Suspension Trainer") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Where can you anchor TRX-style straps?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SuspensionAnchorKind.entries.forEach { a ->
                    val on = a in selected
                    FilterChip(
                        selected = on,
                        onClick = { selected = if (on) selected - a else selected + a },
                        label = { Text(a.label()) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ModalityPicker(modalities = modalities, onChange = { modalities = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        buildCatalogItem(existingId, EquipmentCatalogKind.SUSPENSION_TRAINER, weightUnit, modalities) {
                            it.copy(suspensionTrainer = SuspensionTrainerOwnership(selected))
                        }
                    )
                },
                enabled = selected.isNotEmpty()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun PlyoBoxEquipmentDialog(
    existingId: String?,
    initial: PlyoBoxOwnership?,
    initialModalities: Set<WorkoutModality>,
    weightUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onSave: (OwnedEquipmentItem) -> Unit,
) {
    var selected by remember(existingId) { mutableStateOf(initial?.kinds ?: emptySet()) }
    var modalities by remember(existingId) { mutableStateOf(initialModalities) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingId == null) "Add Plyo Box" else "Edit Plyo Box") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PlyoBoxKind.entries.forEach { k ->
                        val on = k in selected
                        FilterChip(
                            selected = on,
                            onClick = { selected = if (on) selected - k else selected + k },
                            label = { Text(k.label()) }
                        )
                    }
                }
                ModalityPicker(modalities = modalities, onChange = { modalities = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        buildCatalogItem(existingId, EquipmentCatalogKind.PLYO_BOX, weightUnit, modalities) {
                            it.copy(plyoBox = PlyoBoxOwnership(selected))
                        }
                    )
                },
                enabled = selected.isNotEmpty()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun BattleRopesEquipmentDialog(
    existingId: String?,
    initial: BattleRopeOwnership?,
    initialModalities: Set<WorkoutModality>,
    weightUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onSave: (OwnedEquipmentItem) -> Unit,
) {
    var selected by remember(existingId) { mutableStateOf(initial?.heft ?: emptySet()) }
    var modalities by remember(existingId) { mutableStateOf(initialModalities) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingId == null) "Add Battle Ropes" else "Edit Battle Ropes") },
        text = {
            Column(
                Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BattleRopeHeft.entries.forEach { h ->
                    val on = h in selected
                    FilterChip(
                        selected = on,
                        onClick = { selected = if (on) selected - h else selected + h },
                        label = { Text(h.label()) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ModalityPicker(modalities = modalities, onChange = { modalities = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        buildCatalogItem(existingId, EquipmentCatalogKind.BATTLE_ROPES, weightUnit, modalities) {
                            it.copy(battleRopes = BattleRopeOwnership(selected))
                        }
                    )
                },
                enabled = selected.isNotEmpty()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun MobilityToolsEquipmentDialog(
    existingId: String?,
    initial: MobilityToolsOwnership?,
    initialModalities: Set<WorkoutModality>,
    weightUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onSave: (OwnedEquipmentItem) -> Unit,
) {
    var foam by remember(existingId) { mutableStateOf(initial?.foamRoller == true) }
    var lax by remember(existingId) { mutableStateOf(initial?.lacrosseBall == true) }
    var peanut by remember(existingId) { mutableStateOf(initial?.peanutBall == true) }
    var gun by remember(existingId) { mutableStateOf(initial?.massageGun == true) }
    var modalities by remember(existingId) { mutableStateOf(initialModalities) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingId == null) "Add Mobility Tools" else "Edit Mobility Tools") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = foam,
                    onClick = { foam = !foam },
                    label = { Text("Foam roller") },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = lax,
                    onClick = { lax = !lax },
                    label = { Text("Lacrosse ball") },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = peanut,
                    onClick = { peanut = !peanut },
                    label = { Text("Peanut / double ball") },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = gun,
                    onClick = { gun = !gun },
                    label = { Text("Massage gun") },
                    modifier = Modifier.fillMaxWidth()
                )
                ModalityPicker(modalities = modalities, onChange = { modalities = it })
            }
        },
        confirmButton = {
            val ok = foam || lax || peanut || gun
            Button(
                onClick = {
                    onSave(
                        buildCatalogItem(existingId, EquipmentCatalogKind.MOBILITY_TOOLS, weightUnit, modalities) {
                            it.copy(
                                mobilityTools = MobilityToolsOwnership(
                                    foamRoller = foam,
                                    lacrosseBall = lax,
                                    peanutBall = peanut,
                                    massageGun = gun
                                )
                            )
                        }
                    )
                },
                enabled = ok
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ParallettesRingsEquipmentDialog(
    existingId: String?,
    initial: ParallettesRingsOwnership?,
    initialModalities: Set<WorkoutModality>,
    weightUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onSave: (OwnedEquipmentItem) -> Unit,
) {
    var par by remember(existingId) { mutableStateOf(initial?.parallettes == true) }
    var rings by remember(existingId) { mutableStateOf(initial?.gymnasticRings == true) }
    var modalities by remember(existingId) { mutableStateOf(initialModalities) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingId == null) "Add Parallettes & Rings" else "Edit Parallettes & Rings") },
        text = {
            Column(
                Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = par,
                    onClick = { par = !par },
                    label = { Text("Parallettes") },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = rings,
                    onClick = { rings = !rings },
                    label = { Text("Gymnastic rings") },
                    modifier = Modifier.fillMaxWidth()
                )
                ModalityPicker(modalities = modalities, onChange = { modalities = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        buildCatalogItem(existingId, EquipmentCatalogKind.PARALLETTE_RINGS, weightUnit, modalities) {
                            it.copy(
                                parallettesRings = ParallettesRingsOwnership(
                                    parallettes = par,
                                    gymnasticRings = rings
                                )
                            )
                        }
                    )
                },
                enabled = par || rings
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
