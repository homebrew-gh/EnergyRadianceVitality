package com.erv.app.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaLibraryManifestMergeTest {

    @Test
    fun updateBase_isUnknownWhenRelayUnreadAndNoLocalCopy() {
        assertNull(mediaLibraryUpdateBase(remote = null, cached = null))
    }

    @Test
    fun updateBase_usesLocalCopyWhenRelayUnread() {
        val cached = manifest(item("heart_rate_graph", "a", 10))
        assertEquals(cached, mediaLibraryUpdateBase(remote = null, cached = cached))
    }

    @Test
    fun updateBase_keepsItemsOnlyTheLocalCopyHas() {
        // A backup published a moment ago may not be on the relay yet.
        val remote = manifest(item("heart_rate_graph", "a", 10))
        val cached = manifest(item("heart_rate_graph", "a", 10), item("heart_rate_graph", "b", 20))
        val base = mediaLibraryUpdateBase(remote, cached)!!
        assertEquals(setOf("a", "b"), base.items.map { it.localId }.toSet())
    }

    @Test
    fun mergeItems_keepsNewestUploadPerSession() {
        val older = item("heart_rate_graph", "a", 10)
        val newer = item("heart_rate_graph", "a", 30)
        val merged = mergeMediaLibraryItems(listOf(older), listOf(newer))
        assertEquals(listOf(newer), merged)
        assertEquals(listOf(newer), mergeMediaLibraryItems(listOf(newer), listOf(older)))
    }

    @Test
    fun mergeItems_keepsSameLocalIdFromDifferentSources() {
        val route = item("cardio_route", "a", 10)
        val graph = item("heart_rate_graph", "a", 10)
        assertEquals(2, mergeMediaLibraryItems(listOf(route), listOf(graph)).size)
    }

    private fun manifest(vararg items: MediaLibraryItem) =
        MediaLibraryManifest(updatedAtEpochSeconds = 1, items = items.toList())

    private fun item(source: String, localId: String, uploadedAt: Long) = MediaLibraryItem(
        id = "$source:$localId",
        source = source,
        localId = localId,
        blobUrl = "https://blossom.local/$localId-$uploadedAt",
        blossomOrigin = "https://blossom.local",
        sha256 = "sha-$localId-$uploadedAt",
        encryptedSha256 = "enc-$localId-$uploadedAt",
        sizeBytes = 1,
        contentType = "image/png",
        encryption = MediaBlobEncryption(keyBase64 = "k", nonceBase64 = "n"),
        uploadedAtEpochSeconds = uploadedAt,
    )
}
