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
import androidx.navigation.compose.rememberNavController
import com.erv.app.data.ThemeMode
import com.erv.app.data.UserPreferences
import com.erv.app.nostr.*
import com.erv.app.ui.navigation.ErvNavHost
import com.erv.app.ui.theme.ErvTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var amberHost: AmberLauncherHost
    private lateinit var keyManager: KeyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        amberHost = AmberLauncherHost(this)
        keyManager = KeyManager(this)

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
                        userPreferences = userPreferences
                    )
                }
            }
        }
    }
}

@Composable
private fun ErvApp(
    keyManager: KeyManager,
    amberHost: AmberLauncherHost,
    userPreferences: UserPreferences
) {
    var loggedIn by remember { mutableStateOf(keyManager.isLoggedIn) }

    if (loggedIn) {
        MainAppShell(
            keyManager = keyManager,
            userPreferences = userPreferences,
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
// Main app shell (post-login): NavHost + BottomSheet categories
// ---------------------------------------------------------------------------

@Composable
private fun MainAppShell(
    keyManager: KeyManager,
    userPreferences: UserPreferences,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()

    ErvNavHost(
        navController = navController,
        keyManager = keyManager,
        userPreferences = userPreferences,
        onLogout = onLogout
    )
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

        errorMessage?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
