package com.erv.app.ui.cardio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.erv.app.cycling.FtmsBleConnectionState
import com.erv.app.cycling.FtmsMachineType
import com.erv.app.cycling.FtmsScanRow
import com.erv.app.cycling.LocalFtms
import com.erv.app.data.SavedBluetoothDevice
import com.erv.app.data.displayName
import com.erv.app.hr.requiredBlePermissionsForHeartRate
import com.erv.app.ui.components.FormSectionLabel

/**
 * Connect a smart trainer or treadmill (Bluetooth FTMS) from a cardio leg on the workout screen.
 * [treadmill] only changes the wording; any FTMS machine can be connected.
 */
@Composable
internal fun SessionFtmsConnectPanel(
    treadmill: Boolean,
    sessionKey: Any,
    modifier: Modifier = Modifier,
) {
    val ftms = LocalFtms.current
    val connection by ftms.connectionState.collectAsState()
    val label by ftms.connectedLabel.collectAsState()
    val status by ftms.statusMessage.collectAsState()
    val machineType by ftms.machineType.collectAsState()
    val savedDevices by ftms.savedDevices.collectAsState()
    val preferredAddress by ftms.preferredDeviceAddress.collectAsState()
    val activeAddress by ftms.activeDeviceAddress.collectAsState()
    val scanRows by ftms.scanRows.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    var pendingConnect by remember { mutableStateOf<SavedBluetoothDevice?>(null) }
    var pendingScan by remember { mutableStateOf(false) }
    val machineWord = if (treadmill) "treadmill" else "smart trainer"

    LaunchedEffect(sessionKey) { ftms.tryPreferredDeviceReconnectOnce() }

    val requestPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val device = pendingConnect
        val scan = pendingScan
        pendingConnect = null
        pendingScan = false
        when {
            device != null && ftms.hasConnectPermission() -> ftms.connectToSavedDevice(device)
            scan && ftms.hasScanPermission() && ftms.hasConnectPermission() -> {
                showDialog = false
                showScan = true
                ftms.startScanForMachines()
            }
        }
    }

    fun scan() {
        pendingConnect = null
        pendingScan = true
        if (!ftms.hasScanPermission() || !ftms.hasConnectPermission()) {
            requestPermissions.launch(requiredBlePermissionsForHeartRate())
        } else {
            pendingScan = false
            showDialog = false
            showScan = true
            ftms.startScanForMachines()
        }
    }

    fun connect(device: SavedBluetoothDevice) {
        pendingScan = false
        if (!ftms.hasConnectPermission()) {
            pendingConnect = device
            requestPermissions.launch(requiredBlePermissionsForHeartRate())
        } else {
            showDialog = false
            ftms.connectToSavedDevice(device)
        }
    }

    val connected = connection == FtmsBleConnectionState.Connected
    val statusText = when (connection) {
        FtmsBleConnectionState.Connected -> when (machineType) {
            FtmsMachineType.TREADMILL -> "${label ?: "Treadmill"} connected. Speed, incline and distance stream live."
            else -> "${label ?: "Trainer"} connected. Power, cadence, speed and distance stream live."
        }
        FtmsBleConnectionState.Connecting -> "Connecting to ${label ?: machineWord}..."
        else -> "Connect a $machineWord for live speed and distance."
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (connected) Color(0xFF80CBC4) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Bluetooth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        connected -> if (machineType == FtmsMachineType.TREADMILL) "Treadmill" else "Smart trainer"
                        treadmill -> "Connect treadmill"
                        else -> "Connect smart trainer"
                    },
                )
            }
        }
    }

    if (showDialog) {
        FtmsMachineDialog(
            machineWord = machineWord,
            connectionState = connection,
            connectedLabel = label,
            statusMessage = status,
            savedDevices = savedDevices,
            preferredAddress = preferredAddress,
            activeAddress = activeAddress,
            onDismiss = { showDialog = false },
            onConnectSavedDevice = { connect(it) },
            onDisconnect = {
                showDialog = false
                ftms.disconnectUser()
            },
            onForget = { ftms.forgetSavedDevice(it.address) },
            onScan = { scan() },
        )
    }
    if (showScan) {
        FtmsScanDialog(
            scanRows = scanRows,
            scanning = connection == FtmsBleConnectionState.Scanning,
            onDismiss = {
                showScan = false
                ftms.stopScanInternal()
            },
            onSelect = { row ->
                showScan = false
                ftms.connectToScannedRow(row)
            },
        )
    }
}

@Composable
internal fun FtmsMachineDialog(
    machineWord: String,
    connectionState: FtmsBleConnectionState,
    connectedLabel: String?,
    statusMessage: String?,
    savedDevices: List<SavedBluetoothDevice>,
    preferredAddress: String?,
    activeAddress: String?,
    onDismiss: () -> Unit,
    onConnectSavedDevice: (SavedBluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onForget: (SavedBluetoothDevice) -> Unit,
    onScan: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Smart trainer or treadmill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (connectionState) {
                        FtmsBleConnectionState.Connected -> "Connected to ${connectedLabel ?: machineWord}."
                        FtmsBleConnectionState.Connecting -> "Connecting to ${connectedLabel ?: machineWord}..."
                        FtmsBleConnectionState.Scanning -> "Scanning..."
                        else ->
                            "Works with trainers and treadmills that use the Bluetooth FTMS standard " +
                                "(Wahoo, Tacx, Zwift Hub, Elite and most newer treadmills). Wake the machine, then scan."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                statusMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (savedDevices.isNotEmpty()) {
                    FormSectionLabel("Saved machines")
                    savedDevices.forEachIndexed { index, device ->
                        Row(Modifier.fillMaxWidth()) {
                            TextButton(onClick = { onConnectSavedDevice(device) }, modifier = Modifier.weight(1f)) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(device.displayName())
                                    val tags = buildList {
                                        if (preferredAddress == device.address) add("Preferred")
                                        if (activeAddress == device.address &&
                                            connectionState == FtmsBleConnectionState.Connected
                                        ) {
                                            add("Connected")
                                        }
                                    }
                                    if (tags.isNotEmpty()) {
                                        Text(
                                            tags.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            TextButton(onClick = { onForget(device) }) { Text("Forget") }
                        }
                        if (index < savedDevices.lastIndex) HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onScan) { Text("Scan") } },
        dismissButton = {
            Row {
                if (connectionState == FtmsBleConnectionState.Connected) {
                    TextButton(onClick = onDisconnect) { Text("Disconnect") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
internal fun FtmsScanDialog(
    scanRows: List<FtmsScanRow>,
    scanning: Boolean,
    onDismiss: () -> Unit,
    onSelect: (FtmsScanRow) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select a machine") },
        text = {
            if (scanRows.isEmpty()) {
                Text(
                    if (scanning) {
                        "Looking for trainers and treadmills. Start pedaling or walking to wake the machine."
                    } else {
                        "Nothing found. Make sure the machine is on and not connected to another app, then scan again."
                    },
                )
            } else {
                Column {
                    scanRows.forEachIndexed { index, row ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(row) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(row.name?.takeIf { it.isNotBlank() } ?: "Fitness machine", style = MaterialTheme.typography.bodyLarge)
                            Text(row.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (index < scanRows.lastIndex) HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
