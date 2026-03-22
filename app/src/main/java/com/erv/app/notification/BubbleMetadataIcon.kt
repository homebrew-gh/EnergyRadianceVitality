package com.erv.app.notification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.erv.app.R

/**
 * Bubble IconCompat for NotificationCompat.BubbleMetadata.
 *
 * Android 15+ may reject foreground notifications when bubble metadata uses
 * IconCompat.createWithResource (resource icons); adaptive bitmap avoids
 * RemoteServiceException / CannotPostForegroundServiceNotificationException.
 */
fun bubbleMetadataIconCompat(context: Context, @DrawableRes drawableRes: Int = R.drawable.ic_stat_erv): IconCompat {
    val drawable = ContextCompat.getDrawable(context, drawableRes)
        ?: return IconCompat.createWithResource(context, drawableRes)
    val sizePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        72f,
        context.resources.displayMetrics
    ).toInt().coerceIn(48, 512)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(canvas)
    return IconCompat.createWithAdaptiveBitmap(bitmap)
}
