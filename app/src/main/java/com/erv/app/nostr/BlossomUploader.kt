package com.erv.app.nostr

import android.util.Base64
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * [Blossom](https://github.com/hzrd149/blossom) blob upload ([BUD-02](https://github.com/hzrd149/blossom/blob/master/buds/2-upload.md)):
 * **PUT** `{origin}/upload` with raw body bytes, **Authorization: Nostr &lt;base64 signed event&gt;** where the event is
 * **kind 24242** (matches common clients such as nostr-tools’ `BlossomClient`).
 *
 * Primal Premium hosts such as `https://blossom.primal.net` use this API, not NIP-96.
 */
object BlossomUploader {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun uploadBlob(
        normalizedOrigin: String,
        bytes: ByteArray,
        contentType: String,
        signer: EventSigner
    ): Result<String> = withContext(Dispatchers.IO) {
        val base = normalizedOrigin.trimEnd('/') + "/"
        if (!base.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(IOException("Blossom server must use HTTPS"))
        }
        val putUrl = "${base}upload"
        val hashHex = Hex.encode(sha256(bytes))
        val now = System.currentTimeMillis() / 1000
        val unsigned = UnsignedEvent(
            pubkey = signer.publicKey,
            createdAt = now,
            kind = 24242,
            tags = listOf(
                listOf("expiration", "${now + 60}"),
                listOf("t", "upload"),
                listOf("x", hashHex)
            ),
            content = "blossom stuff"
        )
        val signed = try {
            signer.sign(unsigned)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
        val authB64 = Base64.encodeToString(
            signed.toJson().toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        val body = bytes.toRequestBody(contentType.toMediaType())
        val request = Request.Builder()
            .url(putUrl)
            .put(body)
            .header("Authorization", "Nostr $authB64")
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val reason = resp.header("X-Reason")?.takeIf { it.isNotBlank() }
                    return@withContext Result.failure(
                        IOException(
                            reason ?: "HTTP ${resp.code}: ${text.take(300)}"
                        )
                    )
                }
                val url = parseBlobDescriptorUrl(text)
                    ?: return@withContext Result.failure(IOException("No URL in Blossom response"))
                Result.success(url)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseBlobDescriptorUrl(jsonText: String): String? {
        return try {
            json.parseToJsonElement(jsonText).jsonObject["url"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }
}
