package com.dawood.orbit.core.designsystem.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The single font family for the product. Swapping in a bundled face (Inter,
 * Geist, …) later is a one-line change here and nothing else moves.
 */
val OrbitFontFamily: FontFamily = FontFamily.Default
val OrbitMonoFamily: FontFamily = FontFamily.Monospace

private val TrimmedLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Float = 0f,
    family: FontFamily = OrbitFontFamily,
) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
    lineHeightStyle = TrimmedLineHeight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/**
 * A closed type scale. Components pick a role from here; arbitrary font sizes
 * are not used anywhere in the app.
 */
@Immutable
data class OrbitTypography(
    /** Hero numerals and one-per-screen statements. */
    val display: TextStyle = style(32, 38, FontWeight.SemiBold, -0.022f),
    /** Screen title. */
    val h1: TextStyle = style(24, 30, FontWeight.SemiBold, -0.018f),
    /** Section title. */
    val h2: TextStyle = style(19, 25, FontWeight.SemiBold, -0.014f),
    /** Card / group title. */
    val h3: TextStyle = style(16, 22, FontWeight.SemiBold, -0.010f),
    /** Dense list heading. */
    val h4: TextStyle = style(14, 19, FontWeight.SemiBold, -0.006f),

    val bodyLarge: TextStyle = style(16, 25, FontWeight.Normal, -0.004f),
    val body: TextStyle = style(14, 21, FontWeight.Normal, -0.002f),
    val bodySmall: TextStyle = style(13, 19, FontWeight.Normal, 0f),

    /** Buttons, tabs, field labels — anything interactive. */
    val label: TextStyle = style(14, 18, FontWeight.Medium, -0.002f),
    val labelSmall: TextStyle = style(12, 16, FontWeight.Medium, 0.002f),

    /** Metadata under a title, timestamps, helper text. */
    val caption: TextStyle = style(12, 16, FontWeight.Normal, 0.002f),
    /** Uppercase section eyebrow. */
    val overline: TextStyle = style(11, 14, FontWeight.SemiBold, 0.06f),

    val mono: TextStyle = style(13, 20, FontWeight.Normal, 0f, OrbitMonoFamily),
    val monoSmall: TextStyle = style(11, 17, FontWeight.Normal, 0f, OrbitMonoFamily),
)
