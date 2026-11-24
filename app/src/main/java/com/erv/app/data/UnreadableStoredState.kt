package com.erv.app.data

import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

private const val TAG = "ErvStoredState"

/** Prefix of the keys that hold copies of stored state that could not be decoded. */
const val UNREADABLE_STATE_BACKUP_SUFFIX = "__unreadable_"

/**
 * Called inside a DataStore `edit` when a repository could not decode its stored JSON and is
 * about to write a fresh state over it. Copies the original text to
 * `<key>__unreadable_<epochSeconds>` first, so one bad field after an update or import cannot
 * wipe a whole library. Returns the backup key, or null when there was nothing to keep.
 */
fun MutablePreferences.preserveUnreadableState(
    key: Preferences.Key<String>,
    raw: String?,
    nowEpochSeconds: Long = System.currentTimeMillis() / 1000L,
): Preferences.Key<String>? {
    if (raw.isNullOrBlank()) return null
    // Keep every distinct copy, but don't store the same text twice.
    val existing = asMap().entries.any { (k, v) ->
        k.name.startsWith(key.name + UNREADABLE_STATE_BACKUP_SUFFIX) && v == raw
    }
    if (existing) return null
    val base = "${key.name}$UNREADABLE_STATE_BACKUP_SUFFIX$nowEpochSeconds"
    var backupKey = stringPreferencesKey(base)
    var n = 1
    while (contains(backupKey)) {
        backupKey = stringPreferencesKey("${base}_${n++}")
    }
    this[backupKey] = raw
    runCatching {
        Log.e(TAG, "Stored ${key.name} could not be decoded; kept a copy in ${backupKey.name} before writing")
    }
    return backupKey
}
