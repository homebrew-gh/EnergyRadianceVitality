package com.erv.app.nostr

import com.erv.app.standards.CUSTOM_STANDARDS_D_TAG
import com.erv.app.standards.STANDARD_RESULTS_D_TAG

/**
 * Reads fitness standard test results (`erv/standards/results`) and custom standards
 * (`erv/standards/custom`), both made on the web companion. Android only publishes these tags
 * as empty replacements when the user deletes their data.
 */
object StandardsSync {
    const val RESULTS_D_TAG = STANDARD_RESULTS_D_TAG
    const val CUSTOM_D_TAG = CUSTOM_STANDARDS_D_TAG

    /** The decrypted JSON of [latest], or null when it cannot be read. */
    suspend fun plaintextOf(latest: NostrEvent, signer: EventSigner): String? =
        try {
            signer.decryptFromSelf(latest.content)
        } catch (_: Exception) {
            null
        }
}
