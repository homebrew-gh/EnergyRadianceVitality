package com.erv.app.notification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.IconCompat
import com.erv.app.R
import java.io.File
import java.io.FileOutputStream

/**
 * Icon for NotificationCompat bubble metadata.
 *
 * API 35+ foreground-service notifications require TYPE_URI_ADAPTIVE_BITMAP for bubble icons
 * (see system log and CannotPostForegroundServiceNotificationException). We rasterize the stat
 * icon to a PNG in cache and use [IconCompat.createWithAdaptiveBitmapContentUri]. Older releases
 * use in-memory adaptive bitmap.
 */
fun bubbleMetadataIconCompat(context: Context, @DrawableRes drawableRes: Int = R.drawable.ic_stat_erv): IconCompat? {
    val drawable = ContextCompat.getDrawable(context, drawableRes) ?: return null
    val sizePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        72f,
        context.resources.displayMetrics
    ).toInt().coerceIn(48, 512)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(canvas)

    return try {
        if (Build.VERSION.SDK_INT >= 35) {
            val dir = File(context.cacheDir, "bubble_icons").apply { mkdirs() }
            val file = File(dir, "bubble_meta_icon.png")
            FileOutputStream(file).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    bitmap.recycle()
                    return null
                }
            }
            val uri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } catch (_: Exception) {
                null
            }
            if (uri != null) {
                try {
                    val icon = IconCompat.createWithAdaptiveBitmapContentUri(uri)
                    bitmap.recycle()
                    return icon
                } catch (_: Exception) {
                    // Fall through to in-memory adaptive bitmap (some OEM / FGS paths reject URI icons).
                }
            }
        }
        val icon = IconCompat.createWithAdaptiveBitmap(bitmap)
        bitmap.recycle()
        icon
    } catch (_: Exception) {
        if (!bitmap.isRecycled) bitmap.recycle()
        null
    }
}
