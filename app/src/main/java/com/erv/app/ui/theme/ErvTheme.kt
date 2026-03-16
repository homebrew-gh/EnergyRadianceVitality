package com.erv.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
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

@Composable
fun ErvTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Dark theme uses same scheme for now; a dedicated dark palette can be added later
    val colorScheme = ErvLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
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
