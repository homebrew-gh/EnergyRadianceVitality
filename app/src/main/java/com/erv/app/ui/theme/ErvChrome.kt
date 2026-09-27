package com.erv.app.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Stack of status bar color requests. The most recently entered screen wins; when it leaves the
 * composition the previous request (or the theme default) applies again. A stack is needed because
 * navigation disposes the outgoing screen after the incoming one has already composed.
 */
class ErvSystemBarController {
    private val requests = mutableStateListOf<StatusBarRequest>()

    internal fun top(): StatusBarRequest? = requests.lastOrNull()
    internal fun add(request: StatusBarRequest) { requests.add(request) }
    internal fun remove(request: StatusBarRequest) { requests.remove(request) }
}

internal class StatusBarRequest(color: Color, lightIcons: Boolean) {
    var color by mutableStateOf(color)
    var lightIcons by mutableStateOf(lightIcons)
}

internal val LocalErvSystemBars = staticCompositionLocalOf<ErvSystemBarController?> { null }

/** Tints the status bar to match a colored header while this composable is on screen. */
@Composable
fun ErvStatusBarColor(color: Color, lightIcons: Boolean = true) {
    val controller = LocalErvSystemBars.current ?: return
    val request = remember { StatusBarRequest(color, lightIcons) }
    SideEffect {
        request.color = color
        request.lightIcons = lightIcons
    }
    DisposableEffect(controller, request) {
        controller.add(request)
        onDispose { controller.remove(request) }
    }
}

/** Default top bar: blends into the page background so content, not chrome, carries the color. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ervTopAppBarColors(): TopAppBarColors {
    val scheme = MaterialTheme.colorScheme
    return TopAppBarDefaults.topAppBarColors(
        containerColor = scheme.background,
        scrolledContainerColor = scheme.surfaceContainer,
        titleContentColor = scheme.onBackground,
        navigationIconContentColor = scheme.onBackground,
        actionIconContentColor = scheme.onSurfaceVariant,
    )
}

/**
 * Saturated header for live sessions. Also tints the status bar to [containerColor] so the header
 * reads as one block.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ervSessionTopAppBarColors(containerColor: Color = ErvSessionRed): TopAppBarColors {
    ErvStatusBarColor(containerColor)
    return TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        titleContentColor = Color.White,
        navigationIconContentColor = Color.White,
        actionIconContentColor = Color.White,
    )
}

@Composable
fun ervLogoSunColor(): Color = if (LocalErvDarkTheme.current) ErvLogoSunDark else ErvLogoSunLight

/** Accent for a category id (see `categories` in CategorySheet). Falls back to the theme primary. */
@Composable
fun ervCategoryAccent(categoryId: String): Color {
    val dark = LocalErvDarkTheme.current
    return when (categoryId) {
        "weight_training" -> if (dark) ErvAccentWeightDark else ErvAccentWeightLight
        "cardio" -> if (dark) ErvAccentCardioDark else ErvAccentCardioLight
        "stretching" -> if (dark) ErvAccentStretchDark else ErvAccentStretchLight
        "light_therapy" -> if (dark) ErvAccentLightTherapyDark else ErvAccentLightTherapyLight
        "supplements" -> if (dark) ErvAccentSupplementsDark else ErvAccentSupplementsLight
        "fasting" -> if (dark) ErvAccentFastingDark else ErvAccentFastingLight
        "body_tracker" -> if (dark) ErvAccentBodyDark else ErvAccentBodyLight
        else -> MaterialTheme.colorScheme.primary
    }
}
