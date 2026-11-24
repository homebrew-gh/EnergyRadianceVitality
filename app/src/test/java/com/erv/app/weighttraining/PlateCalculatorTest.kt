package com.erv.app.weighttraining

import com.erv.app.data.BarbellOwnership
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.EquipmentCatalogKind
import com.erv.app.data.OwnedEquipmentItem
import com.erv.app.data.PlateOwnership
import com.erv.app.data.PlatePairEntry
import com.erv.app.data.StandardBarbellType
import com.erv.app.data.lbToKg
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateCalculatorTest {

    /** `shared/test-cases/plate_calculator.json`, found by walking up from the test's working folder. */
    private fun sharedCases(): JsonArray {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val file = File(dir, "shared/test-cases/plate_calculator.json")
            if (file.isFile) return Json.parseToJsonElement(file.readText()).jsonObject.getValue("cases").jsonArray
            dir = dir.parentFile
        }
        error("shared/test-cases/plate_calculator.json not found")
    }

    private fun JsonElement.toOption(): PlateLoadOption? =
        if (this is JsonNull) null else PlateLoadOption(
            total = jsonObject.getValue("total").jsonPrimitive.double,
            plates = jsonObject.getValue("plates").jsonArray.map { it.jsonPrimitive.double },
        )

    @Test
    fun sharedCasesMatchTheBuilder() {
        val cases = sharedCases()
        assertTrue(cases.isNotEmpty())
        for (element in cases) {
            val c = element as JsonObject
            val name = c.getValue("name").jsonPrimitive.content
            val result = calculatePlateLoad(
                target = c.getValue("target").jsonPrimitive.double,
                bar = c.getValue("bar").jsonPrimitive.double,
                plates = c.getValue("plates").jsonArray.map {
                    PlateStack(
                        it.jsonObject.getValue("weight").jsonPrimitive.double,
                        it.jsonObject.getValue("pairCount").jsonPrimitive.int,
                    )
                },
                loading = PlateLoadingStyle.valueOf(c.getValue("loading").jsonPrimitive.content),
            )
            val expected = PlateLoadResult(
                exact = c.getValue("exact").toOption(),
                below = c.getValue("below").toOption(),
                above = c.getValue("above").toOption(),
            )
            assertEquals(name, expected, result)
        }
    }

    private val equipment = listOf(
        OwnedEquipmentItem(
            id = "bar-oly",
            name = "Bar",
            catalogKind = EquipmentCatalogKind.BARBELL,
            barbell = BarbellOwnership(StandardBarbellType.OLYMPIC_MENS),
        ),
        OwnedEquipmentItem(
            id = "bar-ez",
            name = "EZ",
            catalogKind = EquipmentCatalogKind.BARBELL,
            barbell = BarbellOwnership(StandardBarbellType.EZ_CURL),
        ),
        OwnedEquipmentItem(
            id = "bar-dl",
            name = "Deadlift bar",
            catalogKind = EquipmentCatalogKind.BARBELL,
            barbell = BarbellOwnership(
                StandardBarbellType.CUSTOM,
                customWeightKg = lbToKg(60.0),
                customName = "Open deadlift bar",
            ),
        ),
        OwnedEquipmentItem(
            id = "plates-a",
            name = "Plates",
            catalogKind = EquipmentCatalogKind.PLATES,
            plates = PlateOwnership(pairs = listOf(PlatePairEntry(lbToKg(45.0), 2), PlatePairEntry(lbToKg(2.5), 1))),
        ),
        OwnedEquipmentItem(
            id = "plates-b",
            name = "More plates",
            catalogKind = EquipmentCatalogKind.PLATES,
            // Entered through the weight-training conversion (0.453592 kg per lb).
            plates = PlateOwnership(pairs = listOf(PlatePairEntry(poundsToKg(45.0), 1))),
        ),
    )

    @Test
    fun countsAnOlympicBarAs45PoundsAndKeepsCustomBarsExact() {
        val (bars, assumed) = plateCalculatorBars(equipment, BodyWeightUnit.LB)
        assertEquals(false, assumed)
        assertEquals(
            listOf("Olympic bar (45 lb)" to 45.0, "EZ curl bar (18 lb)" to 18.0, "Open deadlift bar (60 lb)" to 60.0),
            bars.map { it.label to it.weight },
        )
    }

    @Test
    fun mergesPlatesEnteredWithEitherPoundConversion() {
        assertEquals(
            PlateCalculatorPlates(listOf(PlateStack(45.0, 3), PlateStack(2.5, 1)), assumed = false),
            plateCalculatorPlates(equipment, BodyWeightUnit.LB),
        )
    }

    @Test
    fun assumesAStandardBarAndPlatesWhenNoneAreEntered() {
        assertEquals(45.0, plateCalculatorBars(emptyList(), BodyWeightUnit.LB).bars.first().weight, 0.0)
        assertTrue(plateCalculatorPlates(emptyList(), BodyWeightUnit.LB).assumed)
    }

    @Test
    fun picksLoadingStyleAndBarPerExercise() {
        fun ex(name: String, equipment: WeightEquipment = WeightEquipment.BARBELL) =
            WeightExercise(id = name, name = name, muscleGroup = "legs", pushOrPull = WeightPushPull.PUSH, equipment = equipment)
        assertEquals(PlateLoadingStyle.BOTH_ENDS, ex("Bench Press").plateLoading())
        assertEquals(PlateLoadingStyle.ONE_END, ex("Landmine Press").plateLoading())
        assertEquals(PlateLoadingStyle.ONE_END, ex("T-Bar Row").plateLoading())
        assertEquals(PlateLoadingStyle.NONE, ex("Barbell Rollout").plateLoading())
        assertNull(ex("Goblet Squat", WeightEquipment.KETTLEBELL).plateLoading())

        val bars = plateCalculatorBars(equipment, BodyWeightUnit.LB).bars
        assertEquals("bar-ez", defaultBarForExercise("EZ-Bar Curl", bars)?.id)
        assertEquals("bar-oly", defaultBarForExercise("Deadlift", bars)?.id)
        assertEquals("bar-dl", defaultBarForExercise("Deadlift", bars, "bar-dl")?.id)
        assertEquals("bar-oly", defaultBarForExercise("Deadlift", bars, "sold-bar")?.id)
    }

    @Test
    fun formatsThePlateLine() {
        assertEquals("Per side: 45 · 25 · 2.5", formatPlateList(listOf(45.0, 25.0, 2.5), PlateLoadingStyle.BOTH_ENDS))
        assertEquals("On the end: 25", formatPlateList(listOf(25.0), PlateLoadingStyle.ONE_END))
        assertEquals("Empty bar", formatPlateList(emptyList(), PlateLoadingStyle.BOTH_ENDS))
    }
}
