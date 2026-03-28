package com.erv.app.hr

import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erv.app.R

fun requiredBlePermissionsForHeartRate(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
fun HeartRateTopBar(
    viewModel: HeartRateBleViewModel,
    onRequestBlePermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!viewModel.bleHardwareAvailable) return

    val connection by viewModel.connectionState.collectAsState()
    val bpm by viewModel.displayBpm.collectAsState()
    val batteryPercent by viewModel.displayBatteryPercent.collectAsState()
    val label by viewModel.connectedLabel.collectAsState()
    val status by viewModel.statusMessage.collectAsState()
    val scanRows by viewModel.scanRows.collectAsState()

    var menuOpen by remember { mutableStateOf(false) }
    var scanDialogOpen by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { menuOpen = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (connection == HeartRateBleConnectionState.Connected) Icons.Default.Favorite
                    else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (connection == HeartRateBleConnectionState.Connected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Column {
                    when (connection) {
                        HeartRateBleConnectionState.Connected -> {
                            val line = bpm?.let { stringResource(R.string.hr_top_bar_bpm, it) }
                                ?: stringResource(R.string.hr_top_bar_connected_waiting)
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            label?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HeartRateBleConnectionState.Scanning -> {
                            Text(
                                text = stringResource(R.string.hr_top_bar_scanning),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        HeartRateBleConnectionState.Connecting -> {
                            Text(
                                text = stringResource(R.string.hr_top_bar_connecting),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        HeartRateBleConnectionState.Error -> {
                            Text(
                                text = stringResource(R.string.hr_top_bar_error),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        HeartRateBleConnectionState.Idle -> {
                            Text(
                                text = stringResource(R.string.hr_top_bar_tap_to_connect),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (connection == HeartRateBleConnectionState.Connected) {
                batteryPercent?.let { pct ->
                    val batteryContentDescription =
                        stringResource(R.string.hr_top_bar_battery_content_description, pct)
                    Text(
                        text = stringResource(R.string.hr_top_bar_battery_percent, pct),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .widthIn(min = 36.dp)
                            .semantics { contentDescription = batteryContentDescription }
                    )
                }
            }
        }
    }

    if (menuOpen) {
        AlertDialog(
            onDismissRequest = { menuOpen = false },
            title = { Text(stringResource(R.string.hr_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        stringResource(R.string.hr_dialog_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        menuOpen = false
                        val needScan = !viewModel.hasScanPermission()
                        val needConn = !viewModel.hasConnectPermission()
                        if (needScan || needConn) {
                            onRequestBlePermissions()
                            return@TextButton
                        }
                        scanDialogOpen = true
                        viewModel.startScanForSensors()
                    }
                ) { Text(stringResource(R.string.hr_dialog_scan)) }
            },
            dismissButton = {
                Row {
                    if (connection == HeartRateBleConnectionState.Connected) {
                        TextButton(
                            onClick = {
                                menuOpen = false
                                viewModel.disconnectUser()
                            }
                        ) { Text(stringResource(R.string.hr_dialog_disconnect)) }
                    }
                    TextButton(onClick = { menuOpen = false }) {
                        Text(stringResource(R.string.hr_dialog_close))
                    }
                }
            }
        )
    }

    if (scanDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                scanDialogOpen = false
                viewModel.stopScanInternal()
            },
            title = { Text(stringResource(R.string.hr_scan_dialog_title)) },
            text = {
                if (scanRows.isEmpty()) {
                    Text(stringResource(R.string.hr_scan_empty))
                } else {
                    LazyColumn {
                        items(scanRows, key = { it.address }) { row ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scanDialogOpen = false
                                        viewModel.connectToScannedRow(row)
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
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scanDialogOpen = false
                        viewModel.stopScanInternal()
                    }
                ) { Text(stringResource(R.string.hr_scan_done)) }
            }
        )
    }
}
