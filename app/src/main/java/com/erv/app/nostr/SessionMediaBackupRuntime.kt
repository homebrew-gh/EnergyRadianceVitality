package com.erv.app.nostr

import android.content.Context
import android.util.Log
import com.erv.app.cardio.CardioHrScaffolding
import com.erv.app.cardio.CardioSession
import com.erv.app.data.UserPreferences
import com.erv.app.weighttraining.WeightWorkoutSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val MEDIA_BACKUP_LOG_TAG = "ErvMediaBackup"

/**
 * Best-effort background session media backup when relay signing is available.
 * [MainActivity] binds [relayPool], [signer], and [keyManager] when the user is logged in.
 */
object SessionMediaBackupRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var relayPool: RelayPool? = null

    @Volatile
    var signer: EventSigner? = null

    @Volatile
    var keyManager: KeyManager? = null

    fun update(relayPool: RelayPool?, signer: EventSigner?, keyManager: KeyManager?) {
        this.relayPool = relayPool
        this.signer = signer
        this.keyManager = keyManager
    }

    fun scheduleCardioBackup(appContext: Context, session: CardioSession, dateIso: String) {
        val (pool, sig, km) = signingOrSkip("cardio ${session.id}") ?: return
        scope.launch {
            runCatching {
                val prefs = UserPreferences(appContext)
                SessionMediaBackup.backupCardioSession(
                    appContext = appContext,
                    session = session,
                    dateIso = dateIso,
                    relayPool = pool,
                    signer = sig,
                    dataRelayUrls = km.relayUrlsForKind30078Publish(),
                    explicitPrivateBlossomOrigin = prefs.blossomPrivateServerOrigin.first(),
                    trustSelfSignedLanTls = prefs.trustSelfSignedLanTls.first(),
                    zoneInputs = prefs.heartRateZoneInputs.first(),
                    routeColorTop = ROUTE_COLOR_TOP,
                    routeColorMid = ROUTE_COLOR_MID,
                    routeColorBottom = ROUTE_COLOR_BOTTOM,
                )
            }.logBackup("cardio ${session.id}")
        }
    }

    fun scheduleWeightBackup(appContext: Context, session: WeightWorkoutSession, dateIso: String) {
        val (pool, sig, km) = signingOrSkip("weight ${session.id}") ?: return
        scope.launch {
            runCatching {
                val prefs = UserPreferences(appContext)
                SessionMediaBackup.backupWeightSession(
                    appContext = appContext,
                    session = session,
                    dateIso = dateIso,
                    relayPool = pool,
                    signer = sig,
                    dataRelayUrls = km.relayUrlsForKind30078Publish(),
                    explicitPrivateBlossomOrigin = prefs.blossomPrivateServerOrigin.first(),
                    trustSelfSignedLanTls = prefs.trustSelfSignedLanTls.first(),
                    zoneInputs = prefs.heartRateZoneInputs.first(),
                )
            }.logBackup("weight ${session.id}")
        }
    }

    fun scheduleHeartRateGraph(
        appContext: Context,
        localId: String,
        dateIso: String,
        heartRate: CardioHrScaffolding,
    ) {
        val (pool, sig, km) = signingOrSkip("heart-rate $localId") ?: return
        scope.launch {
            runCatching {
                val prefs = UserPreferences(appContext)
                SessionMediaBackup.backupHeartRateGraph(
                    appContext = appContext,
                    localId = localId,
                    dateIso = dateIso,
                    heartRate = heartRate,
                    relayPool = pool,
                    signer = sig,
                    dataRelayUrls = km.relayUrlsForKind30078Publish(),
                    explicitPrivateBlossomOrigin = prefs.blossomPrivateServerOrigin.first(),
                    trustSelfSignedLanTls = prefs.trustSelfSignedLanTls.first(),
                    zoneInputs = prefs.heartRateZoneInputs.first(),
                )
            }.logBackup("heart-rate $localId")
        }
    }

    private fun signingOrSkip(label: String): Triple<RelayPool, EventSigner, KeyManager>? {
        val pool = relayPool
        val sig = signer
        val km = keyManager
        if (pool == null || sig == null || km == null) {
            Log.i(MEDIA_BACKUP_LOG_TAG, "$label skipped: relay signing unavailable")
            return null
        }
        return Triple(pool, sig, km)
    }

    private fun Result<SessionMediaBackupResult>.logBackup(label: String) {
        onSuccess { result ->
            Log.i(
                MEDIA_BACKUP_LOG_TAG,
                "$label origin=${result.origin} uploaded=${result.uploaded} " +
                    "reused=${result.reused} failed=${result.failed} manifestQueued=${result.manifestQueued}",
            )
        }
        onFailure { error ->
            Log.w(MEDIA_BACKUP_LOG_TAG, "$label backup failed", error)
        }
    }

    private const val ROUTE_COLOR_TOP = 0xFF2E0808.toInt()
    private const val ROUTE_COLOR_MID = 0xFF6B0000.toInt()
    private const val ROUTE_COLOR_BOTTOM = 0xFF7A1515.toInt()
}
