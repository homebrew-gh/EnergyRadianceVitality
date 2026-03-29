package com.erv.app.ui.programs

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erv.app.programs.FitnessProgram
import com.erv.app.programs.ProgramBlockKind
import com.erv.app.programs.ProgramDayBlock
import com.erv.app.programs.ProgramTemplateCategory
import com.erv.app.programs.ProgramTemplateOption
import com.erv.app.programs.ProgramTemplates
import com.erv.app.programs.ProgramWeekDay
import com.erv.app.programs.displayLabel
import com.erv.app.programs.isoDayOfWeekLabel
import com.erv.app.programs.normalizedWeek
import java.util.UUID

private val WizardKinds = listOf(
    ProgramBlockKind.WEIGHT,
    ProgramBlockKind.FLEX_TRAINING,
    ProgramBlockKind.CARDIO,
    ProgramBlockKind.STRETCH_CATALOG,
    ProgramBlockKind.HEAT_COLD,
    ProgramBlockKind.OTHER,
    ProgramBlockKind.CUSTOM,
    ProgramBlockKind.REST
)

private data class CustomProgramWizardDraft(
    val name: String = "Custom program",
    val description: String = "",
    val sourceLabel: String = "",
    val selectedDays: Set<Int> = setOf(1, 3, 5),
    val setActive: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramCreateEntrySheet(
    onDismiss: () -> Unit,
    onStartCustom: () -> Unit,
    onStartTemplate: () -> Unit,
    onImportJson: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("New program", style = MaterialTheme.typography.titleLarge)
            Text(
                "Start from scratch, use a template, or import a plan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(onClick = onStartCustom, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 8.dp))
                Text("Start custom program")
            }
            FilledTonalButton(onClick = onStartTemplate, modifier = Modifier.fillMaxWidth()) {
                Text("Start from template")
            }
            TextButton(onClick = onImportJson, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.Icon(Icons.Default.Upload, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 8.dp))
                Text("Import JSON")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomProgramWizardSheet(
    onDismiss: () -> Unit,
    onCreateProgram: (FitnessProgram, Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var step by rememberSaveable { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf(CustomProgramWizardDraft()) }
    val blockKindsByDay = remember {
        mutableStateMapOf<Int, Set<ProgramBlockKind>>().apply {
            draft.selectedDays.forEach { put(it, setOf(ProgramBlockKind.WEIGHT)) }
        }
    }
    val stepContentScroll = rememberScrollState()

    fun syncDays(keep: Set<Int>) {
        blockKindsByDay.keys.toList().forEach { day ->
            if (day !in keep) blockKindsByDay.remove(day)
        }
        keep.forEach { day ->
            if (blockKindsByDay[day] == null) blockKindsByDay[day] = setOf(ProgramBlockKind.WEIGHT)
        }
    }

    fun buildProgram(): FitnessProgram {
        val now = System.currentTimeMillis() / 1000
        return FitnessProgram(
            id = UUID.randomUUID().toString(),
            name = draft.name.trim().ifBlank { "Custom program" },
            description = draft.description.trim().ifBlank { null },
            sourceLabel = draft.sourceLabel.trim().ifBlank { "Custom builder" },
            createdAtEpochSeconds = now,
            lastModifiedEpochSeconds = now,
            weeklySchedule = draft.selectedDays.sorted().map { day ->
                ProgramWeekDay(
                    dayOfWeek = day,
                    blocks = WizardKinds
                        .filter { it in (blockKindsByDay[day] ?: setOf(ProgramBlockKind.WEIGHT)) }
                        .map { kind -> defaultWizardBlockForKind(kind) }
                )
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Custom program builder", style = MaterialTheme.typography.titleLarge)
            Text(
                when (step) {
                    0 -> "Step 1 of 4: name your program and add a short description."
                    1 -> "Step 2 of 4: choose the days you plan to train."
                    2 -> "Step 3 of 4: choose one or more block types for each selected day."
                    else -> "Step 4 of 4: review the weekly scaffold. You can fine-tune everything in the advanced editor next."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(stepContentScroll),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (step) {
                    0 -> {
                        OutlinedTextField(
                            value = draft.name,
                            onValueChange = { draft = draft.copy(name = it) },
                            label = { Text("Program name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = draft.description,
                            onValueChange = { draft = draft.copy(description = it) },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        OutlinedTextField(
                            value = draft.sourceLabel,
                            onValueChange = { draft = draft.copy(sourceLabel = it) },
                            label = { Text("Source label (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    1 -> {
                        Text("Training days", style = MaterialTheme.typography.labelMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (1..7).forEach { day ->
                                FilterChip(
                                    selected = day in draft.selectedDays,
                                    onClick = {
                                        val next = if (day in draft.selectedDays) {
                                            draft.selectedDays - day
                                        } else {
                                            draft.selectedDays + day
                                        }
                                        draft = draft.copy(selectedDays = next)
                                        syncDays(next)
                                    },
                                    label = { Text(isoDayOfWeekLabel(day).take(3)) }
                                )
                            }
                        }
                        Text(
                            "Pick the days you want this program to scaffold. You can add recovery or extra blocks later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    2 -> {
                        if (draft.selectedDays.isEmpty()) {
                            Text(
                                "Choose at least one day before setting block types.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "Select as many categories as you want for each day. The builder creates placeholder blocks that you can define later in the advanced editor.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            draft.selectedDays.sorted().forEach { day ->
                                val selectedKinds = blockKindsByDay[day].orEmpty()
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(isoDayOfWeekLabel(day), style = MaterialTheme.typography.titleMedium)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            WizardKinds.forEach { kind ->
                                                FilterChip(
                                                    selected = kind in selectedKinds,
                                                    onClick = {
                                                        blockKindsByDay[day] = if (kind in selectedKinds) {
                                                            selectedKinds - kind
                                                        } else {
                                                            selectedKinds + kind
                                                        }
                                                    },
                                                    label = { Text(wizardKindLabel(kind)) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        val preview = buildProgram()
                        ProgramWeekPreviewCard(
                            title = preview.name,
                            subtitle = preview.description ?: "Custom scaffold",
                            previewProgram = preview
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Set as active program", style = MaterialTheme.typography.bodyMedium)
                            FilterChip(
                                selected = draft.setActive,
                                onClick = { draft = draft.copy(setActive = !draft.setActive) },
                                label = { Text(if (draft.setActive) "Yes" else "No") }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        if (step == 0) {
                            onDismiss()
                        } else {
                            step--
                        }
                    }
                ) {
                    Text(if (step == 0) "Cancel" else "Back")
                }
                FilledTonalButton(
                    onClick = {
                        if (step < 3) {
                            step++
                        } else {
                            onCreateProgram(buildProgram(), draft.setActive)
                        }
                    },
                    enabled = when (step) {
                        0 -> draft.name.trim().isNotEmpty()
                        1 -> draft.selectedDays.isNotEmpty()
                        2 -> draft.selectedDays.isNotEmpty()
                        else -> true
                    }
                ) {
                    Text(if (step == 3) "Create and open" else "Next")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProgramTemplatePickerSheet(
    onDismiss: () -> Unit,
    onPickTemplate: (ProgramTemplateOption) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategory by rememberSaveable { mutableStateOf<ProgramTemplateCategory?>(null) }
    val visible = ProgramTemplates.allOptions.filter { opt ->
        selectedCategory == null || opt.category == selectedCategory
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Start from a template", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Browse a few common structures, then customize the plan before it is created.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All") }
                    )
                    ProgramTemplateCategory.entries.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category.displayLabel()) }
                        )
                    }
                }
            }
            items(visible, key = { it.id }) { opt ->
                val preview = remember(opt.id) { opt.build() }
                Card(
                    onClick = { onPickTemplate(opt) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(opt.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                opt.frequencyLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            opt.category.displayLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            opt.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ProgramWeekPreview(preview)
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramTemplateCustomizeSheet(
    option: ProgramTemplateOption,
    onDismiss: () -> Unit,
    onCreateProgram: (FitnessProgram, Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val preview = remember(option.id) { option.build() }
    var name by rememberSaveable(option.id) { mutableStateOf(preview.name) }
    var description by rememberSaveable(option.id) { mutableStateOf(preview.description.orEmpty()) }
    var setActive by rememberSaveable(option.id) { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Customize template", style = MaterialTheme.typography.titleLarge)
            Text(
                "Give the program your own name, review the week, then open the advanced editor for details.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Program name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            ProgramWeekPreviewCard(
                title = option.title,
                subtitle = "${option.category.displayLabel()} · ${option.frequencyLabel}",
                previewProgram = preview
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Set as active program", style = MaterialTheme.typography.bodyMedium)
                FilterChip(
                    selected = setActive,
                    onClick = { setActive = !setActive },
                    label = { Text(if (setActive) "Yes" else "No") }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                FilledTonalButton(
                    onClick = {
                        onCreateProgram(
                            preview.copy(
                                name = name.trim().ifBlank { preview.name },
                                description = description.trim().ifBlank { null }
                            ),
                            setActive
                        )
                    },
                    enabled = name.trim().isNotEmpty()
                ) {
                    Text("Create and open")
                }
            }
        }
    }
}

@Composable
private fun ProgramWeekPreviewCard(
    title: String,
    subtitle: String,
    previewProgram: FitnessProgram,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ProgramWeekPreview(previewProgram)
        }
    }
}

@Composable
private fun ProgramWeekPreview(program: FitnessProgram) {
    val rows = remember(program) { previewRows(program) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (day, summary) ->
            Text(
                "${isoDayOfWeekLabel(day).take(3)}: $summary",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun previewRows(program: FitnessProgram): List<Pair<Int, String>> =
    program.normalizedWeek()
        .filter { it.blocks.isNotEmpty() }
        .map { day ->
            day.dayOfWeek to day.blocks.joinToString(" + ") { previewBlockLabel(it) }
        }

private fun previewBlockLabel(block: ProgramDayBlock): String =
    block.title?.takeIf { it.isNotBlank() } ?: when (block.kind) {
        ProgramBlockKind.WEIGHT -> "Weight"
        ProgramBlockKind.UNIFIED_ROUTINE -> "Routine"
        ProgramBlockKind.FLEX_TRAINING -> "Workout"
        ProgramBlockKind.CARDIO -> block.cardioActivity?.replace('_', ' ')?.lowercase()
            ?.replaceFirstChar { it.titlecase() } ?: "Cardio"
        ProgramBlockKind.STRETCH_ROUTINE -> "Stretch routine"
        ProgramBlockKind.STRETCH_CATALOG -> "Stretch"
        ProgramBlockKind.HEAT_COLD -> when (block.heatColdMode) {
            "SAUNA" -> "Sauna"
            "COLD_PLUNGE" -> "Cold plunge"
            else -> "Hot / cold"
        }
        ProgramBlockKind.REST -> "Rest"
        ProgramBlockKind.CUSTOM -> "Custom"
        ProgramBlockKind.OTHER -> "Habits"
    }

private fun wizardKindLabel(kind: ProgramBlockKind): String = when (kind) {
    ProgramBlockKind.UNIFIED_ROUTINE -> "Routine"
    ProgramBlockKind.FLEX_TRAINING -> "Workout"
    ProgramBlockKind.STRETCH_CATALOG -> "Stretch"
    ProgramBlockKind.HEAT_COLD -> "Heat / cold"
    ProgramBlockKind.OTHER -> "Habits"
    else -> kind.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
}

private fun defaultWizardBlockForKind(kind: ProgramBlockKind): ProgramDayBlock = when (kind) {
    ProgramBlockKind.WEIGHT -> ProgramDayBlock(
        kind = ProgramBlockKind.WEIGHT,
        title = "Workout",
        notes = "Choose exercises or a saved routine in the advanced editor."
    )
    ProgramBlockKind.UNIFIED_ROUTINE -> ProgramDayBlock(
        kind = ProgramBlockKind.UNIFIED_ROUTINE,
        title = "Routine",
        notes = "Attach a saved unified routine in the advanced editor."
    )
    ProgramBlockKind.FLEX_TRAINING -> ProgramDayBlock(
        kind = ProgramBlockKind.FLEX_TRAINING,
        title = "Workout",
        targetMinutes = 45,
        notes = "Complete this with either a cardio session or a weight workout logged through ERV."
    )
    ProgramBlockKind.CARDIO -> ProgramDayBlock(
        kind = ProgramBlockKind.CARDIO,
        title = "Cardio",
        cardioActivity = "RUN",
        targetMinutes = 30
    )
    ProgramBlockKind.STRETCH_CATALOG -> ProgramDayBlock(
        kind = ProgramBlockKind.STRETCH_CATALOG,
        title = "Mobility",
        targetMinutes = 15,
        notes = "Choose poses or switch to a saved stretch routine in the advanced editor."
    )
    ProgramBlockKind.HEAT_COLD -> ProgramDayBlock(
        kind = ProgramBlockKind.HEAT_COLD,
        title = "Sauna",
        heatColdMode = "SAUNA",
        targetMinutes = 15
    )
    ProgramBlockKind.OTHER -> ProgramDayBlock(
        kind = ProgramBlockKind.OTHER,
        title = "Habits",
        checklistItems = listOf("Add your first checklist item")
    )
    ProgramBlockKind.CUSTOM -> ProgramDayBlock(
        kind = ProgramBlockKind.CUSTOM,
        title = "Custom block",
        notes = "Fill out the details in the advanced editor."
    )
    ProgramBlockKind.REST -> ProgramDayBlock(
        kind = ProgramBlockKind.REST,
        title = "Rest"
    )
    ProgramBlockKind.STRETCH_ROUTINE -> ProgramDayBlock(
        kind = ProgramBlockKind.STRETCH_ROUTINE,
        title = "Stretch routine"
    )
}
