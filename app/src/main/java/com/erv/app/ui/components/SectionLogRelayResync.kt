package com.erv.app.ui.components

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.erv.app.nostr.CurrentRelayDataSync
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.RelayPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SectionLogRelayResyncIconButton(
    appContext: Context,
    relayPool: RelayPool?,
    signer: EventSigner?,
    dataRelayUrls: List<String>,
    dayLogEntries: List<Pair<String, String>>,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    contentColor: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    var syncing by remember { mutableStateOf(false) }
    val canSync = relayPool != null &&
        signer != null &&
        dataRelayUrls.isNotEmpty() &&
        dayLogEntries.isNotEmpty()

    IconButton(
        onClick = {
            val pool = relayPool ?: return@IconButton
            val sig = signer ?: return@IconButton
            if (dataRelayUrls.isEmpty() || dayLogEntries.isEmpty()) return@IconButton
            scope.launch {
                syncing = true
                try {
                    val drain = withContext(Dispatchers.IO) {
                        CurrentRelayDataSync.forceResyncDayLogs(
                            appContext = appContext,
                            relayPool = pool,
                            signer = sig,
                            dataRelayUrls = dataRelayUrls,
                            dayLogEntries = dayLogEntries,
                        )
                    }
                    snackbarHostState.showSnackbar(
                        CurrentRelayDataSync.formatDayLogResyncMessage(dayLogEntries.size, drain)
                    )
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Relay sync failed: ${e.message}")
                } finally {
                    syncing = false
                }
            }
        },
        enabled = canSync && !syncing,
        modifier = modifier,
    ) {
        if (syncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = contentColor,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.CloudUpload,
                contentDescription = "Sync log to relay",
                tint = contentColor,
            )
        }
    }
}
