package com.erv.app.programs

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.UUID

@Serializable
enum class ProgramStrategyMode {
    MANUAL,
    REPEAT,
    ROTATION,
    CHALLENGE,
}

@Serializable
data class ProgramRotationEntry(
    val id: String = UUID.randomUUID().toString(),
    val programId: String? = null,
    val repeatWeeks: Int = 1,
)

@Serializable
data class ProgramStrategy(
    val mode: ProgramStrategyMode = ProgramStrategyMode.MANUAL,
    val repeatProgramId: String? = null,
    val rotationStartDate: String? = null,
    val rotationEntries: List<ProgramRotationEntry> = emptyList(),
    val rotationRepeats: Boolean = true,
    val challengeName: String? = null,
    val challengeProgramId: String? = null,
    val challengeStartDate: String? = null,
    val challengeLengthDays: Int = 75,
)

data class ResolvedProgramStrategy(
    val mode: ProgramStrategyMode,
    val program: FitnessProgram? = null,
    val title: String,
    val detail: String? = null,
    val isUsingStrategy: Boolean = false,
    val challengeDayNumber: Int? = null,
    val challengeTotalDays: Int? = null,
)

fun ProgramsLibraryState.resolveProgramStrategyForDate(date: LocalDate): ResolvedProgramStrategy {
    val strategy = strategy
    return when (strategy.mode) {
        ProgramStrategyMode.MANUAL -> {
            val program = activeProgramId?.let(::programById)
            ResolvedProgramStrategy(
                mode = ProgramStrategyMode.MANUAL,
                program = program,
                title = program?.name ?: "Manual selection",
                detail = if (program == null) "Choose a program" else "Manual active program",
                isUsingStrategy = false,
            )
        }

        ProgramStrategyMode.REPEAT -> {
            val program = strategy.repeatProgramId?.let(::programById)
            if (program == null) {
                resolveProgramStrategyForDateFallback()
            } else {
                ResolvedProgramStrategy(
                    mode = ProgramStrategyMode.REPEAT,
                    program = program,
                    title = program.name,
                    detail = "Repeats weekly",
                    isUsingStrategy = true,
                )
            }
        }

        ProgramStrategyMode.ROTATION -> resolveRotationStrategyForDate(strategy, date)
        ProgramStrategyMode.CHALLENGE -> resolveChallengeStrategyForDate(strategy, date)
    }
}

fun ProgramsLibraryState.programBlocksForDate(date: LocalDate): List<ProgramDayBlock> {
    val resolved = resolveProgramStrategyForDate(date)
    return programBlocksForCalendarDay(resolved.program, date.dayOfWeek.value)
}

fun ProgramsLibraryState.strategySummaryForDate(date: LocalDate): String {
    val resolved = resolveProgramStrategyForDate(date)
    return when {
        resolved.program == null && resolved.detail != null -> resolved.detail
        resolved.detail.isNullOrBlank() -> resolved.title
        else -> "${resolved.title} · ${resolved.detail}"
    }
}

private fun ProgramsLibraryState.resolveProgramStrategyForDateFallback(): ResolvedProgramStrategy {
    val program = activeProgramId?.let(::programById)
    return ResolvedProgramStrategy(
        mode = ProgramStrategyMode.MANUAL,
        program = program,
        title = program?.name ?: "Manual selection",
        detail = if (program == null) "Choose a program" else "Manual active program",
        isUsingStrategy = false,
    )
}

private fun ProgramsLibraryState.resolveRotationStrategyForDate(
    strategy: ProgramStrategy,
    date: LocalDate,
): ResolvedProgramStrategy {
    val start = strategy.rotationStartDate.parseIsoDateOrNull() ?: return resolveProgramStrategyForDateFallback()
    val entries = strategy.rotationEntries
        .mapNotNull { entry ->
            val program = entry.programId?.let(::programById) ?: return@mapNotNull null
            entry.copy(programId = program.id) to program
        }
        .filter { (entry, _) -> entry.repeatWeeks > 0 }
    if (entries.isEmpty()) return resolveProgramStrategyForDateFallback()
    if (date.isBefore(start)) {
        return ResolvedProgramStrategy(
            mode = ProgramStrategyMode.ROTATION,
            title = "Program Rotation",
            detail = "Starts $start",
            isUsingStrategy = true,
        )
    }
    val elapsedWeeks = ChronoUnit.DAYS.between(start, date).toInt() / 7
    val expanded = entries.flatMap { (entry, program) ->
        List(entry.repeatWeeks.coerceIn(1, 52)) { program }
    }
    if (expanded.isEmpty()) return resolveProgramStrategyForDateFallback()
    val selectedIndex = if (strategy.rotationRepeats) {
        elapsedWeeks % expanded.size
    } else {
        if (elapsedWeeks >= expanded.size) {
            return ResolvedProgramStrategy(
                mode = ProgramStrategyMode.ROTATION,
                title = "Program Rotation",
                detail = "Rotation complete",
                isUsingStrategy = true,
            )
        }
        elapsedWeeks
    }
    val program = expanded[selectedIndex]
    return ResolvedProgramStrategy(
        mode = ProgramStrategyMode.ROTATION,
        program = program,
        title = program.name,
        detail = "Rotation week ${selectedIndex + 1}",
        isUsingStrategy = true,
    )
}

private fun ProgramsLibraryState.resolveChallengeStrategyForDate(
    strategy: ProgramStrategy,
    date: LocalDate,
): ResolvedProgramStrategy {
    val program = strategy.challengeProgramId?.let(::programById) ?: return resolveProgramStrategyForDateFallback()
    val start = strategy.challengeStartDate.parseIsoDateOrNull() ?: return resolveProgramStrategyForDateFallback()
    val totalDays = strategy.challengeLengthDays.coerceIn(1, 365)
    val end = start.plusDays((totalDays - 1).toLong())
    val title = strategy.challengeName?.trim().takeUnless { it.isNullOrBlank() } ?: "Challenge Plan"
    return when {
        date.isBefore(start) -> ResolvedProgramStrategy(
            mode = ProgramStrategyMode.CHALLENGE,
            title = title,
            detail = "Starts $start",
            isUsingStrategy = true,
        )

        date.isAfter(end) -> ResolvedProgramStrategy(
            mode = ProgramStrategyMode.CHALLENGE,
            title = title,
            detail = "Completed on $end",
            isUsingStrategy = true,
        )

        else -> {
            val dayNumber = ChronoUnit.DAYS.between(start, date).toInt() + 1
            ResolvedProgramStrategy(
                mode = ProgramStrategyMode.CHALLENGE,
                program = program,
                title = program.name,
                detail = "$title · Day $dayNumber of $totalDays",
                isUsingStrategy = true,
                challengeDayNumber = dayNumber,
                challengeTotalDays = totalDays,
            )
        }
    }
}

fun ProgramStrategy.sanitized(validProgramIds: Set<String>): ProgramStrategy {
    val cleaned = copy(
        repeatProgramId = repeatProgramId?.takeIf(validProgramIds::contains),
        rotationEntries = rotationEntries
            .mapNotNull { entry ->
                val programId = entry.programId?.takeIf(validProgramIds::contains) ?: return@mapNotNull null
                entry.copy(programId = programId, repeatWeeks = entry.repeatWeeks.coerceIn(1, 52))
            },
        challengeProgramId = challengeProgramId?.takeIf(validProgramIds::contains),
        challengeLengthDays = challengeLengthDays.coerceIn(1, 365),
    )
    return when (cleaned.mode) {
        ProgramStrategyMode.MANUAL -> cleaned
        ProgramStrategyMode.REPEAT ->
            if (cleaned.repeatProgramId == null) ProgramStrategy() else cleaned
        ProgramStrategyMode.ROTATION ->
            if (cleaned.rotationEntries.isEmpty() || cleaned.rotationStartDate.parseIsoDateOrNull() == null) ProgramStrategy() else cleaned
        ProgramStrategyMode.CHALLENGE ->
            if (cleaned.challengeProgramId == null || cleaned.challengeStartDate.parseIsoDateOrNull() == null) ProgramStrategy() else cleaned
    }
}

private fun String?.parseIsoDateOrNull(): LocalDate? {
    if (this.isNullOrBlank()) return null
    return try {
        LocalDate.parse(trim())
    } catch (_: DateTimeParseException) {
        null
    }
}
