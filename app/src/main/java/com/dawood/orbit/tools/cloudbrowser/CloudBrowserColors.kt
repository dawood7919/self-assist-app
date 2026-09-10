package com.dawood.orbit.tools.cloudbrowser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Exact visual copy of the Cloud Browser HTML mockup (feature-local override).
 *
 * The user explicitly ordered a pixel-faithful replica of the mockup for this
 * tool only, which overrides the Orbit design-system rules here (same
 * precedent as the calculator's feature-local styling). Every token below is
 * a FIXED mockup value: the mockup is dark-only, so these colors do not
 * adapt to light/dark theme.
 *
 * Mockup tokens: bg #0B1220, panel #121B2E, panel2 #182338, line #233047,
 * lineSoft #1B2740, text #EAF0FB, dim #8FA0BD, faint #5C6C88, blue #2F6BFF,
 * blueDim #1C3F8F, green #22C55E, red #EF4444, amber #F59E0B, purple #A855F7.
 *
 * Single source for ALL cloud-browser colors, radii, type scale and spacing:
 * screens and sheets reference only these members (plus [CloudSpacing]).
 */
object CloudColors {

    // ------------------------------------------------------------------
    // Fixed mockup palette (dark-only, no theme adaptation).
    // ------------------------------------------------------------------

    val Bg = Color(0xFF0B1220)
    val Panel = Color(0xFF121B2E)
    val Panel2 = Color(0xFF182338)
    val Line = Color(0xFF233047)
    val LineSoft = Color(0xFF1B2740)
    val Text = Color(0xFFEAF0FB)
    val Dim = Color(0xFF8FA0BD)
    val Faint = Color(0xFF5C6C88)
    val Blue = Color(0xFF2F6BFF)
    val BlueDim = Color(0xFF1C3F8F)
    val Green = Color(0xFF22C55E)
    val Red = Color(0xFFEF4444)
    val Amber = Color(0xFFF59E0B)
    val Purple = Color(0xFFA855F7)
    val White = Color(0xFFFFFFFF)

    /** Active-pill / highlighted text on blue fills. */
    val OnBlue = Color(0xFFDBE6FF)

    /** Intro hero gradient stops (#0E2A4D -> #123A63 -> #0B1220). */
    val HeroTop = Color(0xFF0E2A4D)
    val HeroMid = Color(0xFF123A63)

    /** Google wordmark multicolor letters (mockup-exact brand colors). */
    val GoogleBlue = Color(0xFF4285F4)
    val GoogleRed = Color(0xFFEA4335)
    val GoogleYellow = Color(0xFFFBBC05)
    val GoogleGreen = Color(0xFF34A853)

    /** Active-session preview gradient stops (#112233 -> #2A4D7A). */
    val PreviewStart = Color(0xFF112233)
    val PreviewEnd = Color(0xFF2A4D7A)

    /**
     * Tinted veil behind the stream placeholder and fullscreen preview.
     *
     * Fixed Black 60% overlay (mockup-exact). Replaces the former
     * 55%-alpha scrim so a single token covers the 60% overlay used by
     * the active-session 4K badge scrim.
     */
    val Scrim = Color(0xFF000000).copy(alpha = 0.6f)

    // ------------------------------------------------------------------
    // Fixed mockup geometry.
    // ------------------------------------------------------------------

    /** Card: panel bg + 1dp line border + 12dp radius + 10dp padding. */
    val CardRadius = 12.dp
    val CardPadding = 10.dp

    /** Primary/outline buttons: 10dp radius. */
    val ButtonRadius = 10.dp

    /** Pills: panel2 + line border + 8dp radius, dim text. */
    val PillRadius = 8.dp

    /** Badges: 6dp radius. */
    val BadgeRadius = 6.dp

    /** Small chips (4K badge scrim, progress caps): 4dp radius. */
    val ChipRadius = 4.dp

    /** Screen body horizontal padding. */
    val BodyPaddingH = 12.dp

    // ------------------------------------------------------------------
    // Fixed mockup type scale.
    // ------------------------------------------------------------------

    /** Label text: dim 11sp. */
    val LabelSize = 11.sp

    /** Body text: 14sp. */
    val BodySize = 14.sp

    /** Small text: 12sp dim. */
    val SmallSize = 12.sp

    /** Title text: 16sp bold. */
    val TitleSize = 16.sp

    /** Badge text: 10sp. */
    val BadgeSize = 10.sp

    /** Bottom-nav emoji glyphs: 16sp; labels 10sp. */
    val NavEmojiSize = 16.sp
    val NavLabelSize = 10.sp

    /** Micro badge text (4K overlay): 8sp. */
    val MicroSize = 8.sp

    /** Large wordmark / preview glyph: 20sp. */
    val LargeSize = 20.sp

    /** Google wordmark letters / feature emoji: 22sp. */
    val WordmarkSize = 22.sp

    /** Detail line height (session rows): 25sp. */
    val DetailLineHeight = 25.sp

    /** Intro hero emoji: 40sp. */
    val HeroEmojiSize = 40.sp

    /** Google wordmark letter spacing: 1sp. */
    val LetterSpacing = 1.sp
}

/**
 * Single source for cloud-browser spacing and fixed mockup geometry.
 *
 * All dp literals in the tool reference these members (or the radius /
 * padding members on [CloudColors]) so no screen or sheet holds its own
 * dimension values.
 */
object CloudSpacing {
    /** Hairline borders and dividers. */
    val BorderWidth = 1.dp

    /** 2dp micro padding / gaps. */
    val PadXxs = 2.dp

    /** 4dp compact padding / gaps / progress height. */
    val PadXs = 4.dp

    /** 6dp icon padding. */
    val PadIcon = 6.dp

    /** Settings / file rows vertical padding: 7dp. */
    val RowPadY = 7.dp

    /** 8dp standard gaps and button vertical padding. */
    val PadSm = 8.dp

    /** 12dp screen gaps and section padding. */
    val PadMd = 12.dp

    /** Wide toolbar gaps (input overlay): 20dp. */
    val ToolbarGap = 20.dp

    /** Logo box size (mockup 44dp). */
    val ButtonHeight = 44.dp

    /** Settings toggle: 30x16dp. */
    val ToggleW = 30.dp
    val ToggleH = 16.dp

    /** Monitor / download track height: 5dp gauge. */
    val GaugeSize = 5.dp

    /** Zoom slider thumb / track / touch target. */
    val ThumbSize = 16.dp
    val TrackHeight = 4.dp
    val TouchHeight = 32.dp

    /** Settings toggle knob: 12dp. */
    val KnobSize = 12.dp

    /** Preview / touchpad / hero heights. */
    val PreviewHeight = 80.dp
    val TouchpadHeight = 120.dp
    val HeroHeight = 140.dp

    /** Track corner radii. */
    val TrackCorner = 2.5.dp
    val ProgressRadius = 2.dp
}
