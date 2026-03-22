package com.erv.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ErvLightColorScheme = lightColorScheme(
    primary = ErvPrimary,
    onPrimary = ErvOnPrimary,
    primaryContainer = ErvPrimaryContainer,
    onPrimaryContainer = ErvOnPrimaryContainer,
    secondary = ErvSecondary,
    onSecondary = ErvOnSecondary,
    secondaryContainer = ErvSecondaryContainer,
    onSecondaryContainer = ErvOnSecondaryContainer,
    tertiary = ErvTertiary,
    onTertiary = ErvOnTertiary,
    tertiaryContainer = ErvTertiaryContainer,
    onTertiaryContainer = ErvOnTertiaryContainer,
    background = ErvBackground,
    onBackground = ErvOnBackground,
    surface = ErvSurface,
    onSurface = ErvOnSurface,
    surfaceVariant = ErvSurfaceVariant,
    onSurfaceVariant = ErvOnSurfaceVariant,
    outline = ErvOutline,
    outlineVariant = ErvOutlineVariant,
    error = ErvError,
    onError = ErvOnError,
    errorContainer = ErvErrorContainer,
    onErrorContainer = ErvOnErrorContainer,
    inverseSurface = ErvInverseSurface,
    inverseOnSurface = ErvInverseOnSurface,
    inversePrimary = ErvInversePrimary,
    scrim = ErvScrim
)

private val ErvDarkColorScheme = darkColorScheme(
    primary = ErvDarkPrimary,
    onPrimary = ErvDarkOnPrimary,
    primaryContainer = ErvDarkPrimaryContainer,
    onPrimaryContainer = ErvDarkOnPrimaryContainer,
    secondary = ErvDarkSecondary,
    onSecondary = ErvDarkOnSecondary,
    secondaryContainer = ErvDarkSecondaryContainer,
    onSecondaryContainer = ErvDarkOnSecondaryContainer,
    tertiary = ErvDarkTertiary,
    onTertiary = ErvDarkOnTertiary,
    tertiaryContainer = ErvDarkTertiaryContainer,
    onTertiaryContainer = ErvDarkOnTertiaryContainer,
    background = ErvDarkBackground,
    onBackground = ErvDarkOnBackground,
    surface = ErvDarkSurface,
    onSurface = ErvDarkOnSurface,
    surfaceVariant = ErvDarkSurfaceVariant,
    onSurfaceVariant = ErvDarkOnSurfaceVariant,
    outline = ErvDarkOutline,
    outlineVariant = ErvDarkOutlineVariant,
    error = ErvDarkError,
    onError = ErvDarkOnError,
    errorContainer = ErvDarkErrorContainer,
    onErrorContainer = ErvDarkOnErrorContainer,
    inverseSurface = ErvDarkInverseSurface,
    inverseOnSurface = ErvDarkInverseOnSurface,
    inversePrimary = ErvDarkInversePrimary,
    scrim = ErvDarkScrim
)

@Composable
fun ErvTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ErvDarkColorScheme else ErvLightColorScheme

    val view = LocalView.current
    val hostContext = LocalContext.current
    // view.context is often ContextThemeWrapper, not Activity — casting caused ClassCastException
    // in bubble activities and other embedded windows.
    if (!view.isInEditMode) {
        SideEffect {
            val activity = hostContext.findActivity() ?: return@SideEffect
            val window = activity.window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ErvTypography,
        content = content
    )
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
