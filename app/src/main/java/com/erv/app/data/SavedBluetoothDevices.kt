package com.erv.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
enum class SavedBluetoothDeviceKind {
    HEART_RATE_MONITOR,
    CYCLING_SPEED_CADENCE_SENSOR,
}

fun SavedBluetoothDeviceKind.displayLabel(): String =
    when (this) {
        SavedBluetoothDeviceKind.HEART_RATE_MONITOR -> "Heart rate monitor"
        SavedBluetoothDeviceKind.CYCLING_SPEED_CADENCE_SENSOR -> "Cycling speed/cadence sensor"
    }

@Serializable
data class SavedBluetoothDevice(
    val address: String,
    val name: String? = null,
    val kind: SavedBluetoothDeviceKind = SavedBluetoothDeviceKind.HEART_RATE_MONITOR,
    val lastConnectedEpochMillis: Long? = null,
)

fun SavedBluetoothDevice.displayName(): String =
    name?.trim()?.takeIf { it.isNotEmpty() } ?: kind.displayLabel()

private val savedBluetoothDevicesJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

fun encodeSavedBluetoothDevices(items: List<SavedBluetoothDevice>): String =
    savedBluetoothDevicesJson.encodeToString(
        ListSerializer(SavedBluetoothDevice.serializer()),
        items
    )

fun decodeSavedBluetoothDevices(raw: String?): List<SavedBluetoothDevice> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        savedBluetoothDevicesJson.decodeFromString(
            ListSerializer(SavedBluetoothDevice.serializer()),
            raw
        )
    } catch (_: Exception) {
        emptyList()
    }
}
