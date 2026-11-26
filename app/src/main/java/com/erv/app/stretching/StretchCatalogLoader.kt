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

    fun load(context: Context): List<StretchCatalogEntry> = loadRoot(context).stretches

    /** The bundled catalog with its revision info; empty when the asset can't be read. */
    fun loadRoot(context: Context): StretchCatalogRoot {
        return try {
            context.assets.open("stretch_catalog.json").bufferedReader().use { reader ->
                json.decodeFromString(StretchCatalogRoot.serializer(), reader.readText())
            }
        } catch (_: SerializationException) {
            StretchCatalogRoot()
        } catch (_: Exception) {
            StretchCatalogRoot()
        }
    }
}
