package com.erv.app.ui.weighttraining

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.erv.app.data.UserPreferences
import com.erv.app.ui.components.FieldLabel
import com.erv.app.ui.components.SectionHeader
import com.erv.app.weighttraining.WeightExercise
import com.erv.app.weighttraining.WeightExercisePickerFilter
import com.erv.app.weighttraining.WeightPushPull
import com.erv.app.weighttraining.displayLabel
import com.erv.app.weighttraining.filterWeightExercisesForPicker
import com.erv.app.weighttraining.formatMuscleGroupHeader
import com.erv.app.weighttraining.groupExercisesByMuscle
import com.erv.app.weighttraining.pickerMuscleKey
import com.erv.app.weighttraining.searchExercisesForPicker

/**
 * Full-screen exercise picker: search, muscle group chips that filter in place, recent exercises
 * first, and one scrolling list.
 *
 * With [onPickMany] set, tapping rows marks them and one button adds them all, in the order tapped.
 * Otherwise a tap picks that exercise through [onPick] and the caller closes the picker.
 * [onCreateNew] gets the search text so the new exercise can start with that name.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WeightPickExerciseDialog(
    exercises: List<WeightExercise>,
    excludeIds: Set<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit = {},
    onCreateNew: ((query: String) -> Unit)? = null,
    /** Newest first, e.g. [com.erv.app.weighttraining.recentExerciseIds]; listed above the rest. */
    recentExerciseIds: List<String> = emptyList(),
    onPickMany: ((List<String>) -> Unit)? = null,
    title: String = if (onPickMany != null) "Add Exercises" else "Choose Exercise",
) {
    val appContext = LocalContext.current.applicationContext
    val userPreferences = remember(appContext) { UserPreferences(appContext) }
    val gymMembership by userPreferences.gymMembership.collectAsState(initial = false)
    val ownedEquipment by userPreferences.ownedEquipment.collectAsState(initial = emptyList())
    val enabledExercisePackIds by userPreferences.enabledWeightExercisePackIds.collectAsState(initial = emptySet())

    var equipmentFilter by rememberSaveable { mutableStateOf(WeightExercisePickerFilter.ALL) }
    var pushPull by rememberSaveable { mutableStateOf<WeightPushPull?>(null) }
    var muscleKey by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    // Ids marked in multi-pick mode, in the order tapped.
    var marked by rememberSaveable { mutableStateOf(listOf<String>()) }

    val available = remember(exercises, excludeIds, equipmentFilter, ownedEquipment, enabledExercisePackIds) {
        filterWeightExercisesForPicker(
            exercises = exercises.filter { it.id !in excludeIds },
            filter = equipmentFilter,
            ownedEquipment = ownedEquipment,
            enabledPackIds = enabledExercisePackIds,
        )
    }
    // Chips come from what's available, so picking Push or Pull doesn't make them jump around.
    val muscleKeys = remember(available) { available.map { it.pickerMuscleKey() }.distinct().sorted() }
    val filtered = remember(available, pushPull, muscleKey) {
        available.filter { ex ->
            (pushPull == null || ex.pushOrPull == pushPull) &&
                (muscleKey == null || ex.pickerMuscleKey() == muscleKey)
        }
    }
    val searching = query.isNotBlank()
    val searchResults = remember(filtered, query) { searchExercisesForPicker(filtered, query) }
    val recent = remember(filtered, recentExerciseIds, searching, muscleKey) {
        if (searching || muscleKey != null) {
            emptyList()
        } else {
            val byId = filtered.associateBy { it.id }
            recentExerciseIds.mapNotNull { byId[it] }
        }
    }
    val grouped = remember(filtered) { groupExercisesByMuscle(filtered) }
    val filtersOn = pushPull != null || muscleKey != null

    val multi = onPickMany != null
    val tap: (String) -> Unit = { id ->
        if (multi) {
            marked = if (id in marked) marked - id else marked + id
        } else {
            onPick(id)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Close")
                            }
                        },
                    )
                },
                bottomBar = {
                    if (multi && marked.isNotEmpty()) {
                        Surface(tonalElevation = 3.dp) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .imePadding()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedButton(onClick = { marked = emptyList() }) { Text("Clear") }
                                Button(
                                    onClick = { onPickMany?.invoke(marked) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(if (marked.size == 1) "Add 1 Exercise" else "Add ${marked.size} Exercises")
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search name, muscle or equipment") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        item(key = "all-muscles") {
                            FilterChip(
                                selected = muscleKey == null,
                                onClick = { muscleKey = null },
                                label = { Text("All Muscles") },
                            )
                        }
                        items(muscleKeys, key = { it }) { key ->
                            FilterChip(
                                selected = muscleKey == key,
                                onClick = { muscleKey = if (muscleKey == key) null else key },
                                label = { Text(formatMuscleGroupHeader(key)) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = equipmentFilter == WeightExercisePickerFilter.ALL,
                            onClick = { equipmentFilter = WeightExercisePickerFilter.ALL },
                            label = { Text(if (gymMembership) "All / Gym" else "All Equipment") },
                        )
                        FilterChip(
                            selected = equipmentFilter == WeightExercisePickerFilter.HOME_READY,
                            onClick = { equipmentFilter = WeightExercisePickerFilter.HOME_READY },
                            label = { FieldLabel("Home-Ready") },
                        )
                        WeightPushPull.entries.forEach { value ->
                            FilterChip(
                                selected = pushPull == value,
                                onClick = { pushPull = if (pushPull == value) null else value },
                                label = { Text(value.displayLabel()) },
                            )
                        }
                    }
                    if (equipmentFilter == WeightExercisePickerFilter.HOME_READY && ownedEquipment.isEmpty()) {
                        Text(
                            "Home-Ready uses your saved equipment from Settings -> Equipment & Gym, plus no-equipment bodyweight exercises.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        val nothingShown = if (searching) searchResults.isEmpty() else filtered.isEmpty()
                        if (nothingShown) {
                            item(key = "empty") {
                                PickerEmptyState(
                                    message = when {
                                        searching -> "No exercises match “${query.trim()}”" +
                                            if (filtersOn) " with these filters." else "."
                                        available.isEmpty() && equipmentFilter == WeightExercisePickerFilter.HOME_READY ->
                                            "No matching home-ready exercises yet."
                                        available.isEmpty() -> "No more exercises to add."
                                        else -> "No exercises with these filters."
                                    },
                                    onClearFilters = if (filtersOn) {
                                        { pushPull = null; muscleKey = null }
                                    } else {
                                        null
                                    },
                                )
                            }
                        } else if (searching) {
                            pickerSection("results", "Results", searchResults, showMuscle = true, multi, marked, tap)
                        } else {
                            if (recent.isNotEmpty()) {
                                pickerSection("recent", "Recent", recent, showMuscle = true, multi, marked, tap)
                            }
                            grouped.forEach { (key, list) ->
                                pickerSection("group-$key", formatMuscleGroupHeader(key), list, showMuscle = false, multi, marked, tap)
                            }
                        }
                        onCreateNew?.let { create ->
                            item(key = "create") {
                                TextButton(
                                    onClick = { create(query.trim()) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Text(
                                        if (searching) "Create “${query.trim()}”" else "Create New Exercise",
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.pickerSection(
    keyPrefix: String,
    header: String,
    exercises: List<WeightExercise>,
    showMuscle: Boolean,
    multi: Boolean,
    marked: List<String>,
    onTap: (String) -> Unit,
) {
    stickyHeader(key = "$keyPrefix-header") {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
            SectionHeader(
                header,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            )
        }
    }
    items(exercises, key = { "$keyPrefix-${it.id}" }) { ex ->
        ExercisePickRow(
            exercise = ex,
            showMuscleGroup = showMuscle,
            multi = multi,
            marked = ex.id in marked,
            onClick = { onTap(ex.id) },
        )
    }
}

@Composable
private fun PickerEmptyState(message: String, onClearFilters: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (onClearFilters != null) {
            OutlinedButton(onClick = onClearFilters, modifier = Modifier.fillMaxWidth()) {
                Text("Clear Filters")
            }
        }
    }
}

@Composable
private fun ExercisePickRow(
    exercise: WeightExercise,
    showMuscleGroup: Boolean,
    multi: Boolean,
    marked: Boolean,
    onClick: () -> Unit,
) {
    val details = buildList {
        if (showMuscleGroup) add(formatMuscleGroupHeader(exercise.pickerMuscleKey()))
        add(exercise.pushOrPull.displayLabel())
        add(exercise.equipment.displayLabel())
    }
    ListItem(
        headlineContent = { Text(exercise.name) },
        supportingContent = { Text(details.joinToString(" · ")) },
        trailingContent = {
            if (multi) {
                Checkbox(checked = marked, onCheckedChange = { onClick() })
            } else {
                Icon(
                    Icons.Filled.AddCircleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (marked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        ),
        modifier = Modifier.clickable(
            onClickLabel = if (multi) "Mark ${exercise.name}" else "Add ${exercise.name}",
            onClick = onClick,
        ),
    )
}
