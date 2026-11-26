package com.erv.app.supplements

import com.erv.app.SectionLogDateFilter
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

@Serializable
enum class SupplementForm { CAPSULE, TABLET, SOFTGEL, GUMMY, POWDER, LIQUID }

fun SupplementForm.label(): String = when (this) {
    SupplementForm.CAPSULE -> "Capsule"
    SupplementForm.TABLET -> "Tablet"
    SupplementForm.SOFTGEL -> "Softgel"
    SupplementForm.GUMMY -> "Gummy"
    SupplementForm.POWDER -> "Powder"
    SupplementForm.LIQUID -> "Liquid"
}

/** Counted forms measure a serving in pieces (2 capsules), not in a unit (5 g). */
fun SupplementForm.isCounted(): Boolean = when (this) {
    SupplementForm.CAPSULE, SupplementForm.TABLET, SupplementForm.SOFTGEL, SupplementForm.GUMMY -> true
    SupplementForm.POWDER, SupplementForm.LIQUID -> false
}

/** Lowercase piece noun for counted forms, e.g. "capsule" / "capsules", "gummy" / "gummies". */
fun SupplementForm.pieceLabel(count: Double): String {
    val one = count == 1.0
    return when (this) {
        SupplementForm.CAPSULE -> if (one) "capsule" else "capsules"
        SupplementForm.TABLET -> if (one) "tablet" else "tablets"
        SupplementForm.SOFTGEL -> if (one) "softgel" else "softgels"
        SupplementForm.GUMMY -> if (one) "gummy" else "gummies"
        SupplementForm.POWDER -> "powder"
        SupplementForm.LIQUID -> "liquid"
    }
}

@Serializable
enum class SupplementUnit { MG, MCG, G, IU, ML, DROPS }

@Serializable
enum class SupplementTimeOfDay {
    MORNING,
    @kotlinx.serialization.SerialName("AFTERNOON") MIDDAY, // was AFTERNOON, label "Midday"
    NIGHT,
    OTHER
}

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
    /** Display label for unit: "-" for counted forms (amount = number of pieces), else e.g. "mg". */
    fun unitDisplayLabel(): String = if (form.isCounted()) "-" else unit.label()

    /** "2 capsules" for counted forms, "5 g" otherwise. */
    fun amountText(total: Double): String =
        if (form.isCounted()) "${formatAmount(total)} ${form.pieceLabel(total)}"
        else "${formatAmount(total)} ${unit.label()}"

    fun summary(): String = buildString {
        append(form.label())
        if (servingSize.isNotBlank()) append(" • $servingSize")
        val dosage = amount?.let { a -> "${amountText(a)} per serving" }
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
                val serving = amount?.let { a -> amountText(a) }
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
    val note: String? = null,
    /** When true, step stays in the routine but is skipped when logging and hidden from dashboard quick log preview. */
    val paused: Boolean = false
)

@Serializable
data class SupplementRoutine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val timeOfDay: SupplementTimeOfDay = SupplementTimeOfDay.MORNING,
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

/** How often a supplement was logged on one day (routine steps and ad hoc). */
data class SupplementTakenStatus(
    val count: Int,
    val lastTakenAtEpochSeconds: Long?
)

/** Today's progress for one routine: how many active steps were logged through it. */
data class SupplementRoutineDayStatus(
    val activeStepCount: Int,
    val takenStepCount: Int,
    val lastLoggedAtEpochSeconds: Long?
) {
    val isComplete: Boolean get() = activeStepCount > 0 && takenStepCount >= activeStepCount
}

fun SupplementLibraryState.takenStatus(supplementId: String, date: LocalDate): SupplementTakenStatus {
    val log = logFor(date) ?: return SupplementTakenStatus(0, null)
    val times = buildList {
        log.routineRuns.forEach { run ->
            run.stepIntakes.filter { it.supplementId == supplementId }
                .forEach { add(it.takenAtEpochSeconds ?: run.takenAtEpochSeconds) }
        }
        log.adHocIntakes.filter { it.supplementId == supplementId }
            .forEach { add(it.takenAtEpochSeconds ?: 0L) }
    }
    return SupplementTakenStatus(
        count = times.size,
        lastTakenAtEpochSeconds = times.filter { it > 0L }.maxOrNull()
    )
}

/** Supplement ids already logged on [date] through [routineId], with the time each was logged. */
fun SupplementLibraryState.routineStepsTakenOn(routineId: String, date: LocalDate): Map<String, Long> {
    val log = logFor(date) ?: return emptyMap()
    val taken = linkedMapOf<String, Long>()
    log.routineRuns.filter { it.routineId == routineId }.forEach { run ->
        run.stepIntakes.forEach { intake ->
            val at = intake.takenAtEpochSeconds ?: run.takenAtEpochSeconds
            val prev = taken[intake.supplementId]
            if (prev == null || at > prev) taken[intake.supplementId] = at
        }
    }
    return taken
}

/** Steps that would be logged by default: not paused and pointing at a supplement that exists. */
fun SupplementLibraryState.activeSteps(routine: SupplementRoutine): List<SupplementRoutineStep> =
    routine.steps.filter { !it.paused && supplementById(it.supplementId) != null }

fun SupplementLibraryState.routineDayStatus(routine: SupplementRoutine, date: LocalDate): SupplementRoutineDayStatus {
    val active = activeSteps(routine).map { it.supplementId }.distinct()
    val taken = routineStepsTakenOn(routine.id, date)
    return SupplementRoutineDayStatus(
        activeStepCount = active.size,
        takenStepCount = active.count { it in taken },
        lastLoggedAtEpochSeconds = taken.values.maxOrNull()
    )
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

fun SupplementLogEntry.isFromRoutine(): Boolean = sourceLabel.startsWith("Routine:")

/** Routine display name when [isFromRoutine]; null if label is malformed. */
fun SupplementLogEntry.routineNameFromSource(): String? =
    if (!isFromRoutine()) null
    else sourceLabel.removePrefix("Routine:").trim().takeIf { it.isNotEmpty() }

/** One calendar day of intakes for log UI (entries sorted by time, then name). */
data class SupplementDayLogSection(
    val logDate: LocalDate,
    val entries: List<SupplementLogEntry>
)

/** Groups flat dated rows into one section per day, newest days first. */
fun List<DatedSupplementLogEntry>.groupIntoDaySectionsNewestFirst(): List<SupplementDayLogSection> {
    if (isEmpty()) return emptyList()
    return groupBy { it.logDate }
        .map { (date, datedList) ->
            SupplementDayLogSection(
                logDate = date,
                entries = datedList
                    .map { it.entry }
                    .sortedWith(
                        compareBy<SupplementLogEntry> { it.takenAtEpochSeconds }
                            .thenBy { it.supplementName.lowercase() }
                            .thenBy { it.intakeId ?: "" }
                    )
            )
        }
        .sortedByDescending { it.logDate }
}

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
        compareBy<SupplementLogEntry> { it.takenAtEpochSeconds }
            .thenBy { it.supplementName.lowercase() }
            .thenBy { it.intakeId ?: "" }
    )
}

data class DatedSupplementLogEntry(val logDate: LocalDate, val entry: SupplementLogEntry)

fun SupplementLibraryState.chronologicalSupplementLogForRange(start: LocalDate, end: LocalDate): List<DatedSupplementLogEntry> {
    val from = if (start <= end) start else end
    val to = if (start <= end) end else start
    val rows = mutableListOf<DatedSupplementLogEntry>()
    var d = from
    while (!d.isAfter(to)) {
        chronologicalSupplementLogFor(d).forEach { rows.add(DatedSupplementLogEntry(d, it)) }
        d = d.plusDays(1)
    }
    return rows.sortedWith(
        compareBy<DatedSupplementLogEntry> { it.logDate }
            .thenBy { it.entry.takenAtEpochSeconds }
            .thenBy { it.entry.supplementName.lowercase() }
            .thenBy { it.entry.intakeId ?: "" }
    )
}

private fun List<DatedSupplementLogEntry>.sortedSupplementNewestFirst(): List<DatedSupplementLogEntry> =
    sortedWith(
        compareByDescending<DatedSupplementLogEntry> { it.entry.takenAtEpochSeconds }
            .thenByDescending { it.logDate }
            .thenBy { it.entry.supplementName.lowercase() }
            .thenBy { it.entry.intakeId ?: "" }
    )

fun SupplementLibraryState.datedSupplementEntriesForSectionLog(filter: SectionLogDateFilter): List<DatedSupplementLogEntry> =
    when (filter) {
        SectionLogDateFilter.AllHistory -> {
            val rows = mutableListOf<DatedSupplementLogEntry>()
            for (dl in logs) {
                val d = LocalDate.parse(dl.date)
                chronologicalSupplementLogFor(d).forEach { rows.add(DatedSupplementLogEntry(d, it)) }
            }
            rows.sortedSupplementNewestFirst()
        }
        is SectionLogDateFilter.SingleDay ->
            chronologicalSupplementLogFor(filter.day).map { DatedSupplementLogEntry(filter.day, it) }
                .sortedSupplementNewestFirst()
        is SectionLogDateFilter.DateRange ->
            chronologicalSupplementLogForRange(filter.startInclusive, filter.endInclusive).sortedSupplementNewestFirst()
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

/**
 * Short dose text for routine cards (matches [groupedSupplementActivityFor] / dashboard Activity when plan has [SupplementDosagePlan.amount]).
 */
fun routineStepListAmountDisplay(step: SupplementRoutineStep, supplement: SupplementEntry?): String {
    val plan = supplement?.dosagePlan
    val qty = (step.quantity ?: 1).coerceAtLeast(1).toDouble()
    if (plan != null && plan.amount != null) {
        val total = qty * plan.amount
        return plan.amountText(total)
    }
    step.dosageOverride?.takeIf { it.isNotBlank() }?.trim()?.let { return it }
    plan?.servingSize?.takeIf { it.isNotBlank() }?.let { return it }
    return plan?.form?.let { it.pieceLabel(1.0) } ?: "take as directed"
}

/** One line per step: "Name amount" like dashboard supplement activity rows. */
fun SupplementRoutineStep.activityStyleSummary(supplement: SupplementEntry?): String {
    val name = supplement?.name ?: "Missing supplement"
    return "$name ${routineStepListAmountDisplay(this, supplement)}"
}

fun SupplementRoutineStep.describe(supplementName: String): String = buildString {
    append(supplementName)
    timeOfDay?.let {
        append(" • ")
        append(
            when (it) {
                SupplementTimeOfDay.MORNING -> "morning"
                SupplementTimeOfDay.MIDDAY -> "midday"
                SupplementTimeOfDay.NIGHT -> "night"
                SupplementTimeOfDay.OTHER -> "other"
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

private fun resolveSupplementAmountDisplay(
    supplement: SupplementEntry,
    intakes: List<SupplementIntake>
): String {
    val plan = supplement.dosagePlan
    val amount = plan.amount
    return if (amount != null) {
        val total = intakes.sumOf { (it.quantity ?: 1).toDouble() } * amount
        plan.amountText(total)
    } else {
        intakes.firstNotBlankDosageTaken()
            ?: supplement.dosagePlan.summary()
    }
}

private fun List<SupplementIntake>.firstNotBlankDosageTaken(): String? =
    firstNotNullOfOrNull { it.dosageTaken?.takeIf { text -> text.isNotBlank() } }

