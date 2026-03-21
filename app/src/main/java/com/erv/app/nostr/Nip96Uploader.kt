package com.erv.app.nostr

import android.util.Base64
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer

/**
 * [NIP-96](https://github.com/nostr-protocol/nips/blob/master/96.md) media upload with
 * [NIP-98](https://github.com/nostr-protocol/nips/blob/master/98.md) HTTP auth (kind 27235).
 *
 * Relays only accept event JSON; images must be hosted elsewhere, then linked from the kind 1 note.
 */
object Nip96Uploader {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Trims and ensures https; empty if user cleared the field. */
    fun normalizeMediaServerOrigin(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return ""
        val withScheme = when {
            t.startsWith("https://", ignoreCase = true) -> t
            t.startsWith("http://", ignoreCase = true) -> t.replaceFirst("http://", "https://", ignoreCase = true)
            else -> "https://${t.trimStart('/')}"
        }
        return withScheme.trimEnd('/')
    }

    suspend fun discoverApiUrl(normalizedOrigin: String): String? = withContext(Dispatchers.IO) {
        val base = normalizedOrigin.trimEnd('/')
        if (!base.startsWith("https://", ignoreCase = true)) return@withContext null
        val url = "$base/.well-known/nostr/nip96.json"
        val req = Request.Builder().url(url).get().build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val obj = json.parseToJsonElement(body).jsonObject
                obj["api_url"]?.jsonPrimitive?.content
                    ?: obj["apiUrl"]?.jsonPrimitive?.content
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun uploadPng(
        apiPostUrl: String,
        pngBytes: ByteArray,
        fileName: String,
        signer: EventSigner
    ): Result<String> = withContext(Dispatchers.IO) {
        val postUrl = apiPostUrl.trim()
        if (!postUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(IOException("Upload URL must use HTTPS"))
        }
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                pngBytes.toRequestBody("image/png".toMediaType())
            )
            .build()
        val buffer = Buffer()
        multipart.writeTo(buffer)
        val rawBody = buffer.readByteArray()
        val contentType = multipart.contentType() ?: return@withContext Result.failure(
            IOException("Missing multipart content type")
        )
        val bodyHash = Hex.encode(sha256(rawBody))
        val authUnsigned = UnsignedEvent(
            pubkey = signer.publicKey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = 27235,
            tags = listOf(
                listOf("u", postUrl),
                listOf("method", "POST"),
                listOf("payload", bodyHash)
            ),
            content = ""
        )
        val signed = try {
            signer.sign(authUnsigned)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
        val authB64 = Base64.encodeToString(
            signed.toJson().toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        val request = Request.Builder()
            .url(postUrl)
            .post(rawBody.toRequestBody(contentType))
            .header("Authorization", "Nostr $authB64")
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("HTTP ${resp.code}: ${text.take(300)}")
                    )
                }
                val url = parseUploadedUrl(text)
                    ?: return@withContext Result.failure(IOException("No URL in upload response"))
                Result.success(url)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadRoutePngFromOrigin(
        normalizedOrigin: String,
        pngBytes: ByteArray,
        fileName: String,
        signer: EventSigner
    ): Result<String> {
        val apiUrl = discoverApiUrl(normalizedOrigin)
            ?: return Result.failure(IOException("NIP-96 discovery failed for this server"))
        return uploadPng(apiUrl, pngBytes, fileName, signer)
    }

    private fun parseUploadedUrl(jsonText: String): String? {
        return try {
            val root = json.parseToJsonElement(jsonText).jsonObject
            val nip94 = root["nip94_event"]?.jsonObject
            val tags = nip94?.get("tags")?.jsonArray
            if (tags != null) {
                for (el in tags) {
                    val arr = el.jsonArray
                    if (arr.size >= 2 && arr[0].jsonPrimitive.content == "url") {
                        return arr[1].jsonPrimitive.content
                    }
                }
            }
            root["url"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }
}
