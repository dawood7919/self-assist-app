package com.dawood.orbit.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dawood.orbit.core.designsystem.theme.OrbitTheme

/**
 * The one app bar in the product.
 *
 * Home, the tool launcher and every individual tool use this same component, so
 * the top of the screen never changes shape as you move between them — only its
 * contents do.
 */
@Composable
fun OrbitTopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    navigation: @Composable (() -> Unit)? = null,
    center: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    showDivider: Boolean = true,
    applyStatusBarInset: Boolean = true,
    background: Color = OrbitTheme.colors.glassSurface,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(background)
            .then(
                if (applyStatusBarInset) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = OrbitTheme.sizes.topBarHeight)
                .padding(horizontal = OrbitTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            navigation?.invoke()

            if (title != null) {
                Column(
                    modifier = Modifier.padding(horizontal = OrbitTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
                ) {
                    OrbitText(
                        text = title,
                        style = OrbitTheme.typography.h3,
                        color = OrbitTheme.colors.textPrimary,
                        maxLines = 1,
                    )
                    if (subtitle != null) {
                        OrbitText(
                            text = subtitle,
                            style = OrbitTheme.typography.caption,
                            color = OrbitTheme.colors.textMuted,
                            maxLines = 1,
                        )
                    }
                }
            }

            if (center != null) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { center() }
            } else {
                Box(Modifier.weight(1f))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
                content = actions,
            )
        }
        if (showDivider) {
            OrbitDivider(color = OrbitTheme.colors.borderSubtle)
        }
    }
}
