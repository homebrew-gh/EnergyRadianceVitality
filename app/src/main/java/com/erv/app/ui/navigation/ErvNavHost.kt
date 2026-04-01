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
import com.erv.app.ui.programs.ProgramDetailScreen
import com.erv.app.ui.programs.ProgramsCategoryScreen
import com.erv.app.ui.supplements.SupplementCategoryScreen
import com.erv.app.ui.supplements.SupplementDetailScreen
import com.erv.app.ui.supplements.SupplementLogScreen
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
    const val cardioCategoryNewWorkout = "category/cardio?openNewWorkout=true"
    const val weightTrainingCategory = "category/weight_training"
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
    const val stretchingLog = "category/stretching/log"
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
    stretchingRepository: StretchingRepository,
    programRepository: ProgramRepository,
    unifiedRoutineRepository: UnifiedRoutineRepository,
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
    onRelaysChanged: () -> Unit = {},
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
                onOpenHeatColdLog = {
                    navController.navigate(Routes.heatColdLog) { launchSingleTop = true }
                },
                onOpenUnifiedRun = { routineId ->
                    navController.navigate(Routes.unifiedRoutineRun(routineId)) {
                        launchSingleTop = true
                    }
                },
                heatColdRepository = heatColdRepository,
                stretchingRepository = stretchingRepository,
                programRepository = programRepository,
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
                onOpenLog = {
                    navController.navigate(Routes.cardioLog) { launchSingleTop = true }
                },
                initialOpenNewWorkout = true,
                onConsumedInitialOpenNewWorkout = {}
            )
        }

        composable(Routes.cardioCategory) {
            CardioCategoryScreen(
                repository = cardioRepository,
                unifiedRoutineRepository = unifiedRoutineRepository,
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
                onOpenLog = {
                    navController.navigate(Routes.cardioLog) { launchSingleTop = true }
                },
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

        composable(Routes.weightTrainingCategory) {
            WeightTrainingCategoryScreen(
                repository = weightRepository,
                unifiedRoutineRepository = unifiedRoutineRepository,
                liveWorkoutViewModel = weightLiveWorkoutViewModel,
                cardioLiveWorkoutViewModel = cardioLiveWorkoutViewModel,
                userPreferences = userPreferences,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
                onReturnToUnifiedRun = { routineId ->
                    if (!navController.popBackStack(Routes.unifiedRoutineRun(routineId), false)) {
                        navController.navigate(Routes.unifiedRoutineRun(routineId)) { launchSingleTop = true }
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

        composable(Routes.category("stretching")) {
            StretchingCategoryScreen(
                repository = stretchingRepository,
                unifiedRoutineRepository = unifiedRoutineRepository,
                userPreferences = userPreferences,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
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
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() }
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
