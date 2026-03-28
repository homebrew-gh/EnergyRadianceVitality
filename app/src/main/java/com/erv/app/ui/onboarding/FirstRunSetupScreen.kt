package com.erv.app.ui.onboarding

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erv.app.bodytracker.BodyTrackerRepository
import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.cardio.CardioRepository
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.EquipmentCatalogKind
import com.erv.app.data.GoalTemplateOptions
import com.erv.app.data.OwnedEquipmentItem
import com.erv.app.data.UserPreferences
import com.erv.app.data.WorkoutModality
import com.erv.app.data.createGoalFromTemplate
import com.erv.app.data.goalTemplateOptionForId
import com.erv.app.heatcold.HeatColdRepository
import com.erv.app.lighttherapy.LightTherapyRepository
import com.erv.app.programs.ProgramRepository
import com.erv.app.stretching.StretchingRepository
import com.erv.app.supplements.SupplementRepository
import com.erv.app.weighttraining.WeightRepository
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class FirstRunSetupStep {
    WELCOME,
    UNITS_BODY,
    GOALS,
    EQUIPMENT,
}

private data class QuickEquipmentOption(
    val kind: EquipmentCatalogKind,
    val label: String,
    val modalities: Set<WorkoutModality>,
)

private val quickEquipmentOptions: List<QuickEquipmentOption> = listOf(
    QuickEquipmentOption(EquipmentCatalogKind.DUMBBELLS, "Dumbbells", setOf(WorkoutModality.WEIGHT_TRAINING)),
    QuickEquipmentOption(EquipmentCatalogKind.BARBELL, "Barbell", setOf(WorkoutModality.WEIGHT_TRAINING)),
    QuickEquipmentOption(EquipmentCatalogKind.KETTLEBELLS, "Kettlebells", setOf(WorkoutModality.WEIGHT_TRAINING, WorkoutModality.HIIT)),
    QuickEquipmentOption(EquipmentCatalogKind.BANDS, "Bands", setOf(WorkoutModality.WEIGHT_TRAINING, WorkoutModality.STRETCHING)),
    QuickEquipmentOption(EquipmentCatalogKind.BENCH, "Bench", setOf(WorkoutModality.WEIGHT_TRAINING)),
    QuickEquipmentOption(EquipmentCatalogKind.SQUAT_RACK, "Rack / cage", setOf(WorkoutModality.WEIGHT_TRAINING)),
    QuickEquipmentOption(EquipmentCatalogKind.PULL_UP_DIP, "Pull-up / dip", setOf(WorkoutModality.WEIGHT_TRAINING)),
    QuickEquipmentOption(EquipmentCatalogKind.CABLE_STATION, "Cable", setOf(WorkoutModality.WEIGHT_TRAINING)),
    QuickEquipmentOption(EquipmentCatalogKind.CARDIO_MACHINES, "Cardio machine", setOf(WorkoutModality.CARDIO, WorkoutModality.HIIT)),
    QuickEquipmentOption(EquipmentCatalogKind.MOBILITY_TOOLS, "Mobility tools", setOf(WorkoutModality.STRETCHING, WorkoutModality.WEIGHT_TRAINING)),
)

suspend fun shouldShowFirstRunSetup(
    context: Context,
    userPreferences: UserPreferences,
): Boolean {
    if (userPreferences.firstRunSetupCompleted.first()) return false
    if (hasExistingUserData(context, userPreferences)) {
        userPreferences.setFirstRunSetupCompleted(true)
        return false
    }
    return true
}

private suspend fun hasExistingUserData(
    context: Context,
    userPreferences: UserPreferences,
): Boolean {
    if (userPreferences.fallbackBodyWeightKg.first() != null) return true
    if (userPreferences.goals.first().isNotEmpty()) return true
    if (userPreferences.selectedGoalIds.first().isNotEmpty()) return true
    if (userPreferences.gymMembership.first()) return true
    if (userPreferences.ownedEquipment.first().isNotEmpty()) return true
    if (userPreferences.localProfileDisplayName.first().isNotBlank()) return true
    if (userPreferences.localProfilePictureUrl.first().isNotBlank()) return true
    if (userPreferences.localProfileBio.first().isNotBlank()) return true

    val appContext = context.applicationContext
    val weightState = WeightRepository(appContext).currentState()
    if (weightState.routines.isNotEmpty() || weightState.logs.isNotEmpty()) return true

    val cardioState = CardioRepository(appContext, userPreferences).currentState()
    if (
        cardioState.routines.isNotEmpty() ||
        cardioState.customActivityTypes.isNotEmpty() ||
        cardioState.quickLaunches.isNotEmpty() ||
        cardioState.logs.isNotEmpty()
    ) return true

    val programState = ProgramRepository(appContext).currentState()
    if (
        programState.programs.isNotEmpty() ||
        programState.activeProgramId != null ||
        programState.completionState.isNotEmpty() ||
        programState.checklistCompletion.isNotEmpty()
    ) return true

    val bodyTrackerState = BodyTrackerRepository(appContext).currentState()
    if (bodyTrackerState.logs.isNotEmpty()) return true

    val supplementState = SupplementRepository(appContext).currentState()
    if (
        supplementState.supplements.isNotEmpty() ||
        supplementState.routines.isNotEmpty() ||
        supplementState.logs.isNotEmpty()
    ) return true

    val lightState = LightTherapyRepository(appContext).currentState()
    if (lightState.devices.isNotEmpty() || lightState.routines.isNotEmpty() || lightState.logs.isNotEmpty()) return true

    val stretchState = StretchingRepository(appContext).currentState()
    if (stretchState.routines.isNotEmpty() || stretchState.logs.isNotEmpty()) return true

    val heatColdState = HeatColdRepository(appContext).currentState()
    if (heatColdState.saunaLogs.isNotEmpty() || heatColdState.coldLogs.isNotEmpty()) return true

    return false
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FirstRunSetupScreen(
    userPreferences: UserPreferences,
    onDone: () -> Unit,
) {
    val savedBodyWeightValue by userPreferences.bodyWeightValue.collectAsState(initial = "")
    val savedBodyWeightUnit by userPreferences.bodyWeightUnit.collectAsState(initial = BodyWeightUnit.LB)
    val savedWeightLoadUnit by userPreferences.weightTrainingLoadUnit.collectAsState(initial = BodyWeightUnit.LB)
    val savedCardioDistanceUnit by userPreferences.cardioDistanceUnit.collectAsState(initial = CardioDistanceUnit.MILES)
    val savedGoals by userPreferences.goals.collectAsState(initial = emptyList())
    val savedGymMembership by userPreferences.gymMembership.collectAsState(initial = false)
    val savedOwnedEquipment by userPreferences.ownedEquipment.collectAsState(initial = emptyList())

    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var bodyWeightValue by rememberSaveable(savedBodyWeightValue) { mutableStateOf(savedBodyWeightValue) }
    var bodyWeightUnit by rememberSaveable(savedBodyWeightUnit.name) { mutableStateOf(savedBodyWeightUnit) }
    var weightLoadUnit by rememberSaveable(savedWeightLoadUnit.name) { mutableStateOf(savedWeightLoadUnit) }
    var cardioDistanceUnit by rememberSaveable(savedCardioDistanceUnit.name) { mutableStateOf(savedCardioDistanceUnit) }
    var selectedGoalTemplateIds by rememberSaveable(savedGoals.joinToString(",") { it.id }) {
        mutableStateOf(
            savedGoals.mapNotNull { goal ->
                GoalTemplateOptions.firstOrNull { template ->
                    template.metricType == goal.metricType && template.cardioFilter == goal.cardioFilter
                }?.id
            }.distinct()
        )
    }
    var gymMembership by rememberSaveable(savedGymMembership) { mutableStateOf(savedGymMembership) }
    var selectedEquipmentKinds by rememberSaveable(savedOwnedEquipment.map { it.catalogKind.name }.sorted().joinToString(",")) {
        mutableStateOf(savedOwnedEquipment.map { it.catalogKind.name }.distinct().sorted())
    }

    val steps = remember {
        listOf(
            FirstRunSetupStep.WELCOME,
            FirstRunSetupStep.UNITS_BODY,
            FirstRunSetupStep.GOALS,
            FirstRunSetupStep.EQUIPMENT,
        )
    }
    val step = steps[stepIndex]
    val scope = rememberCoroutineScope()

    fun finishSetup(saveValues: Boolean) {
        scope.launch {
            if (saveValues) {
                userPreferences.setCardioDistanceUnit(cardioDistanceUnit)
                userPreferences.setWeightTrainingLoadUnit(weightLoadUnit)
                userPreferences.setFallbackBodyWeight(bodyWeightValue, bodyWeightUnit)
                userPreferences.setGoals(
                    selectedGoalTemplateIds.mapNotNull(::goalTemplateOptionForId).map(::createGoalFromTemplate)
                )
                userPreferences.setGymMembership(gymMembership)
                userPreferences.setOwnedEquipment(buildQuickSetupEquipment(selectedEquipmentKinds))
            }
            userPreferences.setFirstRunSetupCompleted(true)
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick setup") },
                actions = {
                    TextButton(onClick = { finishSetup(saveValues = false) }) {
                        Text("Skip for now")
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { stepIndex = (stepIndex - 1).coerceAtLeast(0) },
                        enabled = stepIndex > 0,
                    ) {
                        Text("Back")
                    }
                    Button(
                        onClick = {
                            if (stepIndex == steps.lastIndex) finishSetup(saveValues = true)
                            else stepIndex++
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (stepIndex == steps.lastIndex) "Finish" else "Next")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "${stepIndex + 1} of ${steps.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            when (step) {
                FirstRunSetupStep.WELCOME -> {
                    Text("Set up the basics", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "A few quick settings make the dashboard, workout planning, and filters much more useful. " +
                            "You can change everything later in Settings.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SetupBullet("Pick your units and optionally add body weight.")
                    SetupBullet("Choose the health and training goals you care about.")
                    SetupBullet("Tell the app if you train at home, in a gym, or both.")
                }

                FirstRunSetupStep.UNITS_BODY -> {
                    Text("Units and body", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Body weight is optional, but helps with calorie estimates and context.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SetupChoiceGroup(
                        title = "Body weight unit",
                        options = listOf("LB" to "Pounds", "KG" to "Kilograms"),
                        selected = bodyWeightUnit.name,
                        onSelect = { bodyWeightUnit = BodyWeightUnit.valueOf(it) },
                    )
                    SetupChoiceGroup(
                        title = "Weight training load unit",
                        options = listOf("LB" to "Pounds", "KG" to "Kilograms"),
                        selected = weightLoadUnit.name,
                        onSelect = { weightLoadUnit = BodyWeightUnit.valueOf(it) },
                    )
                    SetupChoiceGroup(
                        title = "Cardio distance unit",
                        options = listOf("MILES" to "Miles", "KILOMETERS" to "Kilometers"),
                        selected = cardioDistanceUnit.name,
                        onSelect = { cardioDistanceUnit = CardioDistanceUnit.valueOf(it) },
                    )
                    OutlinedTextField(
                        value = bodyWeightValue,
                        onValueChange = { bodyWeightValue = it },
                        label = { Text("Current body weight (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                FirstRunSetupStep.GOALS -> {
                    Text("What matters most?", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Pick a few starter goals. We will create editable weekly goals that you can fine-tune later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    GoalTemplateOptions.forEach { option ->
                        val selected = option.id in selectedGoalTemplateIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedGoalTemplateIds =
                                    if (selected) selectedGoalTemplateIds - option.id
                                    else selectedGoalTemplateIds + option.id
                            },
                            label = { Text(option.title) },
                        )
                        Text(
                            option.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }
                }

                FirstRunSetupStep.EQUIPMENT -> {
                    Text("Equipment and gym", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "If you have gym access, gym exercises can be treated as available. Home equipment still helps with home-ready filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gym membership", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Turn this on if you train with access to a full gym.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = gymMembership, onCheckedChange = { gymMembership = it })
                    }
                    Text(
                        "Home equipment",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickEquipmentOptions.forEach { option ->
                            val selected = option.kind.name in selectedEquipmentKinds
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedEquipmentKinds =
                                        if (selected) selectedEquipmentKinds - option.kind.name
                                        else selectedEquipmentKinds + option.kind.name
                                },
                                label = { Text(option.label) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupBullet(text: String) {
    Text(
        "• $text",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupChoiceGroup(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(label) }
                )
            }
        }
    }
}

private fun buildQuickSetupEquipment(
    selectedKinds: List<String>,
): List<OwnedEquipmentItem> =
    quickEquipmentOptions
        .filter { it.kind.name in selectedKinds.toSet() }
        .map { option ->
            OwnedEquipmentItem(
                id = UUID.randomUUID().toString(),
                name = option.label,
                modalities = option.modalities,
                catalogKind = option.kind,
            )
        }
