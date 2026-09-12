package com.dawood.orbit.core.designsystem.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Semantic colour tokens.
 *
 * Nothing in the app should reference a raw hex value. Every surface, text
 * colour and border resolves through this object so that a theme change (light
 * / dark) or an accent change repaints the whole product without touching a
 * single component.
 */
@Immutable
data class OrbitColors(
    val isDark: Boolean,

    // Canvas & surfaces
    val backgroundBase: Color,
    val backgroundSubtle: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceSunken: Color,
    val surfaceHover: Color,
    val surfacePressed: Color,
    val surfaceSelected: Color,

    // Translucent chrome (top bar, sidebar, sheets)
    val glassSurface: Color,
    val glassBorder: Color,

    // Lines
    val border: Color,
    val borderStrong: Color,
    val borderSubtle: Color,

    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textPlaceholder: Color,
    val textInverse: Color,
    val textOnAccent: Color,

    // Accent ramp
    val accent: Color,
    val accentHover: Color,
    val accentPressed: Color,
    val accentSubtle: Color,
    val accentSubtleHover: Color,
    val accentBorder: Color,
    val focusRing: Color,

    // Status
    val success: Color,
    val successSubtle: Color,
    val warning: Color,
    val warningSubtle: Color,
    val error: Color,
    val errorSubtle: Color,
    val info: Color,
    val infoSubtle: Color,

    // Utility
    val scrim: Color,
    val shadowAmbient: Color,
    val shadowSpot: Color,
    val skeleton: Color,
    val skeletonHighlight: Color,
)

/**
 * A selectable accent identity. The accent is the single point of colour in an
 * otherwise neutral product, so it is defined once and derived everywhere.
 */
@Immutable
data class OrbitAccent(
    val id: String,
    val label: String,
    val light: Color,
    val lightHover: Color,
    val lightPressed: Color,
    val dark: Color,
    val darkHover: Color,
    val darkPressed: Color,
    val onAccent: Color = Color.White,
) {
    fun base(isDark: Boolean): Color = if (isDark) dark else light
}

object OrbitAccents {
    val Indigo = OrbitAccent(
        id = "indigo",
        label = "Indigo",
        light = Color(0xFF5B5BD6),
        lightHover = Color(0xFF5150C4),
        lightPressed = Color(0xFF4544AE),
        dark = Color(0xFF8A87FF),
        darkHover = Color(0xFF9C99FF),
        darkPressed = Color(0xFF7C79F0),
    )

    val Violet = OrbitAccent(
        id = "violet",
        label = "Violet",
        light = Color(0xFF7C4DDA),
        lightHover = Color(0xFF6F43C6),
        lightPressed = Color(0xFF6039AE),
        dark = Color(0xFFAE8CFF),
        darkHover = Color(0xFFBE9FFF),
        darkPressed = Color(0xFF9E7CF0),
    )

    val Blue = OrbitAccent(
        id = "blue",
        label = "Blue",
        light = Color(0xFF2F6FED),
        lightHover = Color(0xFF2A63D6),
        lightPressed = Color(0xFF2455BC),
        dark = Color(0xFF6FA0FF),
        darkHover = Color(0xFF85AEFF),
        darkPressed = Color(0xFF5E90F0),
    )

    val Teal = OrbitAccent(
        id = "teal",
        label = "Teal",
        light = Color(0xFF0E8C7A),
        lightHover = Color(0xFF0C7C6C),
        lightPressed = Color(0xFF0A6A5C),
        dark = Color(0xFF34C5AC),
        darkHover = Color(0xFF4AD3BC),
        darkPressed = Color(0xFF2BB39C),
    )

    val Amber = OrbitAccent(
        id = "amber",
        label = "Amber",
        light = Color(0xFFB26A00),
        lightHover = Color(0xFF9E5E00),
        lightPressed = Color(0xFF875000),
        dark = Color(0xFFF0A93B),
        darkHover = Color(0xFFFFB851),
        darkPressed = Color(0xFFDB982F),
    )

    val Rose = OrbitAccent(
        id = "rose",
        label = "Rose",
        light = Color(0xFFD2455F),
        lightHover = Color(0xFFBC3E55),
        lightPressed = Color(0xFFA33549),
        dark = Color(0xFFFF8095),
        darkHover = Color(0xFFFF93A5),
        darkPressed = Color(0xFFF06F85),
    )

    val Graphite = OrbitAccent(
        id = "graphite",
        label = "Graphite",
        light = Color(0xFF3E4149),
        lightHover = Color(0xFF33363D),
        lightPressed = Color(0xFF282A30),
        dark = Color(0xFFC3C7D0),
        darkHover = Color(0xFFD4D8E0),
        darkPressed = Color(0xFFB0B4BD),
        onAccent = Color(0xFF15161A),
    )

    val all = listOf(Indigo, Violet, Blue, Teal, Amber, Rose, Graphite)

    fun fromId(id: String?): OrbitAccent = all.firstOrNull { it.id == id } ?: Indigo
}

private object Neutral {
    // Light
    val L0 = Color(0xFFFFFFFF)
    val L50 = Color(0xFFF9F9FB)
    val L100 = Color(0xFFF4F4F7)
    val L150 = Color(0xFFEEEEF2)
    val L200 = Color(0xFFE7E7EC)
    val L300 = Color(0xFFD6D7DE)
    val L500 = Color(0xFF8B8E99)
    val L600 = Color(0xFF6B6E79)
    val L700 = Color(0xFF52555F)
    val L900 = Color(0xFF17181C)

    // Dark
    val D0 = Color(0xFF0A0A0D)
    val D50 = Color(0xFF0F1014)
    val D100 = Color(0xFF141519)
    val D150 = Color(0xFF1A1C21)
    val D200 = Color(0xFF202228)
    val D250 = Color(0xFF26282F)
    val D300 = Color(0xFF34363E)
    val D500 = Color(0xFF6E7280)
    val D600 = Color(0xFF8B8F9B)
    val D700 = Color(0xFFA8ACB7)
    val D900 = Color(0xFFF2F3F6)
}

fun orbitLightColors(accent: OrbitAccent): OrbitColors = OrbitColors(
    isDark = false,

    backgroundBase = Color(0xFFF6F6F8),
    backgroundSubtle = Neutral.L100,
    surface = Neutral.L0,
    surfaceElevated = Neutral.L0,
    surfaceSunken = Neutral.L100,
    surfaceHover = Neutral.L100,
    surfacePressed = Neutral.L150,
    surfaceSelected = accent.light.copy(alpha = 0.09f),

    glassSurface = Color(0xF2FFFFFF),
    glassBorder = Color(0x14000000),

    border = Neutral.L200,
    borderStrong = Neutral.L300,
    borderSubtle = Neutral.L150,

    textPrimary = Neutral.L900,
    textSecondary = Neutral.L700,
    textMuted = Neutral.L500,
    textPlaceholder = Color(0xFFA6A9B3),
    textInverse = Neutral.L0,
    textOnAccent = accent.onAccent,

    accent = accent.light,
    accentHover = accent.lightHover,
    accentPressed = accent.lightPressed,
    accentSubtle = accent.light.copy(alpha = 0.10f),
    accentSubtleHover = accent.light.copy(alpha = 0.16f),
    accentBorder = accent.light.copy(alpha = 0.30f),
    focusRing = accent.light.copy(alpha = 0.45f),

    success = Color(0xFF16855A),
    successSubtle = Color(0xFF16855A).copy(alpha = 0.11f),
    warning = Color(0xFF9A5F06),
    warningSubtle = Color(0xFFB8790C).copy(alpha = 0.14f),
    error = Color(0xFFC3364B),
    errorSubtle = Color(0xFFC3364B).copy(alpha = 0.10f),
    info = Color(0xFF2F6FED),
    infoSubtle = Color(0xFF2F6FED).copy(alpha = 0.10f),

    scrim = Color(0xFF0D0E12).copy(alpha = 0.34f),
    shadowAmbient = Color(0xFF0B0C10).copy(alpha = 0.10f),
    shadowSpot = Color(0xFF0B0C10).copy(alpha = 0.14f),
    skeleton = Neutral.L150,
    skeletonHighlight = Neutral.L50,
)

fun orbitDarkColors(accent: OrbitAccent): OrbitColors = OrbitColors(
    isDark = true,

    backgroundBase = Neutral.D0,
    backgroundSubtle = Neutral.D50,
    surface = Neutral.D100,
    surfaceElevated = Neutral.D150,
    surfaceSunken = Neutral.D50,
    surfaceHover = Neutral.D200,
    surfacePressed = Neutral.D250,
    surfaceSelected = accent.dark.copy(alpha = 0.15f),

    glassSurface = Color(0xF01A1C21),
    glassBorder = Color(0x1FFFFFFF),

    border = Neutral.D250,
    borderStrong = Neutral.D300,
    borderSubtle = Color(0xFF1E1F25),

    textPrimary = Neutral.D900,
    textSecondary = Neutral.D700,
    textMuted = Neutral.D500,
    textPlaceholder = Color(0xFF5F636D),
    textInverse = Neutral.D0,
    textOnAccent = if (accent.id == "graphite") Color(0xFF15161A) else Color(0xFF11121A),

    accent = accent.dark,
    accentHover = accent.darkHover,
    accentPressed = accent.darkPressed,
    accentSubtle = accent.dark.copy(alpha = 0.16f),
    accentSubtleHover = accent.dark.copy(alpha = 0.24f),
    accentBorder = accent.dark.copy(alpha = 0.34f),
    focusRing = accent.dark.copy(alpha = 0.55f),

    success = Color(0xFF45CE8E),
    successSubtle = Color(0xFF45CE8E).copy(alpha = 0.16f),
    warning = Color(0xFFF0B44B),
    warningSubtle = Color(0xFFF0B44B).copy(alpha = 0.16f),
    error = Color(0xFFFF7183),
    errorSubtle = Color(0xFFFF7183).copy(alpha = 0.16f),
    info = Color(0xFF7FADFF),
    infoSubtle = Color(0xFF7FADFF).copy(alpha = 0.16f),

    scrim = Color(0xFF000000).copy(alpha = 0.58f),
    shadowAmbient = Color(0xFF000000).copy(alpha = 0.44f),
    shadowSpot = Color(0xFF000000).copy(alpha = 0.55f),
    skeleton = Neutral.D200,
    skeletonHighlight = Neutral.D250,
)
