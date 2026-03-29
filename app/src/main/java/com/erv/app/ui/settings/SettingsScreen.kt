@file:OptIn(ExperimentalMaterial3Api::class)
package com.erv.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.erv.app.R
import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.OwnedEquipmentItem
import com.erv.app.data.SavedBluetoothDevice
import com.erv.app.data.SavedBluetoothDeviceKind
import com.erv.app.data.StretchGuidedTtsVoice
import com.erv.app.data.ThemeMode
import com.erv.app.data.UserPreferences
import com.erv.app.data.WorkoutMediaUploadBackend
import com.erv.app.data.displayLabel
import com.erv.app.data.displayName
import com.erv.app.datadeletion.DataDeletionManager
import com.erv.app.dataexport.DataExportCategory
import com.erv.app.cycling.CyclingCscBleConnectionState
import com.erv.app.cycling.LocalCyclingCsc
import com.erv.app.hr.HeartRateBleConnectionState
import com.erv.app.hr.LocalHeartRateBle
import com.erv.app.hr.requiredBlePermissionsForHeartRate
import com.erv.app.nostr.ConnectionState
import com.erv.app.nostr.CurrentRelayDataCoverage
import com.erv.app.nostr.CurrentRelayDataSync
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.FitnessEquipmentSync
import com.erv.app.nostr.KeyManager
import com.erv.app.nostr.Nip01Metadata
import com.erv.app.nostr.Nip65
import com.erv.app.nostr.Nip96Uploader
import com.erv.app.nostr.NipB7
import com.erv.app.nostr.ProfileMetadata
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.RelayOutboxStatus
import com.erv.app.nostr.RelayOutboxItemFailure
import com.erv.app.nostr.RelayOutboxStatusStore
import com.erv.app.nostr.RelayPublishOutbox
import com.erv.app.nostr.RelayPublishOutbox.PendingItemStatus
import com.erv.app.nostr.SettingsSync
import com.erv.app.cardio.CardioRepository
import com.erv.app.bodytracker.BodyTrackerRepository
import com.erv.app.heatcold.HeatColdRepository
import com.erv.app.lighttherapy.LightTherapyRepository
import com.erv.app.reminders.RoutineReminderRepository
import com.erv.app.stretching.StretchingRepository
import com.erv.app.supplements.SupplementRepository
import com.erv.app.programs.ProgramRepository
import com.erv.app.unifiedroutines.UnifiedRoutineRepository
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.ui.navigation.RelayDataSyncTopBarIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private const val WSS_PREFIX = "wss://"

private const val ERV_GITHUB_URL = "https://github.com/homebrew-gh/EnergyRadianceVitality"
private const val ERV_CONTACT_EMAIL = "erv_contact@proton.me"
private const val ERV_CONTACT_NOSTR_HANDLE = "homebrew_bitcoiner"

private object SettingsRoutes {
    const val HOME = "settings_home"
    const val APPEARANCE = "settings_appearance"
    const val UNITS = "settings_units"
    const val CARDIO = "settings_cardio"
    const val STRENGTH = "settings_strength"
    const val ACCOUNT = "settings_account"
    const val RELAYS = "settings_relays"
    const val EQUIPMENT = "settings_equipment"
    const val STRETCHING = "settings_stretching"
    const val SAVED_DEVICES = "settings_saved_devices"
    const val DATA_IMPORT_EXPORT = "settings_data_import_export?category={category}"
    const val DATA_MANAGEMENT = "settings_data_management"
    const val IMPORT_DOC = "settings_import_doc/{docKey}"

    fun importDocRoute(docKey: String) = "settings_import_doc/$docKey"

    fun dataImportExportRoute(category: DataExportCategory? = null): String =
        when (category) {
            null -> "settings_data_import_export"
            else -> "settings_data_import_export?category=${category.name}"
        }
}

@Composable
fun SettingsScreen(
    keyManager: KeyManager,
    userPreferences: UserPreferences,
    weightRepository: WeightRepository,
    cardioRepository: CardioRepository,
    stretchingRepository: StretchingRepository,
    heatColdRepository: HeatColdRepository,
    lightTherapyRepository: LightTherapyRepository,
    supplementRepository: SupplementRepository,
    programRepository: ProgramRepository,
    unifiedRoutineRepository: UnifiedRoutineRepository,
    bodyTrackerRepository: BodyTrackerRepository,
    reminderRepository: RoutineReminderRepository,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    onRelaysChanged: () -> Unit = {},
    showDeferNostrLoginEntry: Boolean = false,
    onRequestNostrLogin: () -> Unit = {},
    onLogout: () -> Unit,
    onAllDataDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val pendingRelayUploadCount by remember(context.applicationContext) {
        RelayPublishOutbox.get(context.applicationContext).pendingCountFlow()
    }.collectAsState(initial = 0)
    val relayOutboxStatus by remember(context.applicationContext) {
        RelayOutboxStatusStore.get(context.applicationContext).statusFlow()
    }.collectAsState(initial = RelayOutboxStatus())
    val pendingRelayItems by remember(context.applicationContext) {
        RelayPublishOutbox.get(context.applicationContext).pendingItemsFlow()
    }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val deletionManager = remember(
        context.applicationContext,
        keyManager,
        userPreferences,
        weightRepository,
        cardioRepository,
        stretchingRepository,
        heatColdRepository,
        lightTherapyRepository,
        supplementRepository,
        programRepository,
        unifiedRoutineRepository,
        bodyTrackerRepository,
        reminderRepository,
    ) {
        DataDeletionManager(
            context = context.applicationContext,
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
        )
    }
    val themeMode by userPreferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val bodyWeightValue by userPreferences.bodyWeightValue.collectAsState(initial = "")
    val bodyWeightUnit by userPreferences.bodyWeightUnit.collectAsState(initial = BodyWeightUnit.LB)
    val cardioDistanceUnit by userPreferences.cardioDistanceUnit.collectAsState(initial = CardioDistanceUnit.MILES)
    val cardioGpsPreferred by userPreferences.cardioGpsRecordingPreferred.collectAsState(initial = true)
    val cardioGpsTrackRetainOnDevice by userPreferences.cardioGpsTrackRetainOnDevice.collectAsState(initial = true)
    val heartRateMaxBpm by userPreferences.heartRateMaxBpm.collectAsState(initial = null)
    val nip96MediaOrigin by userPreferences.nip96MediaServerOrigin.collectAsState(initial = "")
    val blossomPublicSaved by userPreferences.blossomPublicServerOrigin.collectAsState(initial = "")
    val blossomPrivateSaved by userPreferences.blossomPrivateServerOrigin.collectAsState(initial = "")
    val workoutMediaBackend by userPreferences.workoutMediaUploadBackend.collectAsState(
        initial = WorkoutMediaUploadBackend.NIP96
    )
    val attachRouteToNostr by userPreferences.attachRouteImageToWorkoutNostrShare.collectAsState(initial = true)
    val neverPublishNip65RelayList by userPreferences.neverPublishNip65RelayList.collectAsState(initial = true)
    var nip96Draft by remember { mutableStateOf("") }
    var blossomPublicDraft by remember { mutableStateOf("") }
    var blossomPrivateDraft by remember { mutableStateOf("") }
    LaunchedEffect(nip96MediaOrigin) { nip96Draft = nip96MediaOrigin }
    LaunchedEffect(blossomPublicSaved) { blossomPublicDraft = blossomPublicSaved }
    LaunchedEffect(blossomPrivateSaved) { blossomPrivateDraft = blossomPrivateSaved }
    val weightTrainingLoadUnit by userPreferences.weightTrainingLoadUnit.collectAsState(initial = BodyWeightUnit.LB)
    val workoutBubbleEnabled by userPreferences.workoutBubbleEnabled.collectAsState(initial = true)
    val gymMembership by userPreferences.gymMembership.collectAsState(initial = false)
    val ownedEquipment by userPreferences.ownedEquipment.collectAsState(initial = emptyList())
    val enabledWeightExercisePackIds by userPreferences.enabledWeightExercisePackIds.collectAsState(initial = emptySet())
    val stretchGuidedTtsVoice by userPreferences.stretchGuidedTtsVoice.collectAsState(
        initial = StretchGuidedTtsVoice.SYSTEM_DEFAULT
    )

    var relayRevision by remember { mutableIntStateOf(0) }
    val allRelays = remember(relayRevision) { keyManager.allRelayUrls() }
    val dataRelayUrls = remember(allRelays, relayRevision) { allRelays.filter { keyManager.isDataRelay(it) } }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var relayCoverage by remember { mutableStateOf<CurrentRelayDataCoverage?>(null) }
    var relayCoverageLoading by remember { mutableStateOf(false) }
    var relayResyncing by remember { mutableStateOf(false) }

    LaunchedEffect(allRelays, relayPool) {
        relayPool?.setRelays(keyManager.relayUrlsForPool())
    }

    val relayStates by (relayPool?.relayStates ?: snapshotFlow { emptyMap<String, ConnectionState>() })
        .collectAsState(initial = emptyMap())

    var newRelaySuffix by remember { mutableStateOf("") }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            snackbarMessage = null
        }
    }

    val nestedNav = rememberNavController()
    BackHandler(enabled = true) {
        if (!nestedNav.popBackStack()) {
            onBack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        NavHost(
            navController = nestedNav,
            startDestination = SettingsRoutes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            composable(SettingsRoutes.HOME) {
                SettingsHomeScreen(
                    onBack = onBack,
                    onOpenSection = { nestedNav.navigate(it) },
                    pendingRelayUploadCount = pendingRelayUploadCount,
                    showDeferNostrLoginEntry = showDeferNostrLoginEntry,
                    onRequestNostrLogin = onRequestNostrLogin,
                )
            }
            composable(
                route = SettingsRoutes.DATA_IMPORT_EXPORT,
                arguments = listOf(
                    navArgument("category") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = true
                    }
                )
            ) { entry ->
                val initialExportCategory = entry.arguments
                    ?.getString("category")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { raw -> DataExportCategory.entries.firstOrNull { it.name == raw } }
                    ?: DataExportCategory.ALL
                SettingsDataImportExportScreen(
                    onBack = { nestedNav.popBackStack() },
                    onOpenDoc = { nestedNav.navigate(SettingsRoutes.importDocRoute(it)) },
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
                    initialExportCategory = initialExportCategory,
                )
            }
            composable(SettingsRoutes.DATA_MANAGEMENT) {
                SettingsDataManagementScreen(
                    onBack = { nestedNav.popBackStack() },
                    onOpenExport = { category ->
                        nestedNav.navigate(SettingsRoutes.dataImportExportRoute(category))
                    },
                    deletionManager = deletionManager,
                    keyManager = keyManager,
                    userPreferences = userPreferences,
                    relayPool = relayPool,
                    relayStates = relayStates,
                    signer = signer,
                    onRequestNostrLogin = onRequestNostrLogin,
                    onShowMessage = { snackbarMessage = it },
                    onAllDataDeleted = onAllDataDeleted,
                )
            }
            composable(
                route = SettingsRoutes.IMPORT_DOC,
                arguments = listOf(navArgument("docKey") { type = NavType.StringType })
            ) { entry ->
                val docKey = entry.arguments?.getString("docKey").orEmpty()
                SettingsImportDocViewerScreen(
                    docKey = docKey,
                    onBack = { nestedNav.popBackStack() }
                )
            }
            composable(SettingsRoutes.APPEARANCE) {
                SettingsSubScreenScaffold(
                    title = "Appearance",
                    onBack = { nestedNav.popBackStack() }
                ) {
                    ThemeSection(
                        themeMode = themeMode,
                        onThemeChange = { mode -> scope.launch { userPreferences.setThemeMode(mode) } }
                    )
                }
            }
            composable(SettingsRoutes.UNITS) {
                SettingsSubScreenScaffold(
                    title = "Units & Body",
                    onBack = { nestedNav.popBackStack() }
                ) {
                    BodyWeightSection(
                        value = bodyWeightValue,
                        unit = bodyWeightUnit,
                        onSave = { raw, u ->
                            scope.launch { userPreferences.setFallbackBodyWeight(raw, u) }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    CardioDistanceSection(
                        unit = cardioDistanceUnit,
                        onUnitChange = { u -> scope.launch { userPreferences.setCardioDistanceUnit(u) } }
                    )
                    Spacer(Modifier.height(12.dp))
                    WeightTrainingLoadSection(
                        unit = weightTrainingLoadUnit,
                        onUnitChange = { u -> scope.launch { userPreferences.setWeightTrainingLoadUnit(u) } }
                    )
                }
            }
            composable(SettingsRoutes.CARDIO) {
                SettingsSubScreenScaffold(
                    title = "Cardio",
                    onBack = { nestedNav.popBackStack() }
                ) {
                    CardioGpsSettingsSection(
                        enabled = cardioGpsPreferred,
                        onChange = { v -> scope.launch { userPreferences.setCardioGpsRecordingPreferred(v) } }
                    )
                    Spacer(Modifier.height(12.dp))
                    CardioGpsRetentionSettingsSection(
                        retainOnDevice = cardioGpsTrackRetainOnDevice,
                        onRetainChange = { v -> scope.launch { userPreferences.setCardioGpsTrackRetainOnDevice(v) } }
                    )
                    Spacer(Modifier.height(12.dp))
                    MaxHeartRateZonesSection(
                        maxBpm = heartRateMaxBpm,
                        onSave = { v -> scope.launch { userPreferences.setHeartRateMaxBpm(v) } }
                    )
                }
            }
            composable(SettingsRoutes.SAVED_DEVICES) {
                SettingsSubScreenScaffold(
                    title = "Saved Devices",
                    onBack = { nestedNav.popBackStack() }
                ) {
                    SavedBluetoothDevicesSection(
                        userPreferences = userPreferences,
                        scope = scope
                    )
                }
            }
            composable(SettingsRoutes.STRENGTH) {
                SettingsSubScreenScaffold(
                    title = "Strength Training",
                    onBack = { nestedNav.popBackStack() }
                ) {
                    LiveWeightWorkoutSettingsSection(
                        workoutBubbleEnabled = workoutBubbleEnabled,
                        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                        onBubbleChange = { enabled ->
                            scope.launch { userPreferences.setWorkoutBubbleEnabled(enabled) }
                        }
                    )
                }
            }
            composable(SettingsRoutes.STRETCHING) {
                SettingsSubScreenScaffold(
                    title = stringResource(R.string.settings_stretch_guided_voice_hub_title),
                    onBack = { nestedNav.popBackStack() }
                ) {
                    StretchGuidedTtsVoiceSection(
                        voice = stretchGuidedTtsVoice,
                        onVoiceChange = { v ->
                            scope.launch { userPreferences.setStretchGuidedTtsVoice(v) }
                        }
                    )
                }
            }
            composable(SettingsRoutes.ACCOUNT) {
                SettingsSubScreenScaffold(
                    title = "Account",
                    onBack = { nestedNav.popBackStack() }
                ) {
                    IdentitySection(
                        keyManager = keyManager,
                        userPreferences = userPreferences,
                        relayPool = relayPool,
                        signer = signer,
                        scope = scope,
                        onShowMessage = { snackbarMessage = it },
                        onLogout = onLogout
                    )
                }
            }
            composable(SettingsRoutes.EQUIPMENT) {
                SettingsSubScreenScaffold(
                    title = "Equipment & Gym",
                    onBack = { nestedNav.popBackStack() }
                ) {
                    FitnessEquipmentSettingsContent(
                        gymMembership = gymMembership,
                        ownedEquipment = ownedEquipment,
                        enabledExercisePackIds = enabledWeightExercisePackIds,
                        weightUnit = weightTrainingLoadUnit,
                        onGymMembershipChange = { enabled ->
                            scope.launch {
                                userPreferences.setGymMembership(enabled)
                                syncFitnessEquipmentToNostr(
                                    context.applicationContext,
                                    relayPool,
                                    signer,
                                    keyManager,
                                    enabled,
                                    ownedEquipment,
                                )
                            }
                        },
                        onEquipmentChange = { list ->
                            scope.launch {
                                userPreferences.setOwnedEquipment(list)
                                syncFitnessEquipmentToNostr(
                                    context.applicationContext,
                                    relayPool,
                                    signer,
                                    keyManager,
                                    gymMembership,
                                    list,
                                )
                            }
                        },
                        onExercisePackIdsChange = { ids ->
                            scope.launch {
                                userPreferences.setEnabledWeightExercisePackIds(ids)
                            }
                        }
                    )
                }
            }
            composable(SettingsRoutes.RELAYS) {
                LaunchedEffect(relayRevision, dataRelayUrls, signer) {
                    if (signer == null || !keyManager.isLoggedIn) {
                        relayCoverage = null
                        relayCoverageLoading = false
                        return@LaunchedEffect
                    }
                    relayCoverageLoading = true
                    try {
                        val localEntries = withContext(Dispatchers.IO) {
                            CurrentRelayDataSync.buildCurrentEntries(
                                userPreferences = userPreferences,
                                weightRepository = weightRepository,
                                cardioRepository = cardioRepository,
                                stretchingRepository = stretchingRepository,
                                heatColdRepository = heatColdRepository,
                                lightTherapyRepository = lightTherapyRepository,
                                supplementRepository = supplementRepository,
                                programRepository = programRepository,
                                bodyTrackerRepository = bodyTrackerRepository,
                            )
                        }
                        relayCoverage = withContext(Dispatchers.IO) {
                            CurrentRelayDataSync.probeCoverage(
                                signer = signer,
                                dataRelayUrls = dataRelayUrls,
                                localEntries = localEntries,
                            )
                        }
                    } catch (_: Exception) {
                        relayCoverage = null
                    } finally {
                        relayCoverageLoading = false
                    }
                }
                SettingsSubScreenScaffold(
                    title = "Relays",
                    onBack = { nestedNav.popBackStack() }
                ) {
                    Text(
                        "Toggle Data (encrypted activity) and Social (public posts) per relay.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_relays_never_publish_nip65_switch),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(R.string.settings_relays_never_publish_nip65_helper),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Switch(
                            checked = neverPublishNip65RelayList,
                            onCheckedChange = { v ->
                                scope.launch { userPreferences.setNeverPublishNip65RelayList(v) }
                            }
                        )
                    }
                    if (pendingRelayUploadCount > 0) {
                        Text(
                            relayUploadCurrentStateText(
                                count = pendingRelayUploadCount,
                                status = relayOutboxStatus,
                                items = pendingRelayItems,
                                dataRelayUrls = dataRelayUrls,
                                relayStates = relayStates,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            relayUploadQueueHint(pendingRelayUploadCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        relayUploadDiagnosticsText(
                            count = pendingRelayUploadCount,
                            status = relayOutboxStatus,
                            dataRelayUrls = dataRelayUrls,
                            relayStates = relayStates,
                        )?.let { detail ->
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }
                        PendingRelayItemsDebugList(
                            items = pendingRelayItems,
                            failuresByDTag = relayOutboxStatus.failuresByDTag,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                    Text(
                        relayUploadStatusGuideText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Text(
                        "ERV saves encrypted uploads locally first. If you are offline or your data relays are disconnected, uploads stay queued here until a relay reconnects. Once a relay accepts an item, it leaves the queue and is treated as sent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    if (signer != null && relayPool != null) {
                        Text(
                            "Current data relay sync",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        val coverageText = when {
                            relayCoverageLoading -> "Checking current encrypted payload coverage on connected data relays…"
                            else -> relayCurrentCoverageText(relayCoverage)
                        }
                        Text(
                            coverageText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    relayResyncing = true
                                    try {
                                        val localEntries = withContext(Dispatchers.IO) {
                                            CurrentRelayDataSync.buildCurrentEntries(
                                                userPreferences = userPreferences,
                                                weightRepository = weightRepository,
                                                cardioRepository = cardioRepository,
                                                stretchingRepository = stretchingRepository,
                                                heatColdRepository = heatColdRepository,
                                                lightTherapyRepository = lightTherapyRepository,
                                                supplementRepository = supplementRepository,
                                                programRepository = programRepository,
                                                bodyTrackerRepository = bodyTrackerRepository,
                                            )
                                        }
                                        val drain = withContext(Dispatchers.IO) {
                                            CurrentRelayDataSync.forceResync(
                                                appContext = context.applicationContext,
                                                relayPool = relayPool,
                                                signer = signer,
                                                dataRelayUrls = dataRelayUrls,
                                                localEntries = localEntries,
                                            )
                                        }
                                        snackbarMessage = buildString {
                                            append("Queued ${localEntries.size} current relay payload(s).")
                                            if (drain.publishedOk > 0 || drain.publishedFail > 0) {
                                                append(" Sent ${drain.publishedOk} now")
                                                if (drain.publishedFail > 0) {
                                                    append(", ${drain.publishedFail} failed and will retry")
                                                }
                                                append('.')
                                            }
                                            if (drain.remaining > 0) {
                                                append(" ${drain.remaining} payload(s) remain queued.")
                                            }
                                        }
                                        relayCoverageLoading = true
                                        relayCoverage = withContext(Dispatchers.IO) {
                                            CurrentRelayDataSync.probeCoverage(
                                                signer = signer,
                                                dataRelayUrls = dataRelayUrls,
                                                localEntries = localEntries,
                                            )
                                        }
                                    } catch (e: Exception) {
                                        snackbarMessage = "Resync failed: ${e.message}"
                                    } finally {
                                        relayCoverageLoading = false
                                        relayResyncing = false
                                    }
                                }
                            },
                            enabled = !relayResyncing && dataRelayUrls.isNotEmpty(),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Text(if (relayResyncing) "Resyncing…" else "Resync Current Data")
                        }
                    }
                    allRelays.forEach { url ->
                        RelayRow(
                            url = url,
                            connectionState = relayStates[url] ?: ConnectionState.Disconnected,
                            isData = keyManager.isDataRelay(url),
                            isSocial = keyManager.isSocialRelay(url),
                            onToggleData = { enabled ->
                                if (enabled) keyManager.addRelay(url) else keyManager.removeRelay(url)
                                relayRevision++
                                onRelaysChanged()
                                hasUnsavedChanges = true
                            },
                            onToggleSocial = { enabled ->
                                if (enabled) keyManager.addSocialRelay(url) else keyManager.removeSocialRelay(url)
                                relayRevision++
                                onRelaysChanged()
                                hasUnsavedChanges = true
                            },
                            onRemove = {
                                keyManager.removeRelayCompletely(url)
                                relayRevision++
                                onRelaysChanged()
                                hasUnsavedChanges = true
                            }
                        )
                    }
                    if (allRelays.isEmpty()) {
                        Text(
                            "No relays configured. Add one below or fetch from network.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    RelayAddRow(
                        suffix = newRelaySuffix,
                        onSuffixChange = { newRelaySuffix = it },
                        onAdd = {
                            val url = normalizeRelayUrl(newRelaySuffix)
                            if (url != null) {
                                keyManager.addRelay(url)
                                relayRevision++
                                onRelaysChanged()
                                hasUnsavedChanges = true
                                newRelaySuffix = ""
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Social relays are imported automatically during relay setup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (hasUnsavedChanges) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (relayPool == null || signer == null) return@Button
                                scope.launch {
                                    saving = true
                                    try {
                                        val settingsOk = SettingsSync.saveToNetwork(
                                            context.applicationContext,
                                            relayPool,
                                            signer,
                                            keyManager,
                                        )
                                        val nip65Ok = if (settingsOk) {
                                            Nip65.publishRelayListIfAllowed(
                                                userPreferences,
                                                relayPool,
                                                signer,
                                                keyManager,
                                            )
                                        } else {
                                            false
                                        }
                                        if (settingsOk) {
                                            hasUnsavedChanges = false
                                            snackbarMessage = when {
                                                nip65Ok -> "Settings saved"
                                                else -> context.getString(R.string.settings_relays_save_nip65_failed)
                                            }
                                        } else {
                                            snackbarMessage = "Save failed — check relay connections"
                                        }
                                    } catch (e: Exception) {
                                        snackbarMessage = "Save failed: ${e.message}"
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (saving) "Saving…" else "Save")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    CardioNostrRouteShareSection(
                        uploadBackend = workoutMediaBackend,
                        onUploadBackendChange = { b -> scope.launch { userPreferences.setWorkoutMediaUploadBackend(b) } },
                        attachRouteImage = attachRouteToNostr,
                        onAttachChange = { v -> scope.launch { userPreferences.setAttachRouteImageToWorkoutNostrShare(v) } },
                        nip96Draft = nip96Draft,
                        onNip96DraftChange = { nip96Draft = it },
                        onSaveNip96Origin = {
                            scope.launch {
                                val n = Nip96Uploader.normalizeMediaServerOrigin(nip96Draft)
                                userPreferences.setNip96MediaServerOrigin(n)
                                nip96Draft = n
                            }
                        },
                        blossomPublicDraft = blossomPublicDraft,
                        onBlossomPublicDraftChange = { blossomPublicDraft = it },
                        blossomPrivateDraft = blossomPrivateDraft,
                        onBlossomPrivateDraftChange = { blossomPrivateDraft = it },
                        onSaveBlossomServers = {
                            scope.launch {
                                val pub = Nip96Uploader.normalizeMediaServerOrigin(blossomPublicDraft)
                                val priv = Nip96Uploader.normalizeMediaServerOrigin(blossomPrivateDraft)
                                userPreferences.setBlossomPublicServerOrigin(pub)
                                userPreferences.setBlossomPrivateServerOrigin(priv)
                                blossomPublicDraft = pub
                                blossomPrivateDraft = priv
                            }
                        },
                        relayPool = relayPool,
                        blossomFetchPubkeyHex = keyManager.publicKeyHex,
                        fetchScope = scope,
                        onUserMessage = { snackbarMessage = it },
                        onApplyFetchedBlossomPublic = { normalized ->
                            blossomPublicDraft = normalized
                            scope.launch { userPreferences.setBlossomPublicServerOrigin(normalized) }
                        }
                    )
                }
            }
        }
    }
}

private fun relayUploadQueueHint(count: Int): String {
    val head = when {
        count > 99 -> "More than 99 encrypted activity updates are"
        count == 1 -> "1 encrypted activity update is"
        else -> "$count encrypted activity updates are"
    }
    return "$head queued for relay upload. Keep the app open online; they retry automatically."
}

private fun relaySettingsSubtitle(count: Int): String {
    return if (count > 0) {
        if (count == 1) {
            "1 queued encrypted upload pending"
        } else {
            "$count queued encrypted uploads pending"
        }
    } else {
        "Nostr relays, sync, and social workout posts"
    }
}

private fun relayUploadCurrentStateText(
    count: Int,
    status: RelayOutboxStatus,
    items: List<PendingItemStatus>,
    dataRelayUrls: List<String>,
    relayStates: Map<String, ConnectionState>,
): String {
    if (count <= 0) {
        return "Sent: no encrypted activity updates are waiting in the relay outbox."
    }
    val connectedCount = dataRelayUrls.count { url ->
        relayStates[url].let { it is ConnectionState.Connected || it is ConnectionState.Authenticated }
    }
    return when {
        status.lastFailureMessage.isNotBlank() ->
            "Failed: the last upload attempt returned an error. ERV keeps the data queued and retries automatically."
        items.any { it.attempts > 0 } ->
            "Retrying: at least one queued upload already tried once and is waiting for its next attempt."
        connectedCount == 0 ->
            "Queued: uploads are saved locally until one of your data relays reconnects."
        else ->
            "Queued: uploads are saved locally and waiting for a connected data relay to accept them."
    }
}

private fun relayUploadStatusGuideText(): String {
    return "Queued = saved locally and waiting. Sent = left the queue after a relay accepted it. Retrying = ERV will try again automatically after backoff or reconnection. Failed = the last attempt returned an error; details appear below while the item stays queued."
}

private fun relayUploadDiagnosticsText(
    count: Int,
    status: RelayOutboxStatus,
    dataRelayUrls: List<String>,
    relayStates: Map<String, ConnectionState>,
): String? {
    if (count <= 0) return null
    if (dataRelayUrls.isEmpty()) {
        return "No data relays are enabled, so queued uploads cannot be sent yet."
    }
    val connectedCount = dataRelayUrls.count { url ->
        relayStates[url].let { it is ConnectionState.Connected || it is ConnectionState.Authenticated }
    }
    if (connectedCount == 0) {
        return "No data relays are connected right now, so uploads remain queued until a relay reconnects."
    }
    if (status.lastFailureMessage.isNotBlank()) {
        return "Last relay upload failure: ${status.lastFailureMessage}. Connected data relays: $connectedCount/${dataRelayUrls.size}."
    }
    return "Connected data relays: $connectedCount/${dataRelayUrls.size}. The app will keep retrying queued uploads."
}

private fun relayCurrentCoverageText(coverage: CurrentRelayDataCoverage?): String {
    coverage ?: return "Coverage will appear after the app can check your current data relays."
    if (coverage.totalPayloadCount == 0) {
        return "No current encrypted payloads are ready to sync yet."
    }
    if (coverage.configuredRelayCount == 0) {
        return "${coverage.totalPayloadCount}/${coverage.totalPayloadCount} current encrypted payloads are ready locally. Add a data relay to sync them."
    }
    if (coverage.connectedRelayCount == 0) {
        return "No data relays are connected right now, so current payload coverage could not be checked."
    }
    return "${coverage.foundPayloadCount}/${coverage.totalPayloadCount} current encrypted payloads were found on connected data relays. Connected relays: ${coverage.connectedRelayCount}/${coverage.configuredRelayCount}. This checks the latest payload for each current section, not historical replaced copies."
}

@Composable
private fun PendingRelayItemsDebugList(
    items: List<PendingItemStatus>,
    failuresByDTag: Map<String, RelayOutboxItemFailure>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val now = System.currentTimeMillis()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Pending relay items:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (item in items.take(8)) {
            Text(
                relayPendingItemText(item, now, failuresByDTag[item.dTag]),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (items.size > 8) {
            Text(
                "Plus ${items.size - 8} more queued item(s).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun relayPendingItemText(
    item: PendingItemStatus,
    nowEpochMs: Long,
    failure: RelayOutboxItemFailure?,
): String {
    val statusLabel = when {
        failure != null -> "failed"
        item.attempts > 0 -> "retrying"
        else -> "queued"
    }
    val retryText = if (item.nextAttemptAtEpochMs <= nowEpochMs) {
        "ready now"
    } else {
        "retries in ${formatRetryDelay(item.nextAttemptAtEpochMs - nowEpochMs)}"
    }
    val failureText = failure?.message?.takeIf { it.isNotBlank() }?.let { " — last failure: $it" }.orEmpty()
    return "${item.dTag} — $statusLabel; attempts ${item.attempts}, $retryText$failureText"
}

private fun formatRetryDelay(ms: Long): String {
    val seconds = (ms / 1000L).coerceAtLeast(1L)
    return when {
        seconds < 60L -> "${seconds}s"
        seconds < 3600L -> "${seconds / 60L}m"
        else -> "${seconds / 3600L}h"
    }
}

@Composable
private fun SettingsHomeScreen(
    onBack: () -> Unit,
    onOpenSection: (String) -> Unit,
    pendingRelayUploadCount: Int,
    showDeferNostrLoginEntry: Boolean,
    onRequestNostrLogin: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    RelayDataSyncTopBarIcon()
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showDeferNostrLoginEntry) {
                FilledTonalButton(
                    onClick = onRequestNostrLogin,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Login or Create NOSTR Identity")
                }
                Text(
                    "Sign in to encrypt and sync health data to your relays. You can keep using the app locally until then.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            SettingsHubSectionLabel("Personal")
            SettingsHubRow(
                title = "Appearance",
                subtitle = "Light, dark, or system theme",
                icon = Icons.Default.SettingsBrightness,
                onClick = { onOpenSection(SettingsRoutes.APPEARANCE) }
            )
            SettingsHubRow(
                title = "Units & Body",
                subtitle = "Weight, distances, and training loads",
                icon = Icons.Default.Speed,
                onClick = { onOpenSection(SettingsRoutes.UNITS) }
            )
            SettingsHubSectionLabel("Training")
            SettingsHubRow(
                title = "Strength Training",
                subtitle = "Live workouts: ongoing notification, optional bubble",
                icon = Icons.Default.FitnessCenter,
                onClick = { onOpenSection(SettingsRoutes.STRENGTH) }
            )
            SettingsHubRow(
                title = "Cardio",
                subtitle = "GPS tracking, zones, and local route retention",
                icon = Icons.AutoMirrored.Filled.DirectionsRun,
                onClick = { onOpenSection(SettingsRoutes.CARDIO) }
            )
            SettingsHubRow(
                title = stringResource(R.string.settings_stretch_guided_voice_hub_title),
                subtitle = stringResource(R.string.settings_stretch_guided_voice_hub_subtitle),
                icon = Icons.Default.Timer,
                onClick = { onOpenSection(SettingsRoutes.STRETCHING) }
            )
            SettingsHubSectionLabel("Devices & Equipment")
            SettingsHubRow(
                title = "Saved Devices",
                subtitle = "Remember Bluetooth sensors for faster reconnects",
                icon = Icons.Default.Favorite,
                onClick = { onOpenSection(SettingsRoutes.SAVED_DEVICES) }
            )
            SettingsHubRow(
                title = "Equipment & Gym",
                subtitle = "What you own, gym access, and workout tags",
                icon = Icons.Default.Inventory2,
                onClick = { onOpenSection(SettingsRoutes.EQUIPMENT) }
            )
            SettingsHubSectionLabel("Account & Data")
            SettingsHubRow(
                title = "Account",
                subtitle = "Keys and logout",
                icon = Icons.Default.Person,
                onClick = { onOpenSection(SettingsRoutes.ACCOUNT) }
            )
            SettingsHubRow(
                title = "Relays",
                subtitle = relaySettingsSubtitle(pendingRelayUploadCount),
                icon = Icons.Default.Cloud,
                pendingBadgeCount = pendingRelayUploadCount,
                onClick = { onOpenSection(SettingsRoutes.RELAYS) }
            )
            SettingsHubRow(
                title = "Data Management",
                subtitle = "Export first, delete sections, or wipe this device",
                icon = Icons.Default.Delete,
                onClick = { onOpenSection(SettingsRoutes.DATA_MANAGEMENT) }
            )
            SettingsHubRow(
                title = "Data Interchange + Backup",
                subtitle = "Interchange files plus ERV-owned backup JSON and restore",
                icon = Icons.Default.Upload,
                onClick = { onOpenSection(SettingsRoutes.dataImportExportRoute()) }
            )
            SettingsHubRow(
                title = "Privacy Policy",
                subtitle = "Local-first limits, signer trust, and where data can live",
                icon = Icons.Default.Description,
                onClick = { onOpenSection(SettingsRoutes.importDocRoute(ImportExportDocAssets.KEY_PRIVACY_POLICY)) }
            )
            SettingsHubRow(
                title = "Android Permissions",
                subtitle = "Why ERV asks for Bluetooth, GPS, camera, and more",
                icon = Icons.Default.Description,
                onClick = { onOpenSection(SettingsRoutes.importDocRoute(ImportExportDocAssets.KEY_PERMISSIONS_GUIDE)) }
            )
            SettingsAboutContactFooter()
        }
    }
}

@Composable
private fun SettingsAboutContactFooter() {
    val context = LocalContext.current
    SettingsHubSectionLabel("About & contact")
    Text(
        text = "Questions, feedback, or bug reports (not for security issues — use SECURITY.md on GitHub).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    Text(
        text = "ERV is local first. Android cloud backup and device transfer are disabled for app data, so use ERV export or backup files when you want an off-device copy.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ERV_GITHUB_URL))
            runCatching { context.startActivity(intent) }
        },
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        ListItem(
            headlineContent = { Text("GitHub", style = MaterialTheme.typography.titleSmall) },
            supportingContent = {
                Text(
                    "Source code, issues, SECURITY.md",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Icon(
                    Icons.Default.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = "Open GitHub in browser"
                )
            },
            modifier = Modifier.semantics { contentDescription = "Open GitHub repository" }
        )
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            val intent = Intent(
                Intent.ACTION_SENDTO,
                Uri.parse("mailto:$ERV_CONTACT_EMAIL")
            )
            runCatching { context.startActivity(intent) }
        },
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        ListItem(
            headlineContent = { Text("Email", style = MaterialTheme.typography.titleSmall) },
            supportingContent = {
                Text(
                    ERV_CONTACT_EMAIL,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Icon(
                    Icons.Default.Email,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = "Open email app"
                )
            },
            modifier = Modifier.semantics { contentDescription = "Send email to $ERV_CONTACT_EMAIL" }
        )
    }
    Text(
        text = "Nostr (NIP-17 DM): $ERV_CONTACT_NOSTR_HANDLE",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsHubSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsSubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsHubRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    pendingBadgeCount: Int = 0,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
            supportingContent = {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingContent = {
                BadgedBox(
                    badge = {
                        if (pendingBadgeCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ) {
                                Text(
                                    if (pendingBadgeCount > 99) "99+" else pendingBadgeCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription =
                            if (pendingBadgeCount > 0) {
                                "Open Relays, $pendingBadgeCount uploads queued"
                            } else {
                                null
                            },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private suspend fun syncFitnessEquipmentToNostr(
    appContext: android.content.Context,
    relayPool: RelayPool?,
    signer: EventSigner?,
    keyManager: KeyManager,
    gymMembership: Boolean,
    ownedEquipment: List<OwnedEquipmentItem>
) {
    val pool = relayPool ?: return
    val sig = signer ?: return
    FitnessEquipmentSync.saveToNetwork(
        appContext,
        pool,
        sig,
        gymMembership,
        ownedEquipment,
        keyManager.relayUrlsForKind30078Publish(),
    )
}

private fun normalizeRelayUrl(input: String): String? {
    val s = input.trim()
    if (s.isEmpty()) return null
    return if (s.startsWith("wss://") || s.startsWith("ws://")) s else "$WSS_PREFIX$s"
}

@Composable
private fun SavedBluetoothDevicesSection(
    userPreferences: UserPreferences,
    scope: CoroutineScope,
) {
    val heartRateBle = LocalHeartRateBle.current
    val cyclingCscBle = LocalCyclingCsc.current
    val savedHeartRateDevices by heartRateBle.savedDevices.collectAsState()
    val preferredHeartRateAddress by heartRateBle.preferredDeviceAddress.collectAsState()
    val activeHeartRateAddress by heartRateBle.activeDeviceAddress.collectAsState()
    val heartRateConnectionState by heartRateBle.connectionState.collectAsState()
    val heartRateScanRows by heartRateBle.scanRows.collectAsState()
    val heartRateStatusMessage by heartRateBle.statusMessage.collectAsState()
    val savedCyclingDevices by cyclingCscBle.savedDevices.collectAsState()
    val preferredCyclingAddress by cyclingCscBle.preferredDeviceAddress.collectAsState()
    val activeCyclingAddress by cyclingCscBle.activeDeviceAddress.collectAsState()
    val cyclingConnectionState by cyclingCscBle.connectionState.collectAsState()
    val cyclingScanRows by cyclingCscBle.scanRows.collectAsState()
    val cyclingStatusMessage by cyclingCscBle.statusMessage.collectAsState()
    val cyclingWheelCircumferenceMm by userPreferences.cyclingWheelCircumferenceMm.collectAsState(initial = 2105)
    var wheelCircumferenceDraft by remember(cyclingWheelCircumferenceMm) {
        mutableStateOf(cyclingWheelCircumferenceMm.toString())
    }
    var heartRateScanDialogOpen by remember { mutableStateOf(false) }
    var cyclingScanDialogOpen by remember { mutableStateOf(false) }
    var pendingHeartRateConnectDevice by remember { mutableStateOf<SavedBluetoothDevice?>(null) }
    var pendingCyclingConnectDevice by remember { mutableStateOf<SavedBluetoothDevice?>(null) }
    var pendingScanTarget by remember { mutableStateOf<SavedBluetoothDeviceKind?>(null) }

    val requestBlePermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (heartRateBle.hasScanPermission() && heartRateBle.hasConnectPermission()) {
            val heartRateConnect = pendingHeartRateConnectDevice
            val cyclingConnect = pendingCyclingConnectDevice
            val scanTarget = pendingScanTarget
            pendingHeartRateConnectDevice = null
            pendingCyclingConnectDevice = null
            pendingScanTarget = null
            when {
                heartRateConnect != null -> heartRateBle.connectToSavedDevice(heartRateConnect)
                cyclingConnect != null -> cyclingCscBle.connectToSavedDevice(cyclingConnect)
                scanTarget == SavedBluetoothDeviceKind.HEART_RATE_MONITOR -> {
                    cyclingScanDialogOpen = false
                    heartRateScanDialogOpen = true
                    heartRateBle.startScanForSensors()
                }
                scanTarget == SavedBluetoothDeviceKind.CYCLING_SPEED_CADENCE_SENSOR -> {
                    heartRateScanDialogOpen = false
                    cyclingScanDialogOpen = true
                    cyclingCscBle.startScanForSensors()
                }
            }
        }
    }

    fun startHeartRateScan() {
        pendingHeartRateConnectDevice = null
        pendingCyclingConnectDevice = null
        pendingScanTarget = SavedBluetoothDeviceKind.HEART_RATE_MONITOR
        if (!heartRateBle.hasScanPermission() || !heartRateBle.hasConnectPermission()) {
            requestBlePermissions.launch(requiredBlePermissionsForHeartRate())
        } else {
            cyclingScanDialogOpen = false
            heartRateScanDialogOpen = true
            heartRateBle.startScanForSensors()
        }
    }

    fun startCyclingScan() {
        pendingHeartRateConnectDevice = null
        pendingCyclingConnectDevice = null
        pendingScanTarget = SavedBluetoothDeviceKind.CYCLING_SPEED_CADENCE_SENSOR
        if (!cyclingCscBle.hasScanPermission() || !cyclingCscBle.hasConnectPermission()) {
            requestBlePermissions.launch(requiredBlePermissionsForHeartRate())
        } else {
            heartRateScanDialogOpen = false
            cyclingScanDialogOpen = true
            cyclingCscBle.startScanForSensors()
        }
    }

    Text(
        "Saved Bluetooth devices",
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
                "ERV remembers sensors you connect so future sessions are faster. The last device you choose becomes the auto-connect device when Bluetooth is on and permission is already granted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Compatible smartwatches can connect when they expose the standard BLE heart rate service. Cycling Speed and Cadence (CSC) sensors can feed live bike workouts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (heartRateStatusMessage != null && savedHeartRateDevices.isEmpty()) {
                Text(
                    heartRateStatusMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (cyclingStatusMessage != null && savedCyclingDevices.isEmpty()) {
                Text(
                    cyclingStatusMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { startHeartRateScan() },
                    modifier = Modifier.weight(1f),
                    enabled = heartRateBle.bleHardwareAvailable
                ) {
                    Text("Scan HR")
                }
                Button(
                    onClick = { startCyclingScan() },
                    modifier = Modifier.weight(1f),
                    enabled = cyclingCscBle.bleHardwareAvailable
                ) {
                    Text("Scan CSC")
                }
            }
            if (!heartRateBle.bleHardwareAvailable) {
                Text(
                    "This phone does not report Bluetooth Low Energy support.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    Text(
        "Cycling wheel circumference",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Used to convert CSC wheel revolutions into bike speed and distance. A common road bike value is around 2105 mm.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = wheelCircumferenceDraft,
                onValueChange = { wheelCircumferenceDraft = it.filter { ch -> ch.isDigit() }.take(4) },
                label = { Text("Wheel circumference (mm)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val mm = wheelCircumferenceDraft.toIntOrNull()?.coerceIn(500, 4000) ?: 2105
                    wheelCircumferenceDraft = mm.toString()
                    scope.launch { userPreferences.setCyclingWheelCircumferenceMm(mm) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save wheel size")
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    Text(
        "Heart rate devices",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    if (savedHeartRateDevices.isEmpty()) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Text(
                "No saved heart rate devices yet. Compatible chest straps and smartwatches that advertise the standard BLE heart rate service will appear here after you connect once.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            savedHeartRateDevices.forEach { device ->
                SavedBluetoothDeviceCard(
                    device = device,
                    isPreferred = preferredHeartRateAddress == device.address,
                    isConnected = activeHeartRateAddress == device.address &&
                        heartRateConnectionState == HeartRateBleConnectionState.Connected,
                    isConnecting = activeHeartRateAddress == device.address &&
                        heartRateConnectionState == HeartRateBleConnectionState.Connecting,
                    statusMessage = heartRateStatusMessage,
                    onConnect = {
                        if (!heartRateBle.hasConnectPermission()) {
                            pendingHeartRateConnectDevice = device
                            pendingCyclingConnectDevice = null
                            pendingScanTarget = null
                            requestBlePermissions.launch(requiredBlePermissionsForHeartRate())
                        } else {
                            heartRateBle.connectToSavedDevice(device)
                        }
                    },
                    onDisconnect = { heartRateBle.disconnectUser() },
                    onForget = { heartRateBle.forgetSavedDevice(device.address) }
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    Text(
        "Cycling speed/cadence sensors",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    if (savedCyclingDevices.isEmpty()) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Text(
                "No saved CSC sensors yet. Connect a standard Cycling Speed and Cadence sensor to use wheel speed, cadence, and sensor distance in cycling live workouts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            savedCyclingDevices.forEach { device ->
                SavedBluetoothDeviceCard(
                    device = device,
                    isPreferred = preferredCyclingAddress == device.address,
                    isConnected = activeCyclingAddress == device.address &&
                        cyclingConnectionState == CyclingCscBleConnectionState.Connected,
                    isConnecting = activeCyclingAddress == device.address &&
                        cyclingConnectionState == CyclingCscBleConnectionState.Connecting,
                    statusMessage = cyclingStatusMessage,
                    onConnect = {
                        if (!cyclingCscBle.hasConnectPermission()) {
                            pendingHeartRateConnectDevice = null
                            pendingCyclingConnectDevice = device
                            pendingScanTarget = null
                            requestBlePermissions.launch(requiredBlePermissionsForHeartRate())
                        } else {
                            cyclingCscBle.connectToSavedDevice(device)
                        }
                    },
                    onDisconnect = { cyclingCscBle.disconnectUser() },
                    onForget = { cyclingCscBle.forgetSavedDevice(device.address) }
                )
            }
        }
    }

    if (heartRateScanDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                heartRateScanDialogOpen = false
                heartRateBle.stopScanInternal()
            },
            title = { Text(stringResource(R.string.hr_scan_dialog_title)) },
            text = {
                if (heartRateScanRows.isEmpty()) {
                    Text(stringResource(R.string.hr_scan_empty))
                } else {
                    Column {
                        heartRateScanRows.forEachIndexed { index, row ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        heartRateScanDialogOpen = false
                                        heartRateBle.connectToScannedRow(row)
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = row.name?.takeIf { it.isNotBlank() }
                                        ?: stringResource(R.string.hr_scan_unknown_name),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = row.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (index < heartRateScanRows.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        heartRateScanDialogOpen = false
                        heartRateBle.stopScanInternal()
                    }
                ) { Text(stringResource(R.string.hr_scan_done)) }
            }
        )
    }

    if (cyclingScanDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                cyclingScanDialogOpen = false
                cyclingCscBle.stopScanInternal()
            },
            title = { Text("Select a cycling sensor") },
            text = {
                if (cyclingScanRows.isEmpty()) {
                    Text("No CSC sensors found yet. Wake the sensor and spin the wheel or crank to advertise.")
                } else {
                    Column {
                        cyclingScanRows.forEachIndexed { index, row ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        cyclingScanDialogOpen = false
                                        cyclingCscBle.connectToScannedRow(row)
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = row.name?.takeIf { it.isNotBlank() } ?: "Cycling sensor",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = row.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (index < cyclingScanRows.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        cyclingScanDialogOpen = false
                        cyclingCscBle.stopScanInternal()
                    }
                ) { Text("Stop scanning") }
            }
        )
    }
}

@Composable
private fun SavedBluetoothDeviceCard(
    device: SavedBluetoothDevice,
    isPreferred: Boolean,
    isConnected: Boolean,
    isConnecting: Boolean,
    statusMessage: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                device.displayName(),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                device.kind.displayLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                device.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isPreferred) {
                Text(
                    "Auto-connect device",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (isConnected) {
                Text(
                    "Connected now",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (isConnecting) {
                Text(
                    "Connecting…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (isPreferred) {
                Text(
                    "ERV will try this device first next time the app starts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if ((isConnected || isConnecting) && statusMessage != null) {
                Text(
                    statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isConnected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Disconnect")
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.weight(1f),
                        enabled = !isConnecting
                    ) {
                        Text(if (isConnecting) "Connecting…" else "Connect")
                    }
                }
                OutlinedButton(
                    onClick = onForget,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Forget")
                }
            }
        }
    }
}

@Composable
private fun MaxHeartRateZonesSection(
    maxBpm: Int?,
    onSave: (Int?) -> Unit
) {
    var draft by remember(maxBpm) { mutableStateOf(maxBpm?.toString().orEmpty()) }
    Text(
        "Heart rate zones (BLE workouts)",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Optional max heart rate (90–230 bpm) for Z1–Z5 time charts after cardio or live lifts. Leave blank to use each workout’s peak BPM as a proxy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { s -> draft = s.filter { ch -> ch.isDigit() }.take(3) },
                label = { Text("Max HR (bpm)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val v = draft.trim().toIntOrNull()
                        onSave(v?.takeIf { it in 90..230 })
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
                OutlinedButton(
                    onClick = {
                        draft = ""
                        onSave(null)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun BodyWeightSection(
    value: String,
    unit: BodyWeightUnit,
    onSave: (String, BodyWeightUnit) -> Unit
) {
    var draft by remember(value) { mutableStateOf(value) }
    var draftUnit by remember(unit) { mutableStateOf(unit) }
    Text(
        "Body weight (for cardio calories)",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Optional. Used with MET estimates. Stored on device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Weight") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = draftUnit == BodyWeightUnit.LB,
                    onClick = { draftUnit = BodyWeightUnit.LB },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("lb") }
                SegmentedButton(
                    selected = draftUnit == BodyWeightUnit.KG,
                    onClick = { draftUnit = BodyWeightUnit.KG },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("kg") }
            }
            Button(
                onClick = { onSave(draft, draftUnit) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save weight") }
        }
    }
}

@Composable
private fun LiveWeightWorkoutSettingsSection(
    workoutBubbleEnabled: Boolean,
    notificationsEnabled: Boolean,
    onBubbleChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Text(
        stringResource(R.string.settings_live_weight_workout_title),
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
                stringResource(R.string.settings_live_weight_workout_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            stringResource(R.string.settings_live_weight_workout_bubble_switch),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            stringResource(R.string.settings_live_weight_workout_bubble_helper),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = workoutBubbleEnabled,
                        onCheckedChange = onBubbleChange
                    )
                }
            } else {
                Text(
                    stringResource(R.string.settings_live_weight_workout_bubble_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!notificationsEnabled) {
                Text(
                    stringResource(R.string.settings_live_weight_workout_notifications_denied),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Text(
                    stringResource(R.string.settings_live_weight_system_bubbles_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        )
                    } catch (_: Exception) {
                        // ignore
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_open_erv_notification_settings))
            }
        }
    }
}

@Composable
private fun WeightTrainingLoadSection(
    unit: BodyWeightUnit,
    onUnitChange: (BodyWeightUnit) -> Unit
) {
    Text(
        "Weight training loads",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Units when logging sets during a live workout. Workouts are still saved in kg for sync.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = unit == BodyWeightUnit.KG,
                    onClick = { onUnitChange(BodyWeightUnit.KG) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("Kilograms") }
                SegmentedButton(
                    selected = unit == BodyWeightUnit.LB,
                    onClick = { onUnitChange(BodyWeightUnit.LB) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("Pounds") }
            }
        }
    }
}

@Composable
private fun CardioDistanceSection(
    unit: CardioDistanceUnit,
    onUnitChange: (CardioDistanceUnit) -> Unit
) {
    Text(
        "Cardio distance & elevation",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Summaries, logs, workout forms, pace, and GPS elevation use this choice. Default is miles and feet; pick kilometers and meters if you prefer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = unit == CardioDistanceUnit.MILES,
                    onClick = { onUnitChange(CardioDistanceUnit.MILES) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("Miles / ft") }
                SegmentedButton(
                    selected = unit == CardioDistanceUnit.KILOMETERS,
                    onClick = { onUnitChange(CardioDistanceUnit.KILOMETERS) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("Kilometers / m") }
            }
        }
    }
}

@Composable
private fun CardioGpsSettingsSection(
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Text(
        stringResource(R.string.settings_cardio_gps_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    stringResource(R.string.settings_cardio_gps_switch),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    stringResource(R.string.settings_cardio_gps_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onChange
            )
        }
    }
}

@Composable
private fun CardioGpsRetentionSettingsSection(
    retainOnDevice: Boolean,
    onRetainChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val cardioDatastoreFile = remember(context) {
        File(context.applicationContext.filesDir, "datastore/erv_cardio.preferences_pb")
    }
    Text(
        stringResource(R.string.settings_cardio_gps_retention_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        stringResource(R.string.settings_cardio_gps_retention_switch),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(R.string.settings_cardio_gps_retention_switch_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = retainOnDevice,
                    onCheckedChange = onRetainChange
                )
            }
            Text(
                stringResource(R.string.settings_cardio_gps_retention_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.settings_cardio_gps_retention_storage_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.settings_cardio_gps_retention_storage_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                cardioDatastoreFile.absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(R.string.settings_cardio_gps_retention_backup),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CardioNostrRouteShareSection(
    uploadBackend: WorkoutMediaUploadBackend,
    onUploadBackendChange: (WorkoutMediaUploadBackend) -> Unit,
    attachRouteImage: Boolean,
    onAttachChange: (Boolean) -> Unit,
    nip96Draft: String,
    onNip96DraftChange: (String) -> Unit,
    onSaveNip96Origin: () -> Unit,
    blossomPublicDraft: String,
    onBlossomPublicDraftChange: (String) -> Unit,
    blossomPrivateDraft: String,
    onBlossomPrivateDraftChange: (String) -> Unit,
    onSaveBlossomServers: () -> Unit,
    relayPool: RelayPool?,
    blossomFetchPubkeyHex: String?,
    fetchScope: CoroutineScope,
    onUserMessage: (String) -> Unit,
    onApplyFetchedBlossomPublic: (String) -> Unit
) {
    val context = LocalContext.current
    var fetchingBlossomServers by remember { mutableStateOf(false) }
    var blossomPickList by remember { mutableStateOf<List<String>?>(null) }
    Text(
        stringResource(R.string.settings_cardio_nostr_route_title),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        stringResource(R.string.settings_cardio_nostr_route_switch),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(R.string.settings_cardio_nostr_route_switch_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = attachRouteImage,
                    onCheckedChange = onAttachChange
                )
            }
            Text(
                stringResource(R.string.settings_cardio_nostr_upload_type),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = uploadBackend == WorkoutMediaUploadBackend.NIP96,
                    onClick = { onUploadBackendChange(WorkoutMediaUploadBackend.NIP96) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text(stringResource(R.string.settings_cardio_nostr_upload_nip96)) }
                SegmentedButton(
                    selected = uploadBackend == WorkoutMediaUploadBackend.BLOSSOM,
                    onClick = { onUploadBackendChange(WorkoutMediaUploadBackend.BLOSSOM) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text(stringResource(R.string.settings_cardio_nostr_upload_blossom)) }
            }
            if (uploadBackend == WorkoutMediaUploadBackend.BLOSSOM) {
                Text(
                    stringResource(R.string.settings_cardio_nostr_fetch_blossom_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        fetchScope.launch {
                            fetchingBlossomServers = true
                            try {
                                val pk = blossomFetchPubkeyHex
                                if (pk == null) {
                                    onUserMessage(context.getString(R.string.settings_cardio_nostr_fetch_no_pubkey))
                                    return@launch
                                }
                                val pool = relayPool
                                if (pool == null) {
                                    onUserMessage(context.getString(R.string.settings_cardio_nostr_fetch_no_pool))
                                    return@launch
                                }
                                if (!pool.awaitAtLeastOneConnected(12_000)) {
                                    onUserMessage(context.getString(R.string.settings_cardio_nostr_fetch_no_relays))
                                    return@launch
                                }
                                val list = NipB7.fetchBlossomServersFromNetwork(pool, pk)
                                when {
                                    list.isEmpty() ->
                                        onUserMessage(context.getString(R.string.settings_cardio_nostr_fetch_blossom_none))
                                    list.size == 1 -> {
                                        val n = Nip96Uploader.normalizeMediaServerOrigin(list.first())
                                        onApplyFetchedBlossomPublic(n)
                                        onUserMessage(context.getString(R.string.settings_cardio_nostr_fetch_blossom_applied))
                                    }
                                    else -> blossomPickList = list
                                }
                            } finally {
                                fetchingBlossomServers = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !fetchingBlossomServers && relayPool != null && blossomFetchPubkeyHex != null
                ) {
                    Text(
                        if (fetchingBlossomServers) {
                            stringResource(R.string.settings_cardio_nostr_fetch_blossom_loading)
                        } else {
                            stringResource(R.string.settings_cardio_nostr_fetch_blossom)
                        }
                    )
                }
            }
            when (uploadBackend) {
                WorkoutMediaUploadBackend.NIP96 -> {
                    OutlinedTextField(
                        value = nip96Draft,
                        onValueChange = onNip96DraftChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_cardio_nostr_server_label_nip96)) },
                        supportingText = {
                            Text(stringResource(R.string.settings_cardio_nostr_server_helper_nip96))
                        },
                        singleLine = true
                    )
                    TextButton(onClick = onSaveNip96Origin) {
                        Text(stringResource(R.string.settings_cardio_nostr_save_nip96_url))
                    }
                }
                WorkoutMediaUploadBackend.BLOSSOM -> {
                    OutlinedTextField(
                        value = blossomPublicDraft,
                        onValueChange = onBlossomPublicDraftChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_cardio_nostr_blossom_public_label)) },
                        supportingText = {
                            Text(stringResource(R.string.settings_cardio_nostr_blossom_public_helper))
                        },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = blossomPrivateDraft,
                        onValueChange = onBlossomPrivateDraftChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_cardio_nostr_blossom_private_label)) },
                        supportingText = {
                            Text(stringResource(R.string.settings_cardio_nostr_blossom_private_helper))
                        },
                        singleLine = true
                    )
                    TextButton(onClick = onSaveBlossomServers) {
                        Text(stringResource(R.string.settings_cardio_nostr_save_blossom_servers))
                    }
                }
            }
        }
    }
    val blossomPick = blossomPickList
    if (blossomPick != null) {
        AlertDialog(
            onDismissRequest = { blossomPickList = null },
            title = { Text(stringResource(R.string.settings_cardio_nostr_fetch_blossom_pick_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    blossomPick.forEach { url ->
                        TextButton(
                            onClick = {
                                val n = Nip96Uploader.normalizeMediaServerOrigin(url)
                                onApplyFetchedBlossomPublic(n)
                                blossomPickList = null
                                onUserMessage(context.getString(R.string.settings_cardio_nostr_fetch_blossom_applied))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(url, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { blossomPickList = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun StretchGuidedTtsVoiceSection(
    voice: StretchGuidedTtsVoice,
    onVoiceChange: (StretchGuidedTtsVoice) -> Unit
) {
    Text(
        stringResource(R.string.settings_stretch_guided_voice_title),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    Text(
        stringResource(R.string.settings_stretch_guided_voice_helper),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (voice) {
                        StretchGuidedTtsVoice.SYSTEM_DEFAULT ->
                            stringResource(R.string.settings_stretch_guided_voice_default)
                        StretchGuidedTtsVoice.PREFER_FEMALE ->
                            stringResource(R.string.settings_stretch_guided_voice_female)
                        StretchGuidedTtsVoice.PREFER_MALE ->
                            stringResource(R.string.settings_stretch_guided_voice_male)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = voice == StretchGuidedTtsVoice.SYSTEM_DEFAULT,
                    onClick = { onVoiceChange(StretchGuidedTtsVoice.SYSTEM_DEFAULT) },
                    shape = SegmentedButtonDefaults.itemShape(0, 3)
                ) {
                    Text(stringResource(R.string.settings_stretch_guided_voice_default))
                }
                SegmentedButton(
                    selected = voice == StretchGuidedTtsVoice.PREFER_FEMALE,
                    onClick = { onVoiceChange(StretchGuidedTtsVoice.PREFER_FEMALE) },
                    shape = SegmentedButtonDefaults.itemShape(1, 3)
                ) {
                    Text(stringResource(R.string.settings_stretch_guided_voice_female))
                }
                SegmentedButton(
                    selected = voice == StretchGuidedTtsVoice.PREFER_MALE,
                    onClick = { onVoiceChange(StretchGuidedTtsVoice.PREFER_MALE) },
                    shape = SegmentedButtonDefaults.itemShape(2, 3)
                ) {
                    Text(stringResource(R.string.settings_stretch_guided_voice_male))
                }
            }
        }
    }
}

@Composable
private fun ThemeSection(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    Text(
        "Theme",
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SettingsBrightness,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (themeMode) {
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.SYSTEM -> "System"
                        ThemeMode.DARK -> "Dark"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = themeMode == ThemeMode.LIGHT,
                    onClick = { onThemeChange(ThemeMode.LIGHT) },
                    shape = SegmentedButtonDefaults.itemShape(0, 3)
                ) {
                    Icon(Icons.Default.LightMode, contentDescription = "Light theme")
                }
                SegmentedButton(
                    selected = themeMode == ThemeMode.SYSTEM,
                    onClick = { onThemeChange(ThemeMode.SYSTEM) },
                    shape = SegmentedButtonDefaults.itemShape(1, 3)
                ) {
                    Icon(Icons.Default.SettingsBrightness, contentDescription = "System theme")
                }
                SegmentedButton(
                    selected = themeMode == ThemeMode.DARK,
                    onClick = { onThemeChange(ThemeMode.DARK) },
                    shape = SegmentedButtonDefaults.itemShape(2, 3)
                ) {
                    Icon(Icons.Default.DarkMode, contentDescription = "Dark theme")
                }
            }
        }
    }
}

@Composable
private fun IdentitySection(
    keyManager: KeyManager,
    userPreferences: UserPreferences,
    relayPool: RelayPool?,
    signer: EventSigner?,
    scope: CoroutineScope,
    onShowMessage: (String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var copiedLabel by remember { mutableStateOf<String?>(null) }

    val pubkey = keyManager.publicKeyHex
    val nostrReady = pubkey != null && signer != null && relayPool != null

    val localName by userPreferences.localProfileDisplayName.collectAsState(initial = null)
    val localPic by userPreferences.localProfilePictureUrl.collectAsState(initial = null)
    val localBio by userPreferences.localProfileBio.collectAsState(initial = null)

    var localDraftName by remember { mutableStateOf("") }
    var localDraftPic by remember { mutableStateOf("") }
    var localDraftBio by remember { mutableStateOf("") }
    LaunchedEffect(localName, localPic, localBio) {
        localDraftName = localName.orEmpty()
        localDraftPic = localPic.orEmpty()
        localDraftBio = localBio.orEmpty()
    }

    val cachedProfile = remember(localName, localPic, localBio) {
        ProfileMetadata(
            picture = localPic.orEmpty().trim(),
            about = localBio.orEmpty(),
        ).withUnifiedDisplayName(localName.orEmpty().trim())
    }
    var metaOverride by remember(pubkey) { mutableStateOf<ProfileMetadata?>(null) }
    val meta = metaOverride ?: cachedProfile
    var loadingProfile by remember(pubkey, relayPool, signer) {
        mutableStateOf(false)
    }
    var publishing by remember { mutableStateOf(false) }

    LaunchedEffect(copiedLabel) {
        if (copiedLabel != null) {
            kotlinx.coroutines.delay(1500)
            copiedLabel = null
        }
    }

    Text(
        "Identity",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val npub = keyManager.npub
            val hex = keyManager.publicKeyHex

            Text(
                text = "Public Key (npub)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (copiedLabel == "npub") "Copied!" else npub ?: "Not logged in",
                style = MaterialTheme.typography.bodySmall,
                color = if (copiedLabel == "npub") MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .then(
                        if (npub != null) Modifier.clickable {
                            clipboardManager.setText(AnnotatedString(npub))
                            copiedLabel = "npub"
                        } else Modifier
                    )
            )

            Text(
                text = "Public Key (hex)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (copiedLabel == "hex") "Copied!" else hex ?: "Not logged in",
                style = MaterialTheme.typography.bodySmall,
                color = if (copiedLabel == "hex") MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .then(
                        if (hex != null) Modifier.clickable {
                            clipboardManager.setText(AnnotatedString(hex))
                            copiedLabel = "hex"
                        } else Modifier
                    )
            )

            Text(
                text = "Login Method",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = keyManager.loginMethod ?: "None",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "If you use a remote signer, that signer may be able to sign or decrypt on your behalf. Only connect signers you trust.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.settings_identity_profile_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (nostrReady) {
                Text(
                    text = stringResource(R.string.settings_identity_profile_helper_nostr),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (loadingProfile) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    ProfilePicturePreview(
                        url = meta.picture,
                        modifier = Modifier.size(72.dp)
                    )
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                loadingProfile = true
                                try {
                                    relayPool!!.awaitAtLeastOneConnected(10_000)
                                    val remote = Nip01Metadata.fetchLatestFromNetwork(
                                        relayPool!!,
                                        pubkey!!,
                                        timeoutMs = 8000
                                    )
                                    if (remote != null && remote.hasPublicTextOrPicture()) {
                                        metaOverride = remote
                                        userPreferences.setLocalProfileDisplayName(remote.primaryLabel())
                                        userPreferences.setLocalProfilePictureUrl(remote.picture)
                                        userPreferences.setLocalProfileBio(remote.about)
                                        onShowMessage(context.getString(R.string.settings_identity_fetch_loaded))
                                    } else {
                                        onShowMessage(context.getString(R.string.settings_identity_fetch_none))
                                    }
                                } catch (_: Exception) {
                                    onShowMessage(context.getString(R.string.settings_identity_fetch_failed))
                                } finally {
                                    loadingProfile = false
                                }
                            }
                        },
                        enabled = !loadingProfile && !publishing
                    ) {
                        Text(stringResource(R.string.settings_identity_fetch_profile))
                    }
                }
                OutlinedTextField(
                    value = meta.primaryLabel(),
                    onValueChange = { metaOverride = meta.withUnifiedDisplayName(it) },
                    label = { Text(stringResource(R.string.settings_identity_display_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loadingProfile && !publishing,
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = meta.picture,
                    onValueChange = { metaOverride = meta.copy(picture = it) },
                    label = { Text(stringResource(R.string.settings_identity_picture_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loadingProfile && !publishing,
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = meta.about,
                    onValueChange = { metaOverride = meta.copy(about = it) },
                    label = { Text(stringResource(R.string.settings_identity_bio)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loadingProfile && !publishing,
                    minLines = 3
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            publishing = true
                            try {
                                val metaOk = Nip01Metadata.publish(
                                    relayPool = relayPool!!,
                                    signer = signer!!,
                                    meta = meta,
                                    relayUrls = keyManager.relayUrlsForKind0Publish()
                                )
                                val nip65Ok = if (metaOk) {
                                    Nip65.publishRelayListIfAllowed(
                                        userPreferences,
                                        relayPool!!,
                                        signer!!,
                                        keyManager,
                                    )
                                } else {
                                    false
                                }
                                if (metaOk) {
                                    userPreferences.setLocalProfileDisplayName(meta.primaryLabel())
                                    userPreferences.setLocalProfilePictureUrl(meta.picture.trim())
                                    userPreferences.setLocalProfileBio(meta.about)
                                    onShowMessage(
                                        if (nip65Ok) {
                                            context.getString(R.string.settings_identity_saved_nostr)
                                        } else {
                                            context.getString(R.string.settings_identity_saved_nostr_relay_list_failed)
                                        }
                                    )
                                } else {
                                    onShowMessage(context.getString(R.string.settings_identity_publish_failed))
                                }
                            } catch (_: Exception) {
                                onShowMessage(context.getString(R.string.settings_identity_publish_failed))
                            } finally {
                                publishing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loadingProfile && !publishing
                ) {
                    Text(stringResource(R.string.settings_identity_save_nostr))
                }
            } else {
                Text(
                    text = stringResource(R.string.settings_identity_profile_helper_local),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                ProfilePicturePreview(
                    url = localDraftPic,
                    modifier = Modifier
                        .size(72.dp)
                        .padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = localDraftName,
                    onValueChange = { localDraftName = it },
                    label = { Text(stringResource(R.string.settings_identity_display_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = localDraftPic,
                    onValueChange = { localDraftPic = it },
                    label = { Text(stringResource(R.string.settings_identity_picture_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = localDraftBio,
                    onValueChange = { localDraftBio = it },
                    label = { Text(stringResource(R.string.settings_identity_bio)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            userPreferences.setLocalProfileDisplayName(localDraftName)
                            userPreferences.setLocalProfilePictureUrl(localDraftPic)
                            userPreferences.setLocalProfileBio(localDraftBio)
                            onShowMessage(context.getString(R.string.settings_identity_saved_local))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_identity_save_local))
                }
            }
        }
    }

    if (keyManager.isLoggedIn) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Logout")
        }
    }
}

@Composable
private fun ProfilePicturePreview(
    url: String,
    modifier: Modifier = Modifier
) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = null
        val u = url.trim()
        if (u.isEmpty()) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            try {
                URL(u).openConnection().apply {
                    connectTimeout = 12_000
                    readTimeout = 12_000
                }.getInputStream().use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } catch (_: Exception) {
                null
            }
        }
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun RelayAddRow(
    suffix: String,
    onSuffixChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        OutlinedTextField(
            value = suffix,
            onValueChange = onSuffixChange,
            prefix = {
                Text(
                    text = WSS_PREFIX,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
            },
            label = { Text("Add relay") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onAdd,
            enabled = suffix.isNotBlank()
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add relay")
        }
    }
}

@Composable
private fun RelayRow(
    url: String,
    connectionState: ConnectionState,
    isData: Boolean,
    isSocial: Boolean,
    onToggleData: (Boolean) -> Unit,
    onToggleSocial: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RelayConnectionLed(connectionState = connectionState)
            Spacer(Modifier.width(8.dp))
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove relay",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        when (connectionState) {
            is ConnectionState.Error -> {
                Text(
                    text = connectionState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 18.dp, top = 2.dp)
                )
            }
            else -> Unit
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 18.dp, top = 2.dp, bottom = 4.dp)
        ) {
            FilterChip(
                selected = isData,
                onClick = { onToggleData(!isData) },
                label = { Text("Data", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp)
            )
            FilterChip(
                selected = isSocial,
                onClick = { onToggleSocial(!isSocial) },
                label = { Text("Social", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp)
            )
        }
    }
}

@Composable
private fun RelayConnectionLed(connectionState: ConnectionState) {
    val (color, desc) = when (connectionState) {
        is ConnectionState.Connected, is ConnectionState.Authenticated -> Color(0xFF4CAF50) to "Connected"
        is ConnectionState.Connecting -> MaterialTheme.colorScheme.tertiary to "Connecting"
        is ConnectionState.Error, is ConnectionState.Disconnected -> Color(0xFFE53935) to "Disconnected"
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, CircleShape)
            .semantics { contentDescription = desc }
    )
}
