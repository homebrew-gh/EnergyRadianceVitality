package com.erv.app.supplements

/**
 * Best-effort mapping from DSLD [SupplementInfo.servingSize] text into [SupplementDosagePlan] fields.
 * Many label strings are ambiguous; callers should keep the original string in [SupplementDosagePlan.servingSize].
 */
data class NihServingDosageParse(
    val form: SupplementForm,
    val amount: Double,
    val unit: SupplementUnit,
)

fun parseNihServingSizeToDosage(servingSize: String?): NihServingDosageParse? {
    val s = servingSize?.trim()?.takeIf { it.isNotBlank() } ?: return null
    countServingRegex.find(s)?.let { m ->
        val amount = m.groupValues[1].toDoubleOrNull() ?: return@let
        if (amount <= 0) return@let
        return NihServingDosageParse(SupplementForm.CAPSULE, amount, SupplementUnit.MG)
    }
    massServingRegex.find(s)?.let { m ->
        val amount = m.groupValues[1].toDoubleOrNull() ?: return@let
        if (amount <= 0) return@let
        val unitRaw = m.groupValues[2].lowercase()
        val unit = when {
            unitRaw == "mcg" || unitRaw == "µg" || unitRaw == "μg" -> SupplementUnit.MCG
            unitRaw == "mg" -> SupplementUnit.MG
            unitRaw == "g" || unitRaw.startsWith("gram") -> SupplementUnit.G
            unitRaw == "iu" -> SupplementUnit.IU
            unitRaw == "ml" -> SupplementUnit.ML
            unitRaw == "drops" || unitRaw == "drop" -> SupplementUnit.DROPS
            else -> return@let
        }
        return NihServingDosageParse(SupplementForm.POWDER, amount, unit)
    }
    return null
}

/** Count-based servings: "2 capsules", "1 softgel", "3 tablets", etc. */
private val countServingRegex = Regex(
    """(?i)^\s*([\d.]+)\s*(?:""" +
        """capsules?|caps?|tablets?(?:\s*\(s\))?|softgels?|gelcaps?|caplets?|""" +
        """veg\.?\s*caps?|vegg?ie\s*caps?|chewables?|gummies?|lozenges?|wafers?""" +
        """)\b"""
)

/** Mass/volume servings: "500 mg", "3 g", "400 IU", "1 mL". Longer unit tokens first. */
private val massServingRegex = Regex(
    """(?i)^\s*([\d.]+)\s*(mcg|µg|μg|mg|iu|m[lL]|ml|grams?|g|drops?|drop)\b"""
)
