@file:OptIn(ExperimentalMaterial3Api::class)
package com.erv.app.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.erv.app.R
import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.OwnedEquipmentItem
import com.erv.app.data.ThemeMode
import com.erv.app.data.UserPreferences
import com.erv.app.data.WorkoutMediaUploadBackend
import com.erv.app.nostr.AmberLauncherHost
import com.erv.app.nostr.AmberSigner
import com.erv.app.nostr.ConnectionState
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.FitnessEquipmentSync
import com.erv.app.nostr.KeyManager
import com.erv.app.nostr.Nip96Uploader
import com.erv.app.nostr.NipB7
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.SettingsSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

private const val WSS_PREFIX = "wss://"

private object SettingsRoutes {
    const val HOME = "settings_home"
    const val APPEARANCE = "settings_appearance"
    const val UNITS = "settings_units"
    const val CARDIO = "settings_cardio"
    const val STRENGTH = "settings_strength"
    const val ACCOUNT = "settings_account"
    const val RELAYS = "settings_relays"
    const val EQUIPMENT = "settings_equipment"
}

@Composable
fun SettingsScreen(
    keyManager: KeyManager,
    amberHost: AmberLauncherHost,
    userPreferences: UserPreferences,
    onBack: () -> Unit,
    onRelaysChanged: () -> Unit = {},
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeMode by userPreferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val bodyWeightValue by userPreferences.bodyWeightValue.collectAsState(initial = "")
    val bodyWeightUnit by userPreferences.bodyWeightUnit.collectAsState(initial = BodyWeightUnit.LB)
    val cardioDistanceUnit by userPreferences.cardioDistanceUnit.collectAsState(initial = CardioDistanceUnit.MILES)
    val cardioGpsPreferred by userPreferences.cardioGpsRecordingPreferred.collectAsState(initial = true)
    val cardioGpsTrackRetainOnDevice by userPreferences.cardioGpsTrackRetainOnDevice.collectAsState(initial = true)
    val nip96MediaOrigin by userPreferences.nip96MediaServerOrigin.collectAsState(initial = "")
    val blossomPublicSaved by userPreferences.blossomPublicServerOrigin.collectAsState(initial = "")
    val blossomPrivateSaved by userPreferences.blossomPrivateServerOrigin.collectAsState(initial = "")
    val workoutMediaBackend by userPreferences.workoutMediaUploadBackend.collectAsState(
        initial = WorkoutMediaUploadBackend.NIP96
    )
    val attachRouteToNostr by userPreferences.attachRouteImageToWorkoutNostrShare.collectAsState(initial = false)
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

    val signer = remember(keyManager, amberHost) {
        keyManager.createLocalSigner()
            ?: (if (keyManager.loginMethod == KeyManager.LOGIN_AMBER && keyManager.publicKeyHex != null && keyManager.amberPackageName != null)
                AmberSigner(keyManager.publicKeyHex!!, amberHost, context.contentResolver, keyManager.amberPackageName!!)
            else null)
    }

    val relayPool = remember(signer) { signer?.let { RelayPool(it) } }
    var relayRevision by remember { mutableIntStateOf(0) }
    val allRelays = remember(relayRevision) { keyManager.allRelayUrls() }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(allRelays, relayPool) {
        relayPool?.setRelays(keyManager.relayUrlsForPool())
    }
    DisposableEffect(relayPool) {
        onDispose { relayPool?.disconnect() }
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
                val homeContext = LocalContext.current
                SettingsHomeScreen(
                    onBack = onBack,
                    onOpenSection = { nestedNav.navigate(it) },
                    workoutBubbleEnabled = workoutBubbleEnabled,
                    notificationsEnabled = NotificationManagerCompat.from(homeContext).areNotificationsEnabled(),
                    onBubbleChange = { enabled ->
                        scope.launch { userPreferences.setWorkoutBubbleEnabled(enabled) }
                    }
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
                    title = "Cardio & Sharing",
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
            composable(SettingsRoutes.ACCOUNT) {
                SettingsSubScreenScaffold(
                    title = "Account",
                    onBack = { nestedNav.popBackStack() }
                ) {
                    IdentitySection(
                        keyManager = keyManager,
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
                        weightUnit = weightTrainingLoadUnit,
                        onGymMembershipChange = { enabled ->
                            scope.launch {
                                userPreferences.setGymMembership(enabled)
                                syncFitnessEquipmentToNostr(relayPool, signer, enabled, ownedEquipment)
                            }
                        },
                        onEquipmentChange = { list ->
                            scope.launch {
                                userPreferences.setOwnedEquipment(list)
                                syncFitnessEquipmentToNostr(relayPool, signer, gymMembership, list)
                            }
                        }
                    )
                }
            }
            composable(SettingsRoutes.RELAYS) {
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
                                        val ok = SettingsSync.saveToNetwork(relayPool, signer, keyManager)
                                        if (ok) {
                                            hasUnsavedChanges = false
                                            snackbarMessage = "Settings saved"
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
                }
            }
        }
    }
}

@Composable
private fun SettingsHomeScreen(
    onBack: () -> Unit,
    onOpenSection: (String) -> Unit,
    workoutBubbleEnabled: Boolean,
    notificationsEnabled: Boolean,
    onBubbleChange: (Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LiveWeightWorkoutSettingsSection(
                workoutBubbleEnabled = workoutBubbleEnabled,
                notificationsEnabled = notificationsEnabled,
                onBubbleChange = onBubbleChange
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "More settings",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
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
            SettingsHubRow(
                title = "Cardio & Sharing",
                subtitle = "GPS tracking and workout route uploads",
                icon = Icons.Default.DirectionsRun,
                onClick = { onOpenSection(SettingsRoutes.CARDIO) }
            )
            SettingsHubRow(
                title = "Strength Training",
                subtitle = "Live workout bubble (same controls as above)",
                icon = Icons.Default.FitnessCenter,
                onClick = { onOpenSection(SettingsRoutes.STRENGTH) }
            )
            SettingsHubRow(
                title = "Equipment & Gym",
                subtitle = "What you own, gym access, and workout tags",
                icon = Icons.Default.Inventory2,
                onClick = { onOpenSection(SettingsRoutes.EQUIPMENT) }
            )
            SettingsHubRow(
                title = "Account",
                subtitle = "Keys and logout",
                icon = Icons.Default.Person,
                onClick = { onOpenSection(SettingsRoutes.ACCOUNT) }
            )
            SettingsHubRow(
                title = "Relays",
                subtitle = "Nostr data and social sync",
                icon = Icons.Default.Cloud,
                onClick = { onOpenSection(SettingsRoutes.RELAYS) }
            )
        }
    }
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
                .padding(bottom = 32.dp),
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
    onClick: () -> Unit
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
                Icon(
                    Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private suspend fun syncFitnessEquipmentToNostr(
    relayPool: RelayPool?,
    signer: EventSigner?,
    gymMembership: Boolean,
    ownedEquipment: List<OwnedEquipmentItem>
) {
    val pool = relayPool ?: return
    val sig = signer ?: return
    FitnessEquipmentSync.saveToNetwork(pool, sig, gymMembership, ownedEquipment)
}

private fun normalizeRelayUrl(input: String): String? {
    val s = input.trim()
    if (s.isEmpty()) return null
    return if (s.startsWith("wss://") || s.startsWith("ws://")) s else "$WSS_PREFIX$s"
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
    onLogout: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var copiedLabel by remember { mutableStateOf<String?>(null) }

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
        }
    }

    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = onLogout,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Logout")
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
