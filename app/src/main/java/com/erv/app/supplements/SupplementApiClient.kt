package com.erv.app.supplements

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class SupplementApiResult(
    val productId: String,
    val productName: String,
    val brand: String?,
    val info: SupplementInfo
)

/**
 * Official NIH Dietary Supplement Label Database (DSLD) API.
 * @see <a href="https://dsld.od.nih.gov/">dsld.od.nih.gov</a>
 */
private const val NIH_DSLD_BASE = "https://api.ods.od.nih.gov/dsld/v9"

class SupplementApiClient(
    private val client: OkHttpClient = OkHttpClient()
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Normalizes a search query for the DSLD API: replace commas (and similar) with spaces
     * and collapse runs of whitespace. E.g. "Thorne, Creatine" -> "Thorne Creatine"
     * so the backend can match products that appear as "Thorne Creatine" in the database.
     */
    fun normalizeSearchQuery(query: String): String {
        return query
            .replace(",", " ")
            .replace(";", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Looks up a scanned product code (UPC/EAN) in DSLD via [search] (`/search-filter` `q`).
     * Labels often store `upcSku` with **spaces**
     * (e.g. `0 74312 02100 8`), while scanners return compact digits — we try several shapes.
     * Many products are still missing from DSLD or have no barcode on file, so misses are common.
     */
    suspend fun searchByProductCode(code: String, size: Int = 20): List<SupplementApiResult> {
        val trimmed = code.trim()
        if (trimmed.isBlank()) return emptyList()
        val digits = trimmed.filter { it.isDigit() }
        val variants = LinkedHashSet<String>().apply {
            add(trimmed)
            if (digits.isNotEmpty()) add(digits)
            if (digits.length == 12) {
                add("0$digits")
                // Matches common DSLD / label print style (see API example `upcSku`).
                add("${digits[0]} ${digits.substring(1, 6)} ${digits.substring(6, 11)} ${digits[11]}")
            }
            if (digits.length == 13) {
                if (digits.startsWith("0")) add(digits.drop(1))
                add("${digits[0]} ${digits.substring(1, 6)} ${digits.substring(6, 11)} ${digits.substring(11, 13)}")
                add("${digits[0]} ${digits.substring(1, 7)} ${digits.substring(7, 12)} ${digits[12]}")
            }
            if (digits.length == 8) {
                add("${digits.substring(0, 4)} ${digits.substring(4, 8)}")
            }
        }
        for (q in variants) {
            val hits = search(q, size)
            if (hits.isNotEmpty()) return hits
        }
        return emptyList()
    }

    suspend fun search(query: String, size: Int = 20): List<SupplementApiResult> = withContext(Dispatchers.IO) {
        val normalized = normalizeSearchQuery(query)
        if (normalized.isBlank()) return@withContext emptyList()

        try {
            val url = "$NIH_DSLD_BASE/search-filter".toHttpUrl().newBuilder()
                .addQueryParameter("q", normalized)
                .addQueryParameter("size", size.toString())
                .build()

            var results = executeSearch(url)
            if (results.isEmpty() && normalized.contains(" ")) {
                val quotedUrl = "$NIH_DSLD_BASE/search-filter".toHttpUrl().newBuilder()
                    .addQueryParameter("q", "\"$normalized\"")
                    .addQueryParameter("size", size.toString())
                    .build()
                results = executeSearch(quotedUrl)
            }
            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun executeSearch(url: okhttp3.HttpUrl): List<SupplementApiResult> {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()
            val root = json.parseToJsonElement(body).jsonObject
            return root["hits"]
                ?.jsonArray
                ?.mapNotNull { it.asSupplementApiResult() }
                .orEmpty()
        }
    }

    private fun JsonElement.asSupplementApiResult(): SupplementApiResult? {
        val obj = jsonObject
        val source = obj["_source"]?.jsonObject ?: return null
        val productName = source.string("fullName")
            ?: source.string("productName")
            ?: return null
        val brand = source.string("brandName") ?: source.string("brand")
        val productId = obj.string("_id") ?: productName
        return SupplementApiResult(
            productId = productId,
            productName = productName,
            brand = brand,
            info = source.toSupplementInfo(productId = productId)
        )
    }

    private fun JsonObject.toSupplementInfo(productId: String): SupplementInfo {
        val ingredients = arrayOrEmpty("allIngredients").mapNotNull { elem ->
            elem.jsonObject.string("name")
        }.distinct()
            .ifEmpty {
                arrayOrEmpty("dietarySupplementsFacts").flatMap { fact ->
                    fact.jsonObject.arrayOrEmpty("ingredients").flatMap { ingredient ->
                        val ingredientObj = ingredient.jsonObject
                        listOfNotNull(ingredientObj.string("name"), ingredientObj.string("altName"))
                    }
                }.distinct()
            }

        val claims = langualDescriptions("claims")
            .ifEmpty { stringArray("langualClaimsOrUses") }
            .ifEmpty { stringArray("claimsOrUses") }

        val form = langualDescriptions("physicalState")
            .ifEmpty { stringArray("langualSupplementForm") }
            .ifEmpty { stringArray("supplementForm") }

        val targetGroup = langualDescriptions("userGroups")
            .ifEmpty { stringArray("langualTargetGroup") }
            .ifEmpty { stringArray("targetGroup") }

        val suggestedUse = runCatching {
            this["statements"]?.jsonArray
                ?.firstOrNull { stmt ->
                    val type = stmt.jsonObject.string("type") ?: ""
                    type.contains("suggest", ignoreCase = true) ||
                            type.contains("usage", ignoreCase = true) ||
                            type.contains("direction", ignoreCase = true)
                }?.jsonObject?.string("notes")
        }.getOrNull() ?: string("suggestedUse")

        val servingSize = this["servingSizes"]?.jsonArray?.firstOrNull()?.jsonObject?.let { ss ->
            val quantity = ss.doubleOrNull("minQuantity")
                ?: ss.intOrNull("minQuantity")?.toDouble()
            val unit = ss.string("unit")
            when {
                quantity != null && unit != null -> "${trimNumber(quantity)} $unit"
                quantity != null -> trimNumber(quantity)
                else -> unit
            }
        } ?: run {
            val firstFact = arrayOrEmpty("dietarySupplementsFacts").firstOrNull()?.jsonObject
            if (firstFact == null) null
            else {
                val quantity = firstFact.doubleOrNull("servingSizeQuantity")
                    ?: firstFact.intOrNull("servingSizeQuantity")?.toDouble()
                val unit = firstFact.string("servingSizeUnitName")
                when {
                    quantity != null && unit != null -> "${trimNumber(quantity)} $unit"
                    quantity != null -> trimNumber(quantity)
                    else -> unit
                }
            }
        }

        val otherIngredients = runCatching {
            this["otheringredients"]?.jsonObject?.string("text")
        }.getOrNull() ?: string("otheringredients")

        return SupplementInfo(
            productId = productId,
            productName = string("fullName") ?: string("productName"),
            brand = string("brandName") ?: string("brand"),
            suggestedUse = suggestedUse,
            claimsOrUses = claims,
            supplementForm = form,
            targetGroup = targetGroup,
            ingredients = ingredients,
            otherIngredients = otherIngredients,
            servingSize = servingSize,
            fetchedAtEpochSeconds = nowEpochSeconds()
        )
    }

    private fun JsonObject.langualDescriptions(name: String): List<String> {
        val elem = this[name] ?: return emptyList()
        return when {
            elem is JsonArray -> elem.mapNotNull { it.jsonObject.string("langualCodeDescription") }
            elem is JsonObject -> listOfNotNull(elem.string("langualCodeDescription"))
            else -> emptyList()
        }
    }

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.doubleOrNull(name: String): Double? =
        this[name]?.jsonPrimitive?.doubleOrNull

    private fun JsonObject.intOrNull(name: String): Int? =
        this[name]?.jsonPrimitive?.intOrNull

    private fun JsonObject.arrayOrEmpty(name: String): JsonArray =
        this[name]?.jsonArray ?: JsonArray(emptyList())

    private fun JsonObject.stringArray(name: String): List<String> =
        arrayOrEmpty(name).mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf { value -> value.isNotBlank() } }

    private fun trimNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}

