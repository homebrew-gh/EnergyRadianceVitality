package com.erv.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.doOnAttach

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
    scrim = ErvScrim,
    surfaceDim = ErvSurfaceDim,
    surfaceBright = ErvSurfaceBright,
    surfaceContainerLowest = ErvSurfaceContainerLowest,
    surfaceContainerLow = ErvSurfaceContainerLow,
    surfaceContainer = ErvSurfaceContainer,
    surfaceContainerHigh = ErvSurfaceContainerHigh,
    surfaceContainerHighest = ErvSurfaceContainerHighest,
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
    scrim = ErvDarkScrim,
    surfaceDim = ErvDarkSurfaceDim,
    surfaceBright = ErvDarkSurfaceBright,
    surfaceContainerLowest = ErvDarkSurfaceContainerLowest,
    surfaceContainerLow = ErvDarkSurfaceContainerLow,
    surfaceContainer = ErvDarkSurfaceContainer,
    surfaceContainerHigh = ErvDarkSurfaceContainerHigh,
    surfaceContainerHighest = ErvDarkSurfaceContainerHighest,
)

/** True when the ERV dark palette is active (follows the in-app Appearance setting, not just the system). */
val LocalErvDarkTheme = staticCompositionLocalOf { false }

@Composable
fun ErvTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ErvDarkColorScheme else ErvLightColorScheme
    val systemBars = remember { ErvSystemBarController() }

    val view = LocalView.current
    val hostContext = LocalContext.current
    if (!view.isInEditMode) {
        LaunchedEffect(systemBars, colorScheme, darkTheme) {
            snapshotFlow { systemBars.top()?.let { it.color to it.lightIcons } }
                .collect { request ->
                    val statusColor = request?.first ?: colorScheme.background
                    val lightStatusIcons = request?.second ?: darkTheme
                    view.doOnAttach {
                        applySystemBarColors(
                            view = view,
                            hostContext = hostContext,
                            statusBarColor = statusColor,
                            lightStatusIcons = lightStatusIcons,
                            navigationBarColor = colorScheme.background,
                            lightNavigationIcons = darkTheme,
                        )
                    }
                }
        }
    }

    CompositionLocalProvider(
        LocalErvDarkTheme provides darkTheme,
        LocalErvSystemBars provides systemBars,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ErvTypography,
            content = content
        )
    }
}

private fun applySystemBarColors(
    view: android.view.View,
    hostContext: Context,
    statusBarColor: Color,
    lightStatusIcons: Boolean,
    navigationBarColor: Color,
    lightNavigationIcons: Boolean,
) {
    // view.context is often ContextThemeWrapper, not Activity — casting caused ClassCastException
    // in bubble activities and other embedded windows.
    val activity = hostContext.findActivity() ?: return
    try {
        val window = activity.window
        window.statusBarColor = statusBarColor.toArgb()
        window.navigationBarColor = navigationBarColor.toArgb()
        WindowCompat.getInsetsController(window, view)?.let { c ->
            c.isAppearanceLightStatusBars = !lightStatusIcons
            c.isAppearanceLightNavigationBars = !lightNavigationIcons
        }
    } catch (_: Throwable) {
        // Ignore: bubble / embedded / transient window states
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
