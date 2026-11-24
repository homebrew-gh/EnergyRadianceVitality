package com.erv.app.weighttraining

import com.erv.app.data.BodyWeightUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class WeightLoadUnitsTest {

    private lateinit var savedLocale: Locale

    @Before
    fun saveLocale() {
        savedLocale = Locale.getDefault()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(savedLocale)
    }

    @Test
    fun poundRoundTrip_showsWhatWasTyped_forEveryQuarterPoundUpTo1000() {
        // 0.25 lb steps cover 1.25 and 2.5 lb plates and microplates.
        for (quarters in 1..4000) {
            val lb = quarters / 4.0
            val typed = formatDecimalForField(lb)
            val kg = parseWeightInputToKg(typed, BodyWeightUnit.LB)!!
            assertEquals("typed $typed lb", typed, formatWeightLoadNumber(kg, BodyWeightUnit.LB))
        }
    }

    @Test
    fun kilogramRoundTrip_keepsQuarterKilos() {
        for (quarters in 1..1600) {
            val typed = formatDecimalForField(quarters / 4.0)
            val kg = parseWeightInputToKg(typed, BodyWeightUnit.KG)!!
            assertEquals(typed, formatWeightLoadNumber(kg, BodyWeightUnit.KG))
        }
    }

    @Test
    fun wholePoundsHaveNoTrailingDecimal() {
        // These redisplayed as "145.0" etc. before.
        for (lb in listOf(9, 145, 175, 205, 215, 305, 325)) {
            val kg = parseWeightInputToKg(lb.toString(), BodyWeightUnit.LB)!!
            assertEquals(lb.toString(), formatWeightLoadNumber(kg, BodyWeightUnit.LB))
        }
    }

    @Test
    fun formatting_ignoresLocaleDecimalComma() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("137.5", formatDecimalForField(137.5))
        assertEquals("7.5", formatRpeFieldForSets(7.5))
        assertEquals("8", formatRpeFieldForSets(8.0))
    }

    @Test
    fun parseDecimalInput_acceptsCommaAndPartialInput() {
        assertEquals(2.5, parseDecimalInput("2,5")!!, 1e-9)
        assertEquals(2.0, parseDecimalInput("2.")!!, 1e-9)
        assertEquals(0.5, parseDecimalInput(".5")!!, 1e-9)
        assertNull(parseDecimalInput(""))
        assertNull(parseDecimalInput("."))
        assertNull(parseDecimalInput("NaN"))
    }

    @Test
    fun numericTextMatches_keepsInProgressDecimals() {
        // The draft the athlete is typing stays while it equals the stored value.
        assertTrue(numericTextMatches("2.", "2"))
        assertTrue(numericTextMatches("2.50", "2.5"))
        assertTrue(numericTextMatches("1.25", "1.25"))
        assertTrue(numericTextMatches("2,5", "2.5"))
        assertTrue(numericTextMatches(".", ""))
        assertTrue(numericTextMatches("", ""))
        // A real change from elsewhere replaces the draft.
        assertFalse(numericTextMatches("1.25", "1.2"))
        assertFalse(numericTextMatches("135", "140"))
        assertFalse(numericTextMatches("", "135"))
        assertFalse(numericTextMatches("135", ""))
    }

    @Test
    fun typingTwoAndAHalf_keystrokeByKeystroke() {
        // Simulates the field: each keystroke is parsed into kg and the field shows the draft
        // only while it matches the stored text.
        var draft = ""
        var storedKg: Double? = null
        for (text in listOf("2", "2.", "2.5")) {
            draft = text
            storedKg = parseWeightInputToKg(text, BodyWeightUnit.LB)
            val storedText = storedKg?.let { formatWeightLoadNumber(it, BodyWeightUnit.LB) }.orEmpty()
            val shown = if (numericTextMatches(draft, storedText)) draft else storedText
            assertEquals(text, shown)
        }
        assertEquals("2.5", formatWeightLoadNumber(storedKg!!, BodyWeightUnit.LB))
    }
}
