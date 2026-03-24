package com.erv.app.weighttraining

import java.util.UUID

/**
 * Parses ERV weight CSV (v1): one row per set. See in-app CSV guide for column definitions.
 */
object WeightCsvHistoryImport {

    private val isoDate = Regex("""^\d{4}-\d{2}-\d{2}$""")

    fun parse(text: String): Pair<ErvWeightHistoryImportEnvelope?, List<String>> {
        val errors = mutableListOf<String>()
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            return null to listOf("CSV is empty")
        }
        val headerCells = splitCsvLine(lines.first()).map { it.lowercase().trim() }
        val colDate = headerCells.indexOfFirst { it == "date" }
        val colSession = headerCells.indexOfFirst { it == "session_key" || it == "sessionkey" }
        val colEx = headerCells.indexOfFirst { it == "exercise_id" || it == "exerciseid" }
        val colSetIdx = headerCells.indexOfFirst { it == "set_index" || it == "setindex" }
        val colReps = headerCells.indexOfFirst { it == "reps" }
        val colWeight = headerCells.indexOfFirst { it == "weight_kg" || it == "weightkg" }
        val colRpe = headerCells.indexOfFirst { it == "rpe" }
        if (colDate < 0 || colSession < 0 || colEx < 0 || colSetIdx < 0 || colReps < 0) {
            return null to listOf(
                "CSV header must include: date, session_key, exercise_id, set_index, reps (optional: weight_kg, rpe)"
            )
        }
        val indexBounds = buildList {
            add(colDate)
            add(colSession)
            add(colEx)
            add(colSetIdx)
            add(colReps)
            if (colWeight >= 0) add(colWeight)
            if (colRpe >= 0) add(colRpe)
        }
        val minCols = indexBounds.max() + 1

        data class Row(
            val date: String,
            val sessionKey: String,
            val exerciseId: String,
            val setIndex: Int,
            val set: WeightSet,
        )

        val rows = mutableListOf<Row>()
        lines.drop(1).forEachIndexed { i, line ->
            val n = i + 2
            val cells = splitCsvLine(line)
            if (cells.size < minCols) {
                errors += "Line $n: not enough columns"
                return@forEachIndexed
            }
            var rowOk = true
            val date = cells[colDate].trim()
            val sessionKey = cells[colSession].trim()
            val exerciseId = cells[colEx].trim()
            if (!isoDate.matches(date)) {
                errors += "Line $n: invalid date (use YYYY-MM-DD)"
                rowOk = false
            }
            if (sessionKey.isEmpty()) {
                errors += "Line $n: session_key is required"
                rowOk = false
            }
            if (exerciseId.isEmpty()) {
                errors += "Line $n: exercise_id is required"
                rowOk = false
            }
            val setIndex = cells[colSetIdx].trim().toIntOrNull()
            if (setIndex == null || setIndex < 1) {
                errors += "Line $n: set_index must be a positive integer"
                rowOk = false
            }
            val reps = cells[colReps].trim().toIntOrNull()
            if (reps == null || reps < 0) {
                errors += "Line $n: reps must be a non-negative integer"
                rowOk = false
            }
            var weightKg: Double? = null
            if (colWeight >= 0 && colWeight < cells.size) {
                val w = cells[colWeight].trim()
                if (w.isNotEmpty()) {
                    val parsed = w.toDoubleOrNull()
                    when {
                        parsed == null -> {
                            errors += "Line $n: weight_kg must be a number or empty"
                            rowOk = false
                        }
                        parsed < 0 -> {
                            errors += "Line $n: weight_kg cannot be negative"
                            rowOk = false
                        }
                        else -> weightKg = parsed
                    }
                }
            }
            var rpe: Double? = null
            if (colRpe >= 0 && colRpe < cells.size) {
                val r = cells[colRpe].trim()
                if (r.isNotEmpty()) {
                    val parsed = r.toDoubleOrNull()
                    when {
                        parsed == null -> {
                            errors += "Line $n: rpe must be a number or empty"
                            rowOk = false
                        }
                        parsed < 0 || parsed > 10 -> {
                            errors += "Line $n: rpe should be between 0 and 10"
                            rowOk = false
                        }
                        else -> rpe = parsed
                    }
                }
            }
            if (rowOk && setIndex != null && reps != null) {
                rows += Row(
                    date = date,
                    sessionKey = sessionKey,
                    exerciseId = exerciseId,
                    setIndex = setIndex,
                    set = WeightSet(reps = reps, weightKg = weightKg, rpe = rpe),
                )
            }
        }
        if (errors.isNotEmpty()) {
            return null to errors.distinct()
        }
        if (rows.isEmpty()) {
            return null to listOf("No data rows after header")
        }

        val byDate =
            linkedMapOf<String, LinkedHashMap<String, LinkedHashMap<String, MutableMap<Int, WeightSet>>>>()
        for (r in rows) {
            val sessMap = byDate.getOrPut(r.date) { linkedMapOf() }
            val exMap = sessMap.getOrPut(r.sessionKey) { linkedMapOf() }
            val setByIndex = exMap.getOrPut(r.exerciseId) { mutableMapOf() }
            setByIndex[r.setIndex] = r.set
        }

        val dayLogs = mutableListOf<WeightDayLog>()
        for ((date, sessMap) in byDate) {
            val workouts = mutableListOf<WeightWorkoutSession>()
            for ((_, exMap) in sessMap) {
                val entries = mutableListOf<WeightWorkoutEntry>()
                for ((exerciseId, setByIndex) in exMap) {
                    val sorted = setByIndex.toSortedMap().values.toList()
                    if (sorted.isNotEmpty()) {
                        entries += WeightWorkoutEntry(exerciseId = exerciseId, sets = sorted)
                    }
                }
                if (entries.isNotEmpty()) {
                    workouts += WeightWorkoutSession(
                        id = UUID.randomUUID().toString(),
                        source = WeightWorkoutSource.IMPORTED,
                        entries = entries,
                    )
                }
            }
            if (workouts.isNotEmpty()) {
                dayLogs += WeightDayLog(date = date, workouts = workouts)
            }
        }
        if (dayLogs.isEmpty()) {
            return null to listOf("No sessions built from CSV rows")
        }
        return ErvWeightHistoryImportEnvelope(
            ervWeightHistoryImportVersion = 1,
            exercises = emptyList(),
            dayLogs = dayLogs,
        ) to emptyList()
    }

    /** Minimal CSV: commas separate fields; fields must not contain commas (per import spec). */
    private fun splitCsvLine(line: String): List<String> {
        return line.split(',').map { it.trim().removeSurrounding("\"", "\"") }
    }
}
