package com.erv.app.supplements

/**
 * Resolves a scanned barcode to NIH DSLD hits: tries [OpenFoodFactsClient] for name/brand, runs
 * [SupplementApiClient.search] on those strings, then falls back to [SupplementApiClient.searchByProductCode].
 */
data class BarcodeDsldLookupResult(
    val dsldResults: List<SupplementApiResult>,
    /** Short explanation for snackbars / dialogs (OFF + DSLD outcome). */
    val userMessage: String,
    val offFoundProduct: Boolean,
    val suggestedNameFromOff: String?,
    val suggestedBrandFromOff: String?,
)

object SupplementBarcodeLookup {

    suspend fun resolve(
        scannedCode: String,
        offClient: OpenFoodFactsClient,
        dsldClient: SupplementApiClient,
        resultLimit: Int = 20,
    ): BarcodeDsldLookupResult {
        val code = scannedCode.trim()
        if (code.isBlank()) {
            return BarcodeDsldLookupResult(
                dsldResults = emptyList(),
                userMessage = "Empty barcode.",
                offFoundProduct = false,
                suggestedNameFromOff = null,
                suggestedBrandFromOff = null
            )
        }

        val merged = LinkedHashMap<String, SupplementApiResult>()
        fun addAll(list: List<SupplementApiResult>) {
            for (r in list) merged[r.productId] = r
        }

        val off = offClient.lookupByBarcode(code)
        val offFound = off != null && (!off.productName.isNullOrBlank() || !off.brands.isNullOrBlank())

        if (offFound && off != null) {
            val brand = off.brands?.trim().orEmpty()
            val name = off.productName?.trim().orEmpty()
            val brandPlusName = listOf(brand, name).filter { it.isNotBlank() }.joinToString(" ").trim()
            if (brandPlusName.isNotBlank()) {
                addAll(dsldClient.search(brandPlusName, resultLimit))
            }
            if (name.isNotBlank() && name != brandPlusName) {
                addAll(dsldClient.search(name, resultLimit))
            }
            val firstBrand = off.brands?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            if (firstBrand != null && name.isNotBlank()) {
                val combo = "$firstBrand $name".trim()
                if (combo != brandPlusName && combo != name) {
                    addAll(dsldClient.search(combo, resultLimit))
                }
            }
        }

        if (merged.isEmpty()) {
            addAll(dsldClient.searchByProductCode(code, resultLimit))
        }

        val results = merged.values.toList()
        val userMessage = buildUserMessage(code, offFound, off, results.isNotEmpty())

        return BarcodeDsldLookupResult(
            dsldResults = results,
            userMessage = userMessage,
            offFoundProduct = offFound,
            suggestedNameFromOff = off?.productName?.trim()?.takeIf { it.isNotBlank() },
            suggestedBrandFromOff = off?.brands?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    private fun buildUserMessage(
        code: String,
        offFound: Boolean,
        off: OpenFoodFactsProduct?,
        dsldHit: Boolean,
    ): String = buildString {
        when {
            offFound && off != null -> {
                val label = listOfNotNull(off.brands?.trim(), off.productName?.trim())
                    .filter { it.isNotBlank() }
                    .joinToString(" — ")
                    .ifBlank { code }
                append("Open Food Facts: ")
                append(label)
                append(". ")
                if (dsldHit) append("NIH DSLD matches below.")
                else append("No NIH DSLD match — try manual search on the supplement screen.")
            }
            else -> {
                append("No Open Food Facts product for this code; tried NIH with the barcode only. ")
                if (dsldHit) append("NIH DSLD matches below.")
                else append("No NIH match.")
            }
        }
    }
}
