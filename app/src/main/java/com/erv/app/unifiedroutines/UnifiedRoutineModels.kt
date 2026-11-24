package com.erv.app.unifiedroutines

import com.erv.app.cardio.CardioHrScaffolding
import com.erv.app.cardio.CardioQuickLaunch
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Old Unified Workouts data, kept only so saved sessions, backups and logged entries' unifiedLink
// still decode. Nothing creates these any more; the Training tab's workouts replace them.

@Serializable
enum class UnifiedRoutineBlockType {
    @SerialName("weight") WEIGHT,
    @SerialName("cardio") CARDIO,
    @SerialName("stretch") STRETCH,
}

@Serializable
data class UnifiedRoutineBlock(
    val id: String = UUID.randomUUID().toString(),
    val type: UnifiedRoutineBlockType,
    val title: String? = null,
    val notes: String? = null,
    val weightExerciseIds: List<String> = emptyList(),
    val weightRoutineId: String? = null,
    val cardioActivity: String? = null,
    val cardioRoutineId: String? = null,
    val cardioQuickLaunchId: String? = null,
    val cardioInlineQuickLaunch: CardioQuickLaunch? = null,
    val stretchRoutineId: String? = null,
    val stretchCatalogIds: List<String> = emptyList(),
    val stretchHoldSecondsPerStretch: Int = 30,
    val targetMinutes: Int? = null,
)

@Serializable
data class UnifiedRoutine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val notes: String? = null,
    val blocks: List<UnifiedRoutineBlock> = emptyList(),
    val createdAtEpochSeconds: Long = System.currentTimeMillis() / 1000,
    val lastModifiedEpochSeconds: Long = System.currentTimeMillis() / 1000,
)

@Serializable
data class UnifiedSessionLink(
    val sessionId: String,
    val routineId: String,
    val blockId: String,
    val displayRef: String,
)

@Serializable
data class UnifiedWorkoutBlockRecap(
    val blockId: String,
    val type: UnifiedRoutineBlockType,
    val title: String? = null,
    val sourceBlock: UnifiedRoutineBlock? = null,
    val startedAtEpochSeconds: Long? = null,
    val finishedAtEpochSeconds: Long? = null,
    val linkedLogDate: String? = null,
    val linkedEntryId: String? = null,
)

@Serializable
data class UnifiedWorkoutSession(
    val id: String = UUID.randomUUID().toString(),
    val displayRef: String = generateUnifiedWorkoutDisplayRef(),
    val routineId: String,
    val routineName: String,
    val startedAtEpochSeconds: Long = nowUnifiedRoutineEpochSeconds(),
    val finishedAtEpochSeconds: Long? = null,
    val routineSnapshot: UnifiedRoutine? = null,
    val startedAsAdHoc: Boolean = false,
    val completedBlockIds: List<String> = emptyList(),
    val lastLaunchedBlockId: String? = null,
    val heartRate: CardioHrScaffolding? = null,
    val blocks: List<UnifiedWorkoutBlockRecap> = emptyList(),
)

@Serializable
data class UnifiedRoutineSessionState(
    val sessionId: String,
    val routineId: String,
    val routineSnapshot: UnifiedRoutine? = null,
    val startedAsAdHoc: Boolean = false,
    val startedAtEpochSeconds: Long = System.currentTimeMillis() / 1000,
    val completedBlockIds: List<String> = emptyList(),
    val lastLaunchedBlockId: String? = null,
)

@Serializable
data class UnifiedRoutineLibraryState(
    val routines: List<UnifiedRoutine> = emptyList(),
    val sessions: List<UnifiedWorkoutSession> = emptyList(),
    val activeSession: UnifiedRoutineSessionState? = null,
) {
    fun routineById(id: String): UnifiedRoutine? = routines.firstOrNull { it.id == id }
    fun sessionById(id: String): UnifiedWorkoutSession? = sessions.firstOrNull { it.id == id }
}

fun nowUnifiedRoutineEpochSeconds(): Long = System.currentTimeMillis() / 1000

fun generateUnifiedWorkoutDisplayRef(nowEpochSeconds: Long = nowUnifiedRoutineEpochSeconds()): String {
    val timePart = nowEpochSeconds.toString(36).uppercase()
    val randomPart = UUID.randomUUID().toString().replace("-", "").takeLast(4).uppercase()
    return "UW-$timePart-$randomPart"
}
