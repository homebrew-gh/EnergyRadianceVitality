package com.erv.app.weighttraining

/** Intent extras and notification IDs shared by FGS, bubble, and [com.erv.app.MainActivity]. */
object WeightLiveWorkoutConstants {
    const val EXTRA_OPEN_WEIGHT_LIVE = "erv.extra.OPEN_WEIGHT_LIVE_WORKOUT"
    /** Bubble / shortcut intent: session start time (epoch seconds). */
    const val EXTRA_BUBBLE_SESSION_START = "erv.extra.BUBBLE_SESSION_START_EPOCH"
    /**
     * Channel v2: must differ from the original `erv_weight_live_workout` so Android creates a new
     * channel with bubble support — existing channels cannot gain [NotificationChannel.setAllowBubbles].
     */
    const val NOTIFICATION_CHANNEL_ID = "erv_weight_live_workout_bubble_v2"
    const val NOTIFICATION_ID = 30078
    const val BUBBLE_SHORTCUT_ID = "erv_weight_live_bubble"
}
