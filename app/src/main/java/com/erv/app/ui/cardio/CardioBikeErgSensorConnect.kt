package com.erv.app.ui.cardio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.erv.app.cardio.isErgMonitorActivity
import com.erv.app.cycling.Concept2BleConnectionState
import com.erv.app.cycling.Concept2ScanRow
import com.erv.app.cycling.CyclingCscBleConnectionState
import com.erv.app.cycling.CyclingCscScanRow
import com.erv.app.cycling.LocalConcept2Pm
import com.erv.app.cycling.LocalCyclingCsc
import com.erv.app.data.SavedBluetoothDevice
import com.erv.app.data.displayName
import com.erv.app.hr.requiredBlePermissionsForHeartRate
import com.erv.app.ui.components.FormSectionLabel

/**
 * Shared Concept2 PM / CSC sensor connection for indoor bike cardio (stationary bike, air bike, etc.).
 * Use before the workout start button or from the workout storyboard.
 */
@Stable
class CardioBikeErgSensorConnectHandle internal constructor(
    internal val enabled: Boolean,
    /** Whether a cycling speed/cadence (CSC) sensor is relevant; false for RowErg / SkiErg. */
    internal val cyclingSensorApplicable: Boolean = true,
) {
    var ergConnected by mutableStateOf(false)
        internal set
    var cscConnected by mutableStateOf(false)
        internal set
    val sensorConnected: Boolean get() = ergConnected || cscConnected
    var liveDistanceMeters by mutableStateOf<Double?>(null)
        internal set
    var cyclingSpeedKmh by mutableStateOf<Double?>(null)
        internal set
    var cyclingCadenceRpm by mutableStateOf<Int?>(null)
        internal set
    var ergPowerWatts by mutableStateOf<Int?>(null)
        internal set

    internal var showErgSensorDialog by mutableStateOf(false)
    internal var showErgScanDialog by mutableStateOf(false)
    internal var showCyclingSensorDialog by mutableStateOf(false)
    internal var showCyclingScanDialog by mutableStateOf(false)
    internal var pendingErgConnectDevice by mutableStateOf<SavedBluetoothDevice?>(null)
    internal var pendingErgScan by mutableStateOf(false)
    internal var pendingCyclingConnectDevice by mutableStateOf<SavedBluetoothDevice?>(null)
    internal var pendingCyclingScan by mutableStateOf(false)
}

@Composable
fun rememberCardioBikeErgSensorConnect(
    enabled: Boolean,
    sessionKey: Any = Unit,
    cyclingSensorApplicable: Boolean = true,
): CardioBikeErgSensorConnectHandle {
    val handle = remember(enabled, sessionKey, cyclingSensorApplicable) {
        CardioBikeErgSensorConnectHandle(
            enabled = enabled,
            cyclingSensorApplicable = cyclingSensorApplicable,
        )
    }

    if (!enabled) {
        handle.ergConnected = false
        handle.cscConnected = false
        handle.liveDistanceMeters = null
        handle.cyclingSpeedKmh = null
        handle.cyclingCadenceRpm = null
        handle.ergPowerWatts = null
        return handle
    }

    val concept2Ble = LocalConcept2Pm.current
    val cyclingCscBle = LocalCyclingCsc.current

    val ergBleConnectionState by concept2Ble.connectionState.collectAsState()
    val ergWorkoutDistanceMeters by concept2Ble.workoutDistanceMeters.collectAsState()
    val ergSpeedKmh by concept2Ble.currentSpeedKmh.collectAsState()
    val ergCadenceRpm by concept2Ble.currentCadenceRpm.collectAsState()
    val ergPower by concept2Ble.currentPowerWatts.collectAsState()
    val cscBleConnectionState by cyclingCscBle.connectionState.collectAsState()
    val cscWorkoutDistanceMeters by cyclingCscBle.workoutDistanceMeters.collectAsState()
    val cscSpeedKmh by cyclingCscBle.currentSpeedKmh.collectAsState()
    val cscCadenceRpm by cyclingCscBle.currentCadenceRpm.collectAsState()

    val savedErgDevices by concept2Ble.savedDevices.collectAsState()
    val preferredErgAddress by concept2Ble.preferredDeviceAddress.collectAsState()
    val activeErgAddress by concept2Ble.activeDeviceAddress.collectAsState()
    val ergConnectedLabel by concept2Ble.connectedLabel.collectAsState()
    val ergBleStatusMessage by concept2Ble.statusMessage.collectAsState()
    val ergScanRows by concept2Ble.scanRows.collectAsState()

    val savedCyclingDevices by cyclingCscBle.savedDevices.collectAsState()
    val preferredCyclingAddress by cyclingCscBle.preferredDeviceAddress.collectAsState()
    val activeCyclingAddress by cyclingCscBle.activeDeviceAddress.collectAsState()
    val cyclingConnectedLabel by cyclingCscBle.connectedLabel.collectAsState()
    val cyclingBleStatusMessage by cyclingCscBle.statusMessage.collectAsState()
    val cyclingScanRows by cyclingCscBle.scanRows.collectAsState()

    handle.ergConnected = ergBleConnectionState == Concept2BleConnectionState.Connected
    handle.cscConnected = cscBleConnectionState == CyclingCscBleConnectionState.Connected
    handle.liveDistanceMeters = when {
        handle.ergConnected -> ergWorkoutDistanceMeters
        handle.cscConnected -> cscWorkoutDistanceMeters
        else -> null
    }
    handle.cyclingSpeedKmh = when {
        handle.ergConnected -> ergSpeedKmh
        handle.cscConnected -> cscSpeedKmh
        else -> null
    }
    handle.cyclingCadenceRpm = when {
        handle.ergConnected -> ergCadenceRpm
        handle.cscConnected -> cscCadenceRpm
        else -> null
    }
    handle.ergPowerWatts = if (handle.ergConnected) ergPower else null

    val requestCyclingBlePermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val pendingConnect = handle.pendingCyclingConnectDevice
        val pendingScan = handle.pendingCyclingScan
        handle.pendingCyclingConnectDevice = null
        handle.pendingCyclingScan = false
        when {
            pendingConnect != null && cyclingCscBle.hasConnectPermission() ->
                cyclingCscBle.connectToSavedDevice(pendingConnect)
            pendingScan && cyclingCscBle.hasScanPermission() && cyclingCscBle.hasConnectPermission() -> {
                handle.showCyclingSensorDialog = false
                handle.showCyclingScanDialog = true
                cyclingCscBle.startScanForSensors()
            }
        }
    }

    val requestErgBlePermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val pendingConnect = handle.pendingErgConnectDevice
        val pendingScan = handle.pendingErgScan
        handle.pendingErgConnectDevice = null
        handle.pendingErgScan = false
        when {
            pendingConnect != null && concept2Ble.hasConnectPermission() ->
                concept2Ble.connectToSavedDevice(pendingConnect)
            pendingScan && concept2Ble.hasScanPermission() && concept2Ble.hasConnectPermission() -> {
                handle.showErgSensorDialog = false
                handle.showErgScanDialog = true
                concept2Ble.startScanForSensors()
            }
        }
    }

    fun openCyclingSensorScan() {
        handle.pendingCyclingConnectDevice = null
        handle.pendingCyclingScan = true
        if (!cyclingCscBle.hasScanPermission() || !cyclingCscBle.hasConnectPermission()) {
            requestCyclingBlePermissions.launch(requiredBlePermissionsForHeartRate())
        } else {
            handle.showCyclingSensorDialog = false
            handle.showCyclingScanDialog = true
            cyclingCscBle.startScanForSensors()
        }
    }

    fun connectSavedCyclingSensor(device: SavedBluetoothDevice) {
        handle.pendingCyclingScan = false
        if (!cyclingCscBle.hasConnectPermission()) {
            handle.pendingCyclingConnectDevice = device
            requestCyclingBlePermissions.launch(requiredBlePermissionsForHeartRate())
        } else {
            handle.pendingCyclingConnectDevice = null
            handle.showCyclingSensorDialog = false
            cyclingCscBle.connectToSavedDevice(device)
        }
    }

    fun openErgSensorScan() {
        handle.pendingErgConnectDevice = null
        handle.pendingErgScan = true
        if (!concept2Ble.hasScanPermission() || !concept2Ble.hasConnectPermission()) {
            requestErgBlePermissions.launch(requiredBlePermissionsForHeartRate())
        } else {
            handle.showErgSensorDialog = false
            handle.showErgScanDialog = true
            concept2Ble.startScanForSensors()
        }
    }

    fun connectSavedErg(device: SavedBluetoothDevice) {
        handle.pendingErgScan = false
        if (!concept2Ble.hasConnectPermission()) {
            handle.pendingErgConnectDevice = device
            requestErgBlePermissions.launch(requiredBlePermissionsForHeartRate())
        } else {
            handle.pendingErgConnectDevice = null
            handle.showErgSensorDialog = false
            concept2Ble.connectToSavedDevice(device)
        }
    }

    LaunchedEffect(sessionKey, enabled) {
        if (enabled) {
            concept2Ble.tryPreferredDeviceReconnectOnce()
            cyclingCscBle.tryPreferredDeviceReconnectOnce()
        }
    }

    if (handle.showCyclingSensorDialog) {
        CardioCyclingSensorSessionDialog(
            connectionState = cscBleConnectionState,
            connectedLabel = cyclingConnectedLabel,
            statusMessage = cyclingBleStatusMessage,
            savedDevices = savedCyclingDevices,
            preferredAddress = preferredCyclingAddress,
            activeAddress = activeCyclingAddress,
            onDismiss = { handle.showCyclingSensorDialog = false },
            onConnectSavedDevice = { connectSavedCyclingSensor(it) },
            onDisconnect = {
                handle.showCyclingSensorDialog = false
                cyclingCscBle.disconnectUser()
            },
            onScan = { openCyclingSensorScan() },
        )
    }

    if (handle.showCyclingScanDialog) {
        CardioCyclingSensorScanDialog(
            scanRows = cyclingScanRows,
            onDismiss = {
                handle.showCyclingScanDialog = false
                cyclingCscBle.stopScanInternal()
            },
            onSelect = { row ->
                handle.showCyclingScanDialog = false
                cyclingCscBle.connectToScannedRow(row)
            },
        )
    }

    if (handle.showErgSensorDialog) {
        CardioConcept2SensorSessionDialog(
            connectionState = ergBleConnectionState,
            connectedLabel = ergConnectedLabel,
            statusMessage = ergBleStatusMessage,
            savedDevices = savedErgDevices,
            preferredAddress = preferredErgAddress,
            activeAddress = activeErgAddress,
            onDismiss = { handle.showErgSensorDialog = false },
            onConnectSavedDevice = { connectSavedErg(it) },
            onDisconnect = {
                handle.showErgSensorDialog = false
                concept2Ble.disconnectUser()
            },
            onScan = { openErgSensorScan() },
        )
    }

    if (handle.showErgScanDialog) {
        CardioConcept2SensorScanDialog(
            scanRows = ergScanRows,
            onDismiss = {
                handle.showErgScanDialog = false
                concept2Ble.stopScanInternal()
            },
            onSelect = { row ->
                handle.showErgScanDialog = false
                concept2Ble.connectToScannedRow(row)
            },
        )
    }

    return handle
}

@Composable
fun CardioBikeErgSensorPreStartPanel(
    handle: CardioBikeErgSensorConnectHandle,
    modifier: Modifier = Modifier,
    lightOnDark: Boolean = false,
    compact: Boolean = false,
) {
    if (!handle.enabled) return

    val titleColor = if (lightOnDark) Color.White.copy(alpha = 0.95f) else MaterialTheme.colorScheme.onSurface
    val bodyColor = if (lightOnDark) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    val connectedColor = Color(0xFF80CBC4)

    val statusText = when {
        handle.ergConnected && handle.cyclingSensorApplicable ->
            "Concept2 connected — power, cadence, and distance will stream live."
        handle.ergConnected ->
            "Concept2 connected — power, stroke rate, and distance will stream live."
        handle.cscConnected -> "Speed sensor connected — distance and cadence will stream live."
        handle.cyclingSensorApplicable ->
            "Connect your Concept2 BikeErg (or a speed sensor) before you start pedaling."
        else -> "Connect your Concept2 erg before you start."
    }

    val containerModifier = if (lightOnDark) {
        modifier.fillMaxWidth()
    } else {
        modifier.fillMaxWidth()
    }

    if (lightOnDark) {
        Surface(
            modifier = containerModifier,
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.12f),
        ) {
            CardioBikeErgPreStartPanelContent(
                handle = handle,
                titleColor = titleColor,
                bodyColor = bodyColor,
                connectedColor = connectedColor,
                statusText = statusText,
                lightOnDark = true,
                compact = compact,
            )
        }
    } else {
        Surface(
            modifier = containerModifier,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ) {
            CardioBikeErgPreStartPanelContent(
                handle = handle,
                titleColor = titleColor,
                bodyColor = bodyColor,
                connectedColor = connectedColor,
                statusText = statusText,
                lightOnDark = false,
                compact = compact,
            )
        }
    }
}

@Composable
private fun CardioBikeErgPreStartPanelContent(
    handle: CardioBikeErgSensorConnectHandle,
    titleColor: Color,
    bodyColor: Color,
    connectedColor: Color,
    statusText: String,
    lightOnDark: Boolean,
    compact: Boolean,
) {
    Column(
        modifier = Modifier.padding(if (compact) 12.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = if (handle.cyclingSensorApplicable) "Bike sensor" else "Erg sensor",
            style = MaterialTheme.typography.titleSmall,
            color = titleColor,
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = if (handle.sensorConnected) connectedColor else bodyColor,
        )
        if (!compact) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { handle.showErgSensorDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = if (lightOnDark) {
                        ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    },
                    border = if (lightOnDark) {
                        ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White))
                    } else {
                        ButtonDefaults.outlinedButtonBorder
                    },
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (handle.ergConnected) "Concept2" else "Connect Concept2")
                }
                if (handle.cyclingSensorApplicable) {
                    OutlinedButton(
                        onClick = { handle.showCyclingSensorDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = if (lightOnDark) {
                            ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                        border = if (lightOnDark) {
                            ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White))
                        } else {
                            ButtonDefaults.outlinedButtonBorder
                        },
                    ) {
                        Icon(Icons.Filled.Bluetooth, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("CSC sensor")
                    }
                }
            }
        } else {
            Button(
                onClick = { handle.showErgSensorDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Bolt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (handle.ergConnected) "Concept2 connected" else "Connect Concept2 bike")
            }
        }
    }
}

@Composable
fun RowScope.CardioBikeErgSensorToolbarActions(
    handle: CardioBikeErgSensorConnectHandle,
    lightOnDark: Boolean = true,
) {
    if (!handle.enabled) return
    val idleTint = if (lightOnDark) Color.White.copy(alpha = 0.88f) else MaterialTheme.colorScheme.onSurface
    IconButton(onClick = { handle.showErgSensorDialog = true }) {
        Icon(
            Icons.Filled.Bolt,
            contentDescription = "Concept2 erg",
            tint = when {
                handle.ergConnected -> Color(0xFF80CBC4)
                else -> idleTint
            },
        )
    }
    if (handle.cyclingSensorApplicable) {
        IconButton(onClick = { handle.showCyclingSensorDialog = true }) {
            Icon(
                Icons.Filled.Bluetooth,
                contentDescription = "Cycling sensor",
                tint = when {
                    handle.cscConnected -> Color(0xFF80CBC4)
                    else -> idleTint
                },
            )
        }
    }
}

@Composable
internal fun CardioBikeErgConnectInlineSection(
    activitySupportsErg: Boolean,
    sessionKey: Any = Unit,
    lightOnDark: Boolean = false,
    compact: Boolean = false,
    cyclingSensorApplicable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val handle = rememberCardioBikeErgSensorConnect(
        enabled = activitySupportsErg,
        sessionKey = sessionKey,
        cyclingSensorApplicable = cyclingSensorApplicable,
    )
    CardioBikeErgSensorPreStartPanel(
        handle = handle,
        modifier = modifier,
        lightOnDark = lightOnDark,
        compact = compact,
    )
}

fun com.erv.app.cardio.CardioActivitySnapshot.supportsBikeErgSensorConnect(): Boolean =
    isErgMonitorActivity()

@Composable
internal fun CardioCyclingSensorSessionDialog(
    connectionState: CyclingCscBleConnectionState,
    connectedLabel: String?,
    statusMessage: String?,
    savedDevices: List<SavedBluetoothDevice>,
    preferredAddress: String?,
    activeAddress: String?,
    onDismiss: () -> Unit,
    onConnectSavedDevice: (SavedBluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onScan: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cycling sensor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (connectionState) {
                    CyclingCscBleConnectionState.Connected ->
                        Text(
                            "Connected to ${connectedLabel ?: "cycling sensor"}.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    CyclingCscBleConnectionState.Connecting ->
                        Text(
                            "Connecting to ${connectedLabel ?: "cycling sensor"}...",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    CyclingCscBleConnectionState.Scanning ->
                        Text(
                            "Scanning for cycling sensors...",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    CyclingCscBleConnectionState.Idle,
                    CyclingCscBleConnectionState.Error ->
                        Text(
                            "Connect a saved CSC sensor or scan for a new one without leaving this workout.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
                statusMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (savedDevices.isEmpty()) {
                    Text(
                        "No saved cycling sensors yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FormSectionLabel("Saved sensors")
                    savedDevices.forEachIndexed { index, device ->
                        TextButton(
                            onClick = { onConnectSavedDevice(device) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(device.displayName())
                                val labels = buildList {
                                    if (preferredAddress == device.address) add("Preferred")
                                    if (activeAddress == device.address &&
                                        connectionState == CyclingCscBleConnectionState.Connected
                                    ) {
                                        add("Connected")
                                    }
                                }
                                if (labels.isNotEmpty()) {
                                    Text(
                                        labels.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (index < savedDevices.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onScan) {
                Text("Scan")
            }
        },
        dismissButton = {
            Row {
                if (connectionState == CyclingCscBleConnectionState.Connected) {
                    TextButton(onClick = onDisconnect) {
                        Text("Disconnect")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
    )
}

@Composable
internal fun CardioCyclingSensorScanDialog(
    scanRows: List<CyclingCscScanRow>,
    onDismiss: () -> Unit,
    onSelect: (CyclingCscScanRow) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select a cycling sensor") },
        text = {
            if (scanRows.isEmpty()) {
                Text("No CSC sensors found yet. Wake the sensor and spin the wheel or crank to advertise.")
            } else {
                Column {
                    scanRows.forEachIndexed { index, row ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(row) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                text = row.name?.takeIf { it.isNotBlank() } ?: "Cycling sensor",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = row.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (index < scanRows.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

@Composable
internal fun CardioConcept2SensorSessionDialog(
    connectionState: Concept2BleConnectionState,
    connectedLabel: String?,
    statusMessage: String?,
    savedDevices: List<SavedBluetoothDevice>,
    preferredAddress: String?,
    activeAddress: String?,
    onDismiss: () -> Unit,
    onConnectSavedDevice: (SavedBluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onScan: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Concept2 erg") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (connectionState) {
                    Concept2BleConnectionState.Connected ->
                        Text(
                            "Connected to ${connectedLabel ?: "Concept2 monitor"}.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    Concept2BleConnectionState.Connecting ->
                        Text(
                            "Connecting to ${connectedLabel ?: "Concept2 monitor"}...",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    Concept2BleConnectionState.Scanning ->
                        Text(
                            "Scanning for Concept2 monitors...",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    Concept2BleConnectionState.Idle,
                    Concept2BleConnectionState.Error ->
                        Text(
                            "Connect a saved Concept2 PM (BikeErg / RowErg / SkiErg) or scan for a new one " +
                                "to stream power, cadence, and distance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
                statusMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (savedDevices.isEmpty()) {
                    Text(
                        "No saved Concept2 monitors yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FormSectionLabel("Saved monitors")
                    savedDevices.forEachIndexed { index, device ->
                        TextButton(
                            onClick = { onConnectSavedDevice(device) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(device.displayName())
                                val labels = buildList {
                                    if (preferredAddress == device.address) add("Preferred")
                                    if (activeAddress == device.address &&
                                        connectionState == Concept2BleConnectionState.Connected
                                    ) {
                                        add("Connected")
                                    }
                                }
                                if (labels.isNotEmpty()) {
                                    Text(
                                        labels.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (index < savedDevices.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onScan) {
                Text("Scan")
            }
        },
        dismissButton = {
            Row {
                if (connectionState == Concept2BleConnectionState.Connected) {
                    TextButton(onClick = onDisconnect) {
                        Text("Disconnect")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
    )
}

@Composable
internal fun CardioConcept2SensorScanDialog(
    scanRows: List<Concept2ScanRow>,
    onDismiss: () -> Unit,
    onSelect: (Concept2ScanRow) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select a Concept2 monitor") },
        text = {
            if (scanRows.isEmpty()) {
                Text("No Concept2 monitors found yet. Wake the PM5 (press a button or start pedaling) so it advertises.")
            } else {
                Column {
                    scanRows.forEachIndexed { index, row ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(row) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                text = row.name?.takeIf { it.isNotBlank() } ?: "Concept2 monitor",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = row.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (index < scanRows.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}
