package com.erv.app.ui.settings

import android.content.Context
import android.net.Uri
import com.erv.app.bodytracker.BodyTrackerRepository
import com.erv.app.cardio.cardioBuiltinActivitiesForUserSelection
import com.erv.app.cardio.CardioImportOutcome
import com.erv.app.cardio.CardioRepository
import com.erv.app.cardio.CardioSync
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.UserPreferences
import com.erv.app.data.displayTitle
import com.erv.app.data.summaryLine
import com.erv.app.dataexport.DataExportCategory
import com.erv.app.dataexport.DataExportFormat
import com.erv.app.dataexport.ErvAppDataExport
import com.erv.app.dataexport.ExportDateSelection
import com.erv.app.dataexport.LocalProfileExportV1
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.KeyManager
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.RelayPublishOutbox
import com.erv.app.heatcold.HeatColdRepository
import com.erv.app.lighttherapy.LightTherapyRepository
import com.erv.app.programs.ProgramImportEnvelope
import com.erv.app.programs.ProgramRepository
import com.erv.app.programs.ProgramSync
import com.erv.app.programs.resolveProgramStrategyForDate
import com.erv.app.programs.strategySummaryForDate
import com.erv.app.reminders.RoutineReminderRepository
import com.erv.app.stretching.StretchCatalogLoader
import com.erv.app.stretching.StretchingRepository
import com.erv.app.supplements.SupplementRepository
import com.erv.app.unifiedroutines.UnifiedRoutineRepository
import com.erv.app.weighttraining.WeightImportOutcome
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.WeightSync
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlinx.coroutines.flow.first

data class PreparedExportFile(
    val bytes: ByteArray,
    val fileName: String,
)

class ImportExportCoordinator(
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
    suspend fun readText(uri: Uri): String? = withContext(Dispatchers.IO) {
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).readText()
        }
    }

    suspend fun buildExport(
        category: DataExportCategory,
        format: DataExportFormat,
        selection: ExportDateSelection,
    ): PreparedExportFile = withContext(Dispatchers.IO) {
        val weight = weightRepository.currentState()
        val cardio = cardioRepository.currentState()
        val stretching = stretchingRepository.currentState()
        val heatCold = heatColdRepository.currentState()
        val lightTherapy = lightTherapyRepository.currentState()
        val supplements = supplementRepository.currentState()
        val programs = programRepository.currentState()
        val unifiedRoutines = unifiedRoutineRepository.currentState()
        val bodyTracker = ErvAppDataExport.buildBodyTrackerExport(
            repository = bodyTrackerRepository,
            state = bodyTrackerRepository.currentState(),
            selection = selection,
        )
        val reminders = reminderRepository.currentState()
        val gymMembership = userPreferences.gymMembership.first()
        val equipment = userPreferences.ownedEquipment.first()
        val goals = userPreferences.goals.first()
        val savedBleDevices = userPreferences.savedBleDevices.first()
        val localProfile = LocalProfileExportV1(
            displayName = userPreferences.localProfileDisplayName.first(),
            pictureUrl = userPreferences.localProfilePictureUrl.first(),
            bio = userPreferences.localProfileBio.first(),
        )
        val bytes = when (format) {
            DataExportFormat.JSON -> {
                val bundle = ErvAppDataExport.buildBundle(
                    category = category,
                    selection = selection,
                    weight = weight,
                    cardio = cardio,
                    stretching = stretching,
                    heatCold = heatCold,
                    light = lightTherapy,
                    supplements = supplements,
                    programs = programs,
                    unifiedRoutines = unifiedRoutines,
                    bodyTracker = bodyTracker,
                    reminders = reminders,
                    gymMembership = gymMembership,
                    ownedEquipment = equipment,
                    goals = goals,
                    savedBluetoothDevices = savedBleDevices,
                    localProfile = localProfile,
                )
                ErvAppDataExport.toJsonString(bundle).toByteArray(StandardCharsets.UTF_8)
            }
            DataExportFormat.CSV -> {
                val text = when (category) {
                    DataExportCategory.WEIGHT_TRAINING ->
                        ErvAppDataExport.weightTrainingToCsv(
                            ErvAppDataExport.filterWeightState(weight, selection)
                        )
                    DataExportCategory.CARDIO ->
                        ErvAppDataExport.cardioToCsv(
                            ErvAppDataExport.filterCardioState(cardio, selection)
                        )
                    else -> ""
                }
                text.toByteArray(StandardCharsets.UTF_8)
            }
        }
        val stem = ErvAppDataExport.defaultExportFileStem(category, format)
        val extension = if (format == DataExportFormat.JSON) "json" else "csv"
        PreparedExportFile(bytes = bytes, fileName = "$stem.$extension")
    }

    suspend fun shareReferenceBundle(
        keys: List<String>,
        bundleTitle: String,
        fileName: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val text = buildCombinedImportReferenceMarkdown(appContext, keys, bundleTitle)
        val dir = File(appContext.cacheDir, "share").apply { mkdirs() }
        val out = File(dir, fileName)
        out.writeText(text, StandardCharsets.UTF_8)
        withContext(Dispatchers.Main) {
            shareMarkdownFromCache(appContext, out)
        }
    }

    suspend fun shareProgramsReferenceBundle(): Boolean = withContext(Dispatchers.IO) {
        val text = buildProgramsReferenceBundleMarkdown()
        val dir = File(appContext.cacheDir, "share").apply { mkdirs() }
        val out = File(dir, "programs_import_reference_and_context_for_ai.md")
        out.writeText(text, StandardCharsets.UTF_8)
        withContext(Dispatchers.Main) {
            shareMarkdownFromCache(appContext, out)
        }
    }

    suspend fun commitWeightImport(outcome: WeightImportOutcome.Success): String {
        weightRepository.commitImportedMerge(outcome)
        val baseMsg =
            "Imported ${outcome.sessionsImported} session(s); ${outcome.affectedDates.size} day(s)"
        val activeRelayPool = relayPool
        val activeSigner = signer
        if (activeRelayPool == null || activeSigner == null) {
            return "$baseMsg (not signed in - local only)"
        }
        val entries = WeightSync.weightImportOutboxEntries(
            weightRepository.currentState(),
            outcome.affectedDates,
        )
        val drain = withContext(Dispatchers.IO) {
            val outbox = RelayPublishOutbox.get(appContext)
            outbox.enqueueAllDigestsAware(appContext, entries)
            outbox.kickDrain(activeRelayPool, activeSigner, keyManager.relayUrlsForKind30078Publish())
        }
        return relayResultMessage(baseMsg, entries.size, drain)
    }

    suspend fun commitCardioImport(outcome: CardioImportOutcome.Success): String {
        cardioRepository.commitImportedCardioMerge(outcome)
        val baseMsg =
            "Imported ${outcome.sessionsImported} cardio session(s); ${outcome.affectedDates.size} day(s)"
        val activeRelayPool = relayPool
        val activeSigner = signer
        if (activeRelayPool == null || activeSigner == null) {
            return "$baseMsg (not signed in - local only)"
        }
        val entries = CardioSync.cardioImportOutboxEntries(
            cardioRepository.currentState(),
            outcome.affectedDates,
        )
        val drain = withContext(Dispatchers.IO) {
            val outbox = RelayPublishOutbox.get(appContext)
            outbox.enqueueAllDigestsAware(appContext, entries)
            outbox.kickDrain(activeRelayPool, activeSigner, keyManager.relayUrlsForKind30078Publish())
        }
        return relayResultMessage(baseMsg, entries.size, drain)
    }

    suspend fun commitProgramsImport(envelope: ProgramImportEnvelope): String {
        programRepository.mergeImportedPrograms(
            imported = envelope.programs,
            setActiveFromImport = envelope.activeProgramId,
            strategyFromImport = envelope.strategy,
        )
        val count = envelope.programs.size
        val activeNote = envelope.activeProgramId?.let { " Active program updated." } ?: ""
        val strategyNote = envelope.strategy?.let { " Program strategy updated." } ?: ""
        val activeRelayPool = relayPool
        val activeSigner = signer
        if (activeRelayPool == null || activeSigner == null) {
            return "Merged $count program(s).$activeNote$strategyNote"
        }
        ProgramSync.publishMaster(
            appContext = appContext,
            relayPool = activeRelayPool,
            signer = activeSigner,
            state = programRepository.currentState(),
            dataRelayUrls = keyManager.relayUrlsForKind30078Publish(),
        )
        return "Merged $count program(s).$activeNote Synced to relays."
    }

    private fun relayResultMessage(
        baseMsg: String,
        queuedCount: Int,
        drain: RelayPublishOutbox.KickDrainResult,
    ): String = buildString {
        append(baseMsg)
        append(". Queued $queuedCount relay upload(s)")
        if (drain.publishedOk > 0 || drain.publishedFail > 0) {
            append(" - sent ${drain.publishedOk} now")
            if (drain.publishedFail > 0) {
                append(", ${drain.publishedFail} will retry")
            }
        }
        if (drain.remaining > 0) {
            append(" - ${drain.remaining} still queued (auto-retry)")
        }
    }

    private suspend fun buildProgramsReferenceBundleMarkdown(): String {
        val weightState = weightRepository.currentState()
        val cardioState = cardioRepository.currentState()
        val stretchState = stretchingRepository.currentState()
        val programsState = programRepository.currentState()
        val unifiedRoutineState = unifiedRoutineRepository.currentState()
        val gymMembership = userPreferences.gymMembership.first()
        val ownedEquipment = userPreferences.ownedEquipment.first()
        val weightUnit = userPreferences.weightTrainingLoadUnit.first()
        val stretchCatalog = StretchCatalogLoader.load(appContext)
        val staticDocs = buildCombinedImportReferenceMarkdown(
            context = appContext,
            keys = ImportExportDocAssets.programReferenceKeys,
            bundleTitle = "Programs - Combined Import Reference"
        )
        val customExercises = weightState.exercises
            .filterNot { it.id.startsWith("erv-weight-exercise-") }
            .sortedBy { it.name.lowercase() }

        return buildString {
            appendLine(staticDocs.trimEnd())
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("# Programs Import Device Context")
            appendLine()
            appendLine(
                "This section is generated from the current device. Prefer these existing ids, routine names, " +
                    "and equipment constraints instead of inventing new ones."
            )
            appendLine()
            appendLine("## Planning Rules")
            appendLine()
            appendLine("- Prefer existing routine ids when they already match the requested workout.")
            appendLine("- Prefer built-in weight exercise ids from the bundled reference above.")
            appendLine("- Use only the listed cardio activity enum names for `cardioActivity`.")
            appendLine("- Reuse an existing program `id` only when updating that same program on re-import.")
            appendLine("- Program JSON can now include an optional strategy block (manual / repeat / rotation / challenge) in the import envelope.")
            appendLine("- `activeProgramId` changes the manual active program; `strategy` updates the higher-level program planner for date-based scheduling.")
            appendLine("- Activating ERV's built-in 75 Hard-style or 75 Soft-style templates in the app still auto-creates a 75-day challenge strategy from that activation date.")
            appendLine("- If the user has home equipment limits, do not assume missing machines or tools are available.")
            appendLine()

            appendMarkdownListSection(
                title = "Gym Access",
                items = listOf(if (gymMembership) "Commercial gym access: yes" else "Commercial gym access: no"),
                emptyMessage = "No gym access context saved."
            )

            appendMarkdownListSection(
                title = "Owned Equipment",
                items = ownedEquipment
                    .sortedBy { it.displayTitle(weightUnit).lowercase() }
                    .map { item ->
                        val detail = item.summaryLine(weightUnit)?.takeIf { it.isNotBlank() }
                        if (detail == null) "${item.displayTitle(weightUnit)}"
                        else "${item.displayTitle(weightUnit)} - $detail"
                    },
                emptyMessage = "No home or personal equipment saved."
            )

            appendMarkdownListSection(
                title = "Saved Weight Routines",
                items = weightState.routines
                    .sortedBy { it.name.lowercase() }
                    .map { routine -> "`weightRoutineId: ${routine.id}` - ${routine.name}" },
                emptyMessage = "No saved weight routines."
            )

            appendMarkdownListSection(
                title = "Saved Cardio Routines",
                items = cardioState.routines
                    .sortedBy { it.name.lowercase() }
                    .map { routine -> "`cardioRoutineId: ${routine.id}` - ${routine.name}" },
                emptyMessage = "No saved cardio routines."
            )

            appendMarkdownListSection(
                title = "Saved Stretch Routines",
                items = stretchState.routines
                    .sortedBy { it.name.lowercase() }
                    .map { routine -> "`stretchRoutineId: ${routine.id}` - ${routine.name}" },
                emptyMessage = "No saved stretch routines."
            )

            appendMarkdownListSection(
                title = "Saved Unified Workouts",
                items = unifiedRoutineState.routines
                    .sortedBy { it.name.lowercase() }
                    .map { routine -> "`unifiedRoutineId: ${routine.id}` - ${routine.name}" },
                emptyMessage = "No saved unified workouts."
            )

            appendMarkdownListSection(
                title = "Existing Custom Weight Exercise Ids",
                items = customExercises.map { exercise -> "`${exercise.id}` - ${exercise.name}" },
                emptyMessage = "No custom weight exercises. Use the bundled built-in weight exercise ids."
            )

            appendMarkdownListSection(
                title = "Built-In Stretch Catalog Ids",
                items = stretchCatalog
                    .sortedBy { it.name.lowercase() }
                    .map { entry -> "`${entry.id}` - ${entry.name}" },
                emptyMessage = "Could not load the built-in stretch catalog."
            )

            appendMarkdownListSection(
                title = "Allowed `cardioActivity` Enum Names",
                items = cardioBuiltinActivitiesForUserSelection().map { activity -> "`${activity.name}`" },
                emptyMessage = "No cardio activity enums available."
            )

            appendLine("## Existing Programs")
            appendLine()
            appendLine(
                programsState.activeProgramId?.let { activeId ->
                    "Current active program id: `$activeId`"
                } ?: "Current active program id: none"
            )
            appendLine("Current program strategy summary: ${programsState.strategySummaryForDate(LocalDate.now())}")
            programsState.resolveProgramStrategyForDate(LocalDate.now()).detail?.takeIf { it.isNotBlank() }?.let { detail ->
                appendLine("Current program strategy detail: $detail")
            }
            appendLine()
            if (programsState.programs.isEmpty()) {
                appendLine("- No existing programs on this device.")
            } else {
                programsState.programs
                    .sortedBy { it.name.lowercase() }
                    .forEach { program ->
                        appendLine("- `${program.id}` - ${program.name}")
                    }
            }
        }
    }

    private fun StringBuilder.appendMarkdownListSection(
        title: String,
        items: List<String>,
        emptyMessage: String,
    ) {
        appendLine("## $title")
        appendLine()
        if (items.isEmpty()) {
            appendLine("- $emptyMessage")
        } else {
            items.forEach { item -> appendLine("- $item") }
        }
        appendLine()
    }
}
