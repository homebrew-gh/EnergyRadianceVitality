package com.erv.app.dataexport

import com.erv.app.cardio.CardioActivitySnapshot
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioDayLog
import com.erv.app.cardio.CardioGpsPoint
import com.erv.app.cardio.CardioGpsTrack
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioSession
import com.erv.app.data.LaunchPadTileId
import com.erv.app.data.ThemeMode
import com.erv.app.fasting.FastingLibraryState
import com.erv.app.fasting.FastingSession
import com.erv.app.fasting.FastingStatus
import com.erv.app.fasting.IntermittentFastingPlan
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErvAppDataExportTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun fullBackupBundle_roundTripsFastingAppPreferencesAndCardioGps() {
        val cardio = CardioLibraryState(
            logs = listOf(
                CardioDayLog(
                    date = "2025-06-01",
                    sessions = listOf(
                        CardioSession(
                            activity = CardioActivitySnapshot(
                                builtin = CardioBuiltinActivity.RUN,
                                displayLabel = "Run",
                            ),
                            durationMinutes = 30,
                            gpsTrack = CardioGpsTrack(
                                points = listOf(
                                    CardioGpsPoint(lat = 40.0, lon = -105.0, epochSeconds = 1L),
                                    CardioGpsPoint(lat = 40.01, lon = -105.01, epochSeconds = 2L),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val fasting = FastingLibraryState(
            activeSession = FastingSession(
                targetDays = 2,
                startedAtEpochSeconds = 100L,
                targetEndEpochSeconds = 200L,
                status = FastingStatus.ACTIVE,
            ),
            history = emptyList(),
            intermittentPlan = IntermittentFastingPlan(protocolLabel = "18:6"),
        )
        val appPreferences = AppPreferencesExportV1(
            themeMode = ThemeMode.DARK.name,
            launchPadTileOrder = listOf(LaunchPadTileId.FASTING, LaunchPadTileId.CARDIO),
            launchPadHiddenTiles = listOf(LaunchPadTileId.LIGHT_THERAPY),
            firstRunSetupCompleted = true,
        )

        val bundle = ErvAppDataExport.buildBundle(
            category = DataExportCategory.ALL,
            selection = ExportDateSelection.AllTime,
            weight = com.erv.app.weighttraining.WeightLibraryState(),
            cardio = cardio,
            stretching = com.erv.app.stretching.StretchLibraryState(),
            heatCold = com.erv.app.heatcold.HeatColdLibraryState(),
            light = com.erv.app.lighttherapy.LightLibraryState(),
            supplements = com.erv.app.supplements.SupplementLibraryState(),
            programs = com.erv.app.programs.ProgramsLibraryState(),
            unifiedRoutines = com.erv.app.unifiedroutines.UnifiedRoutineLibraryState(),
            bodyTracker = BodyTrackerExportV1(libraryState = com.erv.app.bodytracker.BodyTrackerLibraryState()),
            reminders = com.erv.app.reminders.RoutineReminderState(),
            gymMembership = false,
            ownedEquipment = emptyList(),
            goals = emptyList(),
            savedBluetoothDevices = emptyList(),
            localProfile = LocalProfileExportV1(),
            fasting = fasting,
            appPreferences = appPreferences,
        )

        val encoded = ErvAppDataExport.toJsonString(bundle)
        assertTrue(encoded.contains("\"fasting\""))
        assertTrue(encoded.contains("\"appPreferences\""))
        assertTrue(encoded.contains("\"gpsTrack\""))
        assertTrue(encoded.contains("\"nsec\"").not())
        assertTrue(encoded.contains("\"relayUrls\"").not())

        val decoded = json.decodeFromString(ErvAppDataExportV1.serializer(), encoded)
        assertNotNull(decoded.fasting)
        assertEquals("18:6", decoded.fasting?.intermittentPlan?.protocolLabel)
        assertNotNull(decoded.appPreferences)
        assertEquals(ThemeMode.DARK.name, decoded.appPreferences?.themeMode)
        assertEquals(1, ErvAppDataExport.cardioGpsTrackSessionCount(decoded.cardio!!))
    }

    @Test
    fun legacyBackupWithoutNewSections_stillDecodes() {
        val legacyJson = """
            {
              "ervAppDataExportVersion": 1,
              "exportedAtEpochSeconds": 1,
              "dateRangeLabel": "all",
              "weightTraining": { "exercises": [], "routines": [], "logs": [] }
            }
        """.trimIndent()
        val decoded = json.decodeFromString(ErvAppDataExportV1.serializer(), legacyJson)
        assertNull(decoded.fasting)
        assertNull(decoded.appPreferences)
    }
}
