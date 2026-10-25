package com.erv.app.hr

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erv.app.R
import com.erv.app.data.displayName
import com.erv.app.ui.components.FormSectionLabelSmall
import kotlinx.coroutines.delay

fun requiredBlePermissionsForHeartRate(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

/** Heart shown when no zone can be computed (no age or max HR in settings). */
private val HeartRateNeutralTint = Color(0xFFE53935)

private data class LiveZone(val index: Int, val name: String, val color: Color)

private fun liveZoneFor(bpm: Int?, zoneInputs: HeartRateZoneInputs): LiveZone? {
    if (bpm == null) return null
    if (zoneInputs.manualMaxBpm == null && zoneInputs.ageYears == null) return null
    val maxHr = resolvedMaxHrForZones(zoneInputs.manualMaxBpm, emptyList(), zoneInputs.ageYears)
    val zone = heartRateZoneIndex(bpm, maxHr, zoneInputs.restingBpm, zoneInputs.method)
    return LiveZone(zone, heartRateZoneShortName(zone), zoneColor(zone))
}

/**
 * Top-bar heart rate control. Outlined heart when no sensor is connected; a live BPM pill
 * tinted by training zone once one is. Tapping opens the sensor sheet.
 *
 * [contentColor] must match the hosting top bar's icon color (white on live-session headers).
 */
@Composable
fun HeartRatePill(
    viewModel: HeartRateBleViewModel,
    zoneInputs: HeartRateZoneInputs,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    if (!viewModel.bleHardwareAvailable) return

    val connection by viewModel.connectionState.collectAsState()
    val bpm by viewModel.displayBpm.collectAsState()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.tryPreferredDeviceReconnectOnce()
    }

    val zone = liveZoneFor(bpm, zoneInputs)
    val heartTint = zone?.color ?: HeartRateNeutralTint

    when {
        connection == HeartRateBleConnectionState.Connected -> {
            val pillDescription = bpm?.let { stringResource(R.string.hr_pill_cd_bpm, it) }
                ?: stringResource(R.string.hr_pill_cd_waiting)
            Surface(
                onClick = { sheetOpen = true },
                shape = CircleShape,
                color = heartTint.copy(alpha = 0.16f),
                contentColor = contentColor,
                modifier = modifier
                    .padding(horizontal = 4.dp)
                    .heightIn(min = 36.dp)
                    .semantics { contentDescription = pillDescription },
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BeatingHeart(bpm = bpm, tint = heartTint, size = 20)
                    Text(
                        text = bpm?.toString() ?: "--",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum",
                        ),
                    )
                }
            }
        }
        else -> {
            val connecting = connection == HeartRateBleConnectionState.Scanning ||
                connection == HeartRateBleConnectionState.Connecting
            val searchingAlpha = if (connecting) {
                val transition = rememberInfiniteTransition(label = "hrPillSearching")
                val a by transition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(700, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "hrPillSearchingAlpha",
                )
                a
            } else {
                1f
            }
            IconButton(onClick = { sheetOpen = true }, modifier = modifier) {
                Icon(
                    imageVector = Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(R.string.hr_pill_cd_connect),
                    tint = if (connection == HeartRateBleConnectionState.Error) {
                        MaterialTheme.colorScheme.error
                    } else {
                        contentColor
                    },
                    modifier = Modifier.alpha(searchingAlpha),
                )
            }
        }
    }

    if (sheetOpen) {
        HeartRateSensorSheet(
            viewModel = viewModel,
            zoneInputs = zoneInputs,
            onDismiss = { sheetOpen = false },
        )
    }
}

@Composable
private fun BeatingHeart(bpm: Int?, tint: Color, size: Int) {
    val scale = remember { Animatable(1f) }
    val latestBpm by rememberUpdatedState(bpm)
    LaunchedEffect(Unit) {
        while (true) {
            val current = latestBpm
            if (current == null || current <= 0) {
                scale.snapTo(1f)
                delay(500)
                continue
            }
            val beatMs = (60_000 / current).coerceIn(300, 2_000)
            scale.animateTo(1.2f, tween(110, easing = FastOutSlowInEasing))
            scale.animateTo(1f, tween(190, easing = FastOutSlowInEasing))
            delay((beatMs - 300).toLong().coerceAtLeast(0L))
        }
    }
    Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(size.dp)
            .scale(scale.value),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeartRateSensorSheet(
    viewModel: HeartRateBleViewModel,
    zoneInputs: HeartRateZoneInputs,
    onDismiss: () -> Unit,
) {
    val connection by viewModel.connectionState.collectAsState()
    val bpm by viewModel.displayBpm.collectAsState()
    val batteryPercent by viewModel.displayBatteryPercent.collectAsState()
    val label by viewModel.connectedLabel.collectAsState()
    val status by viewModel.statusMessage.collectAsState()
    val scanRows by viewModel.scanRows.collectAsState()
    val savedDevices by viewModel.savedDevices.collectAsState()
    val activeAddress by viewModel.activeDeviceAddress.collectAsState()
    var scanning by remember { mutableStateOf(false) }

    fun beginScan() {
        scanning = true
        viewModel.startScanForSensors()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) beginScan()
    }

    fun requestScan() {
        if (!viewModel.hasScanPermission() || !viewModel.hasConnectPermission()) {
            permissionLauncher.launch(requiredBlePermissionsForHeartRate())
        } else {
            beginScan()
        }
    }

    fun dismiss() {
        if (scanning) viewModel.stopScanInternal()
        onDismiss()
    }

    LaunchedEffect(connection) {
        if (connection == HeartRateBleConnectionState.Connected) scanning = false
    }

    ModalBottomSheet(
        onDismissRequest = ::dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.hr_dialog_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(20.dp))

            val zone = liveZoneFor(bpm, zoneInputs)
            when (connection) {
                HeartRateBleConnectionState.Connected -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BeatingHeart(bpm = bpm, tint = zone?.color ?: HeartRateNeutralTint, size = 36)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = bpm?.toString() ?: "--",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontFeatureSettings = "tnum",
                            ),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.hr_sheet_bpm_unit),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        zone?.let { z ->
                            Surface(shape = CircleShape, color = z.color.copy(alpha = 0.18f)) {
                                Text(
                                    text = "Z${z.index} · ${z.name}",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val deviceLine = listOfNotNull(
                        label,
                        batteryPercent?.let { stringResource(R.string.hr_sheet_battery, it) },
                    ).joinToString(" · ")
                    if (deviceLine.isNotEmpty()) {
                        Text(
                            text = deviceLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (zone == null && bpm != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.hr_sheet_zone_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    val headline = when (connection) {
                        HeartRateBleConnectionState.Scanning -> stringResource(R.string.hr_sheet_scanning)
                        HeartRateBleConnectionState.Connecting -> stringResource(R.string.hr_sheet_connecting)
                        HeartRateBleConnectionState.Error -> stringResource(R.string.hr_sheet_error)
                        else -> stringResource(R.string.hr_sheet_not_connected)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (connection == HeartRateBleConnectionState.Scanning ||
                            connection == HeartRateBleConnectionState.Connecting
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (connection == HeartRateBleConnectionState.Error) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.hr_dialog_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            status?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val otherSaved = savedDevices.filter { it.address != activeAddress }
            if (!scanning && otherSaved.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                FormSectionLabelSmall(stringResource(R.string.hr_sheet_saved_sensors))
                otherSaved.forEach { device ->
                    SensorRow(
                        title = device.displayName(),
                        subtitle = device.address,
                        onClick = { viewModel.connectToSavedDevice(device) },
                    )
                }
            }

            if (scanning) {
                Spacer(Modifier.height(20.dp))
                FormSectionLabelSmall(stringResource(R.string.hr_scan_dialog_title))
                if (scanRows.isEmpty()) {
                    Text(
                        text = stringResource(R.string.hr_scan_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    scanRows.forEach { row ->
                        SensorRow(
                            title = row.name?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.hr_scan_unknown_name),
                            subtitle = row.address,
                            onClick = {
                                scanning = false
                                viewModel.connectToScannedRow(row)
                            },
                        )
                    }
                }
                TextButton(
                    onClick = {
                        scanning = false
                        viewModel.stopScanInternal()
                    },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.hr_scan_done)) }
            } else {
                Spacer(Modifier.height(24.dp))
                if (connection == HeartRateBleConnectionState.Connected) {
                    OutlinedButton(
                        onClick = { viewModel.disconnectUser() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.hr_dialog_disconnect)) }
                } else {
                    Button(
                        onClick = ::requestScan,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Bluetooth, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.hr_dialog_scan))
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}
