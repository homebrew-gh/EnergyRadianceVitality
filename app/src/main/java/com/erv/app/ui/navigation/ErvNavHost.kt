package com.erv.app.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.erv.app.ui.lighttherapy.LightLogScreen
import com.erv.app.ui.lighttherapy.LightTherapyCategoryScreen
import com.erv.app.ui.settings.SettingsScreen
import com.erv.app.supplements.SupplementRepository
import com.erv.app.ui.supplements.SupplementCategoryScreen
import com.erv.app.ui.supplements.SupplementDetailScreen
import com.erv.app.ui.supplements.SupplementLogScreen
import kotlinx.coroutines.flow.StateFlow

object Routes {
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val GOALS = "goals"
    fun category(id: String) = "category/$id"
    fun supplementDetail(id: String) = "category/supplements/detail/$id"
    const val supplementLog = "category/supplements/log"
    const val lightTherapyLog = "category/light_therapy/log"
}

@Composable
fun ErvNavHost(
    navController: NavHostController,
    keyManager: KeyManager,
    amberHost: AmberLauncherHost,
    userPreferences: UserPreferences,
    supplementRepository: SupplementRepository,
    lightTherapyRepository: LightTherapyRepository,
    relayPool: RelayPool?,
    signer: EventSigner?,
    pendingReminderRoutineId: StateFlow<String?>,
    consumePendingReminderRoutineId: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pendingRoutineId = pendingReminderRoutineId.collectAsState(initial = null).value
    LaunchedEffect(pendingRoutineId) {
        if (pendingRoutineId != null) {
            navController.navigate(Routes.DASHBOARD) {
                launchSingleTop = true
            }
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
                supplementRepository = supplementRepository,
                lightTherapyRepository = lightTherapyRepository,
                relayPool = relayPool,
                signer = signer,
                pendingReminderRoutineId = pendingRoutineId,
                onConsumePendingReminderRoutineId = consumePendingReminderRoutineId,
                onNavigateToCategory = { category ->
                    navController.navigate(category.route)
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                keyManager = keyManager,
                amberHost = amberHost,
                userPreferences = userPreferences,
                onBack = { navController.popBackStack() },
                onLogout = onLogout
            )
        }

        composable(Routes.GOALS) {
            ComingSoonScreen(
                title = "Goals",
                onBack = { navController.popBackStack() }
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
                state = state,
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
                state = state,
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
            if (cat.id == "supplements" || cat.id == "light_therapy") return@forEach
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
