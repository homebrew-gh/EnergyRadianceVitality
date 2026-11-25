package com.erv.app.ui.workouts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.cardio.CardioPaceGoal
import com.erv.app.cardio.formatCardioDistanceFromMeters
import com.erv.app.cardio.formatCardioElapsedClock
import com.erv.app.workouts.PaceGoalState
import com.erv.app.workouts.PaceGoalStatus
import kotlin.math.abs

private val AheadGreen = Color(0xFF2E9E5B)
private val BehindAmber = Color(0xFFE08A1E)

/**
 * The pace goal as a race against a ghost: a track from start to the goal distance, the goal
 * pace's marker where it would be now, and you where you are. Ahead, on pace or behind, in words
 * and colour, with the finish the pace so far is heading for.
 */
@Composable
internal fun SessionPaceGoalTrack(
    goal: CardioPaceGoal,
    status: PaceGoalStatus,
    distanceUnit: CardioDistanceUnit,
    cycling: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = when (status.state) {
        PaceGoalState.AHEAD -> AheadGreen
        PaceGoalState.ON_PACE -> MaterialTheme.colorScheme.primary
        PaceGoalState.BEHIND -> BehindAmber
    }
    val headline = when (status.state) {
        PaceGoalState.AHEAD -> "Ahead ${formatCardioElapsedClock(status.aheadSeconds)}"
        PaceGoalState.ON_PACE -> "On Pace"
        PaceGoalState.BEHIND -> "Behind ${formatCardioElapsedClock(-status.aheadSeconds)}"
    }
    val gap = abs(status.aheadMeters).takeIf { status.state != PaceGoalState.ON_PACE && it >= 1.0 }
        ?.let { formatCardioDistanceFromMeters(it, distanceUnit) }
    val you by animateFloatAsState(status.youFraction, label = "pace_goal_you")
    val ghost by animateFloatAsState(status.goalFraction, label = "pace_goal_ghost")

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                listOfNotNull(headline, gap).joinToString(" · "),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
            ) {
                val marker = 28.dp
                val travel = maxWidth - marker
                // The track, filled up to you.
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(you.coerceIn(0.01f, 1f))
                        .height(8.dp)
                        .background(color.copy(alpha = 0.6f), RoundedCornerShape(4.dp)),
                )
                Icon(
                    Icons.Filled.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(20.dp),
                )
                // The goal pace's ghost.
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = travel * ghost)
                        .size(marker)
                        .border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                )
                // You.
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = travel * you)
                        .size(marker)
                        .background(color, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (cycling) Icons.Filled.DirectionsBike else Icons.Filled.DirectionsRun,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                buildString {
                    append("Goal ${formatCardioDistanceFromMeters(goal.distanceMeters, distanceUnit)} in ")
                    append(formatCardioElapsedClock(goal.seconds))
                    status.projectedFinishSeconds?.let { append(" · On track for ${formatCardioElapsedClock(it)}") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
