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
import com.erv.app.ui.weighttraining.WeightLiveWorkoutViewModel
import com.erv.app.ui.weighttraining.WeightExerciseDetailScreen
import com.erv.app.ui.weighttraining.WeightTrainingCategoryScreen
import com.erv.app.ui.weighttraining.WeightTrainingLogScreen
import com.erv.app.data.BodyWeightUnit
import com.erv.app.heatcold.HeatColdLibraryState
import com.erv.app.heatcold.HeatColdMode
import com.erv.app.heatcold.HeatColdRepository
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.ui.heatcold.HeatColdCategoryScreen
import com.erv.app.ui.heatcold.HeatColdLogScreen
import com.erv.app.ui.supplements.SupplementCategoryScreen
import com.erv.app.ui.supplements.SupplementDetailScreen
import com.erv.app.ui.supplements.SupplementLogScreen
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
    const val cardioCategory = "category/cardio"
    const val cardioCategoryNewWorkout = "category/cardio?openNewWorkout=true"
    const val weightTrainingCategory = "category/weight_training"
    const val weightTrainingLog = "category/weight_training/log"
    const val weightTrainingLogOpenCalendarRoute = "category/weight_training/log/open/{logDate}"
    fun weightTrainingLogOpenCalendar(logDateIso: String) = "category/weight_training/log/open/$logDateIso"
    const val weightExerciseDetailRoute = "category/weight_training/exercise/{exerciseId}"
    fun weightExerciseDetail(exerciseId: String) = "category/weight_training/exercise/$exerciseId"
    const val heatColdLog = "category/heat_cold/log"
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
    weightLiveWorkoutViewModel: WeightLiveWorkoutViewModel,
    relayPool: RelayPool?,
    signer: EventSigner?,
    pendingReminderRoutineId: StateFlow<String?>,
    consumePendingReminderRoutineId: () -> Unit,
    navigateToWeightLiveWorkout: MutableStateFlow<Boolean>,
    onRelaysChanged: () -> Unit = {},
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pendingRoutineId = pendingReminderRoutineId.collectAsState(initial = null).value
    val openWeightLive by navigateToWeightLiveWorkout.collectAsState()
    LaunchedEffect(pendingRoutineId) {
        if (pendingRoutineId != null) {
            navController.navigate(Routes.DASHBOARD) {
                launchSingleTop = true
            }
        }
    }
    LaunchedEffect(openWeightLive) {
        if (openWeightLive) {
            navController.navigate(Routes.weightTrainingCategory) {
                launchSingleTop = true
            }
            navigateToWeightLiveWorkout.value = false
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
                heatColdRepository = heatColdRepository,
                viewModel = dashboardViewModel
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                keyManager = keyManager,
                amberHost = amberHost,
                userPreferences = userPreferences,
                onBack = { navController.popBackStack() },
                onRelaysChanged = onRelaysChanged,
                onLogout = onLogout
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
                userPreferences = userPreferences,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
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
                userPreferences = userPreferences,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
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
                onBack = { navController.popBackStack() }
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
                initialSelectedDate = initialDate,
                openCalendarInitially = true
            )
        }

        composable(Routes.weightTrainingCategory) {
            val selectedDate by dashboardViewModel.selectedDate.collectAsState()
            WeightTrainingCategoryScreen(
                selectedDate = selectedDate,
                repository = weightRepository,
                liveWorkoutViewModel = weightLiveWorkoutViewModel,
                userPreferences = userPreferences,
                relayPool = relayPool,
                signer = signer,
                onBack = { navController.popBackStack() },
                onOpenLog = {
                    navController.navigate(Routes.weightTrainingLog) { launchSingleTop = true }
                },
                onOpenExerciseDetail = { exerciseId ->
                    navController.navigate(Routes.weightExerciseDetail(exerciseId))
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
            val loadUnit by userPreferences.weightTrainingLoadUnit.collectAsState(initial = BodyWeightUnit.KG)
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
            if (cat.id == "supplements" || cat.id == "light_therapy" || cat.id == "cardio" ||
                cat.id == "weight_training" || cat.id == "heat_cold"
            ) return@forEach
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
