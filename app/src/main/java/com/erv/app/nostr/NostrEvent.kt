package com.erv.app.nostr

import kotlinx.serialization.json.*

/**
 * An unsigned Nostr event (no id or signature yet).
 * Call [computeId] to get the event ID (SHA-256 of canonical serialization).
 */
data class UnsignedEvent(
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String
) {
    fun computeId(): String {
        val serialized = buildJsonArray {
            add(0)
            add(pubkey)
            add(createdAt)
            add(kind)
            add(buildJsonArray {
                for (tag in tags) {
                    add(buildJsonArray { for (t in tag) add(t) })
                }
            })
            add(content)
        }.toString()
        return Hex.encode(sha256(serialized.toByteArray(Charsets.UTF_8)))
    }
}

/**
 * A signed Nostr event with id and Schnorr signature.
 */
data class NostrEvent(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String
) {
    fun toJson(): String = buildJsonObject {
        put("id", id)
        put("pubkey", pubkey)
        put("created_at", createdAt)
        put("kind", kind)
        put("tags", buildJsonArray {
            for (tag in tags) {
                add(buildJsonArray { for (t in tag) add(t) })
            }
        })
        put("content", content)
        put("sig", sig)
    }.toString()

    companion object {
        fun fromJson(json: JsonObject): NostrEvent = NostrEvent(
            id = json["id"]!!.jsonPrimitive.content,
            pubkey = json["pubkey"]!!.jsonPrimitive.content,
            createdAt = json["created_at"]!!.jsonPrimitive.long,
            kind = json["kind"]!!.jsonPrimitive.int,
            tags = json["tags"]!!.jsonArray.map { tagArr ->
                tagArr.jsonArray.map { it.jsonPrimitive.content }
            },
            content = json["content"]!!.jsonPrimitive.content,
            sig = json["sig"]!!.jsonPrimitive.content
        )

        fun fromJson(jsonString: String): NostrEvent =
            fromJson(Json.parseToJsonElement(jsonString).jsonObject)
    }
}
