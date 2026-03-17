package com.erv.app.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.erv.app.data.UserPreferences
import com.erv.app.nostr.AmberLauncherHost
import com.erv.app.nostr.KeyManager
import com.erv.app.ui.dashboard.DashboardScreen
import com.erv.app.ui.settings.SettingsScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val GOALS = "goals"
    fun category(id: String) = "category/$id"
}

@Composable
fun ErvNavHost(
    navController: NavHostController,
    keyManager: KeyManager,
    amberHost: AmberLauncherHost,
    userPreferences: UserPreferences,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
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

        categories.forEach { cat ->
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
