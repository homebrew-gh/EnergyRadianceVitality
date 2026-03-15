package com.erv.app

import android.content.Context
import android.widget.Toast

/**
 * Central place for user-visible feedback via toasts.
 * Use for publish success and errors so behavior is consistent across the app.
 */
object UserFeedback {

    /**
     * Show a short success toast (e.g. after an event was published successfully).
     * Uses [Toast.LENGTH_SHORT].
     */
    @JvmStatic
    fun showSuccess(context: Context, message: CharSequence? = null) {
        val text = message?.ifEmpty { null } ?: context.getString(R.string.toast_event_published)
        Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
    }

    /**
     * Show an error toast (e.g. when publish or sync fails).
     * Uses [Toast.LENGTH_LONG] so the user has time to read.
     */
    @JvmStatic
    fun showError(context: Context, message: CharSequence? = null) {
        val text = message?.ifEmpty { null }
            ?: context.getString(R.string.toast_publish_failed)
        Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()
    }
}
