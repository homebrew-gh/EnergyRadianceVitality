package com.erv.app.cardio

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

/** What is saved while a cardio timer is live, so a killed app can pick the run back up. */
@Serializable
data class CardioSavedLiveSession(
    val session: CardioActiveTimerSession,
    val suppressNotification: Boolean = false,
)

/**
 * Keeps the live cardio timer in a small file in app storage. Written on every change (start,
 * split, finished leg) on one background thread, so writes land in order; read once at startup.
 */
object CardioLiveSessionStore {
    private const val TAG = "CardioLiveSessionStore"
    private const val FILE_NAME = "cardio_live_session.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val io = Executors.newSingleThreadExecutor()

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    /** Pure, so it is unit tested. */
    fun encode(saved: CardioSavedLiveSession): String =
        json.encodeToString(CardioSavedLiveSession.serializer(), saved)

    /** Null when [raw] is not a saved session this version can read. */
    fun decode(raw: String): CardioSavedLiveSession? =
        runCatching { json.decodeFromString(CardioSavedLiveSession.serializer(), raw) }
            .onFailure { Log.w(TAG, "Unreadable saved cardio session; ignoring it", it) }
            .getOrNull()

    fun read(context: Context): CardioSavedLiveSession? {
        val f = file(context)
        if (!f.isFile) return null
        val raw = runCatching { f.readText() }.getOrNull() ?: return null
        return decode(raw)
    }

    /** Saves [saved], or removes the file when it is null. */
    fun write(context: Context, saved: CardioSavedLiveSession?) {
        val f = file(context)
        val text = saved?.let { encode(it) }
        io.execute {
            try {
                if (text == null) {
                    f.delete()
                } else {
                    val tmp = File(f.parentFile, "$FILE_NAME.tmp")
                    tmp.writeText(text)
                    if (!tmp.renameTo(f)) {
                        f.writeText(text)
                        tmp.delete()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not save cardio session", e)
            }
        }
    }
}
