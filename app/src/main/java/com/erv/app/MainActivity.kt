package com.erv.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.erv.app.nostr.*
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var amberHost: AmberLauncherHost
    private lateinit var keyManager: KeyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        amberHost = AmberLauncherHost(this)
        keyManager = KeyManager(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ErvApp(keyManager = keyManager, amberHost = amberHost)
                }
            }
        }
    }
}

@Composable
private fun ErvApp(keyManager: KeyManager, amberHost: AmberLauncherHost) {
    var loggedIn by remember { mutableStateOf(keyManager.isLoggedIn) }

    if (loggedIn) {
        HomeScreen(
            keyManager = keyManager,
            amberHost = amberHost,
            onLogout = {
                keyManager.logout()
                loggedIn = false
            }
        )
    } else {
        LoginScreen(
            keyManager = keyManager,
            amberHost = amberHost,
            onLoginSuccess = { loggedIn = true }
        )
    }
}

// ---------------------------------------------------------------------------
// Login
// ---------------------------------------------------------------------------

@Composable
private fun LoginScreen(
    keyManager: KeyManager,
    amberHost: AmberLauncherHost,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
        Text("ERV", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Energy Radiance Vitality", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))

        // --- nsec login ---
        Text("Login with nsec", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = nsecInput,
            onValueChange = { nsecInput = it; errorMessage = null },
            label = { Text("nsec or hex private key") },
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
            Text("Login")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                try {
                    val nsec = keyManager.generateKeys()
                    Toast.makeText(context, "Key generated. Back up your nsec!", Toast.LENGTH_LONG).show()
                    nsecInput = nsec
                    onLoginSuccess()
                } catch (e: Exception) {
                    errorMessage = e.message
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate new keys")
        }

        // --- Amber login ---
        if (amberAvailable) {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))
            Text("Login with Amber", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        try {
                            val pubkey = AmberSigner.getPublicKey(amberHost)
                            keyManager.loginWithAmber(pubkey)
                            onLoginSuccess()
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Amber connection failed"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Connect with Amber")
            }
        }

        // --- Error ---
        errorMessage?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

// ---------------------------------------------------------------------------
// Home / Connected
// ---------------------------------------------------------------------------

@Composable
private fun HomeScreen(
    keyManager: KeyManager,
    amberHost: AmberLauncherHost,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var relayUrl by remember { mutableStateOf(keyManager.relayUrl ?: "") }
    var nostrClient by remember { mutableStateOf<NostrClient?>(null) }
    val connectionState = nostrClient?.connectionState?.collectAsState()?.value
        ?: ConnectionState.Disconnected

    var publishResult by remember { mutableStateOf<String?>(null) }

    // Build signer from stored keys
    val signer: EventSigner? = remember(keyManager.loginMethod) {
        when (keyManager.loginMethod) {
            KeyManager.LOGIN_NSEC -> keyManager.createLocalSigner()
            KeyManager.LOGIN_AMBER -> AmberSigner(keyManager.publicKeyHex!!, amberHost)
            else -> null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("ERV", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))

        // --- Identity ---
        Text("Identity", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = keyManager.npub ?: "Unknown",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Login: ${keyManager.loginMethod ?: "none"}",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))

        // --- Relay ---
        Text("Relay", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = relayUrl,
            onValueChange = { relayUrl = it },
            label = { Text("Relay URL (wss://...)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        val stateLabel = when (connectionState) {
            is ConnectionState.Disconnected -> "Disconnected"
            is ConnectionState.Connecting -> "Connecting..."
            is ConnectionState.Connected -> "Connected (awaiting auth)"
            is ConnectionState.Authenticated -> "Authenticated"
            is ConnectionState.Error -> "Error: ${(connectionState as ConnectionState.Error).message}"
        }
        Text("Status: $stateLabel", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val url = relayUrl.trim()
                    if (url.isNotBlank() && signer != null) {
                        keyManager.relayUrl = url
                        val client = NostrClient(signer)
                        nostrClient?.destroy()
                        nostrClient = client
                        client.connect(url)
                    }
                },
                enabled = relayUrl.isNotBlank() && signer != null &&
                    (connectionState is ConnectionState.Disconnected ||
                        connectionState is ConnectionState.Error)
            ) {
                Text("Connect")
            }

            OutlinedButton(
                onClick = {
                    nostrClient?.disconnect()
                },
                enabled = connectionState !is ConnectionState.Disconnected
            ) {
                Text("Disconnect")
            }
        }

        Spacer(Modifier.height(24.dp))

        // --- Test publish ---
        Text("Test", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Publish a test kind-30078 event to verify the full pipeline: " +
                "key -> sign -> NIP-44 encrypt -> NIP-42 auth -> relay.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                scope.launch {
                    publishResult = null
                    try {
                        val ok = nostrClient?.publishTestEvent() ?: false
                        publishResult = if (ok) "Published successfully" else "Publish failed"
                        if (ok) {
                            UserFeedback.showSuccess(context)
                        } else {
                            UserFeedback.showError(context, "Relay rejected the event")
                        }
                    } catch (e: Exception) {
                        publishResult = "Error: ${e.message}"
                        UserFeedback.showError(context, e.message)
                    }
                }
            },
            enabled = connectionState is ConnectionState.Connected ||
                connectionState is ConnectionState.Authenticated
        ) {
            Text("Publish test event")
        }

        publishResult?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                nostrClient?.destroy()
                nostrClient = null
                onLogout()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Logout")
        }
    }
}
