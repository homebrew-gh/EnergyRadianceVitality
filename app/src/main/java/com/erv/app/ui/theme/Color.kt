package com.erv.app.ui.theme

import androidx.compose.ui.graphics.Color

// --- Light palette: sun-drenched warmth ---

val ErvPrimary = Color(0xFFC45C26)          // burnt orange
val ErvOnPrimary = Color(0xFFFFFFFF)
val ErvPrimaryContainer = Color(0xFFFFE0CC) // light peach
val ErvOnPrimaryContainer = Color(0xFF3D1600)

val ErvSecondary = Color(0xFFD4A04A)        // golden amber
val ErvOnSecondary = Color(0xFFFFFFFF)
val ErvSecondaryContainer = Color(0xFFFFECC8) // warm gold tint
val ErvOnSecondaryContainer = Color(0xFF3A2900)

val ErvTertiary = Color(0xFF8B6B4A)         // warm brown
val ErvOnTertiary = Color(0xFFFFFFFF)
val ErvTertiaryContainer = Color(0xFFF5E0C8)
val ErvOnTertiaryContainer = Color(0xFF2E1A06)

val ErvBackground = Color(0xFFFFFBF7)       // very warm white
val ErvOnBackground = Color(0xFF2C1810)

val ErvSurface = Color(0xFFFFF8F0)          // warm cream
val ErvOnSurface = Color(0xFF2C1810)        // dark brown
val ErvSurfaceVariant = Color(0xFFF5E6D8)   // slightly darker cream
val ErvOnSurfaceVariant = Color(0xFF5C4434) // medium brown

val ErvOutline = Color(0xFF8C7060)          // muted brown
val ErvOutlineVariant = Color(0xFFCFBDAD)

val ErvError = Color(0xFFBA1A1A)
val ErvOnError = Color(0xFFFFFFFF)
val ErvErrorContainer = Color(0xFFFFDAD6)
val ErvOnErrorContainer = Color(0xFF410002)

val ErvInverseSurface = Color(0xFF362F2B)
val ErvInverseOnSurface = Color(0xFFFAEFE7)
val ErvInversePrimary = Color(0xFFFFB68E)

val ErvScrim = Color(0xFF000000)

// --- Dark palette: warm ember glow with red-wine surfaces ---

val ErvDarkPrimary = Color(0xFFFFA38A)           // warm coral
val ErvDarkOnPrimary = Color(0xFF5A1D19)
val ErvDarkPrimaryContainer = Color(0xFF8A2E2A)  // deep red-orange
val ErvDarkOnPrimaryContainer = Color(0xFFFFE5E2)

val ErvDarkSecondary = Color(0xFFFFC48A)         // amber peach
val ErvDarkOnSecondary = Color(0xFF4A2310)
val ErvDarkSecondaryContainer = Color(0xFF6A3328)
val ErvDarkOnSecondaryContainer = Color(0xFFFFE8D7)

val ErvDarkTertiary = Color(0xFFE1A899)          // dusty rose
val ErvDarkOnTertiary = Color(0xFF3C1D1D)
val ErvDarkTertiaryContainer = Color(0xFF633036)
val ErvDarkOnTertiaryContainer = Color(0xFFFFE3E2)

val ErvDarkBackground = Color(0xFF160B0E)        // deep wine
val ErvDarkOnBackground = Color(0xFFF7E6E4)

val ErvDarkSurface = Color(0xFF251116)           // wine surface
val ErvDarkOnSurface = Color(0xFFF7E6E4)         // warm rose-cream text
val ErvDarkSurfaceVariant = Color(0xFF432126)    // muted wine
val ErvDarkOnSurfaceVariant = Color(0xFFD9B9B7)

val ErvDarkOutline = Color(0xFFB07A7A)           // muted rose
val ErvDarkOutlineVariant = Color(0xFF634145)

val ErvDarkError = Color(0xFFFFB4AB)
val ErvDarkOnError = Color(0xFF690005)
val ErvDarkErrorContainer = Color(0xFF93000A)
val ErvDarkOnErrorContainer = Color(0xFFFFB4AB)

val ErvDarkInverseSurface = Color(0xFFF5E6D8)
val ErvDarkInverseOnSurface = Color(0xFF2C1810)
val ErvDarkInversePrimary = Color(0xFFC45C26)

val ErvDarkScrim = Color(0xFF000000)

// --- Light Therapy accent (theme-aware) ---
// Light theme: darker red so cards/top bar stand out on light background
val ErvLightTherapyRedDark = Color(0xFF2E0808)
val ErvLightTherapyRedMid = Color(0xFF6B0000)
val ErvLightTherapyRedGlow = Color(0xFF7A1515)
// Dark theme: slightly lighter red so they stand out on dark background
val ErvDarkTherapyRedDark = Color(0xFF3D1212)
val ErvDarkTherapyRedMid = Color(0xFF9B2222)
val ErvDarkTherapyRedGlow = Color(0xFFB83A3A)

// --- Sauna / hot side: use same red family as Light therapy (see ErvLightTherapyRed*). ---

// --- Cold Plunge accent (theme-aware, soft desaturated blues; stops kept mid-toned so white timer text reads well) ---
val ErvColdDark = Color(0xFF34495C)
val ErvColdMid = Color(0xFF5F83A0)
val ErvColdGlow = Color(0xFF7FA3C0)
val ErvDarkColdDark = Color(0xFF394E64)
val ErvDarkColdMid = Color(0xFF5E80A0)
val ErvDarkColdGlow = Color(0xFF7E9FC0)

// Category bottom sheet (dashboard menu). Light = airy yellow-gold.
val ErvCategoryMenuMutedGold = Color(0xFFFFEBB0)
// Dark = solid gold sheet; category tiles use default elevated Material colors (same as dashboard routines).
val ErvDarkCategoryMenuMutedGold = Color(0xFFD4AF37)
/** "MENU" label on the sheet handle (readable on gold); tiles use theme elevated surfaces. */
val ErvDarkCategoryMenuOnSurface = Color(0xFF1A1408)
val ErvDarkCategoryMenuHandleAccent = Color(0xFFA67C00)
val ErvDarkCategoryMenuDivider = Color(0xFF926E0A)

// Calendar: day cell when any activity was logged (supplements, light, cardio, weight, sauna/cold).
val ErvCalendarActivityDayLight = Color(0xFFFFF0D4)
val ErvCalendarActivityDayDark = Color(0xFF3D3518)
