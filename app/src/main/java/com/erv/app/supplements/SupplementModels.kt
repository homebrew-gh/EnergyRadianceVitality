package com.erv.app.supplements

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

@Serializable
data class SupplementInfo(
    val source: String = "dsld",
    val productId: String? = null,
    val productName: String? = null,
    val brand: String? = null,
    val suggestedUse: String? = null,
    val claimsOrUses: List<String> = emptyList(),
    val supplementForm: List<String> = emptyList(),
    val targetGroup: List<String> = emptyList(),
    val ingredients: List<String> = emptyList(),
    val otherIngredients: String? = null,
    val servingSize: String? = null,
    val fetchedAtEpochSeconds: Long = 0L
)

@Serializable
data class SupplementEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val dosage: String,
    val frequency: String,
    val whenToTake: String,
    val notes: String = "",
    val productId: String? = null,
    val info: SupplementInfo? = null
)

@Serializable
data class SupplementRoutineStep(
    val supplementId: String,
    val dosageOverride: String? = null,
    val note: String? = null
)

@Serializable
data class SupplementRoutine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val steps: List<SupplementRoutineStep> = emptyList(),
    val notes: String = ""
)

@Serializable
data class SupplementIntake(
    val supplementId: String,
    val supplementName: String,
    val dosageTaken: String? = null,
    val takenAtEpochSeconds: Long? = null,
    val note: String? = null
)

@Serializable
data class SupplementRoutineRun(
    val id: String = UUID.randomUUID().toString(),
    val routineId: String,
    val routineName: String,
    val takenAtEpochSeconds: Long = nowEpochSeconds(),
    val stepIntakes: List<SupplementIntake> = emptyList()
)

@Serializable
data class SupplementDayLog(
    val date: String,
    val routineRuns: List<SupplementRoutineRun> = emptyList(),
    val adHocIntakes: List<SupplementIntake> = emptyList()
)

@Serializable
data class SupplementLibraryState(
    val supplements: List<SupplementEntry> = emptyList(),
    val routines: List<SupplementRoutine> = emptyList(),
    val logs: List<SupplementDayLog> = emptyList()
) {
    fun supplementById(id: String): SupplementEntry? = supplements.firstOrNull { it.id == id }
    fun routineById(id: String): SupplementRoutine? = routines.firstOrNull { it.id == id }
    fun logFor(date: LocalDate): SupplementDayLog? = logs.firstOrNull { it.date == date.toString() }

    fun summaryFor(date: LocalDate): SupplementDaySummary {
        val log = logFor(date)
        val routineRuns = log?.routineRuns.orEmpty()
        val adHocIntakes = log?.adHocIntakes.orEmpty()
        val routineNames = routineRuns.map { it.routineName }
        val uniqueSupplements = buildSet {
            routineRuns.forEach { run -> run.stepIntakes.forEach { add(it.supplementId) } }
            adHocIntakes.forEach { add(it.supplementId) }
        }
        return SupplementDaySummary(
            date = date,
            routineCount = routineRuns.size,
            adHocCount = adHocIntakes.size,
            uniqueSupplementCount = uniqueSupplements.size,
            routineNames = routineNames
        )
    }
}

data class SupplementDaySummary(
    val date: LocalDate,
    val routineCount: Int,
    val adHocCount: Int,
    val uniqueSupplementCount: Int,
    val routineNames: List<String>
)

fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000

