package com.erv.app.ui.cardio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Kayaking
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.ui.graphics.vector.ImageVector
import com.erv.app.cardio.CardioActivitySnapshot
import com.erv.app.cardio.CardioBuiltinActivity

/** Row icon for the Activities tab and similar lists. */
fun cardioActivityListIcon(snapshot: CardioActivitySnapshot): ImageVector {
    snapshot.builtin?.let { return cardioBuiltinListIcon(it) }
    return Icons.Filled.FitnessCenter
}

private fun cardioBuiltinListIcon(b: CardioBuiltinActivity): ImageVector = when (b) {
    CardioBuiltinActivity.WALK -> Icons.Filled.DirectionsWalk
    CardioBuiltinActivity.RUN -> Icons.Filled.DirectionsRun
    CardioBuiltinActivity.SPRINT -> Icons.Filled.Speed
    CardioBuiltinActivity.RUCK -> Icons.Filled.Backpack
    CardioBuiltinActivity.HIKE -> Icons.Filled.Terrain
    CardioBuiltinActivity.BIKE -> Icons.Filled.DirectionsBike
    CardioBuiltinActivity.SWIM -> Icons.Filled.Pool
    CardioBuiltinActivity.ELLIPTICAL -> Icons.Filled.FitnessCenter
    CardioBuiltinActivity.ROWING -> Icons.Filled.Kayaking
    CardioBuiltinActivity.STATIONARY_BIKE -> Icons.Filled.PedalBike
    CardioBuiltinActivity.JUMP_ROPE -> Icons.Filled.SportsGymnastics
    CardioBuiltinActivity.OTHER -> Icons.Filled.FitnessCenter
}
