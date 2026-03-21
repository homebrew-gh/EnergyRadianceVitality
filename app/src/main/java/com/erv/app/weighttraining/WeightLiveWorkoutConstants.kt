package com.erv.app.weighttraining

/** Intent extras and notification IDs shared by FGS, bubble, and [com.erv.app.MainActivity]. */
object WeightLiveWorkoutConstants {
    const val EXTRA_OPEN_WEIGHT_LIVE = "erv.extra.OPEN_WEIGHT_LIVE_WORKOUT"
    /** Bubble / shortcut intent: session start time (epoch seconds). */
    const val EXTRA_BUBBLE_SESSION_START = "erv.extra.BUBBLE_SESSION_START_EPOCH"
    const val NOTIFICATION_CHANNEL_ID = "erv_weight_live_workout"
    const val NOTIFICATION_ID = 30078
    const val BUBBLE_SHORTCUT_ID = "erv_weight_live_bubble"
}
