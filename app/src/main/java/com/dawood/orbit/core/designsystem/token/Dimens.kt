package com.dawood.orbit.core.designsystem.token

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A 4pt spacing ramp. Layout never invents its own gaps. */
@Immutable
data class OrbitSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
    val huge: Dp = 40.dp,
    val giant: Dp = 56.dp,
)

@Immutable
data class OrbitRadius(
    val xs: Dp = 6.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 28.dp,
) {
    val shapeXs: Shape get() = RoundedCornerShape(xs)
    val shapeSm: Shape get() = RoundedCornerShape(sm)
    val shapeMd: Shape get() = RoundedCornerShape(md)
    val shapeLg: Shape get() = RoundedCornerShape(lg)
    val shapeXl: Shape get() = RoundedCornerShape(xl)
    val shapeXxl: Shape get() = RoundedCornerShape(xxl)
    val pill: Shape get() = RoundedCornerShape(percent = 50)
}

/** Icon sizes, control heights and the fixed chrome measurements. */
@Immutable
data class OrbitSizes(
    val iconXs: Dp = 14.dp,
    val iconSm: Dp = 16.dp,
    val iconMd: Dp = 18.dp,
    val iconLg: Dp = 22.dp,
    val iconXl: Dp = 28.dp,

    val controlSm: Dp = 30.dp,
    val controlMd: Dp = 38.dp,
    val controlLg: Dp = 46.dp,

    /** Anything tappable is at least this tall on touch surfaces. */
    val minTouchTarget: Dp = 48.dp,

    val hairline: Dp = 1.dp,

    val topBarHeight: Dp = 56.dp,
    val bottomNavHeight: Dp = 62.dp,
    val sidebarWidth: Dp = 264.dp,
    val sidebarWideWidth: Dp = 288.dp,
    val railWidth: Dp = 76.dp,
    val toolSidebarWidth: Dp = 280.dp,
    val inspectorWidth: Dp = 320.dp,

    /** Reading measure for long-form content on very wide displays. */
    val contentMaxWidth: Dp = 1180.dp,
    val readingMaxWidth: Dp = 720.dp,

    /** Comfortable width for a tool workspace that is wider than prose. */
    val workspaceMaxWidth: Dp = 900.dp,

    /** Poster square used by list rows that represent a video or a file. */
    val thumbnail: Dp = 56.dp,

    /**
     * How tall an inline preview of a picked image or generated code may grow.
     * Capped so the thing being previewed never pushes its own controls off
     * the screen.
     */
    val previewMaxHeight: Dp = 320.dp,
)
