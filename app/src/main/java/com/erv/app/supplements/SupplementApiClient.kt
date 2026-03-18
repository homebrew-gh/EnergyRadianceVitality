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

class SupplementApiClient(
    private val client: OkHttpClient = OkHttpClient()
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String, size: Int = 10): List<SupplementApiResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        try {
            val url = "http://3.216.223.78/dsldnxt/api/search-filter".toHttpUrl().newBuilder()
                .addQueryParameter("q", trimmed)
                .addQueryParameter("size", size.toString())
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext emptyList()

                val root = json.parseToJsonElement(body).jsonObject
                root["hits"]
                    ?.jsonArray
                    ?.mapNotNull { it.asSupplementApiResult() }
                    .orEmpty()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun JsonElement.asSupplementApiResult(): SupplementApiResult? {
        val obj = jsonObject
        val source = obj["_source"]?.jsonObject ?: return null
        val productName = source.string("productName") ?: return null
        val brand = source.string("brand")
        val productId = source.string("dsldId") ?: obj.string("_id") ?: productName
        return SupplementApiResult(
            productId = productId,
            productName = productName,
            brand = brand,
            info = source.toSupplementInfo(productId = productId)
        )
    }

    private fun JsonObject.toSupplementInfo(productId: String): SupplementInfo {
        val facts = arrayOrEmpty("dietarySupplementsFacts")
        val firstFact = facts.firstOrNull()?.jsonObject
        val ingredients = facts.flatMap { fact ->
            fact.jsonObject.arrayOrEmpty("ingredients").flatMap { ingredient ->
                val ingredientObj = ingredient.jsonObject
                listOfNotNull(
                    ingredientObj.string("name"),
                    ingredientObj.string("altName")
                )
            }
        }.distinct()

        val claims = stringArray("langualClaimsOrUses")
            .ifEmpty { stringArray("claimsOrUses") }
        val form = stringArray("langualSupplementForm")
            .ifEmpty { stringArray("supplementForm") }
        val targetGroup = stringArray("langualTargetGroup")
            .ifEmpty { stringArray("targetGroup") }
        val servingSize = when {
            firstFact == null -> null
            else -> {
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

        return SupplementInfo(
            productId = productId,
            productName = string("productName"),
            brand = string("brand"),
            suggestedUse = string("suggestedUse"),
            claimsOrUses = claims,
            supplementForm = form,
            targetGroup = targetGroup,
            ingredients = ingredients,
            otherIngredients = string("otheringredients"),
            servingSize = servingSize,
            fetchedAtEpochSeconds = nowEpochSeconds()
        )
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

