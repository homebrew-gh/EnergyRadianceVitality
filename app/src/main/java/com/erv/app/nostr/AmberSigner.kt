package com.erv.app.nostr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
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
 * Delegates event signing and NIP-44 encryption/decryption to an external signer app via intents.
 */
class AmberSigner(
    override val publicKey: String,
    private val host: AmberLauncherHost
) : EventSigner {

    private val npub: String = Bech32.npubEncode(Hex.decode(publicKey))

    override suspend fun sign(event: UnsignedEvent): NostrEvent {
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
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:${unsigned.toJson()}"))
        intent.putExtra("type", "sign_event")
        intent.putExtra("id", id)
        intent.putExtra("current_user", npub)

        val result = host.launchForResult(intent)
        check(result.resultCode == Activity.RESULT_OK) { "Amber signing cancelled or failed" }

        val signature = result.data?.getStringExtra("signature")
            ?: error("No signature returned from signer")

        return unsigned.copy(sig = signature)
    }

    override suspend fun nip44Encrypt(plaintext: String, peerPubkeyHex: String): String {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$plaintext"))
        intent.putExtra("type", "nip44_encrypt")
        intent.putExtra("pubkey", peerPubkeyHex)
        intent.putExtra("current_user", npub)

        val result = host.launchForResult(intent)
        check(result.resultCode == Activity.RESULT_OK) { "Amber encryption cancelled or failed" }

        return result.data?.getStringExtra("signature")
            ?: error("No encrypted data returned from signer")
    }

    override suspend fun nip44Decrypt(ciphertext: String, peerPubkeyHex: String): String {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$ciphertext"))
        intent.putExtra("type", "nip44_decrypt")
        intent.putExtra("pubkey", peerPubkeyHex)
        intent.putExtra("current_user", npub)

        val result = host.launchForResult(intent)
        check(result.resultCode == Activity.RESULT_OK) { "Amber decryption cancelled or failed" }

        return result.data?.getStringExtra("signature")
            ?: error("No decrypted data returned from signer")
    }

    companion object {
        /**
         * Request the user's public key from a NIP-55 signer (e.g. Amber).
         * Returns the 32-byte hex public key.
         */
        suspend fun getPublicKey(host: AmberLauncherHost): String {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:"))
            intent.putExtra("type", "get_public_key")

            val result = host.launchForResult(intent)
            check(result.resultCode == Activity.RESULT_OK) { "Amber connection cancelled or failed" }

            val key = result.data?.getStringExtra("signature")
                ?: error("No public key returned from signer")

            return if (key.startsWith("npub")) {
                Hex.encode(Bech32.npubDecode(key))
            } else {
                key
            }
        }

        fun isAvailable(context: Context): Boolean {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:"))
            return context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
        }
    }
}
