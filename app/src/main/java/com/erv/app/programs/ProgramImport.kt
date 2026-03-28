package com.erv.app.programs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Canonical JSON envelope for coach/AI-authored programs (Settings import can reuse the same shape later).
 *
 * Example:
 * ```json
 * {
 *   "ervImportVersion": 1,
 *   "programs": [ { "name": "My block", "weeklySchedule": [ ... ] } ],
 *   "activeProgramId": "optional-uuid"
 * }
 * ```
 */
@Serializable
data class ProgramImportEnvelope(
    val ervImportVersion: Int = 1,
    val programs: List<FitnessProgram> = emptyList(),
    val activeProgramId: String? = null
)

object ProgramImport {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun parse(text: String): Pair<ProgramImportEnvelope?, List<String>> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null to listOf("Empty file")
        return try {
            val element = json.parseToJsonElement(trimmed)
            val envelope = element.toEnvelope()
            val errs = validate(envelope)
            if (errs.isNotEmpty()) null to errs else envelope to emptyList()
        } catch (e: Exception) {
            null to listOf("Invalid JSON: ${e.message ?: "parse error"}")
        }
    }

    private fun JsonElement.toEnvelope(): ProgramImportEnvelope = when (this) {
        is JsonObject -> {
            when {
                this.containsKey("programs") || this.containsKey("ervImportVersion") ->
                    json.decodeFromJsonElement(ProgramImportEnvelope.serializer(), this)
                looksLikeProgram(this) -> ProgramImportEnvelope(programs = listOf(json.decodeFromJsonElement(FitnessProgram.serializer(), this)))
                else -> ProgramImportEnvelope()
            }
        }
        is JsonArray -> {
            val programs = this.mapNotNull { el ->
                try {
                    json.decodeFromJsonElement(FitnessProgram.serializer(), el)
                } catch (_: Exception) {
                    null
                }
            }
            ProgramImportEnvelope(programs = programs)
        }
        else -> ProgramImportEnvelope()
    }

    private fun looksLikeProgram(obj: JsonObject): Boolean =
        obj.containsKey("name") && (obj.containsKey("weeklySchedule") || obj.containsKey("id"))

    fun validate(envelope: ProgramImportEnvelope): List<String> {
        val errors = mutableListOf<String>()
        if (envelope.programs.isEmpty()) {
            errors += "At least one program is required"
        }
        envelope.programs.forEachIndexed { i, p ->
            if (p.name.isBlank()) errors += "Program #${i + 1}: name is required"
            p.weeklySchedule.forEach { day ->
                if (day.dayOfWeek !in 1..7) {
                    errors += "Program \"${p.name}\": dayOfWeek must be 1–7 (ISO), got ${day.dayOfWeek}"
                }
            }
        }
        val active = envelope.activeProgramId
        if (active != null && envelope.programs.none { it.id == active }) {
            errors += "activeProgramId does not match any imported program id"
        }
        return errors.distinct()
    }
}
