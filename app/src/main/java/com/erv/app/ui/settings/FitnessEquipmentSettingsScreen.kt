@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.erv.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erv.app.data.BarbellOwnership
import com.erv.app.data.BandOwnership
import com.erv.app.data.BattleRopeOwnership
import com.erv.app.data.BenchOwnership
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.CableStationOwnership
import com.erv.app.data.CardioMachinesOwnership
import com.erv.app.data.DumbbellOwnership
import com.erv.app.data.EquipmentCatalogKind
import com.erv.app.data.JumpRopeOwnership
import com.erv.app.data.KettlebellOwnership
import com.erv.app.data.MedicineBallOwnership
import com.erv.app.data.MobilityToolsOwnership
import com.erv.app.data.OwnedEquipmentItem
import com.erv.app.data.ParallettesRingsOwnership
import com.erv.app.data.PlateOwnership
import com.erv.app.data.PlyoBoxOwnership
import com.erv.app.data.PullUpOwnership
import com.erv.app.data.SquatRackOwnership
import com.erv.app.data.SuspensionTrainerOwnership
import com.erv.app.data.WorkoutModality
import com.erv.app.data.displayLabel
import com.erv.app.data.displayTitle
import com.erv.app.data.summaryLine
import com.erv.app.weighttraining.availableWeightExercisePacks
import com.erv.app.weighttraining.weightExercisePackExerciseCount
import java.util.UUID

@Composable
fun FitnessEquipmentSettingsContent(
    gymMembership: Boolean,
    ownedEquipment: List<OwnedEquipmentItem>,
    enabledExercisePackIds: Set<String>,
    weightUnit: BodyWeightUnit,
    onGymMembershipChange: (Boolean) -> Unit,
    onEquipmentChange: (List<OwnedEquipmentItem>) -> Unit,
    onExercisePackIdsChange: (Set<String>) -> Unit,
) {
    var dialog by remember { mutableStateOf<EquipmentEditorDialog?>(null) }

    Text(
        "Your Equipment & Gym",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Tell the app what you have access to. This stays on your device and can guide " +
                    "tailored workouts from a future assistant.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        "Gym Membership",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "I have access to a full gym (machines, racks, cable, etc.)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = gymMembership,
                    onCheckedChange = onGymMembershipChange
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    Text(
        "Exercise Packs",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Turn on niche exercise libraries only when you have the specialty equipment. Disabled packs stay out of the main exercise list.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            availableWeightExercisePacks().forEach { pack ->
                val enabled = pack.id in enabledExercisePackIds
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            pack.title,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "${pack.description} ${weightExercisePackExerciseCount(pack.id)} exercises.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { isEnabled ->
                            val next = if (isEnabled) {
                                enabledExercisePackIds + pack.id
                            } else {
                                enabledExercisePackIds - pack.id
                            }
                            onExercisePackIdsChange(next)
                        }
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    Text(
        "Quick Add",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Pick a category — a form opens with common options. Weights follow your strength unit in " +
                    "Settings → Units & body.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickAddCategoryButton(
                    label = "Barbell",
                    ownedEquipment = ownedEquipment,
                    category = EquipmentCatalogKind.BARBELL,
                    onClick = {
                        dialog = EquipmentEditorDialog.Barbell(null, null, defaultModalities(EquipmentCatalogKind.BARBELL))
                    }
                )
                QuickAddCategoryButton(
                    label = "Dumbbells",
                    ownedEquipment = ownedEquipment,
                    category = EquipmentCatalogKind.DUMBBELLS,
                    onClick = {
                        dialog = EquipmentEditorDialog.Dumbbells(null, null, defaultModalities(EquipmentCatalogKind.DUMBBELLS))
                    }
                )
                QuickAddCategoryButton(
                    label = "Kettlebells",
                    ownedEquipment = ownedEquipment,
                    category = EquipmentCatalogKind.KETTLEBELLS,
                    onClick = {
                        dialog = EquipmentEditorDialog.Kettlebells(null, null, defaultModalities(EquipmentCatalogKind.KETTLEBELLS))
                    }
                )
                QuickAddCategoryButton(
                    label = "Plates",
                    ownedEquipment = ownedEquipment,
                    category = EquipmentCatalogKind.PLATES,
                    onClick = {
                        dialog = EquipmentEditorDialog.Plates(null, null, defaultModalities(EquipmentCatalogKind.PLATES))
                    }
                )
                QuickAddCategoryButton(
                    label = "Bands",
                    ownedEquipment = ownedEquipment,
                    category = EquipmentCatalogKind.BANDS,
                    onClick = {
                        dialog = EquipmentEditorDialog.Bands(null, null, defaultModalities(EquipmentCatalogKind.BANDS))
                    }
                )
                QuickAddCategoryButton(
                    label = "Bench",
                    ownedEquipment = ownedEquipment,
                    category = EquipmentCatalogKind.BENCH,
                    onClick = {
                        dialog = EquipmentEditorDialog.Bench(null, null, defaultModalities(EquipmentCatalogKind.BENCH))
                    }
                )
                QuickAddCategoryButton(
                    label = "Rack / Cage",
                    ownedEquipment = ownedEquipment,
                    category = EquipmentCatalogKind.SQUAT_RACK,
                    onClick = {
                        dialog = EquipmentEditorDialog.SquatRack(null, null, defaultModalities(EquipmentCatalogKind.SQUAT_RACK))
                    }
                )
                QuickAddCategoryButton(
                    label = "Pull-Up / Dip",
                    ownedEquipment = ownedEquipment,
                    category = EquipmentCatalogKind.PULL_UP_DIP,
                    onClick = {
                        dialog = EquipmentEditorDialog.PullUpDip(null, null, defaultModalities(EquipmentCatalogKind.PULL_UP_DIP))
                    }
                )
                QuickAddCategoryButton(
                    label = "Cardio",
                    ownedEquipment = ownedEquipment,
                    category = EquipmentCatalogKind.CARDIO_MACHINES,
                    onClick = {
                        dialog = EquipmentEditorDialog.CardioMachines(null, null, defaultModalities(EquipmentCatalogKind.CARDIO_MACHINES))
                    }
                )
                QuickAddCategoryButton(
                    label = "Cable",
                    ownedEquipment = ownedEquipment,
                    category = EquipmentCatalogKind.CABLE_STATION,
                    onClick = {
                        dialog = EquipmentEditorDialog.CableStation(null, null, defaultModalities(EquipmentCatalogKind.CABLE_STATION))
                    }
                )
                QuickAddCategoryButton(
                    label = "Jump Rope",
                    ownedEquipment = ownedEquipment,
                    category = EquipmentCatalogKind.JUMP_ROPE,
                    onClick = {
                        dialog = EquipmentEditorDialog.JumpRope(null, null, defaultModalities(EquipmentCatalogKind.JUMP_ROPE))
                    }
                )
                QuickAddCategoryButton(
                    label = "Med Ball",
                    ownedEquipment = ownedEquipment,
                    category = EquipmentCatalogKind.MEDICINE_BALL,
                    onClick = {
                        dialog = EquipmentEditorDialog.MedicineBall(null, null, defaultModalities(EquipmentCatalogKind.MEDICINE_BALL))
                    }
                )
                QuickAddCategoryButton(
                    label = "Suspension",
                    ownedEquipment = ownedEquipment,
                    category = EquipmentCatalogKind.SUSPENSION_TRAINER,
                    onClick = {
                        dialog = EquipmentEditorDialog.SuspensionTrainer(null, null, defaultModalities(EquipmentCatalogKind.SUSPENSION_TRAINER))
                    }
                )
                QuickAddCategoryButton(
                    label = "Plyo Box",
                    ownedEquipment = ownedEquipment,
                    category = EquipmentCatalogKind.PLYO_BOX,
                    onClick = {
                        dialog = EquipmentEditorDialog.PlyoBox(null, null, defaultModalities(EquipmentCatalogKind.PLYO_BOX))
                    }
                )
                QuickAddCategoryButton(
                    label = "Battle Ropes",
                    ownedEquipment = ownedEquipment,
                    category = EquipmentCatalogKind.BATTLE_ROPES,
                    onClick = {
                        dialog = EquipmentEditorDialog.BattleRopes(null, null, defaultModalities(EquipmentCatalogKind.BATTLE_ROPES))
                    }
                )
                QuickAddCategoryButton(
                    label = "Mobility",
                    ownedEquipment = ownedEquipment,
                    category = EquipmentCatalogKind.MOBILITY_TOOLS,
                    onClick = {
                        dialog = EquipmentEditorDialog.MobilityTools(null, null, defaultModalities(EquipmentCatalogKind.MOBILITY_TOOLS))
                    }
                )
                QuickAddCategoryButton(
                    label = "Parallettes / Rings",
                    ownedEquipment = ownedEquipment,
                    category = EquipmentCatalogKind.PARALLETTE_RINGS,
                    onClick = {
                        dialog = EquipmentEditorDialog.ParallettesRings(null, null, defaultModalities(EquipmentCatalogKind.PARALLETTE_RINGS))
                    }
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    Text(
        "Your inventory",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Add anything else (bench, bike, cable attachments, etc.) — or edit items from quick add.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (ownedEquipment.isEmpty()) {
                Text(
                    "No equipment added yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ownedEquipment.forEach { item ->
                    EquipmentRow(
                        item = item,
                        weightUnit = weightUnit,
                        onEdit = {
                            dialog = when (item.catalogKind) {
                                EquipmentCatalogKind.MANUAL -> EquipmentEditorDialog.Manual(
                                    item.id,
                                    item.name,
                                    item.modalities
                                )
                                EquipmentCatalogKind.BARBELL -> EquipmentEditorDialog.Barbell(
                                    item.id,
                                    item.barbell,
                                    item.modalities
                                )
                                EquipmentCatalogKind.DUMBBELLS -> EquipmentEditorDialog.Dumbbells(
                                    item.id,
                                    item.dumbbells,
                                    item.modalities
                                )
                                EquipmentCatalogKind.KETTLEBELLS -> EquipmentEditorDialog.Kettlebells(
                                    item.id,
                                    item.kettlebells,
                                    item.modalities
                                )
                                EquipmentCatalogKind.PLATES -> EquipmentEditorDialog.Plates(
                                    item.id,
                                    item.plates,
                                    item.modalities
                                )
                                EquipmentCatalogKind.BANDS -> EquipmentEditorDialog.Bands(
                                    item.id,
                                    item.bands,
                                    item.modalities
                                )
                                EquipmentCatalogKind.BENCH -> EquipmentEditorDialog.Bench(
                                    item.id,
                                    item.bench,
                                    item.modalities
                                )
                                EquipmentCatalogKind.SQUAT_RACK -> EquipmentEditorDialog.SquatRack(
                                    item.id,
                                    item.squatRack,
                                    item.modalities
                                )
                                EquipmentCatalogKind.PULL_UP_DIP -> EquipmentEditorDialog.PullUpDip(
                                    item.id,
                                    item.pullUp,
                                    item.modalities
                                )
                                EquipmentCatalogKind.CARDIO_MACHINES -> EquipmentEditorDialog.CardioMachines(
                                    item.id,
                                    item.cardioMachines,
                                    item.modalities
                                )
                                EquipmentCatalogKind.CABLE_STATION -> EquipmentEditorDialog.CableStation(
                                    item.id,
                                    item.cableStation,
                                    item.modalities
                                )
                                EquipmentCatalogKind.JUMP_ROPE -> EquipmentEditorDialog.JumpRope(
                                    item.id,
                                    item.jumpRope,
                                    item.modalities
                                )
                                EquipmentCatalogKind.MEDICINE_BALL -> EquipmentEditorDialog.MedicineBall(
                                    item.id,
                                    item.medicineBalls,
                                    item.modalities
                                )
                                EquipmentCatalogKind.SUSPENSION_TRAINER -> EquipmentEditorDialog.SuspensionTrainer(
                                    item.id,
                                    item.suspensionTrainer,
                                    item.modalities
                                )
                                EquipmentCatalogKind.PLYO_BOX -> EquipmentEditorDialog.PlyoBox(
                                    item.id,
                                    item.plyoBox,
                                    item.modalities
                                )
                                EquipmentCatalogKind.BATTLE_ROPES -> EquipmentEditorDialog.BattleRopes(
                                    item.id,
                                    item.battleRopes,
                                    item.modalities
                                )
                                EquipmentCatalogKind.MOBILITY_TOOLS -> EquipmentEditorDialog.MobilityTools(
                                    item.id,
                                    item.mobilityTools,
                                    item.modalities
                                )
                                EquipmentCatalogKind.PARALLETTE_RINGS -> EquipmentEditorDialog.ParallettesRings(
                                    item.id,
                                    item.parallettesRings,
                                    item.modalities
                                )
                            }
                        },
                        onDelete = {
                            onEquipmentChange(ownedEquipment.filter { it.id != item.id })
                        }
                    )
                }
            }
            Button(
                onClick = {
                    dialog = EquipmentEditorDialog.Manual(null, "", emptySet())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Other Equipment")
            }
        }
    }

    when (val d = dialog) {
        is EquipmentEditorDialog.Manual -> ManualEquipmentDialog(
            existingId = d.existingId,
            nameDraft = d.nameDraft,
            initialModalities = d.modalities,
            onDismiss = { dialog = null },
            onSave = { name, modalities ->
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    dialog = null
                    return@ManualEquipmentDialog
                }
                val id = d.existingId ?: UUID.randomUUID().toString()
                val newItem = OwnedEquipmentItem(
                    id = id,
                    name = trimmed,
                    modalities = modalities,
                    catalogKind = EquipmentCatalogKind.MANUAL
                )
                val next = if (d.existingId == null) {
                    ownedEquipment + newItem
                } else {
                    ownedEquipment.map { if (it.id == d.existingId) newItem else it }
                }
                onEquipmentChange(next)
                dialog = null
            }
        )
        is EquipmentEditorDialog.Barbell -> BarbellEquipmentDialog(
            existingId = d.existingId,
            initial = d.ownership,
            initialModalities = d.modalities,
            weightUnit = weightUnit,
            onDismiss = { dialog = null },
            onSave = { item ->
                mergeEquipmentItem(ownedEquipment, d.existingId, item, onEquipmentChange)
                dialog = null
            }
        )
        is EquipmentEditorDialog.Dumbbells -> DumbbellEquipmentDialog(
            existingId = d.existingId,
            initial = d.ownership,
            initialModalities = d.modalities,
            weightUnit = weightUnit,
            onDismiss = { dialog = null },
            onSave = { item ->
                mergeEquipmentItem(ownedEquipment, d.existingId, item, onEquipmentChange)
                dialog = null
            }
        )
        is EquipmentEditorDialog.Kettlebells -> KettlebellEquipmentDialog(
            existingId = d.existingId,
            initial = d.ownership,
            initialModalities = d.modalities,
            weightUnit = weightUnit,
            onDismiss = { dialog = null },
            onSave = { item ->
                mergeEquipmentItem(ownedEquipment, d.existingId, item, onEquipmentChange)
                dialog = null
            }
        )
        is EquipmentEditorDialog.Plates -> PlateEquipmentDialog(
            existingId = d.existingId,
            initial = d.ownership,
            initialModalities = d.modalities,
            weightUnit = weightUnit,
            onDismiss = { dialog = null },
            onSave = { item ->
                mergeEquipmentItem(ownedEquipment, d.existingId, item, onEquipmentChange)
                dialog = null
            }
        )
        is EquipmentEditorDialog.Bands -> BandEquipmentDialog(
            existingId = d.existingId,
            initial = d.ownership,
            initialModalities = d.modalities,
            weightUnit = weightUnit,
            onDismiss = { dialog = null },
            onSave = { item ->
                mergeEquipmentItem(ownedEquipment, d.existingId, item, onEquipmentChange)
                dialog = null
            }
        )
        is EquipmentEditorDialog.Bench -> BenchEquipmentDialog(
            existingId = d.existingId,
            initial = d.ownership,
            initialModalities = d.modalities,
            weightUnit = weightUnit,
            onDismiss = { dialog = null },
            onSave = { item ->
                mergeEquipmentItem(ownedEquipment, d.existingId, item, onEquipmentChange)
                dialog = null
            }
        )
        is EquipmentEditorDialog.SquatRack -> SquatRackEquipmentDialog(
            existingId = d.existingId,
            initial = d.ownership,
            initialModalities = d.modalities,
            weightUnit = weightUnit,
            onDismiss = { dialog = null },
            onSave = { item ->
                mergeEquipmentItem(ownedEquipment, d.existingId, item, onEquipmentChange)
                dialog = null
            }
        )
        is EquipmentEditorDialog.PullUpDip -> PullUpDipEquipmentDialog(
            existingId = d.existingId,
            initial = d.ownership,
            initialModalities = d.modalities,
            weightUnit = weightUnit,
            onDismiss = { dialog = null },
            onSave = { item ->
                mergeEquipmentItem(ownedEquipment, d.existingId, item, onEquipmentChange)
                dialog = null
            }
        )
        is EquipmentEditorDialog.CardioMachines -> CardioMachinesEquipmentDialog(
            existingId = d.existingId,
            initial = d.ownership,
            initialModalities = d.modalities,
            weightUnit = weightUnit,
            onDismiss = { dialog = null },
            onSave = { item ->
                mergeEquipmentItem(ownedEquipment, d.existingId, item, onEquipmentChange)
                dialog = null
            }
        )
        is EquipmentEditorDialog.CableStation -> CableStationEquipmentDialog(
            existingId = d.existingId,
            initial = d.ownership,
            initialModalities = d.modalities,
            weightUnit = weightUnit,
            onDismiss = { dialog = null },
            onSave = { item ->
                mergeEquipmentItem(ownedEquipment, d.existingId, item, onEquipmentChange)
                dialog = null
            }
        )
        is EquipmentEditorDialog.JumpRope -> JumpRopeEquipmentDialog(
            existingId = d.existingId,
            initial = d.ownership,
            initialModalities = d.modalities,
            weightUnit = weightUnit,
            onDismiss = { dialog = null },
            onSave = { item ->
                mergeEquipmentItem(ownedEquipment, d.existingId, item, onEquipmentChange)
                dialog = null
            }
        )
        is EquipmentEditorDialog.MedicineBall -> MedicineBallEquipmentDialog(
            existingId = d.existingId,
            initial = d.ownership,
            initialModalities = d.modalities,
            weightUnit = weightUnit,
            onDismiss = { dialog = null },
            onSave = { item ->
                mergeEquipmentItem(ownedEquipment, d.existingId, item, onEquipmentChange)
                dialog = null
            }
        )
        is EquipmentEditorDialog.SuspensionTrainer -> SuspensionTrainerEquipmentDialog(
            existingId = d.existingId,
            initial = d.ownership,
            initialModalities = d.modalities,
            weightUnit = weightUnit,
            onDismiss = { dialog = null },
            onSave = { item ->
                mergeEquipmentItem(ownedEquipment, d.existingId, item, onEquipmentChange)
                dialog = null
            }
        )
        is EquipmentEditorDialog.PlyoBox -> PlyoBoxEquipmentDialog(
            existingId = d.existingId,
            initial = d.ownership,
            initialModalities = d.modalities,
            weightUnit = weightUnit,
            onDismiss = { dialog = null },
            onSave = { item ->
                mergeEquipmentItem(ownedEquipment, d.existingId, item, onEquipmentChange)
                dialog = null
            }
        )
        is EquipmentEditorDialog.BattleRopes -> BattleRopesEquipmentDialog(
            existingId = d.existingId,
            initial = d.ownership,
            initialModalities = d.modalities,
            weightUnit = weightUnit,
            onDismiss = { dialog = null },
            onSave = { item ->
                mergeEquipmentItem(ownedEquipment, d.existingId, item, onEquipmentChange)
                dialog = null
            }
        )
        is EquipmentEditorDialog.MobilityTools -> MobilityToolsEquipmentDialog(
            existingId = d.existingId,
            initial = d.ownership,
            initialModalities = d.modalities,
            weightUnit = weightUnit,
            onDismiss = { dialog = null },
            onSave = { item ->
                mergeEquipmentItem(ownedEquipment, d.existingId, item, onEquipmentChange)
                dialog = null
            }
        )
        is EquipmentEditorDialog.ParallettesRings -> ParallettesRingsEquipmentDialog(
            existingId = d.existingId,
            initial = d.ownership,
            initialModalities = d.modalities,
            weightUnit = weightUnit,
            onDismiss = { dialog = null },
            onSave = { item ->
                mergeEquipmentItem(ownedEquipment, d.existingId, item, onEquipmentChange)
                dialog = null
            }
        )
        null -> Unit
    }
}

private fun defaultModalities(kind: EquipmentCatalogKind): Set<WorkoutModality> =
    when (kind) {
        EquipmentCatalogKind.BANDS -> setOf(
            WorkoutModality.STRETCHING,
            WorkoutModality.WEIGHT_TRAINING
        )
        EquipmentCatalogKind.CARDIO_MACHINES,
        EquipmentCatalogKind.JUMP_ROPE -> setOf(
            WorkoutModality.CARDIO,
            WorkoutModality.HIIT
        )
        EquipmentCatalogKind.BATTLE_ROPES,
        EquipmentCatalogKind.PLYO_BOX -> setOf(
            WorkoutModality.HIIT,
            WorkoutModality.WEIGHT_TRAINING
        )
        EquipmentCatalogKind.MOBILITY_TOOLS -> setOf(
            WorkoutModality.STRETCHING,
            WorkoutModality.WEIGHT_TRAINING
        )
        EquipmentCatalogKind.PARALLETTE_RINGS -> setOf(
            WorkoutModality.WEIGHT_TRAINING,
            WorkoutModality.HIIT
        )
        else -> setOf(WorkoutModality.WEIGHT_TRAINING)
    }

private fun mergeEquipmentItem(
    owned: List<OwnedEquipmentItem>,
    existingId: String?,
    item: OwnedEquipmentItem,
    onEquipmentChange: (List<OwnedEquipmentItem>) -> Unit,
) {
    val next = if (existingId == null) owned + item else owned.map { if (it.id == existingId) item else it }
    onEquipmentChange(next)
}

private sealed class EquipmentEditorDialog {
    data class Manual(
        val existingId: String?,
        val nameDraft: String,
        val modalities: Set<WorkoutModality>,
    ) : EquipmentEditorDialog()

    data class Barbell(
        val existingId: String?,
        val ownership: BarbellOwnership?,
        val modalities: Set<WorkoutModality>,
    ) : EquipmentEditorDialog()

    data class Dumbbells(
        val existingId: String?,
        val ownership: DumbbellOwnership?,
        val modalities: Set<WorkoutModality>,
    ) : EquipmentEditorDialog()

    data class Kettlebells(
        val existingId: String?,
        val ownership: KettlebellOwnership?,
        val modalities: Set<WorkoutModality>,
    ) : EquipmentEditorDialog()

    data class Plates(
        val existingId: String?,
        val ownership: PlateOwnership?,
        val modalities: Set<WorkoutModality>,
    ) : EquipmentEditorDialog()

    data class Bands(
        val existingId: String?,
        val ownership: BandOwnership?,
        val modalities: Set<WorkoutModality>,
    ) : EquipmentEditorDialog()

    data class Bench(
        val existingId: String?,
        val ownership: BenchOwnership?,
        val modalities: Set<WorkoutModality>,
    ) : EquipmentEditorDialog()

    data class SquatRack(
        val existingId: String?,
        val ownership: SquatRackOwnership?,
        val modalities: Set<WorkoutModality>,
    ) : EquipmentEditorDialog()

    data class PullUpDip(
        val existingId: String?,
        val ownership: PullUpOwnership?,
        val modalities: Set<WorkoutModality>,
    ) : EquipmentEditorDialog()

    data class CardioMachines(
        val existingId: String?,
        val ownership: CardioMachinesOwnership?,
        val modalities: Set<WorkoutModality>,
    ) : EquipmentEditorDialog()

    data class CableStation(
        val existingId: String?,
        val ownership: CableStationOwnership?,
        val modalities: Set<WorkoutModality>,
    ) : EquipmentEditorDialog()

    data class JumpRope(
        val existingId: String?,
        val ownership: JumpRopeOwnership?,
        val modalities: Set<WorkoutModality>,
    ) : EquipmentEditorDialog()

    data class MedicineBall(
        val existingId: String?,
        val ownership: MedicineBallOwnership?,
        val modalities: Set<WorkoutModality>,
    ) : EquipmentEditorDialog()

    data class SuspensionTrainer(
        val existingId: String?,
        val ownership: SuspensionTrainerOwnership?,
        val modalities: Set<WorkoutModality>,
    ) : EquipmentEditorDialog()

    data class PlyoBox(
        val existingId: String?,
        val ownership: PlyoBoxOwnership?,
        val modalities: Set<WorkoutModality>,
    ) : EquipmentEditorDialog()

    data class BattleRopes(
        val existingId: String?,
        val ownership: BattleRopeOwnership?,
        val modalities: Set<WorkoutModality>,
    ) : EquipmentEditorDialog()

    data class MobilityTools(
        val existingId: String?,
        val ownership: MobilityToolsOwnership?,
        val modalities: Set<WorkoutModality>,
    ) : EquipmentEditorDialog()

    data class ParallettesRings(
        val existingId: String?,
        val ownership: ParallettesRingsOwnership?,
        val modalities: Set<WorkoutModality>,
    ) : EquipmentEditorDialog()
}

@Composable
private fun QuickAddCategoryButton(
    label: String,
    ownedEquipment: List<OwnedEquipmentItem>,
    category: EquipmentCatalogKind,
    onClick: () -> Unit,
) {
    val hasItem = ownedEquipment.any { it.catalogKind == category }
    OutlinedButton(onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (hasItem) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(label)
        }
    }
}

@Composable
private fun EquipmentRow(
    item: OwnedEquipmentItem,
    weightUnit: BodyWeightUnit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.displayTitle(weightUnit),
                    style = MaterialTheme.typography.titleSmall
                )
                item.summaryLine(weightUnit)?.let { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (item.modalities.isEmpty()) {
                    Text(
                        "No workout tags — tap edit to choose Cardio, Strength, etc.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        item.modalities.forEach { m ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    m.displayLabel(),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit equipment")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove equipment")
            }
        }
    }
}
