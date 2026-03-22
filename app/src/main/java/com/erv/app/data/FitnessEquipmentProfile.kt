package com.erv.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** App workout areas; stable names for storage and future AI / workout planning. */
@Serializable
enum class WorkoutModality {
    CARDIO,
    WEIGHT_TRAINING,
    STRETCHING,
    HIIT,
}

fun WorkoutModality.displayLabel(): String =
    when (this) {
        WorkoutModality.CARDIO -> "Cardio"
        WorkoutModality.WEIGHT_TRAINING -> "Weight training"
        WorkoutModality.STRETCHING -> "Stretching"
        WorkoutModality.HIIT -> "HIIT"
    }

/** Plaintext payload encrypted into kind 30078, `d` tag `erv/equipment` (replaceable). */
@Serializable
data class FitnessEquipmentNostrPayload(
    val gymMembership: Boolean = false,
    val equipment: List<OwnedEquipmentItem> = emptyList(),
)

private val equipmentJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

fun encodeOwnedEquipmentList(items: List<OwnedEquipmentItem>): String =
    equipmentJson.encodeToString(ListSerializer(OwnedEquipmentItem.serializer()), items)

fun decodeOwnedEquipmentList(raw: String?): List<OwnedEquipmentItem> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        equipmentJson.decodeFromString(ListSerializer(OwnedEquipmentItem.serializer()), raw)
    } catch (_: Exception) {
        emptyList()
    }
}
