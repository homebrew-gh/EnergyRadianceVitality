package com.erv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.erv.app.data.ThemeMode
import com.erv.app.data.UserPreferences
import com.erv.app.nostr.AmberLauncherHost
import com.erv.app.nostr.AmberSigner
import com.erv.app.nostr.ConnectionState
import com.erv.app.nostr.KeyManager
import com.erv.app.nostr.RelayPool
import kotlinx.coroutines.launch

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
    var relayUrls by remember { mutableStateOf(keyManager.relayUrls) }

    LaunchedEffect(relayUrls, relayPool) {
        relayPool?.setRelays(relayUrls)
    }
    DisposableEffect(relayPool) {
        onDispose { relayPool?.disconnect() }
    }

    val relayStates by (relayPool?.relayStates ?: snapshotFlow { emptyMap<String, ConnectionState>() })
        .collectAsState(initial = emptyMap())

    var newRelaySuffix by remember { mutableStateOf("") }

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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // --- Relays ---
            Text(
                "Relays",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            relayUrls.forEach { url ->
                RelayRow(
                    url = url,
                    connectionState = relayStates[url] ?: ConnectionState.Disconnected,
                    onRemove = {
                        keyManager.removeRelay(url)
                        relayUrls = keyManager.relayUrls
                    }
                )
            }

            if (relayUrls.isEmpty()) {
                Text(
                    "No relays configured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = newRelaySuffix,
                    onValueChange = { newRelaySuffix = it },
                    leadingIcon = {
                        Text(
                            text = WSS_PREFIX,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    label = { Text("Add relay") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val url = newRelaySuffix.trim().let { s ->
                            if (s.isEmpty()) null
                            else if (s.startsWith("wss://") || s.startsWith("ws://")) s
                            else "$WSS_PREFIX$s"
                        }
                        if (url != null) {
                            keyManager.addRelay(url)
                            relayUrls = keyManager.relayUrls
                            newRelaySuffix = ""
                        }
                    },
                    enabled = newRelaySuffix.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add relay")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- Identity ---
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
                    Text(
                        text = "Public Key",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = keyManager.npub ?: "Not logged in",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- Theme ---
            Text(
                "Theme",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ThemeMode.entries.forEach { mode ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    RadioButton(
                        selected = themeMode == mode,
                        onClick = { scope.launch { userPreferences.setThemeMode(mode) } }
                    )
                    Text(
                        text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp, top = 12.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RelayRow(
    url: String,
    connectionState: ConnectionState,
    onRemove: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
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
