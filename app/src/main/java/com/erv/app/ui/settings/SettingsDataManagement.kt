package com.erv.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.erv.app.datadeletion.DataDeletionManager
import com.erv.app.dataexport.DataExportCategory
import com.erv.app.dataexport.UserDataSection
import com.erv.app.data.UserPreferences
import com.erv.app.nostr.ConnectionState
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.KeyManager
import com.erv.app.nostr.RelayPool
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDataManagementScreen(
    onBack: () -> Unit,
    onOpenExport: (DataExportCategory) -> Unit,
    deletionManager: DataDeletionManager,
    keyManager: KeyManager,
    userPreferences: UserPreferences,
    relayPool: RelayPool?,
    relayStates: Map<String, ConnectionState>,
    signer: EventSigner?,
    onRequestNostrLogin: () -> Unit,
    onShowMessage: (String) -> Unit,
    onAllDataDeleted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val hasUsedNostrIdentity by userPreferences.hasUsedNostrIdentity.collectAsState(initial = false)
    val lastKnownDataRelayUrls by userPreferences.lastKnownDataRelayUrls.collectAsState(initial = emptyList())
    var pendingTarget by remember { mutableStateOf<DeleteTarget?>(null) }
    var showTypedConfirm by remember { mutableStateOf(false) }
    var confirmText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }

    fun dismissDialogs() {
        pendingTarget = null
        showTypedConfirm = false
        confirmText = ""
        deleting = false
    }

    fun relayCleanupGuidance(): String? {
        if (!hasUsedNostrIdentity) return null
        if (!keyManager.isLoggedIn) {
            return if (lastKnownDataRelayUrls.isNotEmpty()) {
                "You used Nostr sync on this device before, but you are not signed in now. ERV can delete local data now, but it cannot try relay cleanup unless you sign in again with the same Nostr identity."
            } else {
                "You used Nostr sync on this device before, but you are not signed in now. ERV can delete local data now, but it cannot try relay cleanup unless you sign in again with the same Nostr identity."
            }
        }
        val configuredDataRelays = keyManager.relayUrlsForKind30078Publish()
        if (configuredDataRelays.isEmpty()) {
            return if (lastKnownDataRelayUrls.isNotEmpty()) {
                "ERV can only try relay cleanup on relays it knows about. No data relays are configured right now, so previously used relays will not receive a cleanup attempt unless you reconnect them first."
            } else {
                null
            }
        }
        val unreachableRelays = configuredDataRelays.filter { url ->
            relayStates[url].let { state ->
                state !is ConnectionState.Connected && state !is ConnectionState.Authenticated
            }
        }
        if (unreachableRelays.isEmpty()) return null
        return buildString {
            append("ERV can only try relay cleanup on data relays it is currently connected to. ")
            append("${unreachableRelays.size} configured relay(s) are not connected right now")
            if (unreachableRelays.size <= 3) {
                append(": ${unreachableRelays.joinToString(", ")}")
            }
            append(". If you continue now, those relays will not receive a cleanup attempt.")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data Management") },
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Delete local data from this device, and when possible ask ERV to publish best-effort relay replacements for previously synced encrypted data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Before any destructive action, export what you want to keep. Relay cleanup is best effort only and cannot guarantee permanent deletion on every relay.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
            UserDataSection.entries.forEach { section ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(section.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            section.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = {
                                pendingTarget = DeleteTarget.Section(section)
                                showTypedConfirm = false
                                confirmText = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Text("Delete ${section.label}", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Delete all data", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Text(
                        "Wipe every local ERV data section on this device, stop future sync by clearing your saved identity, and try best-effort relay cleanup for known encrypted data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            pendingTarget = DeleteTarget.AllData
                            showTypedConfirm = false
                            confirmText = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text("Delete All Data", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    pendingTarget?.takeIf { !showTypedConfirm }?.let { target ->
        AlertDialog(
            onDismissRequest = { dismissDialogs() },
            title = { Text("Export before delete?") },
            text = {
                Text(
                    when (target) {
                        is DeleteTarget.Section ->
                            "Export ${target.section.label.lowercase()} before you delete it? Exported files are plain text unless you encrypt them separately."
                        DeleteTarget.AllData ->
                            "Export everything before you delete it? Exported files are plain text unless you encrypt them separately."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onOpenExport(target.exportCategory)
                        dismissDialogs()
                    }
                ) {
                    Text("Export First")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { dismissDialogs() }) {
                        Text("Cancel")
                    }
                    TextButton(onClick = { showTypedConfirm = true }) {
                        Text("Continue")
                    }
                }
            },
        )
    }

    pendingTarget?.takeIf { showTypedConfirm }?.let { target ->
        val relayGuidance = relayCleanupGuidance()
        AlertDialog(
            onDismissRequest = {
                if (!deleting) dismissDialogs()
            },
            title = {
                Text(
                    when (target) {
                        is DeleteTarget.Section -> "Confirm ${target.section.label} deletion"
                        DeleteTarget.AllData -> "Confirm full data deletion"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        when (target) {
                            is DeleteTarget.Section ->
                                buildString {
                                    append("This removes ${target.section.label.lowercase()} from this device.")
                                    if (target.section.relayBacked) {
                                        append(" ERV will also try a best-effort relay cleanup for known encrypted copies.")
                                    }
                                }
                            DeleteTarget.AllData ->
                                "This removes all ERV data from this device, clears your saved identity and relay settings, and tries a best-effort relay cleanup for known encrypted copies."
                        }
                    )
                    relayGuidance?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        "Type DELETE to confirm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it },
                        label = { Text("Confirmation") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = confirmText == "DELETE" && !deleting,
                    onClick = {
                        deleting = true
                        scope.launch {
                            val result = when (target) {
                                is DeleteTarget.Section ->
                                    deletionManager.deleteSection(target.section, relayPool, signer)
                                DeleteTarget.AllData ->
                                    deletionManager.deleteAllData(relayPool, signer)
                            }
                            dismissDialogs()
                            if (result.requiresAppLogout) {
                                onAllDataDeleted()
                            } else {
                                onShowMessage(result.message)
                            }
                        }
                    }
                ) {
                    Text(if (deleting) "Deleting..." else "Delete")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!keyManager.isLoggedIn && hasUsedNostrIdentity) {
                        TextButton(
                            onClick = {
                                dismissDialogs()
                                onRequestNostrLogin()
                            },
                            enabled = !deleting,
                        ) {
                            Text("Sign In First")
                        }
                    }
                    TextButton(
                        onClick = { dismissDialogs() },
                        enabled = !deleting,
                    ) {
                        Text("Cancel")
                    }
                }
            },
        )
    }
}

private sealed class DeleteTarget(val exportCategory: DataExportCategory) {
    data class Section(val section: UserDataSection) : DeleteTarget(section.exportCategory)
    data object AllData : DeleteTarget(DataExportCategory.ALL)
}
