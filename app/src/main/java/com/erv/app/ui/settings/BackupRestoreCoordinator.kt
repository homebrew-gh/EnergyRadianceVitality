package com.erv.app.ui.settings

import android.content.Context
import com.erv.app.bodytracker.BodyTrackerRepository
import com.erv.app.data.UserPreferences
import com.erv.app.dataexport.BodyTrackerExportV1
import com.erv.app.dataexport.BodyTrackerPhotoExportV1
import com.erv.app.dataexport.ErvAppDataExportV1
import com.erv.app.nostr.CurrentRelayDataSync
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.KeyManager
import com.erv.app.nostr.RelayPool
import com.erv.app.heatcold.HeatColdRepository
import com.erv.app.lighttherapy.LightTherapyRepository
import com.erv.app.programs.ProgramRepository
import com.erv.app.reminders.RoutineReminderRepository
import com.erv.app.stretching.StretchingRepository
import com.erv.app.supplements.SupplementRepository
import com.erv.app.unifiedroutines.UnifiedRoutineRepository
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.cardio.CardioRepository
import java.io.File
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

data class BackupPreviewSection(
    val title: String,
    val summary: String,
)

data class BackupRestorePreview(
    val bundle: ErvAppDataExportV1,
    val sections: List<BackupPreviewSection>,
)

class BackupRestoreCoordinator(
    private val appContext: Context,
    private val userPreferences: UserPreferences,
    private val keyManager: KeyManager,
    private val weightRepository: WeightRepository,
    private val cardioRepository: CardioRepository,
    private val stretchingRepository: StretchingRepository,
    private val heatColdRepository: HeatColdRepository,
    private val lightTherapyRepository: LightTherapyRepository,
    private val supplementRepository: SupplementRepository,
    private val programRepository: ProgramRepository,
    private val unifiedRoutineRepository: UnifiedRoutineRepository,
    private val bodyTrackerRepository: BodyTrackerRepository,
    private val reminderRepository: RoutineReminderRepository,
    private val relayPool: RelayPool?,
    private val signer: EventSigner?,
) {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun previewBackupText(text: String): Result<BackupRestorePreview> = runCatching {
        val bundle = json.decodeFromString(ErvAppDataExportV1.serializer(), text.trim())
        require(bundle.ervAppDataExportVersion == 1) {
            "Unsupported ERV backup version: ${bundle.ervAppDataExportVersion}"
        }
        val sections = buildPreviewSections(bundle)
        require(sections.isNotEmpty()) {
            "This ERV backup file does not contain any restorable sections."
        }
        BackupRestorePreview(bundle = bundle, sections = sections)
    }

    suspend fun restore(preview: BackupRestorePreview): String = withContext(Dispatchers.IO) {
        val bundle = preview.bundle
        val decodedPhotos = bundle.bodyTracker?.photos?.associate { photo ->
            photo.photoId to decodePhotoBytes(photo)
        }.orEmpty()

        bundle.weightTraining?.let { weightRepository.replaceAll(it) }
        bundle.cardio?.let { cardioRepository.replaceAll(it) }
        bundle.stretching?.let { stretchingRepository.replaceAll(it) }
        bundle.heatCold?.let { heatColdRepository.replaceAll(it) }
        bundle.lightTherapy?.let { lightTherapyRepository.replaceAll(it) }
        bundle.supplements?.let { supplementRepository.replaceAll(it) }
        bundle.programs?.let {
            programRepository.replaceAll(it)
            userPreferences.clearProgramLaunchTargets()
        }
        bundle.unifiedRoutines?.let { unifiedRoutineRepository.replaceAll(it) }
        bundle.bodyTracker?.let { restoreBodyTracker(it, decodedPhotos) }
        bundle.reminders?.let {
            reminderRepository.replaceAll(it)
            reminderRepository.restoreAllSchedules()
        }
        bundle.fitnessEquipment?.let {
            userPreferences.setGymMembership(it.gymMembership)
            userPreferences.setOwnedEquipment(it.equipment)
        }
        bundle.personalData?.let {
            userPreferences.setGoals(it.goals)
            userPreferences.setSavedBleDevices(it.savedBluetoothDevices)
            userPreferences.setLocalProfileDisplayName(it.localProfile.displayName)
            userPreferences.setLocalProfilePictureUrl(it.localProfile.pictureUrl)
            userPreferences.setLocalProfileBio(it.localProfile.bio)
        }

        val sectionsRestored = preview.sections.size
        val syncMessage = resyncRelaysIfPossible()
        buildString {
            append("Restored $sectionsRestored section(s).")
            if (syncMessage != null) {
                append(' ')
                append(syncMessage)
            }
        }
    }

    private fun buildPreviewSections(bundle: ErvAppDataExportV1): List<BackupPreviewSection> {
        val sections = mutableListOf<BackupPreviewSection>()
        bundle.weightTraining?.let {
            sections += BackupPreviewSection(
                title = "Weight training",
                summary = "${it.logs.size} log day(s), ${it.routines.size} routine(s), ${it.exercises.size} exercise(s)"
            )
        }
        bundle.cardio?.let {
            sections += BackupPreviewSection(
                title = "Cardio",
                summary = "${it.logs.size} log day(s), ${it.routines.size} routine(s), ${it.quickLaunches.size} quick launch(es)"
            )
        }
        bundle.stretching?.let {
            sections += BackupPreviewSection(
                title = "Stretching",
                summary = "${it.logs.size} log day(s), ${it.routines.size} routine(s)"
            )
        }
        bundle.heatCold?.let {
            sections += BackupPreviewSection(
                title = "Heat and cold",
                summary = "${it.saunaLogs.size} sauna log(s), ${it.coldLogs.size} cold log(s)"
            )
        }
        bundle.lightTherapy?.let {
            sections += BackupPreviewSection(
                title = "Light therapy",
                summary = "${it.logs.size} log day(s), ${it.routines.size} routine(s), ${it.devices.size} device(s)"
            )
        }
        bundle.supplements?.let {
            sections += BackupPreviewSection(
                title = "Supplements",
                summary = "${it.logs.size} log day(s), ${it.routines.size} routine(s), ${it.supplements.size} supplement(s)"
            )
        }
        bundle.programs?.let {
            sections += BackupPreviewSection(
                title = "Programs",
                summary = "${it.programs.size} program(s), active=${it.activeProgramId ?: "none"}"
            )
        }
        bundle.unifiedRoutines?.let {
            sections += BackupPreviewSection(
                title = "Unified routines",
                summary = "${it.routines.size} routine(s), ${it.sessions.size} session(s)"
            )
        }
        bundle.bodyTracker?.let {
            sections += BackupPreviewSection(
                title = "Body tracker",
                summary = "${it.libraryState.logs.size} log day(s), ${it.photos.size} photo file(s)"
            )
        }
        bundle.reminders?.let {
            sections += BackupPreviewSection(
                title = "Reminders",
                summary = "${it.reminders.size} reminder schedule(s)"
            )
        }
        bundle.fitnessEquipment?.let {
            sections += BackupPreviewSection(
                title = "Equipment profile",
                summary = "${it.equipment.size} owned item(s), gym membership=${if (it.gymMembership) "yes" else "no"}"
            )
        }
        bundle.personalData?.let {
            val profileBits = listOf(
                bundle.personalData.localProfile.displayName,
                bundle.personalData.localProfile.pictureUrl,
                bundle.personalData.localProfile.bio,
            ).count { it.isNotBlank() }
            sections += BackupPreviewSection(
                title = "Personal data",
                summary = "${bundle.personalData.goals.size} goal(s), ${bundle.personalData.savedBluetoothDevices.size} saved device(s), $profileBits local profile field(s)"
            )
        }
        return sections
    }

    private suspend fun restoreBodyTracker(
        export: BodyTrackerExportV1,
        decodedPhotos: Map<String, ByteArray>,
    ) {
        bodyTrackerRepository.clearAllData()
        decodedPhotos.forEach { (photoId, bytes) ->
            val file = bodyTrackerRepository.photoFile(photoId)
            writeBytes(file, bytes)
        }
        bodyTrackerRepository.replaceAll(export.libraryState)
    }

    private fun decodePhotoBytes(photo: BodyTrackerPhotoExportV1): ByteArray =
        try {
            Base64.getDecoder().decode(photo.base64Data)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Could not decode body tracker photo ${photo.fileName}")
        }

    private fun writeBytes(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        file.outputStream().use { output -> output.write(bytes) }
    }

    private suspend fun resyncRelaysIfPossible(): String? {
        val activeRelayPool = relayPool ?: return null
        val activeSigner = signer ?: return null
        val relayUrls = keyManager.relayUrlsForKind30078Publish()
        if (relayUrls.isEmpty()) return null
        val entries = CurrentRelayDataSync.buildCurrentEntries(
            userPreferences = userPreferences,
            weightRepository = weightRepository,
            cardioRepository = cardioRepository,
            stretchingRepository = stretchingRepository,
            heatColdRepository = heatColdRepository,
            lightTherapyRepository = lightTherapyRepository,
            supplementRepository = supplementRepository,
            programRepository = programRepository,
            bodyTrackerRepository = bodyTrackerRepository,
        )
        val drain = CurrentRelayDataSync.forceResync(
            appContext = appContext,
            relayPool = activeRelayPool,
            signer = activeSigner,
            dataRelayUrls = relayUrls,
            localEntries = entries,
        )
        return buildString {
            append("Relay resync queued for ${entries.size} payload(s)")
            if (drain.publishedOk > 0 || drain.publishedFail > 0) {
                append(" - sent ${drain.publishedOk} now")
                if (drain.publishedFail > 0) {
                    append(", ${drain.publishedFail} will retry")
                }
            }
            if (drain.remaining > 0) {
                append(" - ${drain.remaining} still queued")
            }
            append('.')
        }
    }
}
