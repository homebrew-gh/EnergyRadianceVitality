package com.erv.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportExportDocAssetsTest {

    @Test
    fun cardioReferenceKeysResolveToExpectedAssets() {
        assertEquals(
            "import_export/cardio_training_import_ai_guide.md",
            ImportExportDocAssets.requirePathForKey(ImportExportDocAssets.KEY_CARDIO_AI)
        )
        assertEquals(
            "import_export/cardio_training_import_csv_guide.md",
            ImportExportDocAssets.requirePathForKey(ImportExportDocAssets.KEY_CARDIO_CSV)
        )
        assertEquals(
            "import_export/cardio_training_nostr_events_reference.md",
            ImportExportDocAssets.requirePathForKey(ImportExportDocAssets.KEY_CARDIO_NOSTR)
        )
    }

    @Test
    fun programReferenceKeysResolveToExpectedAssets() {
        assertEquals(
            "import_export/programs_import_ai_guide.md",
            ImportExportDocAssets.requirePathForKey(ImportExportDocAssets.KEY_PROGRAM_AI)
        )
        assertTrue(ImportExportDocAssets.programReferenceKeys.contains(ImportExportDocAssets.KEY_WEIGHT_BUILTIN))
    }

    @Test
    fun everySharedReferenceKeyHasConcreteTitleAndPath() {
        val sharedKeys = buildList {
            addAll(ImportExportDocAssets.weightReferenceKeys)
            addAll(ImportExportDocAssets.cardioReferenceKeys)
            addAll(ImportExportDocAssets.programReferenceKeys)
        }
        assertTrue(sharedKeys.isNotEmpty())
        sharedKeys.forEach { key ->
            assertTrue(ImportExportDocAssets.requirePathForKey(key).startsWith("import_export/"))
            assertNotEquals("Reference", ImportExportDocAssets.titleForKey(key))
        }
    }
}
