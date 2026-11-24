package com.erv.app.workouts

import com.erv.app.weighttraining.WeightSet
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The phone side of `shared/test-cases/workout_examples.json`. The web builder runs the same file
 * in `workoutExamples.test.ts`, so a field one side writes and the other drops fails here or there.
 */
class WorkoutSharedExamplesTest {

    private val shared: JsonObject by lazy {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val file = File(dir, "shared/test-cases/workout_examples.json")
            if (file.isFile) return@lazy Json.parseToJsonElement(file.readText()).jsonObject
            dir = dir.parentFile
        }
        error("shared/test-cases/workout_examples.json not found")
    }

    private val examples: JsonArray get() = shared.getValue("workouts").jsonArray

    private fun stringList(key: String): List<String> =
        shared.getValue(key).jsonArray.map { it.jsonPrimitive.content }

    /**
     * Every value in [expected] is in [actual] with the same meaning. The phone also writes its
     * defaults (empty lists, nulls, rounds 1), so extra keys in [actual] are fine, but a key the
     * phone drops or renames is not.
     */
    private fun assertKeeps(expected: JsonElement, actual: JsonElement?, path: String) {
        when (expected) {
            is JsonNull -> if (actual != null && actual !is JsonNull) fail("$path: expected null, got $actual")
            is JsonObject -> {
                if (actual !is JsonObject) fail("$path: expected an object, got $actual")
                val obj = actual as JsonObject
                expected.forEach { (key, value) -> assertKeeps(value, obj[key], "$path.$key") }
            }
            is JsonArray -> {
                if (actual !is JsonArray) fail("$path: expected a list, got $actual")
                val list = actual as JsonArray
                assertEquals("$path: list size", expected.size, list.size)
                expected.forEachIndexed { index, value -> assertKeeps(value, list[index], "$path[$index]") }
            }
            is JsonPrimitive -> {
                if (actual !is JsonPrimitive || actual is JsonNull) fail("$path: missing, expected $expected")
                val value = actual as JsonPrimitive
                val expectedNumber = if (expected.isString) null else expected.doubleOrNull
                if (expectedNumber != null) {
                    assertEquals("$path", expectedNumber, value.doubleOrNull ?: Double.NaN, 1e-9)
                } else {
                    assertEquals("$path", expected.content, value.content)
                }
            }
        }
    }

    @Test
    fun every_example_survives_a_relay_round_trip() {
        assertTrue(examples.isNotEmpty())
        examples.forEachIndexed { index, example ->
            val workout = WorkoutSync.json.decodeFromJsonElement(Workout.serializer(), example)
            val written = WorkoutSync.json.encodeToJsonElement(Workout.serializer(), workout)
            assertKeeps(example, written, "workouts[$index]")
            assertEquals(workout, WorkoutSync.json.decodeFromJsonElement(Workout.serializer(), written))
        }
    }

    @Test
    fun the_file_imports_as_a_workout_envelope() {
        val envelope = decodeWorkoutImportEnvelope(shared.toString()).getOrThrow()
        assertEquals(1, envelope.ervWorkoutImportVersion)
        val fromRelay = examples.map { WorkoutSync.json.decodeFromJsonElement(Workout.serializer(), it) }
        assertEquals(fromRelay, envelope.workouts)
        // The import file leaves defaults out; reading it back must still give the same workouts.
        val reencoded = decodeWorkoutImportEnvelope(encodeWorkoutImportEnvelope(envelope)).getOrThrow()
        assertEquals(fromRelay, reencoded.workouts)
    }

    @Test
    fun segment_kinds_match_the_shared_list() {
        val descriptor = WorkoutSegmentKind.serializer().descriptor
        val phoneKinds = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }.sorted()
        assertEquals((stringList("segmentKinds") + stringList("reservedSegmentKinds")).sorted(), phoneKinds)
    }

    @Test
    fun set_targets_match_the_shared_cases() {
        shared.getValue("setTargets").jsonArray.forEach { element ->
            val case = element.jsonObject
            val name = case.getValue("name").jsonPrimitive.content
            val set = WorkoutSync.json.decodeFromJsonElement(WeightSet.serializer(), case.getValue("set"))
            val seeded = set.seedForLiveWorkout()
            assertEquals("$name reps", case.getValue("reps").jsonPrimitive.intOrNull, seeded.targetReps)
            assertEquals("$name weight", case.getValue("weightKg").jsonPrimitive.doubleOrNull, seeded.targetWeightKg)
            assertEquals(
                "$name duration",
                case.getValue("durationSeconds").jsonPrimitive.intOrNull,
                seeded.targetDurationSeconds,
            )
        }
    }
}
