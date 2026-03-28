package com.erv.app.nostr

import com.erv.app.bodytracker.BodyTrackerDayLog
import com.erv.app.bodytracker.BodyTrackerLibraryState
import com.erv.app.bodytracker.isNostrEmpty
import com.erv.app.cardio.CardioCustomActivityType
import com.erv.app.cardio.CardioDayLog
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioQuickLaunch
import com.erv.app.cardio.CardioRoutine
import com.erv.app.cardio.CardioSession
import com.erv.app.data.FitnessEquipmentNostrPayload
import com.erv.app.data.OwnedEquipmentItem
import com.erv.app.heatcold.HeatColdDayLog
import com.erv.app.heatcold.HeatColdLibraryState
import com.erv.app.heatcold.withStableSessionIds
import com.erv.app.lighttherapy.LightDayLog
import com.erv.app.lighttherapy.LightDevice
import com.erv.app.lighttherapy.LightLibraryState
import com.erv.app.lighttherapy.LightRoutine
import com.erv.app.lighttherapy.withStableSessionIds
import com.erv.app.programs.FitnessProgram
import com.erv.app.programs.ProgramCompletionMark
import com.erv.app.programs.ProgramsLibraryState
import com.erv.app.programs.sanitized
import com.erv.app.stretching.StretchDayLog
import com.erv.app.stretching.StretchLibraryState
import com.erv.app.stretching.StretchRoutine
import com.erv.app.stretching.withStableSessionIds
import com.erv.app.supplements.SupplementDayLog
import com.erv.app.supplements.SupplementEntry
import com.erv.app.supplements.SupplementIntake
import com.erv.app.supplements.SupplementLibraryState
import com.erv.app.supplements.SupplementRoutine
import com.erv.app.supplements.SupplementRoutineRun
import com.erv.app.weighttraining.WeightDayLog
import com.erv.app.weighttraining.WeightExercise
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightRoutine
import com.erv.app.weighttraining.WeightWorkoutSession
import kotlin.math.max

/**
 * Semantic merge of relay snapshots into local state: union by stable id where available, last-write-wins
 * using coarse timestamps for session-like records, and **local wins** on catalog rows without reliable times.
 */
object LibraryStateMerge {

    fun mergeCardio(local: CardioLibraryState, remote: CardioLibraryState): CardioLibraryState {
        val routines = mergeByIdPreferLocal(remote.routines, local.routines, CardioRoutine::id)
        val customActivityTypes =
            mergeByIdPreferLocal(remote.customActivityTypes, local.customActivityTypes, CardioCustomActivityType::id)
        val quickLaunches = mergeByIdPreferLocal(remote.quickLaunches, local.quickLaunches, CardioQuickLaunch::id)
        val dates = (local.logs.map { it.date } + remote.logs.map { it.date }).distinct().sorted()
        val logs = dates.mapNotNull { d ->
            val l = local.logs.firstOrNull { it.date == d }
            val r = remote.logs.firstOrNull { it.date == d }
            when {
                l == null -> r
                r == null -> l
                else -> mergeCardioDay(l, r)
            }
        }
        return CardioLibraryState(
            routines = routines,
            customActivityTypes = customActivityTypes,
            quickLaunches = quickLaunches,
            logs = logs.sortedBy { it.date }
        )
    }

    private fun mergeCardioDay(local: CardioDayLog, remote: CardioDayLog): CardioDayLog {
        val merged = mergeSessionsByIdLoggedAt(local.sessions, remote.sessions)
        return CardioDayLog(date = local.date, sessions = merged)
    }

    private fun mergeSessionsByIdLoggedAt(local: List<CardioSession>, remote: List<CardioSession>): List<CardioSession> {
        val byId = remote.associateBy { it.id }.toMutableMap()
        for (s in local) {
            val other = byId[s.id]
            byId[s.id] = when {
                other == null -> s
                s.loggedAtEpochSeconds >= other.loggedAtEpochSeconds -> s
                else -> other
            }
        }
        return byId.values.sortedWith(compareBy({ it.loggedAtEpochSeconds }, { it.id }))
    }

    fun mergeWeight(local: WeightLibraryState, remote: WeightLibraryState): WeightLibraryState {
        val exercises = mergeByIdPreferLocal(remote.exercises, local.exercises, WeightExercise::id)
        val routines = mergeWeightRoutinesByLastModified(remote.routines, local.routines)
        val dates = (local.logs.map { it.date } + remote.logs.map { it.date }).distinct().sorted()
        val logs = dates.mapNotNull { d ->
            val l = local.logs.firstOrNull { it.date == d }
            val r = remote.logs.firstOrNull { it.date == d }
            when {
                l == null -> r
                r == null -> l
                else -> mergeWeightDay(l, r)
            }
        }
        return WeightLibraryState(
            exercises = exercises,
            routines = routines,
            logs = logs.sortedBy { it.date }
        )
    }

    private fun mergeWeightDay(local: WeightDayLog, remote: WeightDayLog): WeightDayLog {
        val byId = remote.workouts.associateBy { it.id }.toMutableMap()
        for (w in local.workouts) {
            val other = byId[w.id]
            byId[w.id] = when {
                other == null -> w
                weightWorkoutEpoch(w) >= weightWorkoutEpoch(other) -> w
                else -> other
            }
        }
        return WeightDayLog(date = local.date, workouts = byId.values.sortedWith(compareBy(::weightWorkoutEpoch, { it.id })))
    }

    private fun weightWorkoutEpoch(w: WeightWorkoutSession): Long =
        max(w.startedAtEpochSeconds ?: 0L, w.finishedAtEpochSeconds ?: 0L)

    private fun mergeWeightRoutinesByLastModified(remote: List<WeightRoutine>, local: List<WeightRoutine>): List<WeightRoutine> {
        val ids = (local.map { it.id } + remote.map { it.id }).toSet()
        return ids.map { id ->
            val l = local.firstOrNull { it.id == id }
            val r = remote.firstOrNull { it.id == id }
            when {
                l == null -> r!!
                r == null -> l
                l.lastModifiedEpochSeconds > r.lastModifiedEpochSeconds -> l
                r.lastModifiedEpochSeconds > l.lastModifiedEpochSeconds -> r
                else -> l
            }
        }.sortedBy { it.name.lowercase() }
    }

    fun mergeSupplement(local: SupplementLibraryState, remote: SupplementLibraryState): SupplementLibraryState {
        val supplements = mergeByIdPreferLocal(remote.supplements, local.supplements, SupplementEntry::id)
        val routines = mergeByIdPreferLocal(remote.routines, local.routines, SupplementRoutine::id)
        val dates = (local.logs.map { it.date } + remote.logs.map { it.date }).distinct().sorted()
        val logs = dates.mapNotNull { d ->
            val l = local.logs.firstOrNull { it.date == d }
            val r = remote.logs.firstOrNull { it.date == d }
            when {
                l == null -> r
                r == null -> l
                else -> mergeSupplementDay(l, r)
            }
        }
        return SupplementLibraryState(
            supplements = supplements,
            routines = routines,
            logs = logs.sortedBy { it.date }
        )
    }

    private fun mergeSupplementDay(local: SupplementDayLog, remote: SupplementDayLog): SupplementDayLog {
        val runs = mergeRoutineRuns(remote.routineRuns, local.routineRuns)
        val adHoc = mergeAdHocIntakes(remote.adHocIntakes, local.adHocIntakes)
        return SupplementDayLog(date = local.date, routineRuns = runs, adHocIntakes = adHoc)
    }

    private fun mergeRoutineRuns(remote: List<SupplementRoutineRun>, local: List<SupplementRoutineRun>): List<SupplementRoutineRun> {
        val byId = remote.associateBy { it.id }.toMutableMap()
        for (r in local) {
            val other = byId[r.id]
            byId[r.id] = when {
                other == null -> r
                r.takenAtEpochSeconds >= other.takenAtEpochSeconds -> r
                else -> other
            }
        }
        return byId.values.sortedWith(compareBy({ it.takenAtEpochSeconds }, { it.id }))
    }

    private fun mergeAdHocIntakes(remote: List<SupplementIntake>, local: List<SupplementIntake>): List<SupplementIntake> {
        fun key(i: SupplementIntake): String =
            i.id?.takeIf { it.isNotBlank() }
                ?: "${i.supplementId}|${i.takenAtEpochSeconds ?: 0L}|${i.dosageTaken}|${i.note}"
        val byKey = remote.associateBy { key(it) }.toMutableMap()
        for (i in local) {
            val k = key(i)
            val other = byKey[k]
            byKey[k] = when {
                other == null -> i
                (i.takenAtEpochSeconds ?: 0L) >= (other.takenAtEpochSeconds ?: 0L) -> i
                else -> other
            }
        }
        return byKey.values.sortedWith(compareBy({ it.takenAtEpochSeconds ?: 0L }, { key(it) }))
    }

    fun mergeLight(local: LightLibraryState, remote: LightLibraryState): LightLibraryState {
        val la = local.withStableSessionIds()
        val ra = remote.withStableSessionIds()
        val devices = mergeByIdPreferLocal(ra.devices, la.devices, LightDevice::id)
        val routines = mergeByIdPreferLocal(ra.routines, la.routines, LightRoutine::id)
        val dates = (la.logs.map { it.date } + ra.logs.map { it.date }).distinct().sorted()
        val logs = dates.mapNotNull { d ->
            val l = la.logs.firstOrNull { it.date == d }
            val r = ra.logs.firstOrNull { it.date == d }
            when {
                l == null -> r
                r == null -> l
                else -> mergeLightDay(l, r)
            }
        }
        return LightLibraryState(
            devices = devices,
            routines = routines,
            logs = logs.sortedBy { it.date }
        )
    }

    private fun mergeLightDay(local: LightDayLog, remote: LightDayLog): LightDayLog {
        val byId = remote.sessions.associateBy { it.id }.toMutableMap()
        for (s in local.sessions) {
            val other = byId[s.id]
            byId[s.id] = when {
                other == null -> s
                s.loggedAtEpochSeconds >= other.loggedAtEpochSeconds -> s
                else -> other
            }
        }
        return LightDayLog(date = local.date, sessions = byId.values.sortedWith(compareBy({ it.loggedAtEpochSeconds }, { it.id })))
    }

    fun mergeHeatCold(local: HeatColdLibraryState, remote: HeatColdLibraryState): HeatColdLibraryState {
        val la = local.withStableSessionIds()
        val ra = remote.withStableSessionIds()
        return HeatColdLibraryState(
            saunaLogs = mergeHeatColdSide(la.saunaLogs, ra.saunaLogs),
            coldLogs = mergeHeatColdSide(la.coldLogs, ra.coldLogs)
        )
    }

    private fun mergeHeatColdSide(local: List<HeatColdDayLog>, remote: List<HeatColdDayLog>): List<HeatColdDayLog> {
        val dates = (local.map { it.date } + remote.map { it.date }).distinct().sorted()
        return dates.mapNotNull { d ->
            val l = local.firstOrNull { it.date == d }
            val r = remote.firstOrNull { it.date == d }
            when {
                l == null -> r
                r == null -> l
                else -> mergeHeatColdDay(l, r)
            }
        }
    }

    private fun mergeHeatColdDay(local: HeatColdDayLog, remote: HeatColdDayLog): HeatColdDayLog {
        val byId = remote.sessions.associateBy { it.id }.toMutableMap()
        for (s in local.sessions) {
            val other = byId[s.id]
            byId[s.id] = when {
                other == null -> s
                s.loggedAtEpochSeconds >= other.loggedAtEpochSeconds -> s
                else -> other
            }
        }
        return HeatColdDayLog(
            date = local.date,
            sessions = byId.values.sortedWith(compareBy({ it.loggedAtEpochSeconds }, { it.id }))
        )
    }

    fun mergeBodyTracker(local: BodyTrackerLibraryState, remote: BodyTrackerLibraryState): BodyTrackerLibraryState {
        val localWinsSettings = local.lengthUnitUpdatedAtEpochSeconds >= remote.lengthUnitUpdatedAtEpochSeconds
        val lengthUnit = if (localWinsSettings) local.lengthUnit else remote.lengthUnit
        val lengthUnitUpdatedAt = if (localWinsSettings) {
            local.lengthUnitUpdatedAtEpochSeconds
        } else {
            remote.lengthUnitUpdatedAtEpochSeconds
        }
        val dates = (local.logs.map { it.date } + remote.logs.map { it.date }).distinct().sorted()
        val logs = dates.mapNotNull { d ->
            val l = local.logs.firstOrNull { it.date == d }
            val r = remote.logs.firstOrNull { it.date == d }
            mergeBodyTrackerDay(l, r)
        }
        return BodyTrackerLibraryState(
            lengthUnit = lengthUnit,
            lengthUnitUpdatedAtEpochSeconds = lengthUnitUpdatedAt,
            logs = logs.sortedBy { it.date }
        )
    }

    /**
     * Relay payloads never carry photos; [remote] always has empty [BodyTrackerDayLog.photos].
     * Local photos are preserved from [local] unless a newer relay snapshot wins the numeric fields,
     * in which case local photos are still kept.
     */
    private fun mergeBodyTrackerDay(local: BodyTrackerDayLog?, remote: BodyTrackerDayLog?): BodyTrackerDayLog? {
        when {
            local == null && remote == null -> return null
            local == null -> {
                val r = remote!!
                if (r.isNostrEmpty()) return null
                return r.copy(photos = emptyList())
            }
            remote == null -> {
                if (local.isNostrEmpty() && local.photos.isEmpty()) return null
                return local
            }
            else -> {
                val localNet = local.copy(photos = emptyList())
                val remoteNet = remote.copy(photos = emptyList())
                val mergedNet = if (localNet.updatedAtEpochSeconds >= remoteNet.updatedAtEpochSeconds) {
                    localNet
                } else {
                    remoteNet
                }
                val out = mergedNet.copy(photos = local.photos)
                if (out.isNostrEmpty() && out.photos.isEmpty()) return null
                return out
            }
        }
    }

    fun mergeStretch(local: StretchLibraryState, remote: StretchLibraryState): StretchLibraryState {
        val la = local.withStableSessionIds()
        val ra = remote.withStableSessionIds()
        val routines = mergeByIdPreferLocal(ra.routines, la.routines, StretchRoutine::id)
        val dates = (la.logs.map { it.date } + ra.logs.map { it.date }).distinct().sorted()
        val logs = dates.mapNotNull { d ->
            val l = la.logs.firstOrNull { it.date == d }
            val r = ra.logs.firstOrNull { it.date == d }
            when {
                l == null -> r
                r == null -> l
                else -> mergeStretchDay(l, r)
            }
        }
        return StretchLibraryState(routines = routines, logs = logs.sortedBy { it.date })
    }

    fun mergePrograms(local: ProgramsLibraryState, remote: ProgramsLibraryState): ProgramsLibraryState {
        val localState = local.sanitized()
        val remoteState = remote.sanitized()
        val useLegacyMerge = localState.masterUpdatedAtEpochSeconds == 0L || remoteState.masterUpdatedAtEpochSeconds == 0L
        val mergedMaster = if (useLegacyMerge) {
            val programs = mergeProgramsByLastModified(remoteState.programs, localState.programs)
            val activeProgramId = localState.activeProgramId
                ?: remoteState.activeProgramId?.takeIf { id -> programs.any { it.id == id } }
            ProgramsLibraryState(
                programs = programs,
                activeProgramId = activeProgramId,
                masterUpdatedAtEpochSeconds = max(localState.masterUpdatedAtEpochSeconds, remoteState.masterUpdatedAtEpochSeconds)
            )
        } else if (localState.masterUpdatedAtEpochSeconds >= remoteState.masterUpdatedAtEpochSeconds) {
            localState.copy(completionState = emptyMap())
        } else {
            remoteState.copy(completionState = emptyMap())
        }
        val completionKeys = (localState.completionState.keys + remoteState.completionState.keys).toSet()
        val mergedCompletion = completionKeys.associateWith { key ->
            mergeProgramCompletionMark(localState.completionState[key], remoteState.completionState[key])
        }
        return mergedMaster.copy(completionState = mergedCompletion).sanitized()
    }

    private fun mergeStretchDay(local: StretchDayLog, remote: StretchDayLog): StretchDayLog {
        val byId = remote.sessions.associateBy { it.id }.toMutableMap()
        for (s in local.sessions) {
            val other = byId[s.id]
            byId[s.id] = when {
                other == null -> s
                s.loggedAtEpochSeconds >= other.loggedAtEpochSeconds -> s
                else -> other
            }
        }
        return StretchDayLog(date = local.date, sessions = byId.values.sortedWith(compareBy({ it.loggedAtEpochSeconds }, { it.id })))
    }

    private fun mergeProgramsByLastModified(remote: List<FitnessProgram>, local: List<FitnessProgram>): List<FitnessProgram> {
        val ids = (local.map { it.id } + remote.map { it.id }).toSet()
        return ids.map { id ->
            val l = local.firstOrNull { it.id == id }
            val r = remote.firstOrNull { it.id == id }
            when {
                l == null -> r!!
                r == null -> l
                l.lastModifiedEpochSeconds >= r.lastModifiedEpochSeconds -> l
                else -> r
            }
        }.sortedBy { it.name.lowercase() }
    }

    private fun mergeProgramCompletionMark(
        local: ProgramCompletionMark?,
        remote: ProgramCompletionMark?,
    ): ProgramCompletionMark {
        return when {
            local == null -> remote!!
            remote == null -> local
            local.updatedAtEpochSeconds >= remote.updatedAtEpochSeconds -> local
            else -> remote
        }
    }

    fun mergeFitnessEquipment(
        localGym: Boolean,
        localEquipment: List<OwnedEquipmentItem>,
        remote: FitnessEquipmentNostrPayload,
    ): FitnessEquipmentNostrPayload {
        val gym = localGym || remote.gymMembership
        val equipment = mergeByIdPreferLocal(remote.equipment, localEquipment, OwnedEquipmentItem::id)
        return FitnessEquipmentNostrPayload(gymMembership = gym, equipment = equipment)
    }

    private fun <T> mergeByIdPreferLocal(remote: List<T>, local: List<T>, id: (T) -> String): List<T> {
        val m = LinkedHashMap<String, T>()
        remote.forEach { m[id(it)] = it }
        local.forEach { m[id(it)] = it }
        return m.values.toList()
    }
}
