package com.dawood.orbit.core.designsystem.theme

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import com.dawood.orbit.core.designsystem.foundation.OrbitIndication
import com.dawood.orbit.core.designsystem.token.OrbitAccent
import com.dawood.orbit.core.designsystem.token.OrbitAccents
import com.dawood.orbit.core.designsystem.token.OrbitColors
import com.dawood.orbit.core.designsystem.token.OrbitElevation
import com.dawood.orbit.core.designsystem.token.OrbitMotion
import com.dawood.orbit.core.designsystem.token.OrbitRadius
import com.dawood.orbit.core.designsystem.token.OrbitSizes
import com.dawood.orbit.core.designsystem.token.OrbitSpacing
import com.dawood.orbit.core.designsystem.token.OrbitTypography
import com.dawood.orbit.core.designsystem.token.orbitDarkColors
import com.dawood.orbit.core.designsystem.token.orbitLightColors

val LocalOrbitColors = compositionLocalOf<OrbitColors> {
    error("OrbitColors not provided. Wrap the content in OrbitTheme { }.")
}
val LocalOrbitTypography = staticCompositionLocalOf { OrbitTypography() }
val LocalOrbitSpacing = staticCompositionLocalOf { OrbitSpacing() }
val LocalOrbitRadius = staticCompositionLocalOf { OrbitRadius() }
val LocalOrbitSizes = staticCompositionLocalOf { OrbitSizes() }
val LocalOrbitElevation = staticCompositionLocalOf { OrbitElevation() }
val LocalOrbitMotion = staticCompositionLocalOf { OrbitMotion() }

/** Default text style inherited by `OrbitText` so slots need not restate it. */
val LocalOrbitTextStyle = compositionLocalOf { OrbitTypography().body }

/** Dims a whole subtree (disabled controls) without per-component branching. */
val LocalOrbitContentAlpha = compositionLocalOf { 1f }

/** Entry point for every token lookup: `OrbitTheme.colors.accent`. */
object OrbitTheme {
    val colors: OrbitColors
        @Composable @ReadOnlyComposable get() = LocalOrbitColors.current
    val typography: OrbitTypography
        @Composable @ReadOnlyComposable get() = LocalOrbitTypography.current
    val spacing: OrbitSpacing
        @Composable @ReadOnlyComposable get() = LocalOrbitSpacing.current
    val radius: OrbitRadius
        @Composable @ReadOnlyComposable get() = LocalOrbitRadius.current
    val sizes: OrbitSizes
        @Composable @ReadOnlyComposable get() = LocalOrbitSizes.current
    val elevation: OrbitElevation
        @Composable @ReadOnlyComposable get() = LocalOrbitElevation.current
    val motion: OrbitMotion
        @Composable @ReadOnlyComposable get() = LocalOrbitMotion.current
}

enum class ThemeMode { System, Light, Dark }

@Composable
fun ThemeMode.resolveIsDark(): Boolean = when (this) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun OrbitTheme(
    themeMode: ThemeMode = ThemeMode.System,
    accent: OrbitAccent = OrbitAccents.Indigo,
    content: @Composable () -> Unit,
) {
    val dark = themeMode.resolveIsDark()
    val reduceMotion = rememberReduceMotion()
    val motion = remember(reduceMotion) { OrbitMotion(reduceMotion) }

    val target = remember(dark, accent) {
        if (dark) orbitDarkColors(accent) else orbitLightColors(accent)
    }
    // Theme and accent changes cross-fade rather than snap, so switching them in
    // Settings never feels like a different app blinked into place.
    val colors = animateOrbitColors(target, motion.slow)

    CompositionLocalProvider(
        LocalOrbitColors provides colors,
        LocalOrbitMotion provides motion,
        LocalOrbitTextStyle provides OrbitTypography().body.copy(color = colors.textPrimary),
        LocalIndication provides rememberOrbitIndication(colors.isDark),
        content = content,
    )
}

@Composable
private fun rememberOrbitIndication(isDark: Boolean): OrbitIndication = remember(isDark) {
    if (isDark) {
        OrbitIndication(
            overlay = Color.White,
            pressedAlpha = 0.055f,
            hoveredAlpha = 0.030f,
            focusedAlpha = 0.040f,
        )
    } else {
        OrbitIndication(
            overlay = Color.Black,
            pressedAlpha = 0.060f,
            hoveredAlpha = 0.030f,
            focusedAlpha = 0.040f,
        )
    }
}

@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

@Composable
private fun animateColor(target: Color, durationMillis: Int): Color =
    animateColorAsState(target, tween(durationMillis), label = "orbitColor").value

@Composable
private fun animateOrbitColors(target: OrbitColors, durationMillis: Int): OrbitColors =
    OrbitColors(
        isDark = target.isDark,
        backgroundBase = animateColor(target.backgroundBase, durationMillis),
        backgroundSubtle = animateColor(target.backgroundSubtle, durationMillis),
        surface = animateColor(target.surface, durationMillis),
        surfaceElevated = animateColor(target.surfaceElevated, durationMillis),
        surfaceSunken = animateColor(target.surfaceSunken, durationMillis),
        surfaceHover = animateColor(target.surfaceHover, durationMillis),
        surfacePressed = animateColor(target.surfacePressed, durationMillis),
        surfaceSelected = animateColor(target.surfaceSelected, durationMillis),
        glassSurface = animateColor(target.glassSurface, durationMillis),
        glassBorder = animateColor(target.glassBorder, durationMillis),
        border = animateColor(target.border, durationMillis),
        borderStrong = animateColor(target.borderStrong, durationMillis),
        borderSubtle = animateColor(target.borderSubtle, durationMillis),
        textPrimary = animateColor(target.textPrimary, durationMillis),
        textSecondary = animateColor(target.textSecondary, durationMillis),
        textMuted = animateColor(target.textMuted, durationMillis),
        textPlaceholder = animateColor(target.textPlaceholder, durationMillis),
        textInverse = animateColor(target.textInverse, durationMillis),
        textOnAccent = animateColor(target.textOnAccent, durationMillis),
        accent = animateColor(target.accent, durationMillis),
        accentHover = animateColor(target.accentHover, durationMillis),
        accentPressed = animateColor(target.accentPressed, durationMillis),
        accentSubtle = animateColor(target.accentSubtle, durationMillis),
        accentSubtleHover = animateColor(target.accentSubtleHover, durationMillis),
        accentBorder = animateColor(target.accentBorder, durationMillis),
        focusRing = animateColor(target.focusRing, durationMillis),
        success = animateColor(target.success, durationMillis),
        successSubtle = animateColor(target.successSubtle, durationMillis),
        warning = animateColor(target.warning, durationMillis),
        warningSubtle = animateColor(target.warningSubtle, durationMillis),
        error = animateColor(target.error, durationMillis),
        errorSubtle = animateColor(target.errorSubtle, durationMillis),
        info = animateColor(target.info, durationMillis),
        infoSubtle = animateColor(target.infoSubtle, durationMillis),
        scrim = animateColor(target.scrim, durationMillis),
        shadowAmbient = target.shadowAmbient,
        shadowSpot = target.shadowSpot,
        skeleton = animateColor(target.skeleton, durationMillis),
        skeletonHighlight = animateColor(target.skeletonHighlight, durationMillis),
    )

/** Resolves the effective text style for a slot, honouring disabled subtrees. */
@Composable
fun orbitTextStyle(style: TextStyle?, color: Color?): TextStyle {
    val base = style ?: LocalOrbitTextStyle.current
    val alpha = LocalOrbitContentAlpha.current
    val fallback = OrbitTheme.colors.textPrimary
    val resolved = color
        ?: base.color.takeIf { it != Color.Unspecified }
        ?: fallback
    return base.copy(color = resolved.copy(alpha = resolved.alpha * alpha))
}
