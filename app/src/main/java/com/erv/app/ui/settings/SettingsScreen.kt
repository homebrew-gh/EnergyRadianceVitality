package com.erv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.erv.app.data.ThemeMode
import com.erv.app.data.UserPreferences
import com.erv.app.nostr.AmberLauncherHost
import com.erv.app.nostr.AmberSigner
import com.erv.app.nostr.ConnectionState
import com.erv.app.nostr.KeyManager
import com.erv.app.nostr.Nip65
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.SettingsSync
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val WSS_PREFIX = "wss://"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    keyManager: KeyManager,
    amberHost: AmberLauncherHost,
    userPreferences: UserPreferences,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val themeMode by userPreferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

    val signer = remember(keyManager, amberHost) {
        keyManager.createLocalSigner()
            ?: (if (keyManager.loginMethod == KeyManager.LOGIN_AMBER && keyManager.publicKeyHex != null)
                AmberSigner(keyManager.publicKeyHex!!, amberHost)
            else null)
    }

    val relayPool = remember(signer) { signer?.let { RelayPool(it) } }
    var relayRevision by remember { mutableIntStateOf(0) }
    val allRelays = remember(relayRevision) { keyManager.allRelayUrls() }
    var fetchingRelays by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val relaysForPool = remember(allRelays, fetchingRelays) {
        (allRelays + if (fetchingRelays) Nip65.bootstrapRelays else emptyList()).distinct()
    }
    LaunchedEffect(relaysForPool, relayPool) {
        relayPool?.setRelays(relaysForPool)
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            ThemeSection(
                themeMode = themeMode,
                onThemeChange = { mode -> scope.launch { userPreferences.setThemeMode(mode) } }
            )

            Spacer(Modifier.height(12.dp))

            IdentitySection(
                keyManager = keyManager,
                onLogout = onLogout
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "Relays",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
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
                        hasUnsavedChanges = true
                    },
                    onToggleSocial = { enabled ->
                        if (enabled) keyManager.addSocialRelay(url) else keyManager.removeSocialRelay(url)
                        relayRevision++
                        hasUnsavedChanges = true
                    },
                    onRemove = {
                        keyManager.removeRelayCompletely(url)
                        relayRevision++
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
                        hasUnsavedChanges = true
                        newRelaySuffix = ""
                    }
                }
            )

            Spacer(Modifier.height(8.dp))
            if (keyManager.socialRelayUrls.isEmpty()) {
                OutlinedButton(
                    onClick = {
                        val pubkey = keyManager.publicKeyHex ?: return@OutlinedButton
                        scope.launch {
                            fetchingRelays = true
                            try {
                                kotlinx.coroutines.delay(1500)
                                val urls = Nip65.fetchRelayListFromNetwork(relayPool!!, pubkey)
                                if (urls.isEmpty()) {
                                    snackbarMessage = "No relay list found. Set up relays in Damus/Primal first, or add manually."
                                } else {
                                    urls.forEach { keyManager.addSocialRelay(it) }
                                    relayRevision++
                                    hasUnsavedChanges = true
                                    snackbarMessage = "Added ${urls.size} relay(s) as social."
                                }
                            } catch (e: Exception) {
                                snackbarMessage = "Fetch failed: ${e.message}"
                            } finally {
                                fetchingRelays = false
                            }
                        }
                    },
                    enabled = relayPool != null && keyManager.publicKeyHex != null && !fetchingRelays,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (fetchingRelays) "Fetching…" else "Fetch social relays from network")
                }
            } else {
                Text(
                    "Social relays imported from network.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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

            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun normalizeRelayUrl(input: String): String? {
    val s = input.trim()
    if (s.isEmpty()) return null
    return if (s.startsWith("wss://") || s.startsWith("ws://")) s else "$WSS_PREFIX$s"
}

@Composable
private fun ThemeSection(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    val currentValue = when (themeMode) {
        ThemeMode.LIGHT -> 0f
        ThemeMode.SYSTEM -> 1f
        ThemeMode.DARK -> 2f
    }

    Text(
        "Theme",
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LightMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Light", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.SettingsBrightness, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("System", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("Dark", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            }
            Slider(
                value = currentValue,
                onValueChange = { value ->
                    val mode = when (value.roundToInt()) {
                        0 -> ThemeMode.LIGHT
                        1 -> ThemeMode.SYSTEM
                        else -> ThemeMode.DARK
                    }
                    onThemeChange(mode)
                },
                valueRange = 0f..2f,
                steps = 1
            )
            Text(
                text = when (themeMode) {
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.SYSTEM -> "System"
                    ThemeMode.DARK -> "Dark"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

@OptIn(ExperimentalMaterial3Api::class)
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
