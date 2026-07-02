package com.erv.app.cardio

import android.content.Context
import com.erv.app.hr.HeartRateZoneInputs
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.SessionMediaBackup

data class CardioRouteMediaBackupResult(
    val origin: String?,
    val uploaded: Boolean,
    val reused: Boolean,
    val failed: Boolean,
    val manifestQueued: Boolean,
)

object CardioRouteMediaBackup {
    suspend fun backupRouteImage(
        appContext: Context,
        session: CardioSession,
        dateIso: String,
        relayPool: RelayPool,
        signer: EventSigner,
        dataRelayUrls: List<String>,
        explicitPrivateBlossomOrigin: String,
        trustSelfSignedLanTls: Boolean,
        colorTop: Int,
        colorMid: Int,
        colorBottom: Int,
        zoneInputs: HeartRateZoneInputs = HeartRateZoneInputs(),
    ): CardioRouteMediaBackupResult {
        val result = SessionMediaBackup.backupCardioSession(
            appContext = appContext,
            session = session,
            dateIso = dateIso,
            relayPool = relayPool,
            signer = signer,
            dataRelayUrls = dataRelayUrls,
            explicitPrivateBlossomOrigin = explicitPrivateBlossomOrigin,
            trustSelfSignedLanTls = trustSelfSignedLanTls,
            zoneInputs = zoneInputs,
            routeColorTop = colorTop,
            routeColorMid = colorMid,
            routeColorBottom = colorBottom,
        )
        return CardioRouteMediaBackupResult(
            origin = result.origin,
            uploaded = result.uploaded > 0,
            reused = result.reused > 0,
            failed = when {
                result.origin == null -> true
                result.uploaded > 0 || result.reused > 0 -> false
                else -> result.failed > 0
            },
            manifestQueued = result.manifestQueued,
        )
    }
}
