package com.erv.app.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.erv.app.data.UserPreferences
import com.erv.app.nostr.AmberLauncherHost
import com.erv.app.nostr.KeyManager
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.RelayPool
import com.erv.app.lighttherapy.LightLibraryState
import com.erv.app.lighttherapy.LightTherapyRepository
import com.erv.app.supplements.SupplementLibraryState
import com.erv.app.ui.dashboard.DashboardScreen
import com.erv.app.ui.dashboard.DashboardViewModel
import com.erv.app.ui.goals.GoalsEditScreen
import com.erv.app.ui.lighttherapy.LightLogScreen
import com.erv.app.ui.lighttherapy.LightTherapyCategoryScreen
import com.erv.app.ui.settings.SettingsScreen
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioRepository
import com.erv.app.supplements.SupplementRepository
import com.erv.app.ui.cardio.CardioCategoryScreen
import com.erv.app.ui.cardio.CardioLogScreen
import com.erv.app.ui.cardio.CardioSessionDetailScreen
import com.erv.app.ui.weighttraining.WeightLiveWorkoutViewModel
import com.erv.app.ui.cardio.CardioLiveWorkoutViewModel
import com.erv.app.ui.weighttraining.WeightExerciseDetailScreen
import com.erv.app.ui.weighttraining.WeightTrainingCategoryScreen
import com.erv.app.ui.weighttraining.WeightTrainingLogScreen
import com.erv.app.data.BodyWeightUnit
import com.erv.app.stretching.StretchLibraryState
import com.erv.app.stretching.StretchingRepository
import com.erv.app.ui.stretching.StretchingCategoryScreen
import com.erv.app.ui.stretching.StretchingLogScreen
import com.erv.app.heatcold.HeatColdLibraryState
import com.erv.app.heatcold.HeatColdMode
import com.erv.app.heatcold.HeatColdRepository
import com.erv.app.fasting.FastingRepository
import com.erv.app.programs.ProgramRepository
import com.erv.app.reminders.RoutineReminderRepository
import com.erv.app.unifiedroutines.UnifiedRoutineLibraryState
import com.erv.app.unifiedroutines.UnifiedRoutineRepository
import com.erv.app.bodytracker.BodyTrackerRepository
import com.erv.app.ui.bodytracker.BodyTrackerCategoryScreen
import com.erv.app.ui.bodytracker.BodyTrackerLogScreen
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.ui.heatcold.HeatColdCategoryScreen
import com.erv.app.ui.heatcold.HeatColdLogScreen
import com.erv.app.ui.fasting.FastingLogScreen
import com.erv.app.ui.fasting.FastingScreen
import com.erv.app.ui.programs.ProgramDetailScreen
import com.erv.app.ui.programs.ProgramsCategoryScreen
import com.erv.app.ui.supplements.SupplementCategoryScreen
import com.erv.app.ui.supplements.SupplementDetailScreen
import com.erv.app.ui.supplements.SupplementLogScreen
import com.erv.app.ui.training.TrainingCategoryScreen
import com.erv.app.ui.workouts.WorkoutComposerScreen
import com.erv.app.ui.workouts.WorkoutLibraryScreen
import com.erv.app.ui.workouts.WorkoutLiveRunScreen
import com.erv.app.workouts.WorkoutLibraryState
import com.erv.app.workouts.WorkoutRepository
import com.erv.app.ui.unifiedroutines.UnifiedRoutineCategoryScreen
import com.erv.app.ui.unifiedroutines.UnifiedRoutineRunScreen
import com.erv.app.ui.unifiedroutines.UnifiedWorkoutSummaryScreen
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object Routes {
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val GOALS = "goals"
    fun category(id: String) = "category/$id"
    fun supplementDetail(id: String) = "category/supplements/detail/$id"
    const val supplementLog = "category/supplements/log"
    const val lightTherapyLog = "category/light_therapy/log"
    const val cardioLog = "category/cardio/log"
    const val cardioLogOpenCalendarRoute = "category/cardio/log/open/{logDate}"
    fun cardioLogOpenCalendar(logDateIso: String) = "category/cardio/log/open/$logDateIso"
    const val cardioSessionDetailRoute = "category/cardio/log/session/{logDate}/{sessionId}"
    fun cardioSessionDetail(logDateIso: String, sessionId: String) =
        "category/cardio/log/session/$logDateIso/$sessionId"
    const val cardioCategory = "category/cardio"
    const val cardioCategoryRoute = "category/cardio?tab={tab}"
    fun cardioCategoryTab(tab: String) = "category/cardio?tab=$tab"
    const val cardioCategoryNewWorkout = "category/cardio?openNewWorkout=true"
    const val weightTrainingCategory = "category/weight_training"
    const val weightTrainingCategoryRoute = "category/weight_training?tab={tab}"
    fun weightTrainingCategoryTab(tab: String) = "category/weight_training?tab=$tab"
    const val trainingCategory = "category/training"
    const val workoutLibrary = "category/training/workouts"
    const val workoutComposerRoute = "category/training/workouts/edit/{workoutId}"
    fun workoutComposer(workoutId: String) = "category/training/workouts/edit/$workoutId"
    const val workoutComposerNew = "category/training/workouts/edit/new"
    const val workoutRunRoute = "category/training/workouts/run/{workoutId}"
    fun workoutRun(workoutId: String) = "category/training/workouts/run/$workoutId"
    const val unifiedRoutinesCategory = "category/unified_routines"
    const val unifiedRoutineRunRoute = "category/unified_routines/run/{routineId}"
    fun unifiedRoutineRun(routineId: String) = "category/unified_routines/run/$routineId"
    const val unifiedWorkoutSummaryRoute = "category/unified_routines/summary/{sessionId}"
    fun unifiedWorkoutSummary(sessionId: String) = "category/unified_routines/summary/$sessionId"
    const val weightTrainingLog = "category/weight_training/log"
    const val weightTrainingLogOpenCalendarRoute = "category/weight_training/log/open/{logDate}"
    fun weightTrainingLogOpenCalendar(logDateIso: String) = "category/weight_training/log/open/$logDateIso"
    const val weightExerciseDetailRoute = "category/weight_training/exercise/{exerciseId}"
    fun weightExerciseDetail(exerciseId: String) = "category/weight_training/exercise/$exerciseId"
    const val heatColdLog = "category/heat_cold/log"
    const val fasting = "category/fasting"
    const val fastingLog = "category/fasting/log"
    const val stretchingLog = "category/stretching/log"
    const val stretchingCategoryRoute = "category/stretching?tab={tab}"
    fun stretchingCategoryTab(tab: String) = "category/stretching?tab=$tab"
    const val programsCategory = "category/programs"
    const val programDetailRoute = "category/programs/{programId}"
    fun programDetail(programId: String) = "category/programs/$programId"
    const val bodyTracker = "category/body_tracker"
    const val bodyTrackerLog = "category/body_tracker/log"
    const val bodyTrackerLogOpenCalendarRoute = "category/body_tracker/log/open/{logDate}"
    fun bodyTrackerLogOpenCalendar(logDateIso: String) = "category/body_tracker/log/open/$logDateIso"

    /** Cardio flows show live HR in-session; the global HR strip above the nav host is redundant there. */
    fun isCardioDestination(route: String?): Boolean =
        route != null && route.startsWith("category/cardio")
}

@Composable
fun ErvNavHost(
    navController: NavHostController,
    keyManager: KeyManager,
    amberHost: AmberLauncherHost,
    userPreferences: UserPreferences,
    dashboardViewModel: DashboardViewModel,
    supplementRepository: SupplementRepository,
    lightTherapyRepository: LightTherapyRepository,
    cardioRepository: CardioRepository,
    weightRepository: WeightRepository,
    heatColdRepository: HeatColdRepository,
    fastingRepository: FastingRepository,
    stretchingRepository: StretchingRepository,
    programRepository: ProgramRepository,
    unifiedRoutineRepository: UnifiedRoutineRepository,
    workoutRepository: WorkoutRepository,
    bodyTrackerRepository: BodyTrackerRepository,
    reminderRepository: RoutineReminderRepository,
    weightLiveWorkoutViewModel: WeightLiveWorkoutViewModel,
    cardioLiveWorkoutViewModel: CardioLiveWorkoutViewModel,
    relayPool: RelayPool?,
    signer: EventSigner?,
    pendingReminderRoutineId: StateFlow<String?>,
    consumePendingReminderRoutineId: () -> Unit,
    navigateToWeightLiveWorkout: MutableStateFlow<Boolean>,
    navigateToCardioLiveWorkout: MutableStateFlow<Boolean>,
    navigateToUnifiedLiveWorkout: MutableStateFlow<String?>,
    navigateToFasting: MutableStateFlow<Boolean>,
    onRelaysChanged: () -> Unit = {},
    onPullRelayData: suspend () -> Unit = {},
    showDeferNostrLoginEntry: Boolean = false,
    onRequestNostrLogin: () -> Unit = {},
    onLogout: () -> Unit,
    onAllDataDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pendingRoutineId = pendingReminderRoutineId.collectAsState(initial = null).value
    val openWeightLive by navigateToWeightLiveWorkout.collectAsState()
    val openCardioLive by navigateToCardioLiveWorkout.collectAsState()
    val openUnifiedLiveRoutineId by navigateToUnifiedLiveWorkout.collectAsState()
    val openFasting by navigateToFasting.collectAsState()
    LaunchedEffect(pendingRoutineId) {
        if (pendingRoutineId != null) {
            navController.navigate(Routes.DASHBOARD) {
                launchSingleTop = true
            }
        }
    }
    LaunchedEffect(openWeightLive) {
        if (openWeightLive) {
            weightLiveWorkoutViewModel.setLiveWorkoutUiExpanded(true)
            navController.navigate(Routes.weightTrainingCategory) {
                launchSingleTop = true
            }
            navigateToWeightLiveWorkout.value = false
        }
    }
    LaunchedEffect(openCardioLive) {
        if (openCardioLive) {
            cardioLiveWorkoutViewModel.setCardioLiveUiExpanded(true)
            navController.navigate(Routes.cardioCategory) {
                launchSingleTop = true
            }
            navigateToCardioLiveWorkout.value = false
        }
    }
    LaunchedEffect(openUnifiedLiveRoutineId) {
        val routineId = openUnifiedLiveRoutineId ?: return@LaunchedEffect
        navController.navigate(Routes.unifiedRoutineRun(routineId)) {
            launchSingleTop = true
        }
        navigateToUnifiedLiveWorkout.value = null
    }
    LaunchedEffect(openFasting) {
        if (openFasting) {
            navController.navigate(Routes.fasting) {
                launchSingleTop = true
            }
            navigateToFasting.value = false
        }
    }
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        modifier = modifier
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToEditGoals = {
                    navController.navigate(Routes.GOALS)
                },
                supplementRepository = supplementRepository,
                lightTherapyRepository = lightTherapyRepository,
                cardioRepository = cardioRepository,
                weightRepository = weightRepository,
                weightLiveWorkoutViewModel = weightLiveWorkoutViewModel,
                cardioLiveWorkoutViewModel = cardioLiveWorkoutViewModel,
                userPreferences = userPreferences,
                relayPool = relayPool,
                signer = signer,
                pendingReminderRoutineId = pendingRoutineId,
                onConsumePendingReminderRoutineId = consumePendingReminderRoutineId,
                onNavigateToCategory = { category ->
                    navController.navigate(category.route)
                },
                onOpenCardioNewWorkout = {
                    navController.navigate(Routes.cardioCategoryNewWorkout) {
                        launchSingleTop = true
                    }
                },
                onOpenCardioLogBackfill = { dashboardDate ->
                    navController.navigate(Routes.cardioLogOpenCalendar(dashboardDate.toString())) {
                        launchSingleTop = true
                    }
                },
                onOpenWeightLogBackfill = { dashboardDate ->
                    navController.navigate(Routes.weightTrainingLogOpenCalendar(dashboardDate.toString())) {
                        launchSingleTop = true
                    }
                },
                onOpenWeightExercisesTab = {
                    navController.navigate(Routes.weightTrainingCategoryTab("Exercises")) {
                        launchSingleTop = true
                    }
                },
                onOpenWeightRoutinesTab = {
                    navController.navigate(Routes.weightTrainingCategoryTab("Routines")) {
                        launchSingleTop = true
                    }
                },
                onOpenHeatColdLog = {
                    navController.navigate(Routes.heatColdLog) { launchSingleTop = true }
                },
                onOpenUnifiedRun = { routineId ->
                    navController.navigate(Routes.unifiedRoutineRun(routineId)) {
                        launchSingleTop = true
                    }
                },
                onOpenWorkoutRun = { workoutId ->
                    navController.navigate(Routes.workoutRun(workoutId)) {
                        launchSingleTop = true
                    }
                },
                heatColdRepository = heatColdRepository,
                stretchingRepository = stretchingRepository,
                programRepository = programRepository,
                workoutRepository = workoutRepository,
                unifiedRoutineRepository = unifiedRoutineRepository,
                viewModel = dashboardViewModel
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                keyManager = keyManager,
                userPreferences = userPreferences,
                weightRepository = weightRepository,
                cardioRepository = cardioRepository,
                stretchingRepository = stretchingRepository,
                heatColdRepository = heatColdRepository,
                lightTherapyRepository = lightTherapyRepository,
                supplementRepository = supplementRepository,
                programRepository = programRepository,
                unifiedRoutineRepository = unifiedRoutineRepository,
                workoutRepository = workoutRepository,
                bodyTrackerRepository = bodyTrackerRepository,
                reminderRepository = reminderRepository,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
                onRelaysChanged = onRelaysChanged,
                showDeferNostrLoginEntry = showDeferNostrLoginEntry,
                onRequestNostrLogin = onRequestNostrLogin,
                onLogout = onLogout,
                onAllDataDeleted = onAllDataDeleted,
            )
        }

        composable(Routes.GOALS) {
            GoalsEditScreen(
                userPreferences = userPreferences,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.category("supplements")) {
            SupplementCategoryScreen(
                repository = supplementRepository,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
                onOpenLog = {
                    navController.navigate(Routes.supplementLog) {
                        launchSingleTop = true
                    }
                },
                onOpenSupplementDetail = { id ->
                    navController.navigate(Routes.supplementDetail(id))
                }
            )
        }

        composable(Routes.supplementLog) {
            val state = supplementRepository.state.collectAsState(initial = SupplementLibraryState()).value
            SupplementLogScreen(
                repository = supplementRepository,
                state = state,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.category("light_therapy")) {
            LightTherapyCategoryScreen(
                repository = lightTherapyRepository,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
                onOpenLog = {
                    navController.navigate(Routes.lightTherapyLog) { launchSingleTop = true }
                }
            )
        }

        composable(Routes.lightTherapyLog) {
            val state = lightTherapyRepository.state.collectAsState(initial = LightLibraryState()).value
            LightLogScreen(
                repository = lightTherapyRepository,
                state = state,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.cardioCategoryNewWorkout) {
            CardioCategoryScreen(
                repository = cardioRepository,
                unifiedRoutineRepository = unifiedRoutineRepository,
                workoutRepository = workoutRepository,
                userPreferences = userPreferences,
                cardioLiveWorkoutViewModel = cardioLiveWorkoutViewModel,
                weightLiveWorkoutViewModel = weightLiveWorkoutViewModel,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
                onReturnToUnifiedRun = { routineId ->
                    if (!navController.popBackStack(Routes.unifiedRoutineRun(routineId), false)) {
                        navController.navigate(Routes.unifiedRoutineRun(routineId)) { launchSingleTop = true }
                    }
                },
                onReturnToWorkoutRun = { workoutId ->
                    if (!navController.popBackStack(Routes.workoutRun(workoutId), false)) {
                        navController.navigate(Routes.workoutRun(workoutId)) { launchSingleTop = true }
                    }
                },
                onOpenLog = {
                    navController.navigate(Routes.cardioLog) { launchSingleTop = true }
                },
                initialOpenNewWorkout = true,
                onConsumedInitialOpenNewWorkout = {}
            )
        }

        composable(
            route = Routes.cardioCategoryRoute,
            arguments = listOf(navArgument("tab") {
                type = NavType.StringType
                defaultValue = "Activities"
            })
        ) { backStackEntry ->
            val initialTab = backStackEntry.arguments?.getString("tab") ?: "Activities"
            CardioCategoryScreen(
                repository = cardioRepository,
                unifiedRoutineRepository = unifiedRoutineRepository,
                workoutRepository = workoutRepository,
                userPreferences = userPreferences,
                cardioLiveWorkoutViewModel = cardioLiveWorkoutViewModel,
                weightLiveWorkoutViewModel = weightLiveWorkoutViewModel,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
                onReturnToUnifiedRun = { routineId ->
                    if (!navController.popBackStack(Routes.unifiedRoutineRun(routineId), false)) {
                        navController.navigate(Routes.unifiedRoutineRun(routineId)) { launchSingleTop = true }
                    }
                },
                onReturnToWorkoutRun = { workoutId ->
                    if (!navController.popBackStack(Routes.workoutRun(workoutId), false)) {
                        navController.navigate(Routes.workoutRun(workoutId)) { launchSingleTop = true }
                    }
                },
                onOpenLog = {
                    navController.navigate(Routes.cardioLog) { launchSingleTop = true }
                },
                initialTab = initialTab,
                initialOpenNewWorkout = false,
                onConsumedInitialOpenNewWorkout = {}
            )
        }

        composable(Routes.cardioLog) {
            val cardioState by cardioRepository.state.collectAsState(initial = CardioLibraryState())
            CardioLogScreen(
                repository = cardioRepository,
                state = cardioState,
                userPreferences = userPreferences,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
                onOpenSessionDetail = { logDate, sessionId ->
                    navController.navigate(Routes.cardioSessionDetail(logDate.toString(), sessionId))
                }
            )
        }

        composable(
            route = Routes.cardioLogOpenCalendarRoute,
            arguments = listOf(navArgument("logDate") { type = NavType.StringType })
        ) { backStackEntry ->
            val logDateStr = backStackEntry.arguments?.getString("logDate").orEmpty()
            val initialDate = runCatching { LocalDate.parse(logDateStr) }.getOrElse { LocalDate.now() }
            val cardioState by cardioRepository.state.collectAsState(initial = CardioLibraryState())
            CardioLogScreen(
                repository = cardioRepository,
                state = cardioState,
                userPreferences = userPreferences,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
                onOpenSessionDetail = { logDate, sessionId ->
                    navController.navigate(Routes.cardioSessionDetail(logDate.toString(), sessionId))
                },
                initialSelectedDate = initialDate,
                openCalendarInitially = true
            )
        }

        composable(
            route = Routes.cardioSessionDetailRoute,
            arguments = listOf(
                navArgument("logDate") { type = NavType.StringType },
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val logDateStr = backStackEntry.arguments?.getString("logDate").orEmpty()
            val sessionId = backStackEntry.arguments?.getString("sessionId").orEmpty()
            val logDate = runCatching { LocalDate.parse(logDateStr) }.getOrElse { LocalDate.now() }
            val cardioState by cardioRepository.state.collectAsState(initial = CardioLibraryState())
            CardioSessionDetailScreen(
                state = cardioState,
                logDate = logDate,
                sessionId = sessionId,
                userPreferences = userPreferences,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.weightTrainingCategoryRoute,
            arguments = listOf(navArgument("tab") {
                type = NavType.StringType
                defaultValue = "Exercises"
            })
        ) { backStackEntry ->
            val initialTab = backStackEntry.arguments?.getString("tab") ?: "Exercises"
            WeightTrainingCategoryScreen(
                repository = weightRepository,
                unifiedRoutineRepository = unifiedRoutineRepository,
                workoutRepository = workoutRepository,
                liveWorkoutViewModel = weightLiveWorkoutViewModel,
                cardioLiveWorkoutViewModel = cardioLiveWorkoutViewModel,
                userPreferences = userPreferences,
                initialTab = initialTab,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
                onReturnToUnifiedRun = { routineId ->
                    if (!navController.popBackStack(Routes.unifiedRoutineRun(routineId), false)) {
                        navController.navigate(Routes.unifiedRoutineRun(routineId)) { launchSingleTop = true }
                    }
                },
                onReturnToWorkoutRun = { workoutId ->
                    if (!navController.popBackStack(Routes.workoutRun(workoutId), false)) {
                        navController.navigate(Routes.workoutRun(workoutId)) { launchSingleTop = true }
                    }
                },
                onReturnToWorkoutLibrary = {
                    // Single pop that clears the weight-training category and the composed run
                    // off the back stack at once, landing directly on the workout library.
                    if (!navController.popBackStack(Routes.workoutLibrary, false)) {
                        navController.navigate(Routes.workoutLibrary) { launchSingleTop = true }
                    }
                },
                onOpenLog = {
                    navController.navigate(Routes.weightTrainingLog) { launchSingleTop = true }
                },
                onOpenExerciseDetail = { exerciseId ->
                    navController.navigate(Routes.weightExerciseDetail(exerciseId))
                }
            )
        }

        composable(Routes.trainingCategory) {
            val programsState by programRepository.state.collectAsState(initial = com.erv.app.programs.ProgramsLibraryState())
            val workoutState by workoutRepository.state.collectAsState(initial = WorkoutLibraryState())
            TrainingCategoryScreen(
                programsState = programsState,
                workoutState = workoutState,
                onBack = { navController.popBackStack() },
                onOpenWeeklyPlanner = {
                    navController.navigate(Routes.programsCategory) { launchSingleTop = true }
                },
                onOpenWorkoutLibrary = {
                    navController.navigate(Routes.workoutLibrary) { launchSingleTop = true }
                },
                onRunWorkout = { workoutId ->
                    navController.navigate(Routes.workoutRun(workoutId)) { launchSingleTop = true }
                },
            )
        }

        composable(Routes.workoutLibrary) {
            val workoutState by workoutRepository.state.collectAsState(initial = WorkoutLibraryState())
            WorkoutLibraryScreen(
                state = workoutState,
                repository = workoutRepository,
                keyManager = keyManager,
                relayPool = relayPool,
                signer = signer,
                onPullFromRelay = onPullRelayData,
                onBack = { navController.popBackStack() },
                onOpenComposer = { workoutId ->
                    if (workoutId == null) {
                        navController.navigate(Routes.workoutComposerNew) { launchSingleTop = true }
                    } else {
                        navController.navigate(Routes.workoutComposer(workoutId)) { launchSingleTop = true }
                    }
                },
                onOpenRun = { workoutId ->
                    navController.navigate(Routes.workoutRun(workoutId)) { launchSingleTop = true }
                },
            )
        }

        composable(Routes.workoutComposerNew) {
            val weightState by weightRepository.state.collectAsState(initial = WeightLibraryState())
            val cardioState by cardioRepository.state.collectAsState(initial = CardioLibraryState())
            WorkoutComposerScreen(
                existing = null,
                repository = workoutRepository,
                weightState = weightState,
                cardioState = cardioState,
                stretchCatalog = stretchingRepository.catalog,
                keyManager = keyManager,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
                onSaved = { workoutId ->
                    navController.popBackStack()
                    navController.navigate(Routes.workoutLibrary) { launchSingleTop = true }
                },
            )
        }

        composable(
            route = Routes.workoutComposerRoute,
            arguments = listOf(navArgument("workoutId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val workoutId = backStackEntry.arguments?.getString("workoutId").orEmpty()
            val workoutState by workoutRepository.state.collectAsState(initial = WorkoutLibraryState())
            val weightState by weightRepository.state.collectAsState(initial = WeightLibraryState())
            val cardioState by cardioRepository.state.collectAsState(initial = CardioLibraryState())
            WorkoutComposerScreen(
                existing = workoutState.workoutById(workoutId),
                repository = workoutRepository,
                weightState = weightState,
                cardioState = cardioState,
                stretchCatalog = stretchingRepository.catalog,
                keyManager = keyManager,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
                onSaved = {
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = Routes.workoutRunRoute,
            arguments = listOf(navArgument("workoutId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val workoutId = backStackEntry.arguments?.getString("workoutId").orEmpty()
            val workoutState by workoutRepository.state.collectAsState(initial = WorkoutLibraryState())
            val weightState by weightRepository.state.collectAsState(initial = WeightLibraryState())
            val cardioState by cardioRepository.state.collectAsState(initial = CardioLibraryState())
            WorkoutLiveRunScreen(
                workoutId = workoutId,
                repository = workoutRepository,
                cardioRepository = cardioRepository,
                weightRepository = weightRepository,
                stretchingRepository = stretchingRepository,
                activeRun = workoutState.activeRun?.takeIf { it.workoutId == workoutId },
                workout = workoutState.workoutById(workoutId),
                weightState = weightState,
                cardioState = cardioState,
                userPreferences = userPreferences,
                weightLiveWorkoutViewModel = weightLiveWorkoutViewModel,
                cardioLiveWorkoutViewModel = cardioLiveWorkoutViewModel,
                onBack = { navController.popBackStack() },
                onOpenWeightCategory = {
                    navController.navigate(Routes.weightTrainingCategory) { launchSingleTop = true }
                },
                onOpenCardioCategory = {
                    navController.navigate(Routes.cardioCategory) { launchSingleTop = true }
                },
                onOpenStretchCategory = {
                    navController.navigate(Routes.category("stretching")) { launchSingleTop = true }
                },
            )
        }

        composable(Routes.unifiedRoutinesCategory) {
            val unifiedState by unifiedRoutineRepository.state.collectAsState(initial = UnifiedRoutineLibraryState())
            val weightState by weightRepository.state.collectAsState(initial = WeightLibraryState())
            val cardioState by cardioRepository.state.collectAsState(initial = CardioLibraryState())
            val stretchState by stretchingRepository.state.collectAsState(initial = StretchLibraryState())
            UnifiedRoutineCategoryScreen(
                repository = unifiedRoutineRepository,
                weightRepository = weightRepository,
                stretchingRepository = stretchingRepository,
                unifiedState = unifiedState,
                weightState = weightState,
                cardioState = cardioState,
                stretchState = stretchState,
                stretchCatalog = stretchingRepository.catalog,
                userPreferences = userPreferences,
                onBack = { navController.popBackStack() },
                onOpenRun = { routineId ->
                    navController.navigate(Routes.unifiedRoutineRun(routineId)) { launchSingleTop = true }
                }
            )
        }

        composable(Routes.weightTrainingLog) {
            WeightTrainingLogScreen(
                repository = weightRepository,
                workoutRepository = workoutRepository,
                userPreferences = userPreferences,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.category("heat_cold")) {
            HeatColdCategoryScreen(
                initialMode = HeatColdMode.SAUNA,
                repository = heatColdRepository,
                userPreferences = userPreferences,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
                onOpenLog = {
                    navController.navigate(Routes.heatColdLog) { launchSingleTop = true }
                }
            )
        }

        composable(Routes.heatColdLog) {
            val state = heatColdRepository.state.collectAsState(initial = HeatColdLibraryState()).value
            HeatColdLogScreen(
                repository = heatColdRepository,
                state = state,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.fasting) {
            FastingScreen(
                repository = fastingRepository,
                onBack = { navController.popBackStack() },
                onOpenLog = {
                    navController.navigate(Routes.fastingLog) { launchSingleTop = true }
                }
            )
        }

        composable(Routes.fastingLog) {
            FastingLogScreen(
                repository = fastingRepository,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.stretchingCategoryRoute,
            arguments = listOf(navArgument("tab") {
                type = NavType.StringType
                defaultValue = "Stretches"
            })
        ) { backStackEntry ->
            val initialTab = backStackEntry.arguments?.getString("tab") ?: "Stretches"
            StretchingCategoryScreen(
                repository = stretchingRepository,
                unifiedRoutineRepository = unifiedRoutineRepository,
                workoutRepository = workoutRepository,
                userPreferences = userPreferences,
                relayPool = relayPool,
                signer = signer,
                initialTab = initialTab,
                onBack = { navController.popBackStack() },
                onReturnToWorkoutRun = { workoutId ->
                    if (!navController.popBackStack(Routes.workoutRun(workoutId), false)) {
                        navController.navigate(Routes.workoutRun(workoutId)) { launchSingleTop = true }
                    }
                },
                onOpenLog = {
                    navController.navigate(Routes.stretchingLog) { launchSingleTop = true }
                }
            )
        }

        composable(Routes.stretchingLog) {
            val state = stretchingRepository.state.collectAsState(initial = StretchLibraryState()).value
            StretchingLogScreen(
                repository = stretchingRepository,
                state = state,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.programsCategory) {
            ProgramsCategoryScreen(
                programRepository = programRepository,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
                onOpenProgram = { id ->
                    navController.navigate(Routes.programDetail(id))
                }
            )
        }

        composable(Routes.bodyTracker) {
            BodyTrackerCategoryScreen(
                repository = bodyTrackerRepository,
                userPreferences = userPreferences,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
                onOpenLog = {
                    navController.navigate(Routes.bodyTrackerLog) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.bodyTrackerLog) {
            BodyTrackerLogScreen(
                repository = bodyTrackerRepository,
                userPreferences = userPreferences,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.bodyTrackerLogOpenCalendarRoute,
            arguments = listOf(navArgument("logDate") { type = NavType.StringType })
        ) { backStackEntry ->
            val logDateStr = backStackEntry.arguments?.getString("logDate").orEmpty()
            val initialDate = runCatching { LocalDate.parse(logDateStr) }.getOrElse { LocalDate.now() }
            BodyTrackerLogScreen(
                repository = bodyTrackerRepository,
                userPreferences = userPreferences,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
                initialDate = initialDate,
                openCalendarInitially = true
            )
        }

        composable(
            route = Routes.programDetailRoute,
            arguments = listOf(navArgument("programId") { type = NavType.StringType })
        ) { backStackEntry ->
            val pid = backStackEntry.arguments?.getString("programId").orEmpty()
            val weightState by weightRepository.state.collectAsState(initial = WeightLibraryState())
            val cardioState by cardioRepository.state.collectAsState(initial = CardioLibraryState())
            val stretchState by stretchingRepository.state.collectAsState(initial = StretchLibraryState())
            val unifiedState by unifiedRoutineRepository.state.collectAsState(initial = UnifiedRoutineLibraryState())
            val workoutState by workoutRepository.state.collectAsState(initial = WorkoutLibraryState())
            ProgramDetailScreen(
                programId = pid,
                programRepository = programRepository,
                weightRepository = weightRepository,
                stretchingRepository = stretchingRepository,
                weightState = weightState,
                cardioState = cardioState,
                stretchState = stretchState,
                stretchCatalog = stretchingRepository.catalog,
                unifiedRoutineState = unifiedState,
                workoutState = workoutState,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
                onStartWorkout = { workoutId ->
                    navController.navigate(Routes.workoutRun(workoutId)) { launchSingleTop = true }
                }
            )
        }

        composable(
            route = Routes.weightTrainingLogOpenCalendarRoute,
            arguments = listOf(navArgument("logDate") { type = NavType.StringType })
        ) { backStackEntry ->
            val logDateStr = backStackEntry.arguments?.getString("logDate").orEmpty()
            val initialDate = runCatching { LocalDate.parse(logDateStr) }.getOrElse { LocalDate.now() }
            WeightTrainingLogScreen(
                repository = weightRepository,
                workoutRepository = workoutRepository,
                userPreferences = userPreferences,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
                initialSelectedDate = initialDate,
                openCalendarInitially = true
            )
        }

        composable(
            route = Routes.weightExerciseDetailRoute,
            arguments = listOf(navArgument("exerciseId") { type = NavType.StringType })
        ) { backStackEntry ->
            val exerciseId = backStackEntry.arguments?.getString("exerciseId").orEmpty()
            val state by weightRepository.state.collectAsState(initial = WeightLibraryState())
            val loadUnit by userPreferences.weightTrainingLoadUnit.collectAsState(initial = BodyWeightUnit.LB)
            WeightExerciseDetailScreen(
                exerciseId = exerciseId,
                library = state,
                loadUnit = loadUnit,
                repository = weightRepository,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.unifiedRoutineRunRoute,
            arguments = listOf(navArgument("routineId") { type = NavType.StringType })
        ) { backStackEntry ->
            val routineId = backStackEntry.arguments?.getString("routineId").orEmpty()
            val unifiedState by unifiedRoutineRepository.state.collectAsState(initial = UnifiedRoutineLibraryState())
            val weightState by weightRepository.state.collectAsState(initial = WeightLibraryState())
            val cardioState by cardioRepository.state.collectAsState(initial = CardioLibraryState())
            val stretchState by stretchingRepository.state.collectAsState(initial = StretchLibraryState())
            UnifiedRoutineRunScreen(
                routineId = routineId,
                repository = unifiedRoutineRepository,
                unifiedState = unifiedState,
                weightState = weightState,
                cardioState = cardioState,
                stretchState = stretchState,
                stretchCatalog = stretchingRepository.catalog,
                userPreferences = userPreferences,
                weightLiveWorkoutViewModel = weightLiveWorkoutViewModel,
                cardioLiveWorkoutViewModel = cardioLiveWorkoutViewModel,
                onBack = { navController.popBackStack() },
                onOpenSummary = { sessionId ->
                    navController.navigate(Routes.unifiedWorkoutSummary(sessionId)) {
                        launchSingleTop = true
                        popUpTo(Routes.unifiedRoutineRunRoute) { inclusive = true }
                    }
                },
                onOpenWeightCategory = {
                    navController.navigate(Routes.weightTrainingCategory) { launchSingleTop = true }
                },
                onOpenCardioCategory = {
                    navController.navigate(Routes.cardioCategory) { launchSingleTop = true }
                },
                onOpenStretchCategory = {
                    navController.navigate(Routes.category("stretching")) { launchSingleTop = true }
                }
            )
        }

        composable(
            route = Routes.unifiedWorkoutSummaryRoute,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId").orEmpty()
            val unifiedState by unifiedRoutineRepository.state.collectAsState(initial = UnifiedRoutineLibraryState())
            val weightState by weightRepository.state.collectAsState(initial = WeightLibraryState())
            val cardioState by cardioRepository.state.collectAsState(initial = CardioLibraryState())
            UnifiedWorkoutSummaryScreen(
                sessionId = sessionId,
                repository = unifiedRoutineRepository,
                unifiedState = unifiedState,
                weightState = weightState,
                cardioState = cardioState,
                userPreferences = userPreferences,
                relayPool = relayPool,
                signer = signer,
                onDone = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.supplementDetail("{supplementId}"),
            arguments = listOf(navArgument("supplementId") { type = NavType.StringType })
        ) { backStackEntry ->
            val supplementId = backStackEntry.arguments?.getString("supplementId").orEmpty()
            SupplementDetailScreen(
                repository = supplementRepository,
                relayPool = relayPool,
                signer = signer,
                supplementId = supplementId,
                onBack = { navController.popBackStack() }
            )
        }

        categories.forEach { cat ->
            if (cat.id in implementedCategoryIds) return@forEach
            composable(cat.route) {
                ComingSoonScreen(
                    title = cat.label,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComingSoonScreen(title: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Coming soon", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                "This feature is under development.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
