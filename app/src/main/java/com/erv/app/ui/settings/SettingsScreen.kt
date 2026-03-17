package com.erv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.erv.app.nostr.NostrClient
import kotlinx.coroutines.flow.flowOf
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
    // Store only the part after "wss://" so user doesn't have to type it
    var relayUrlSuffix by remember {
        mutableStateOf(
            keyManager.relayUrl
                ?.removePrefix(WSS_PREFIX)
                ?.removePrefix("ws://")
                ?: ""
        )
    }

    val signer = remember(keyManager, amberHost) {
        keyManager.createLocalSigner()
            ?: (if (keyManager.loginMethod == KeyManager.LOGIN_AMBER && keyManager.publicKeyHex != null)
                AmberSigner(keyManager.publicKeyHex!!, amberHost)
            else null)
    }
    val nostrClient = remember(signer) { signer?.let { NostrClient(it) } }
    val fullRelayUrl = remember(relayUrlSuffix) {
        relayUrlSuffix.trim().let { if (it.isEmpty()) null else if (it.startsWith("wss://") || it.startsWith("ws://")) it else "$WSS_PREFIX$it" }
    }
    LaunchedEffect(fullRelayUrl, nostrClient) {
        if (fullRelayUrl != null && nostrClient != null) {
            nostrClient.connect(fullRelayUrl)
        }
    }
    DisposableEffect(nostrClient) {
        onDispose { nostrClient?.disconnect() }
    }
    val connectionState by (nostrClient?.connectionState ?: flowOf(ConnectionState.Disconnected))
        .collectAsState(initial = ConnectionState.Disconnected)

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
            // --- Relay ---
            Row(
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Relay",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.width(8.dp))
                RelayConnectionLed(connectionState = connectionState)
            }
            OutlinedTextField(
                value = relayUrlSuffix,
                onValueChange = {
                    relayUrlSuffix = it
                    val normalized = it.trim().let { s ->
                        if (s.isEmpty()) null
                        else if (s.startsWith("wss://") || s.startsWith("ws://")) s
                        else "$WSS_PREFIX$s"
                    }
                    keyManager.relayUrl = normalized
                },
                leadingIcon = {
                    Text(
                        text = WSS_PREFIX,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = { Text("Relay URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

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
