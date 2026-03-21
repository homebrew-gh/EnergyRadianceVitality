package com.erv.app.nostr

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Bridge between coroutine-based signer calls and Android's ActivityResult API.
 * Must be created before the Activity reaches STARTED state (e.g. as a property or in onCreate before super).
 */
class AmberLauncherHost(activity: ComponentActivity) {
    @Volatile
    private var continuation: CancellableContinuation<ActivityResult>? = null

    val launcher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            continuation?.resume(result)
            continuation = null
        }

    suspend fun launchForResult(intent: Intent): ActivityResult =
        suspendCancellableCoroutine { cont ->
            continuation = cont
            cont.invokeOnCancellation { continuation = null }
            launcher.launch(intent)
        }
}

/**
 * NIP-55 remote signer (e.g. Amber).
 *
 * Uses the **content resolver** path for background (no-UI) signing/encryption/decryption
 * when the user has pre-authorized those permissions. Falls back to the intent (Activity)
 * path if the content resolver returns null (e.g. first use or permissions not yet granted).
 */
class AmberSigner(
    override val publicKey: String,
    private val host: AmberLauncherHost,
    private val contentResolver: ContentResolver,
    private val signerPackageName: String
) : EventSigner {

    private val npub: String = Bech32.npubEncode(Hex.decode(publicKey))
    private val singleFlight = Mutex()

    // ------------------------------------------------------------------
    // EventSigner implementation
    // ------------------------------------------------------------------

    override suspend fun sign(event: UnsignedEvent): NostrEvent = singleFlight.withLock {
        val id = event.computeId()
        val unsigned = NostrEvent(
            id = id,
            pubkey = event.pubkey,
            createdAt = event.createdAt,
            kind = event.kind,
            tags = event.tags,
            content = event.content,
            sig = ""
        )
        val eventJson = unsigned.toJson()

        val signature = signViaContentResolver(eventJson)
        if (signature != null) return unsigned.copy(sig = signature)

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$eventJson"))
        intent.`package` = signerPackageName
        intent.putExtra("type", "sign_event")
        intent.putExtra("id", id)
        intent.putExtra("current_user", npub)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val result = host.launchForResult(intent)
        check(result.resultCode == Activity.RESULT_OK) { "Amber signing cancelled or failed" }

        val sig = result.data?.getStringExtra("signature")
            ?: result.data?.getStringExtra("result")
            ?: error("No signature returned from signer")

        return unsigned.copy(sig = sig)
    }

    override suspend fun nip44Encrypt(plaintext: String, peerPubkeyHex: String): String = singleFlight.withLock {
        val bg = encryptViaContentResolver(plaintext, peerPubkeyHex)
        if (bg != null) return bg

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$plaintext"))
        intent.`package` = signerPackageName
        intent.putExtra("type", "nip44_encrypt")
        intent.putExtra("pubkey", peerPubkeyHex)
        intent.putExtra("current_user", npub)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val result = host.launchForResult(intent)
        check(result.resultCode == Activity.RESULT_OK) { "Amber encryption cancelled or failed" }

        return result.data?.getStringExtra("signature")
            ?: result.data?.getStringExtra("result")
            ?: error("No encrypted data returned from signer")
    }

    override suspend fun nip44Decrypt(ciphertext: String, peerPubkeyHex: String): String = singleFlight.withLock {
        val bg = decryptViaContentResolver(ciphertext, peerPubkeyHex)
        if (bg != null) return bg

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$ciphertext"))
        intent.`package` = signerPackageName
        intent.putExtra("type", "nip44_decrypt")
        intent.putExtra("pubkey", peerPubkeyHex)
        intent.putExtra("current_user", npub)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val result = host.launchForResult(intent)
        check(result.resultCode == Activity.RESULT_OK) { "Amber decryption cancelled or failed" }

        return result.data?.getStringExtra("signature")
            ?: result.data?.getStringExtra("result")
            ?: error("No decrypted data returned from signer")
    }

    // ------------------------------------------------------------------
    // Content resolver helpers (background, no UI)
    // ------------------------------------------------------------------

    private suspend fun signViaContentResolver(eventJson: String): String? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse("content://$signerPackageName.SIGN_EVENT")
            val cursor = contentResolver.query(
                uri, arrayOf(eventJson, "", npub), null, null, null
            ) ?: return@withContext null
            cursor.use {
                if (it.getColumnIndex("rejected") >= 0) return@withContext null
                if (!it.moveToFirst()) return@withContext null
                val idx = it.getColumnIndex("result")
                if (idx < 0) return@withContext null
                it.getString(idx)
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun encryptViaContentResolver(plaintext: String, peerPubkeyHex: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse("content://$signerPackageName.NIP44_ENCRYPT")
                val cursor = contentResolver.query(
                    uri, arrayOf(plaintext, peerPubkeyHex, npub), null, null, null
                ) ?: return@withContext null
                cursor.use {
                    if (it.getColumnIndex("rejected") >= 0) return@withContext null
                    if (!it.moveToFirst()) return@withContext null
                    val idx = it.getColumnIndex("result")
                    if (idx < 0) return@withContext null
                    it.getString(idx)
                }
            } catch (_: Exception) {
                null
            }
        }

    private suspend fun decryptViaContentResolver(ciphertext: String, peerPubkeyHex: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse("content://$signerPackageName.NIP44_DECRYPT")
                val cursor = contentResolver.query(
                    uri, arrayOf(ciphertext, peerPubkeyHex, npub), null, null, null
                ) ?: return@withContext null
                cursor.use {
                    if (it.getColumnIndex("rejected") >= 0) return@withContext null
                    if (!it.moveToFirst()) return@withContext null
                    val idx = it.getColumnIndex("result")
                    if (idx < 0) return@withContext null
                    it.getString(idx)
                }
            } catch (_: Exception) {
                null
            }
        }

    // ------------------------------------------------------------------
    // Static helpers
    // ------------------------------------------------------------------

    companion object {
        /**
         * Request the user's public key from a NIP-55 signer (e.g. Amber).
         * Also requests default permissions so subsequent operations can use
         * the content resolver (background, no UI) path.
         *
         * Returns (publicKeyHex, signerPackageName).
         */
        suspend fun getPublicKey(host: AmberLauncherHost): Pair<String, String> {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:"))
            intent.putExtra("type", "get_public_key")
            intent.putExtra("permissions", REQUESTED_PERMISSIONS)

            val result = host.launchForResult(intent)
            check(result.resultCode == Activity.RESULT_OK) { "Amber connection cancelled or failed" }

            val key = result.data?.getStringExtra("signature")
                ?: result.data?.getStringExtra("result")
                ?: error("No public key returned from signer")

            val packageName = result.data?.getStringExtra("package")
                ?: error("No package name returned from signer")

            val hex = if (key.startsWith("npub")) {
                Hex.encode(Bech32.npubDecode(key))
            } else {
                key
            }

            return hex to packageName
        }

        fun isAvailable(context: Context): Boolean {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:"))
            return context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
        }

        private val REQUESTED_PERMISSIONS =
            """[{"type":"sign_event","kind":22242},{"type":"sign_event","kind":24242},{"type":"sign_event","kind":27235},{"type":"sign_event","kind":30078},{"type":"nip44_encrypt"},{"type":"nip44_decrypt"}]"""
    }
}
