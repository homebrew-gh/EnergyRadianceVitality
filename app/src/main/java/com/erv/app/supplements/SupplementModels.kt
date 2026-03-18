package com.erv.app.supplements

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

@Serializable
enum class SupplementForm { CAPSULE, POWDER }

@Serializable
enum class SupplementFrequency { DAILY, WEEKLY, MONTHLY, MORE_THAN_ONCE_PER_DAY }

@Serializable
enum class SupplementUnit { MG, MCG, G, IU, ML, DROPS }

@Serializable
enum class SupplementTimeOfDay { MORNING, AFTERNOON, NIGHT }

@Serializable
enum class SupplementWeekday { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

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
data class SupplementDosagePlan(
    val form: SupplementForm = SupplementForm.POWDER,
    val servingSize: String = "",
    val amount: Double? = null,
    val unit: SupplementUnit = SupplementUnit.MG,
) {
    /** Display label for unit: "-" when form is Capsule (amount = number of capsules), else e.g. "mg". */
    fun unitDisplayLabel(): String = if (form == SupplementForm.CAPSULE) "-" else unit.label()

    fun summary(): String = buildString {
        append(
            when (form) {
                SupplementForm.CAPSULE -> "Capsule"
                SupplementForm.POWDER -> "Powder"
            }
        )
        if (servingSize.isNotBlank()) append(" • $servingSize")
        val dosage = when (form) {
            SupplementForm.CAPSULE -> amount?.let { a -> "${formatAmount(a)} capsule${if (a == 1.0) "" else "s"} per serving" }
            SupplementForm.POWDER -> amount?.let { a -> "${formatAmount(a)} ${unit.label()} per serving" }
        }
        if (!dosage.isNullOrBlank()) append(" • $dosage")
    }.trim()

    fun routinePreview(supplementId: String): List<SupplementRoutineStep> = listOfNotNull(
        supplementStep(
            supplementId,
            SupplementTimeOfDay.MORNING,
            1,
            if (servingSize.isNotBlank()) servingSize else "Daily serving"
        )
    )

    private fun supplementStep(
        supplementId: String,
        timeOfDay: SupplementTimeOfDay?,
        quantity: Int,
        notePrefix: String
    ): SupplementRoutineStep? {
        if (quantity <= 0) return null
        return SupplementRoutineStep(
            supplementId = supplementId,
            timeOfDay = timeOfDay,
            quantity = quantity,
            note = buildString {
                append(notePrefix)
                append(": ")
                val serving = when (form) {
                    SupplementForm.CAPSULE -> amount?.let { a -> "${formatAmount(a)} capsule${if (a == 1.0) "" else "s"}" }
                    SupplementForm.POWDER -> amount?.let { a -> "${formatAmount(a)} ${unit.label()}" }
                }
                if (!serving.isNullOrBlank()) append("$quantity x $serving")
                else if (servingSize.isNotBlank()) append("$quantity x $servingSize")
                else append("$quantity serving${if (quantity == 1) "" else "s"}")
            }
        )
    }
}

@Serializable
data class SupplementEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val brand: String = "",
    val dosagePlan: SupplementDosagePlan = SupplementDosagePlan(),
    val notes: String = "",
    val productId: String? = null,
    val info: SupplementInfo? = null
)

@Serializable
data class SupplementRoutineStep(
    val supplementId: String,
    val timeOfDay: SupplementTimeOfDay? = null,
    val quantity: Int? = null,
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
    val note: String? = null,
    /** Multiplier for serving size (e.g. 2 = 2× the stated serving). Default 1. */
    val quantity: Int? = 1,
    /** Unique id for this intake (used to edit/delete from log). Omitted for legacy data. */
    val id: String? = null
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

data class SupplementActivityRow(
    val supplementId: String,
    val supplementName: String,
    val amountDisplay: String,
    val intakeCount: Int
)

data class SupplementLogEntry(
    val supplementId: String,
    val supplementName: String,
    val dosageTaken: String,
    val takenAtEpochSeconds: Long,
    val sourceLabel: String,
    /** Intake id for delete; null for legacy entries. */
    val intakeId: String? = null
)

fun SupplementLibraryState.groupedSupplementActivityFor(date: LocalDate): List<SupplementActivityRow> {
    val log = logFor(date) ?: return emptyList()
    val intakesBySupplementId = linkedMapOf<String, MutableList<SupplementIntake>>()

    fun record(intake: SupplementIntake) {
        intakesBySupplementId.getOrPut(intake.supplementId) { mutableListOf() }.add(intake)
    }

    log.routineRuns.forEach { run -> run.stepIntakes.forEach(::record) }
    log.adHocIntakes.forEach(::record)

    return intakesBySupplementId.entries.mapNotNull { (supplementId, intakes) ->
        val supplement = supplementById(supplementId)
        val supplementName = supplement?.name ?: intakes.firstOrNull()?.supplementName ?: return@mapNotNull null
        val amountDisplay = supplement?.let { resolveSupplementAmountDisplay(it, intakes) }
            ?: intakes.firstNotBlankDosageTaken()
            ?: "take as directed"
        SupplementActivityRow(
            supplementId = supplementId,
            supplementName = supplementName,
            amountDisplay = amountDisplay,
            intakeCount = intakes.size
        )
    }.sortedBy { it.supplementName.lowercase() }
}

fun SupplementLibraryState.chronologicalSupplementLogFor(date: LocalDate): List<SupplementLogEntry> {
    val log = logFor(date) ?: return emptyList()
    val entries = buildList {
        log.routineRuns.forEach { run ->
            run.stepIntakes.forEach { intake ->
                add(
                    SupplementLogEntry(
                        supplementId = intake.supplementId,
                        supplementName = intake.supplementName,
                        dosageTaken = intake.dosageTaken?.takeIf { it.isNotBlank() }
                            ?: "take as directed",
                        takenAtEpochSeconds = intake.takenAtEpochSeconds ?: run.takenAtEpochSeconds,
                        sourceLabel = "Routine: ${run.routineName}",
                        intakeId = intake.id
                    )
                )
            }
        }
        log.adHocIntakes.forEach { intake ->
            add(
                SupplementLogEntry(
                    supplementId = intake.supplementId,
                    supplementName = intake.supplementName,
                    dosageTaken = intake.dosageTaken?.takeIf { it.isNotBlank() }
                        ?: "take as directed",
                    takenAtEpochSeconds = intake.takenAtEpochSeconds ?: 0L,
                    sourceLabel = "Ad hoc",
                    intakeId = intake.id
                )
            )
        }
    }

    return entries.sortedWith(
        compareByDescending<SupplementLogEntry> { it.takenAtEpochSeconds }
            .thenBy { it.supplementName.lowercase() }
    )
}

fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000

private fun formatAmount(amount: Double): String =
    if (amount % 1.0 == 0.0) amount.toInt().toString() else amount.toString()

fun SupplementUnit.label(): String = when (this) {
    SupplementUnit.MG -> "mg"
    SupplementUnit.MCG -> "mcg"
    SupplementUnit.G -> "g"
    SupplementUnit.IU -> "IU"
    SupplementUnit.ML -> "mL"
    SupplementUnit.DROPS -> "drops"
}

fun SupplementRoutineStep.describe(supplementName: String): String = buildString {
    append(supplementName)
    timeOfDay?.let {
        append(" • ")
        append(
            when (it) {
                SupplementTimeOfDay.MORNING -> "morning"
                SupplementTimeOfDay.AFTERNOON -> "afternoon"
                SupplementTimeOfDay.NIGHT -> "night"
            }
        )
    }
    quantity?.takeIf { it > 0 }?.let {
        append(" x")
        append(it)
    }
    dosageOverride?.takeIf { it.isNotBlank() }?.let {
        append(" • ")
        append(it)
    }
    note?.takeIf { it.isNotBlank() }?.let {
        append(" • ")
        append(it)
    }
}

fun SupplementWeekday.shortLabel(): String = when (this) {
    SupplementWeekday.MONDAY -> "Mon"
    SupplementWeekday.TUESDAY -> "Tue"
    SupplementWeekday.WEDNESDAY -> "Wed"
    SupplementWeekday.THURSDAY -> "Thu"
    SupplementWeekday.FRIDAY -> "Fri"
    SupplementWeekday.SATURDAY -> "Sat"
    SupplementWeekday.SUNDAY -> "Sun"
}

private fun resolveSupplementAmountDisplay(
    supplement: SupplementEntry,
    intakes: List<SupplementIntake>
): String {
    val plan = supplement.dosagePlan
    val amount = plan.amount
    return if (amount != null) {
        val total = intakes.sumOf { (it.quantity ?: 1).toDouble() } * amount
        when (plan.form) {
            SupplementForm.CAPSULE -> "${formatAmount(total)} capsule${if (total == 1.0) "" else "s"}"
            SupplementForm.POWDER -> "${formatAmount(total)} ${plan.unit.label()}"
        }
    } else {
        intakes.firstNotBlankDosageTaken()
            ?: supplement.dosagePlan.summary()
    }
}

private fun List<SupplementIntake>.firstNotBlankDosageTaken(): String? =
    firstNotNullOfOrNull { it.dosageTaken?.takeIf { text -> text.isNotBlank() } }

