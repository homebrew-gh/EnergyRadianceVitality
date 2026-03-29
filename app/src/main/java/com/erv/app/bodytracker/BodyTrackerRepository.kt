package com.erv.app.bodytracker

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.UUID

private val Context.bodyTrackerDataStore: DataStore<Preferences> by preferencesDataStore(name = "erv_body_tracker")

class BodyTrackerRepository(context: Context) {

    private val appContext = context.applicationContext

    private object Keys {
        val STATE = stringPreferencesKey("body_tracker_state")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val photosDir: File by lazy {
        File(appContext.filesDir, "body_tracker_photos").apply { mkdirs() }
    }

    val state: Flow<BodyTrackerLibraryState> = appContext.bodyTrackerDataStore.data.map { prefs ->
        decodeState(prefs[Keys.STATE])
    }

    suspend fun currentState(): BodyTrackerLibraryState =
        decodeState(appContext.bodyTrackerDataStore.data.first()[Keys.STATE])

    suspend fun setLengthUnit(unit: BodyMeasurementLengthUnit) {
        updateState {
            it.copy(
                lengthUnit = unit,
                lengthUnitUpdatedAtEpochSeconds = nowEpochSeconds()
            )
        }
    }

    /**
     * @param fromMeasurementFormSave When true (Body tracker "Save this day"), clearing all relay-visible
     * fields while keeping photos bumps [BodyTrackerDayLog.updatedAtEpochSeconds] so an empty payload can
     * replace older data on Nostr. Photo-only adds use false.
     */
    suspend fun saveDayLog(log: BodyTrackerDayLog, fromMeasurementFormSave: Boolean = false) {
        updateState { current ->
            if (log.isEffectivelyEmpty()) {
                log.photos.forEach { deletePhotoFileIfExists(it.id) }
                current.copy(logs = current.logs.filter { it.date != log.date })
            } else {
                val parsedDate = runCatching { LocalDate.parse(log.date) }.getOrNull()
                val prev = parsedDate?.let { current.logFor(it) }
                val hadRelayVisibleBefore = prev?.isNostrEmpty() == false
                val stored = when {
                    log.isNostrEmpty() && fromMeasurementFormSave && hadRelayVisibleBefore ->
                        log.copy(updatedAtEpochSeconds = nowEpochSeconds())
                    log.isNostrEmpty() ->
                        log.copy(updatedAtEpochSeconds = prev?.updatedAtEpochSeconds ?: 0L)
                    else ->
                        log.copy(updatedAtEpochSeconds = nowEpochSeconds())
                }
                current.copy(logs = current.logs.upsertLog(stored))
            }
        }
    }

    suspend fun replaceAll(newState: BodyTrackerLibraryState) {
        appContext.bodyTrackerDataStore.edit { prefs ->
            prefs[Keys.STATE] = json.encodeToString(BodyTrackerLibraryState.serializer(), newState)
        }
    }

    suspend fun clearAllData() {
        withContext(Dispatchers.IO) {
            photosDir.listFiles()?.forEach { file ->
                if (file.isFile) file.delete()
            }
        }
        replaceAll(BodyTrackerLibraryState())
    }

    suspend fun addPhotoFromUri(date: LocalDate, uri: Uri) {
        val id = UUID.randomUUID().toString()
        val dest = File(photosDir, "$id.jpg")
        val success = withContext(Dispatchers.IO) {
            try {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                dest.exists() && dest.length() > 0L
            } catch (_: Exception) {
                if (dest.exists()) dest.delete()
                false
            }
        }
        if (!success) {
            if (dest.exists()) dest.delete()
            return
        }
        updateState { current ->
            val existing = current.logFor(date) ?: BodyTrackerDayLog(date = date.toString())
            val photo = BodyTrackerPhoto(id = id)
            val updated = existing.copy(photos = existing.photos + photo)
            current.copy(logs = current.logs.upsertLog(updated))
        }
    }

    suspend fun deleteDayLog(date: LocalDate) {
        updateState { current ->
            val log = current.logFor(date) ?: return@updateState current
            log.photos.forEach { deletePhotoFileIfExists(it.id) }
            current.copy(logs = current.logs.filter { it.date != date.toString() })
        }
    }

    suspend fun removePhoto(date: LocalDate, photoId: String) {
        deletePhotoFileIfExists(photoId)
        updateState { current ->
            val existing = current.logFor(date) ?: return@updateState current
            val nextPhotos = existing.photos.filterNot { it.id == photoId }
            val next = existing.copy(photos = nextPhotos)
            if (next.isEffectivelyEmpty()) {
                current.copy(logs = current.logs.filter { it.date != date.toString() })
            } else {
                current.copy(logs = current.logs.upsertLog(next))
            }
        }
    }

    fun photoFile(photoId: String): File = File(photosDir, "$photoId.jpg")

    private fun deletePhotoFileIfExists(photoId: String) {
        val f = photoFile(photoId)
        if (f.exists()) f.delete()
    }

    private suspend fun updateState(transform: (BodyTrackerLibraryState) -> BodyTrackerLibraryState) {
        appContext.bodyTrackerDataStore.edit { prefs ->
            val current = decodeState(prefs[Keys.STATE])
            val updated = transform(current)
            prefs[Keys.STATE] = json.encodeToString(BodyTrackerLibraryState.serializer(), updated)
        }
    }

    private fun decodeState(raw: String?): BodyTrackerLibraryState {
        if (raw.isNullOrBlank()) return BodyTrackerLibraryState()
        return try {
            json.decodeFromString(BodyTrackerLibraryState.serializer(), raw)
        } catch (_: SerializationException) {
            BodyTrackerLibraryState()
        } catch (_: IllegalArgumentException) {
            BodyTrackerLibraryState()
        }
    }
}
