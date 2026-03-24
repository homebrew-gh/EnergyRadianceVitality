package com.erv.app.supplements

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Minimal client for [Open Food Facts](https://world.openfoodfacts.org/) product-by-barcode lookup.
 * Data is [ODbL](https://opendatacommons.org/licenses/odbl/) — attribute in the app when presenting OFF-derived text.
 *
 * API: [Open Food Facts API](https://openfoodfacts.github.io/openfoodfacts-server/api/)
 */
private const val OFF_USER_AGENT = "ERV/0.1.0 (Android; com.erv.app; dietary-supplement lookup)"

data class OpenFoodFactsProduct(
    val barcode: String,
    val productName: String?,
    val brands: String?,
)

class OpenFoodFactsClient(
    private val client: OkHttpClient = OkHttpClient()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookupByBarcode(barcode: String): OpenFoodFactsProduct? = withContext(Dispatchers.IO) {
        val digits = barcode.trim().filter { it.isDigit() }
        if (digits.isBlank()) return@withContext null
        val url = "https://world.openfoodfacts.org/api/v0/product/$digits.json".toHttpUrl()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", OFF_USER_AGENT)
            .header("Accept", "application/json")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext null
                val root = json.parseToJsonElement(body).jsonObject
                val status = root["status"]?.jsonPrimitive?.intOrNull ?: 0
                if (status != 1) return@withContext null
                val product = root["product"]?.jsonObject ?: return@withContext null
                OpenFoodFactsProduct(
                    barcode = digits,
                    productName = product.pickLocalizedName(),
                    brands = product.stringField("brands")?.takeIf { it.isNotBlank() }
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}

private fun JsonObject.pickLocalizedName(): String? {
    val keys = listOf(
        "product_name_en",
        "product_name",
        "generic_name_en",
        "generic_name",
        "abbreviated_product_name_en",
        "abbreviated_product_name"
    )
    for (k in keys) {
        stringField(k)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
    }
    return null
}

private fun JsonObject.stringField(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
