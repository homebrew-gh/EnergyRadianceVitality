package com.erv.app

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.erv.app.data.ThemeMode
import com.erv.app.data.UserPreferences
import com.erv.app.data.WorkoutMediaUploadBackend
import com.erv.app.nostr.*
import com.erv.app.cardio.CardioRepository
import com.erv.app.heatcold.HeatColdRepository
import com.erv.app.heatcold.HeatColdSync
import com.erv.app.cardio.CardioSync
import com.erv.app.weighttraining.WeightLiveWorkoutConstants
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.WeightSync
import com.erv.app.lighttherapy.LightSync
import com.erv.app.lighttherapy.LightTherapyRepository
import com.erv.app.supplements.SupplementRepository
import com.erv.app.supplements.SupplementSync
import com.erv.app.reminders.RoutineReminderRepository
import com.erv.app.reminders.RoutineReminderScheduler
import com.erv.app.ui.navigation.ErvNavHost
import com.erv.app.ui.dashboard.DashboardViewModel
import com.erv.app.ui.weighttraining.WeightLiveWorkoutViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erv.app.ui.onboarding.RelaySetupScreen
import com.erv.app.ui.theme.ErvTheme
import androidx.core.content.ContextCompat
import android.os.Build
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainActivity : AppCompatActivity() {

    private lateinit var amberHost: AmberLauncherHost
    private lateinit var keyManager: KeyManager
    private val pendingReminderRoutineId = MutableStateFlow<String?>(null)
    private val navigateToWeightLiveWorkout = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        amberHost = AmberLauncherHost(this)
        keyManager = KeyManager(this)
        handleReminderIntent(intent)
        if (intent.getBooleanExtra(WeightLiveWorkoutConstants.EXTRA_OPEN_WEIGHT_LIVE, false)) {
            navigateToWeightLiveWorkout.value = true
        }

        setContent {
            val userPreferences = remember { UserPreferences(this@MainActivity) }
            val themeMode by userPreferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

            ErvTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                }
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ErvApp(
                        keyManager = keyManager,
                        amberHost = amberHost,
                        userPreferences = userPreferences,
                        pendingReminderRoutineId = pendingReminderRoutineId,
                        consumePendingReminderRoutineId = { pendingReminderRoutineId.value = null },
                        navigateToWeightLiveWorkout = navigateToWeightLiveWorkout
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReminderIntent(intent)
        if (intent.getBooleanExtra(WeightLiveWorkoutConstants.EXTRA_OPEN_WEIGHT_LIVE, false)) {
            navigateToWeightLiveWorkout.value = true
        }
    }

    private fun handleReminderIntent(intent: android.content.Intent?) {
        pendingReminderRoutineId.value = intent?.getStringExtra(com.erv.app.reminders.RoutineReminderScheduler.EXTRA_ROUTINE_ID)
    }
}

private enum class AppState { LoggedOut, Onboarding, Ready }

@Composable
private fun ErvApp(
    keyManager: KeyManager,
    amberHost: AmberLauncherHost,
    userPreferences: UserPreferences,
    pendingReminderRoutineId: StateFlow<String?>,
    consumePendingReminderRoutineId: () -> Unit,
    navigateToWeightLiveWorkout: MutableStateFlow<Boolean>
) {
    val context = LocalContext.current
    var appState by remember {
        mutableStateOf(if (keyManager.isLoggedIn) AppState.Ready else AppState.LoggedOut)
    }
    var onboardingLoading by remember { mutableStateOf(false) }

    fun resolveSigner(): EventSigner? {
        return keyManager.createLocalSigner()
            ?: (if (keyManager.loginMethod == KeyManager.LOGIN_AMBER && keyManager.publicKeyHex != null && keyManager.amberPackageName != null)
                AmberSigner(keyManager.publicKeyHex!!, amberHost, context.contentResolver, keyManager.amberPackageName!!)
            else null)
    }
    val signer = remember(appState) {
        if (appState == AppState.LoggedOut) null else resolveSigner()
    }
    var onboardingPool by remember { mutableStateOf<RelayPool?>(null) }
    DisposableEffect(onboardingPool) {
        val pool = onboardingPool
        onDispose { pool?.disconnect() }
    }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        userPreferences.ensureMediaKeysSplitV1()
        userPreferences.ensureCardioDistanceDefaultMiles()
    }

    when (appState) {
        AppState.LoggedOut -> LoginScreen(
            keyManager = keyManager,
            amberHost = amberHost,
            onLoginSuccess = {
                resolveSigner()?.let { activeSigner ->
                    appState = AppState.Onboarding
                    onboardingLoading = true
                    onboardingPool = null
                    scope.launch {
                        val resolved = runPostLoginSetup(keyManager, activeSigner, userPreferences)
                        if (resolved) {
                            onboardingLoading = false
                            appState = AppState.Ready
                        } else {
                            val pool = RelayPool(activeSigner)
                            pool.setRelays(keyManager.relayUrlsForPool())
                            onboardingPool = pool
                            onboardingLoading = false
                        }
                    }
                } ?: run {
                    onboardingLoading = false
                    appState = AppState.LoggedOut
                }
            }
        )
        AppState.Onboarding -> {
            if (onboardingLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Setting up your relays…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                RelaySetupScreen(
                    keyManager = keyManager,
                    relayPool = onboardingPool,
                    onContinue = {
                        scope.launch {
                            onboardingPool?.let { pool ->
                                pool.setRelays(keyManager.relayUrlsForPool())
                                delay(1500)
                                resolveSigner()?.let { currentSigner ->
                                    SettingsSync.saveToNetwork(pool, currentSigner, keyManager)
                                }
                            }
                            appState = AppState.Ready
                            onboardingPool = null
                        }
                    }
                )
            }
        }
        AppState.Ready -> MainAppShell(
            keyManager = keyManager,
            amberHost = amberHost,
            userPreferences = userPreferences,
            pendingReminderRoutineId = pendingReminderRoutineId,
            consumePendingReminderRoutineId = consumePendingReminderRoutineId,
            navigateToWeightLiveWorkout = navigateToWeightLiveWorkout,
            onLogout = {
                keyManager.logout()
                appState = AppState.LoggedOut
            }
        )
    }
}

/**
 * After login: connect (bootstrap relays only if none saved), fetch NIP-65 relay list and NIP-B7 Blossom
 * servers (kind 10063) in parallel, then fetch erv/settings from the network. If nothing yields stored relays,
 * applies [KeyManager.DEFAULT_RELAYS].
 * Returns true if settings were found (skip onboarding), false otherwise.
 */
private suspend fun runPostLoginSetup(
    keyManager: KeyManager,
    signer: EventSigner,
    userPreferences: UserPreferences
): Boolean {
    val pool = RelayPool(signer)
    try {
        pool.setRelays(keyManager.relayUrlsForPool())
        pool.awaitAtLeastOneConnected(timeoutMs = 15_000)

        val pubkey = keyManager.publicKeyHex ?: return false
        val (nip65Urls, blossomUrls) = coroutineScope {
            val nip65 = async { Nip65.fetchRelayListFromNetwork(pool, pubkey, timeoutMs = 8000) }
            val nipB7 = async { NipB7.fetchBlossomServersFromNetwork(pool, pubkey, timeoutMs = 8000) }
            nip65.await() to nipB7.await()
        }
        nip65Urls.forEach { keyManager.addSocialRelay(it) }
        applyImportedBlossomServersFromProfile(userPreferences, blossomUrls)

        pool.setRelays(keyManager.relayUrlsForPool())
        delay(1500)

        val config = SettingsSync.fetchFromNetwork(pool, signer, pubkey, timeoutMs = 5000)
        if (config != null) {
            SettingsSync.applyToKeyManager(config, keyManager)
        }
        keyManager.populateDefaultRelaysIfStillEmpty()
        return config != null
    } finally {
        pool.disconnect()
    }
}

/**
 * If the user has not set a **public** Blossom URL yet, copies the first entry from their kind 10063 list
 * (same source as “Load from my Nostr profile” in Settings) and switches upload type to Blossom.
 */
private suspend fun applyImportedBlossomServersFromProfile(
    userPreferences: UserPreferences,
    blossomUrls: List<String>
) {
    if (userPreferences.peekBlossomPublicServerOrigin().isNotBlank()) return
    val first = blossomUrls.firstOrNull() ?: return
    val normalized = Nip96Uploader.normalizeMediaServerOrigin(first)
    if (normalized.isEmpty()) return
    userPreferences.setBlossomPublicServerOrigin(normalized)
    userPreferences.setWorkoutMediaUploadBackend(WorkoutMediaUploadBackend.BLOSSOM)
}

// ---------------------------------------------------------------------------
// Main app shell (post-login): NavHost + BottomSheet categories
// ---------------------------------------------------------------------------

@Composable
private fun MainAppShell(
    keyManager: KeyManager,
    amberHost: AmberLauncherHost,
    userPreferences: UserPreferences,
    pendingReminderRoutineId: StateFlow<String?>,
    consumePendingReminderRoutineId: () -> Unit,
    navigateToWeightLiveWorkout: MutableStateFlow<Boolean>,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val supplementRepository = remember(context) { SupplementRepository(context) }
    val lightTherapyRepository = remember(context) { LightTherapyRepository(context) }
    val cardioRepository = remember(context) { CardioRepository(context) }
    val weightRepository = remember(context) { WeightRepository(context) }
    val heatColdRepository = remember(context) { HeatColdRepository(context) }
    val reminderRepository = remember(context) { RoutineReminderRepository(context) }
    val signer = remember(keyManager, amberHost) {
        keyManager.createLocalSigner()
            ?: (if (keyManager.loginMethod == KeyManager.LOGIN_AMBER && keyManager.publicKeyHex != null && keyManager.amberPackageName != null)
                AmberSigner(keyManager.publicKeyHex!!, amberHost, context.contentResolver, keyManager.amberPackageName!!)
            else null)
    }
    val relayPool = remember(signer) { signer?.let { RelayPool(it) } }
    var relayUrlsVersion by remember { mutableIntStateOf(0) }
    var initialSyncDone by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }
    val mainScope = rememberCoroutineScope()
    val activityForLifecycle = context as ComponentActivity
    val dashboardViewModel = viewModel<DashboardViewModel>(viewModelStoreOwner = activityForLifecycle)
    DisposableEffect(activityForLifecycle, reminderRepository) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mainScope.launch { reminderRepository.restoreAllSchedules() }
            }
        }
        activityForLifecycle.lifecycle.addObserver(observer)
        onDispose { activityForLifecycle.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(relayPool, relayUrlsVersion) {
        relayPool?.setRelays(keyManager.relayUrlsForPool())
    }
    LaunchedEffect(relayPool, signer, supplementRepository, lightTherapyRepository, cardioRepository, weightRepository, heatColdRepository) {
        if (relayPool == null || signer == null) {
            initialSyncDone = true
            return@LaunchedEffect
        }
        delay(1500)
        val pubkey = signer.publicKey
        kotlinx.coroutines.coroutineScope {
            awaitAll(
                async {
                    SupplementSync.fetchFromNetwork(relayPool, signer, pubkey, timeoutMs = 8000)
                        ?.let { supplementRepository.replaceAll(it) }
                },
                async {
                    LightSync.fetchFromNetwork(relayPool, signer, pubkey, timeoutMs = 8000)
                        ?.let { lightTherapyRepository.replaceAll(it) }
                },
                async {
                    CardioSync.fetchFromNetwork(relayPool, signer, pubkey, timeoutMs = 8000)
                        ?.let { cardioRepository.replaceAll(it) }
                },
                async {
                    WeightSync.fetchFromNetwork(relayPool, signer, pubkey, timeoutMs = 8000)
                        ?.let { weightRepository.replaceAll(it) }
                },
                async {
                    HeatColdSync.fetchFromNetwork(relayPool, signer, pubkey, timeoutMs = 8000)
                        ?.let { heatColdRepository.replaceAll(it) }
                }
            )
        }
        initialSyncDone = true
    }
    LaunchedEffect(reminderRepository) {
        reminderRepository.restoreAllSchedules()
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    DisposableEffect(relayPool) {
        onDispose { relayPool?.disconnect() }
    }

    Box(Modifier.fillMaxSize()) {
        val weightLiveWorkoutViewModel =
            viewModel<WeightLiveWorkoutViewModel>(viewModelStoreOwner = activityForLifecycle)
        ErvNavHost(
        navController = navController,
        keyManager = keyManager,
        amberHost = amberHost,
        userPreferences = userPreferences,
        dashboardViewModel = dashboardViewModel,
        supplementRepository = supplementRepository,
        lightTherapyRepository = lightTherapyRepository,
        cardioRepository = cardioRepository,
        weightRepository = weightRepository,
        heatColdRepository = heatColdRepository,
        weightLiveWorkoutViewModel = weightLiveWorkoutViewModel,
        relayPool = relayPool,
        signer = signer,
        pendingReminderRoutineId = pendingReminderRoutineId,
        consumePendingReminderRoutineId = consumePendingReminderRoutineId,
        navigateToWeightLiveWorkout = navigateToWeightLiveWorkout,
        onRelaysChanged = { relayUrlsVersion++ },
        onLogout = onLogout
        )
        if (!initialSyncDone) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Restoring your data…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Login — routes between Welcome, Existing-user sign-in, and New-user backup
// ---------------------------------------------------------------------------

private enum class LoginStep { Welcome, ExistingUser, BackupKey }

@Composable
private fun LoginScreen(
    keyManager: KeyManager,
    amberHost: AmberLauncherHost,
    onLoginSuccess: () -> Unit
) {
    var step by remember { mutableStateOf(LoginStep.Welcome) }
    var generatedNsec by remember { mutableStateOf<String?>(null) }

    when (step) {
        LoginStep.Welcome -> WelcomeScreen(
            onGetStarted = {
                try {
                    generatedNsec = keyManager.generateKeys()
                    step = LoginStep.BackupKey
                } catch (_: Exception) { /* astronomically unlikely */ }
            },
            onExistingAccount = { step = LoginStep.ExistingUser }
        )
        LoginStep.ExistingUser -> ExistingUserScreen(
            keyManager = keyManager,
            amberHost = amberHost,
            onLoginSuccess = onLoginSuccess,
            onBack = { step = LoginStep.Welcome }
        )
        LoginStep.BackupKey -> BackupKeyScreen(
            nsec = generatedNsec!!,
            onContinue = onLoginSuccess,
            onBack = {
                keyManager.logout()
                generatedNsec = null
                step = LoginStep.Welcome
            }
        )
    }
}

@Composable
private fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onExistingAccount: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_sun),
            contentDescription = null,
            tint = Color(0xFFFFD600),
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("ERV", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(4.dp))
        Text("Energy Radiance Vitality", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Track your health and wellness.\nYour data stays yours.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Get started")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onExistingAccount,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("I already have an account")
        }
    }
}

@Composable
private fun ExistingUserScreen(
    keyManager: KeyManager,
    amberHost: AmberLauncherHost,
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var nsecInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val amberAvailable = remember { AmberSigner.isAvailable(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Sign in", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter your private key to access your account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = nsecInput,
            onValueChange = { nsecInput = it; errorMessage = null },
            label = { Text("Private key (nsec or hex)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                try {
                    keyManager.loginWithNsec(nsecInput.trim())
                    onLoginSuccess()
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Invalid key"
                }
            },
            enabled = nsecInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign in")
        }

        if (amberAvailable) {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        try {
                            val (pubkey, packageName) = AmberSigner.getPublicKey(amberHost)
                            keyManager.loginWithAmber(pubkey, packageName)
                            onLoginSuccess()
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Connection failed"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign in with Amber")
            }
        }

        errorMessage?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onBack) {
            Text("Back")
        }
    }
}

@Composable
private fun BackupKeyScreen(
    nsec: String,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Save your private key", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "This is the only way to access your account. " +
                "Write it down or save it in a password manager. " +
                "If you lose it, your data cannot be recovered.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    clipboardManager.setText(AnnotatedString(nsec))
                    copied = true
                },
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Your private key",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = nsec,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (copied) "Copied!" else "Tap to copy",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (copied) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = confirmed,
                onCheckedChange = { confirmed = it }
            )
            Text(
                text = "I have saved my private key",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { confirmed = !confirmed }
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onContinue,
            enabled = confirmed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) {
            Text("Cancel")
        }
    }
}
