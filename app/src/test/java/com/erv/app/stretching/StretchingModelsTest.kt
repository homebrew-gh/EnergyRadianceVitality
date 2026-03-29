package com.erv.app.stretching

import org.junit.Assert.assertEquals
import org.junit.Test

class StretchingModelsTest {

    @Test
    fun groupedByCategory_sortsCategoriesAndNamesAlphabetically() {
        val grouped = listOf(
            StretchCatalogEntry(id = "3", name = "Z Reach", category = "shoulders"),
            StretchCatalogEntry(id = "1", name = "B Fold", category = "legs"),
            StretchCatalogEntry(id = "2", name = "A Fold", category = "legs"),
            StretchCatalogEntry(id = "4", name = "Neck Tilt", category = "neck")
        ).groupedByCategory()

        assertEquals(listOf("legs", "neck", "shoulders"), grouped.map { it.first })
        assertEquals(listOf("A Fold", "B Fold"), grouped.first { it.first == "legs" }.second.map { it.name })
    }
}
