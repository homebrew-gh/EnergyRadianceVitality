package com.erv.app.stretching

import android.content.Context
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object StretchCatalogLoader {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun load(context: Context): List<StretchCatalogEntry> {
        return try {
            context.assets.open("stretch_catalog.json").bufferedReader().use { reader ->
                val root = json.decodeFromString(StretchCatalogRoot.serializer(), reader.readText())
                root.stretches
            }
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
