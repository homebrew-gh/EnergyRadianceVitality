package com.erv.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erv.app.ui.components.FieldLabel
import com.erv.app.R
import com.erv.app.nostr.ConnectionState
import com.erv.app.nostr.KeyManager
import com.erv.app.nostr.RelayPool
import androidx.compose.runtime.snapshotFlow
import com.erv.app.nostr.RelayUrls

private const val WSS_PREFIX = "wss://"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelaySetupScreen(
    keyManager: KeyManager,
    relayPool: RelayPool?,
    trustSelfSignedLanTls: Boolean = false,
    onTrustTlsChange: (Boolean) -> Unit = {},
    onContinue: () -> Unit
) {
    var relayRevision by remember { mutableIntStateOf(0) }
    val allRelays = remember(relayRevision) { keyManager.allRelayUrls() }
    val urlsForPool = remember(relayRevision) { keyManager.relayUrlsForPool() }

    LaunchedEffect(urlsForPool, relayPool) {
        relayPool?.setRelays(urlsForPool)
    }
    // Relays (including NIP-65 social) are already populated in runPostLoginSetup before this screen loads.

    val relayStates by (relayPool?.relayStates ?: snapshotFlow { emptyMap<String, ConnectionState>() })
        .collectAsState(initial = emptyMap())

    var newRelaySuffix by remember { mutableStateOf("") }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            Text("Set up your relays", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Relays store and deliver your data. " +
                    "Toggle Data for encrypted activity storage, " +
                    "and Social for public posts like workout summaries.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Data — your activity is NIP-44 encrypted. Only you can read it, even on public relays.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Social — workout summaries are posted as plain text so friends and followers can see them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            RelaySelfSignedTlsRow(
                checked = trustSelfSignedLanTls,
                onCheckedChange = onTrustTlsChange,
            )

            Spacer(Modifier.height(12.dp))

            allRelays.forEach { url ->
                SetupRelayRow(
                    url = url,
                    connectionState = relayStates[url] ?: ConnectionState.Disconnected,
                    isData = keyManager.isDataRelay(url),
                    isSocial = keyManager.isSocialRelay(url),
                    onToggleData = { enabled ->
                        if (enabled) keyManager.addRelay(url) else keyManager.removeRelay(url)
                        relayRevision++
                    },
                    onToggleSocial = { enabled ->
                        if (enabled) keyManager.addSocialRelay(url) else keyManager.removeSocialRelay(url)
                        relayRevision++
                    },
                    onRemove = {
                        keyManager.removeRelayCompletely(url)
                        relayRevision++
                    }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
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
                    label = { FieldLabel("Add relay") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val url = RelayUrls.normalize(newRelaySuffix, addDefaultScheme = true)
                        if (url != null) {
                            keyManager.addRelay(url)
                            relayRevision++
                            newRelaySuffix = ""
                        }
                    },
                    enabled = newRelaySuffix.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add relay")
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onContinue,
                enabled = allRelays.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupRelayRow(
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
            ConnectionLed(connectionState)
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
                label = { FieldLabel("Data", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp)
            )
            FilterChip(
                selected = isSocial,
                onClick = { onToggleSocial(!isSocial) },
                label = { FieldLabel("Social", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp)
            )
        }
    }
}

@Composable
internal fun RelaySelfSignedTlsRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_relays_trust_self_signed_switch),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.settings_relays_trust_self_signed_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ConnectionLed(state: ConnectionState) {
    val (color, desc) = when (state) {
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
