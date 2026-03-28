package com.erv.app.bodytracker

import com.erv.app.SectionLogDateFilter
import kotlinx.serialization.Serializable
import java.time.LocalDate

fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000

/** Stored in JSON; display/input may use inches per [BodyTrackerLibraryState.lengthUnit]. */
@Serializable
enum class BodyMeasurementKey {
    NECK,
    SHOULDERS,
    CHEST,
    WAIST,
    HIPS,
    BICEP_LEFT,
    BICEP_RIGHT,
    FOREARM_LEFT,
    FOREARM_RIGHT,
    WRIST,
    THIGH_LEFT,
    THIGH_RIGHT,
    CALF_LEFT,
    CALF_RIGHT,
    ANKLE
}

fun BodyMeasurementKey.displayLabel(): String = when (this) {
    BodyMeasurementKey.NECK -> "Neck"
    BodyMeasurementKey.SHOULDERS -> "Shoulders"
    BodyMeasurementKey.CHEST -> "Chest"
    BodyMeasurementKey.WAIST -> "Waist"
    BodyMeasurementKey.HIPS -> "Hips"
    BodyMeasurementKey.BICEP_LEFT -> "Bicep (left)"
    BodyMeasurementKey.BICEP_RIGHT -> "Bicep (right)"
    BodyMeasurementKey.FOREARM_LEFT -> "Forearm (left)"
    BodyMeasurementKey.FOREARM_RIGHT -> "Forearm (right)"
    BodyMeasurementKey.WRIST -> "Wrist"
    BodyMeasurementKey.THIGH_LEFT -> "Thigh (left)"
    BodyMeasurementKey.THIGH_RIGHT -> "Thigh (right)"
    BodyMeasurementKey.CALF_LEFT -> "Calf (left)"
    BodyMeasurementKey.CALF_RIGHT -> "Calf (right)"
    BodyMeasurementKey.ANKLE -> "Ankle"
}

@Serializable
enum class BodyMeasurementLengthUnit {
    CENTIMETERS,
    INCHES
}

@Serializable
data class BodyTrackerPhoto(
    val id: String,
    val addedAtEpochSeconds: Long = nowEpochSeconds()
)

@Serializable
data class BodyTrackerDayLog(
    val date: String,
    /** Body weight in kg when set. */
    val weightKg: Double? = null,
    /** Linear measurements stored in centimeters; keys are [BodyMeasurementKey.name]. */
    val measurementsCm: Map<String, Double> = emptyMap(),
    val note: String = "",
    val photos: List<BodyTrackerPhoto> = emptyList(),
    /**
     * Bumped when weight / measurements / note are saved. Used for Nostr merge and publish;
     * not advanced when only local photos change.
     */
    val updatedAtEpochSeconds: Long = 0L
)

@Serializable
data class BodyTrackerLibraryState(
    val lengthUnit: BodyMeasurementLengthUnit = BodyMeasurementLengthUnit.CENTIMETERS,
    /** For Nostr merge of display preference (cm vs in). */
    val lengthUnitUpdatedAtEpochSeconds: Long = 0L,
    val logs: List<BodyTrackerDayLog> = emptyList()
) {
    fun logFor(date: LocalDate): BodyTrackerDayLog? =
        logs.firstOrNull { it.date == date.toString() }
}

fun List<BodyTrackerDayLog>.upsertLog(log: BodyTrackerDayLog): List<BodyTrackerDayLog> {
    val items = toMutableList()
    val index = items.indexOfFirst { it.date == log.date }
    if (index >= 0) items[index] = log else items.add(log)
    return items.sortedBy { it.date }
}

fun BodyTrackerDayLog.isEffectivelyEmpty(): Boolean =
    weightKg == null && measurementsCm.isEmpty() && note.isBlank() && photos.isEmpty()

/** Network-synced fields only (photos are always local). */
fun BodyTrackerDayLog.isNostrEmpty(): Boolean =
    weightKg == null && measurementsCm.isEmpty() && note.isBlank()

/** Days that count as “logged” for history / calendar dots (any saved data or photos). */
fun BodyTrackerDayLog.hasActivityEntry(): Boolean = !isEffectivelyEmpty()

fun BodyTrackerLibraryState.bodyTrackerDayLogsForSectionLog(filter: SectionLogDateFilter): List<BodyTrackerDayLog> {
    val withActivity = logs.filter { it.hasActivityEntry() }
    return when (filter) {
        SectionLogDateFilter.AllHistory ->
            withActivity.sortedByDescending { it.date }
        is SectionLogDateFilter.SingleDay ->
            withActivity.filter { it.date == filter.day.toString() }
        is SectionLogDateFilter.DateRange -> {
            withActivity.filter { log ->
                val d = runCatching { LocalDate.parse(log.date) }.getOrNull() ?: return@filter false
                !d.isBefore(filter.startInclusive) && !d.isAfter(filter.endInclusive)
            }.sortedByDescending { it.date }
        }
    }
}

fun datesWithBodyTrackerActivity(state: BodyTrackerLibraryState): Set<LocalDate> =
    state.logs.filter { it.hasActivityEntry() }.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.toSet()
