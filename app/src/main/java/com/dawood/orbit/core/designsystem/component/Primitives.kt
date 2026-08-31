package com.dawood.orbit.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.designsystem.theme.orbitTextStyle

/**
 * The only text primitive in the app. Everything inherits from the type scale,
 * so no screen can introduce an off-scale size by accident.
 */
@Composable
fun OrbitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle? = null,
    color: Color? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    textAlign: TextAlign? = null,
    softWrap: Boolean = true,
) {
    val resolved = orbitTextStyle(style, color)
    BasicText(
        text = text,
        modifier = modifier,
        style = if (textAlign != null) resolved.copy(textAlign = textAlign) else resolved,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
    )
}

/** The only icon primitive. Size comes from the token set, never a literal. */
@Composable
fun OrbitIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = OrbitTheme.sizes.iconMd,
    tint: Color = OrbitTheme.colors.textSecondary,
) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
fun OrbitDivider(
    modifier: Modifier = Modifier,
    color: Color = OrbitTheme.colors.borderSubtle,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(OrbitTheme.sizes.hairline)
            .background(color),
    )
}

@Composable
fun OrbitVerticalDivider(
    modifier: Modifier = Modifier,
    color: Color = OrbitTheme.colors.borderSubtle,
) {
    Box(
        modifier
            .fillMaxHeight()
            .width(OrbitTheme.sizes.hairline)
            .background(color),
    )
}

@Composable
fun VSpace(height: Dp) {
    Spacer(Modifier.height(height))
}

@Composable
fun HSpace(width: Dp) {
    Spacer(Modifier.width(width))
}
