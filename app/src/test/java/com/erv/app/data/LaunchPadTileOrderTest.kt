package com.erv.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchPadTileOrderTest {

    @Test
    fun resolveVisibleLaunchPadTileOrder_usesDefaultOrder_whenNoSavedOrder() {
        val visible = setOf(
            LaunchPadTileId.PROGRAMS,
            LaunchPadTileId.STRETCHING,
            LaunchPadTileId.CARDIO,
            LaunchPadTileId.WEIGHT_TRAINING,
            LaunchPadTileId.HOT_COLD,
            LaunchPadTileId.BODY_TRACKER,
        )

        val resolved = resolveVisibleLaunchPadTileOrder(
            storedOrder = emptyList(),
            visibleTileIds = visible,
        )

        assertEquals(
            listOf(
                LaunchPadTileId.PROGRAMS,
                LaunchPadTileId.STRETCHING,
                LaunchPadTileId.CARDIO,
                LaunchPadTileId.WEIGHT_TRAINING,
                LaunchPadTileId.HOT_COLD,
                LaunchPadTileId.BODY_TRACKER,
            ),
            resolved,
        )
    }

    @Test
    fun mergeVisibleLaunchPadTileOrder_preservesHiddenTileSlots() {
        val stored = listOf(
            LaunchPadTileId.PROGRAMS,
            LaunchPadTileId.SUPPLEMENTS,
            LaunchPadTileId.STRETCHING,
            LaunchPadTileId.CARDIO,
            LaunchPadTileId.LIGHT_THERAPY,
            LaunchPadTileId.WEIGHT_TRAINING,
            LaunchPadTileId.HOT_COLD,
            LaunchPadTileId.BODY_TRACKER,
        )

        val merged = mergeVisibleLaunchPadTileOrder(
            storedOrder = stored,
            visibleOrder = listOf(
                LaunchPadTileId.BODY_TRACKER,
                LaunchPadTileId.PROGRAMS,
                LaunchPadTileId.STRETCHING,
                LaunchPadTileId.CARDIO,
                LaunchPadTileId.WEIGHT_TRAINING,
                LaunchPadTileId.HOT_COLD,
            ),
        )

        assertEquals(
            listOf(
                LaunchPadTileId.BODY_TRACKER,
                LaunchPadTileId.SUPPLEMENTS,
                LaunchPadTileId.PROGRAMS,
                LaunchPadTileId.STRETCHING,
                LaunchPadTileId.LIGHT_THERAPY,
                LaunchPadTileId.CARDIO,
                LaunchPadTileId.WEIGHT_TRAINING,
                LaunchPadTileId.HOT_COLD,
            ),
            merged,
        )
    }
}
