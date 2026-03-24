package com.erv.app.cardio

import java.util.Locale
import java.util.UUID

/**
 * Parses ERV cardio CSV (v1): one row per session. See in-app CSV guide for column definitions.
 */
object CardioCsvHistoryImport {

    private val isoDate = Regex("""^\d{4}-\d{2}-\d{2}$""")

    fun parse(
        text: String,
        existingCustomTypes: List<CardioCustomActivityType>,
    ): Pair<ErvCardioHistoryImportEnvelope?, List<String>> {
        val errors = mutableListOf<String>()
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            return null to listOf("CSV is empty")
        }
        val headerCells = splitCsvLine(lines.first()).map { it.lowercase(Locale.US).trim() }
        val colDate = headerCells.indexOfFirst { it == "date" }
        val colDuration = headerCells.indexOfFirst { it == "duration_minutes" || it == "durationminutes" }
        val colBuiltin = headerCells.indexOfFirst { it == "builtin" }
        val colCustomName = headerCells.indexOfFirst {
            it == "custom_activity_name" || it == "customactivityname"
        }
        val colCustomId = headerCells.indexOfFirst { it == "custom_type_id" || it == "customtypeid" }
        val colDisplay = headerCells.indexOfFirst { it == "display_label" || it == "displaylabel" }
        val colDistM = headerCells.indexOfFirst { it == "distance_m" || it == "distancem" }
        val colModality = headerCells.indexOfFirst { it == "modality" }
        val colSpeed = headerCells.indexOfFirst { it == "speed" }
        val colSpeedUnit = headerCells.indexOfFirst { it == "speed_unit" || it == "speedunit" }
        val colIncline = headerCells.indexOfFirst { it == "incline_pct" || it == "inclinepct" }
        val colPackKg = headerCells.indexOfFirst { it == "pack_kg" || it == "packkg" }
        val colRuckOutdoor = headerCells.indexOfFirst { it == "ruck_load_kg" || it == "ruckloadkg" }
        val colKcal = headerCells.indexOfFirst { it == "estimated_kcal" || it == "estimatedkcal" }
        val colStart = headerCells.indexOfFirst { it == "start_epoch_seconds" || it == "startepochseconds" }
        val colEnd = headerCells.indexOfFirst { it == "end_epoch_seconds" || it == "endepochseconds" }

        if (colDate < 0 || colDuration < 0) {
            return null to listOf("CSV header must include: date, duration_minutes")
        }
        if (colBuiltin < 0 && colCustomName < 0 && colCustomId < 0) {
            return null to listOf(
                "CSV header must include one of: builtin, custom_activity_name, or custom_type_id"
            )
        }

        val indexBounds = buildList {
            add(colDate)
            add(colDuration)
            if (colBuiltin >= 0) add(colBuiltin)
            if (colCustomName >= 0) add(colCustomName)
            if (colCustomId >= 0) add(colCustomId)
            if (colDisplay >= 0) add(colDisplay)
            if (colDistM >= 0) add(colDistM)
            if (colModality >= 0) add(colModality)
            if (colSpeed >= 0) add(colSpeed)
            if (colSpeedUnit >= 0) add(colSpeedUnit)
            if (colIncline >= 0) add(colIncline)
            if (colPackKg >= 0) add(colPackKg)
            if (colRuckOutdoor >= 0) add(colRuckOutdoor)
            if (colKcal >= 0) add(colKcal)
            if (colStart >= 0) add(colStart)
            if (colEnd >= 0) add(colEnd)
        }
        val minCols = indexBounds.max() + 1

        val nameToId = existingCustomTypes.associate { it.name.lowercase(Locale.US).trim() to it.id }
            .toMutableMap()
        val knownCustomIds = existingCustomTypes.map { it.id }.toMutableSet()
        val newCustomTypes = mutableListOf<CardioCustomActivityType>()
        val sessionsByDate = linkedMapOf<String, MutableList<CardioSession>>()

        lines.drop(1).forEachIndexed { i, line ->
            val n = i + 2
            val cells = splitCsvLine(line)
            fun cell(col: Int): String = if (col >= 0 && col < cells.size) cells[col].trim() else ""
            if (cells.size < minCols) {
                errors += "Line $n: not enough columns"
                return@forEachIndexed
            }

            val date = cell(colDate)
            if (!isoDate.matches(date)) {
                errors += "Line $n: invalid date (use YYYY-MM-DD)"
                return@forEachIndexed
            }
            val dur = cell(colDuration).toIntOrNull()
            if (dur == null || dur < 1) {
                errors += "Line $n: duration_minutes must be a positive integer"
                return@forEachIndexed
            }

            val builtinStr = cell(colBuiltin)
            val customName = cell(colCustomName)
            val customIdIn = cell(colCustomId)

            val builtinParsed: CardioBuiltinActivity? = if (builtinStr.isNotEmpty()) {
                try {
                    CardioBuiltinActivity.valueOf(builtinStr.uppercase(Locale.US))
                } catch (_: IllegalArgumentException) {
                    errors += "Line $n: unknown builtin `$builtinStr` (use e.g. RUN, WALK, BIKE)"
                    return@forEachIndexed
                }
            } else {
                null
            }

            val activity: CardioActivitySnapshot = when {
                builtinParsed != null -> {
                    if (customName.isNotEmpty() || customIdIn.isNotEmpty()) {
                        errors += "Line $n: do not combine builtin with custom_activity_name / custom_type_id"
                        return@forEachIndexed
                    }
                    val label = cell(colDisplay).ifEmpty { builtinParsed.displayName() }
                    CardioActivitySnapshot(
                        builtin = builtinParsed,
                        customTypeId = null,
                        customName = null,
                        displayLabel = label,
                    )
                }
                customIdIn.isNotEmpty() -> {
                    if (customIdIn !in knownCustomIds) {
                        errors += "Line $n: unknown custom_type_id (define custom_activity_name first or use an id from the app)"
                        return@forEachIndexed
                    }
                    val label = cell(colDisplay).ifEmpty { customName.ifEmpty { "Custom" } }
                    CardioActivitySnapshot(
                        builtin = null,
                        customTypeId = customIdIn,
                        customName = customName.ifEmpty { null },
                        displayLabel = label,
                    )
                }
                customName.isNotEmpty() -> {
                    val key = customName.lowercase(Locale.US)
                    val id = nameToId.getOrPut(key) {
                        val fresh = UUID.randomUUID().toString()
                        newCustomTypes += CardioCustomActivityType(id = fresh, name = customName)
                        knownCustomIds += fresh
                        fresh
                    }
                    val label = cell(colDisplay).ifEmpty { customName }
                    CardioActivitySnapshot(
                        builtin = null,
                        customTypeId = id,
                        customName = customName,
                        displayLabel = label,
                    )
                }
                else -> {
                    errors += "Line $n: set builtin, custom_type_id, or custom_activity_name"
                    return@forEachIndexed
                }
            }

            val modality = if (cell(colModality).isNotEmpty()) {
                try {
                    CardioModality.valueOf(cell(colModality).uppercase(Locale.US))
                } catch (_: IllegalArgumentException) {
                    errors += "Line $n: modality must be OUTDOOR or INDOOR_TREADMILL"
                    return@forEachIndexed
                }
            } else {
                CardioModality.OUTDOOR
            }

            if (modality == CardioModality.INDOOR_TREADMILL) {
                val b = activity.builtin
                if (b != null && !b.supportsTreadmillModality()) {
                    errors += "Line $n: ${b.name} cannot use INDOOR_TREADMILL in CSV"
                    return@forEachIndexed
                }
            }

            val distRaw = cell(colDistM)
            val distanceMetersParsed: Double? = if (distRaw.isNotEmpty()) {
                val d = distRaw.toDoubleOrNull()
                if (d == null) {
                    errors += "Line $n: distance_m must be a number"
                    return@forEachIndexed
                }
                d
            } else {
                null
            }

            var treadmill: CardioTreadmillParams? = null
            if (modality == CardioModality.INDOOR_TREADMILL) {
                val b = activity.builtin
                if (b != null && b.supportsTreadmillModality()) {
                    val speedStr = cell(colSpeed)
                    if (b == CardioBuiltinActivity.SPRINT && speedStr.isEmpty()) {
                        treadmill = null
                    } else {
                        val speed = speedStr.toDoubleOrNull()
                        if (speed == null || speed <= 0) {
                            errors += "Line $n: speed required for indoor ${b.name} (omit speed only for SPRINT)"
                            return@forEachIndexed
                        }
                        val unitStr = cell(colSpeedUnit).uppercase(Locale.US).ifEmpty { "MPH" }
                        val unit = try {
                            CardioSpeedUnit.valueOf(unitStr)
                        } catch (_: IllegalArgumentException) {
                            errors += "Line $n: speed_unit must be MPH or KMH"
                            return@forEachIndexed
                        }
                        val inc = cell(colIncline).toDoubleOrNull() ?: 0.0
                        var packKg: Double? = null
                        if (b == CardioBuiltinActivity.RUCK && cell(colPackKg).isNotEmpty()) {
                            packKg = cell(colPackKg).toDoubleOrNull()
                            if (packKg == null) {
                                errors += "Line $n: pack_kg must be a number"
                                return@forEachIndexed
                            }
                        }
                        treadmill = CardioTreadmillParams(
                            speed = speed,
                            speedUnit = unit,
                            inclinePercent = inc,
                            distanceMeters = distanceMetersParsed,
                            loadKg = packKg,
                        )
                    }
                }
            }

            val ruckOutdoor: Double? = if (modality == CardioModality.OUTDOOR &&
                activity.builtin == CardioBuiltinActivity.RUCK &&
                cell(colRuckOutdoor).isNotEmpty()
            ) {
                val v = cell(colRuckOutdoor).toDoubleOrNull()
                if (v == null) {
                    errors += "Line $n: ruck_load_kg must be a number"
                    return@forEachIndexed
                }
                v
            } else {
                null
            }

            val kcal: Double? = if (cell(colKcal).isNotEmpty()) {
                val v = cell(colKcal).toDoubleOrNull()
                if (v == null) {
                    errors += "Line $n: estimated_kcal must be a number"
                    return@forEachIndexed
                }
                v
            } else {
                null
            }

            val start: Long? = if (cell(colStart).isNotEmpty()) {
                val v = cell(colStart).toLongOrNull()
                if (v == null) {
                    errors += "Line $n: start_epoch_seconds must be an integer"
                    return@forEachIndexed
                }
                v
            } else {
                null
            }
            val end: Long? = if (cell(colEnd).isNotEmpty()) {
                val v = cell(colEnd).toLongOrNull()
                if (v == null) {
                    errors += "Line $n: end_epoch_seconds must be an integer"
                    return@forEachIndexed
                }
                v
            } else {
                null
            }

            val session = CardioSession(
                activity = activity,
                modality = modality,
                treadmill = treadmill,
                durationMinutes = dur,
                distanceMeters = distanceMetersParsed,
                estimatedKcal = kcal,
                source = CardioSessionSource.MANUAL,
                startEpochSeconds = start,
                endEpochSeconds = end,
                ruckLoadKg = ruckOutdoor,
            )
            sessionsByDate.getOrPut(date) { mutableListOf() }.add(session)
        }

        if (errors.isNotEmpty()) {
            return null to errors.distinct()
        }
        if (sessionsByDate.isEmpty()) {
            return null to listOf("No data rows after header")
        }

        val dayLogs = sessionsByDate.map { (d, list) ->
            CardioDayLog(date = d, sessions = list)
        }.sortedBy { it.date }

        return ErvCardioHistoryImportEnvelope(
            ervCardioHistoryImportVersion = 1,
            customActivityTypes = newCustomTypes.distinctBy { it.id },
            dayLogs = dayLogs,
        ) to emptyList()
    }

    private fun splitCsvLine(line: String): List<String> =
        line.split(',').map { it.trim().removeSurrounding("\"", "\"") }
}
