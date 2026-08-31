package com.dawood.orbit.tools.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitIconTile
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.component.containerColor
import com.dawood.orbit.core.designsystem.component.contentColor
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.model.ToolStatus
import com.dawood.orbit.tools.registry.ToolRegistry

/**
 * The card that represents a tool anywhere it appears: Home, the launcher,
 * search results, favourites. Because it is one component, adding a tool never
 * introduces a new visual style into the product.
 */
@Composable
fun ToolCard(
    tool: Tool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavourite: Boolean = false,
    onToggleFavourite: (() -> Unit)? = null,
    footnote: String? = null,
) {
    val category = ToolRegistry.categoryOf(tool)
    val enabled = tool.status != ToolStatus.Planned

    OrbitCard(
        modifier = modifier.defaultMinSize(minHeight = 132.dp),
        onClick = onClick,
        enabled = enabled,
        contentDescription = "${tool.name}. ${tool.description}",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            OrbitIconTile(
                icon = tool.icon,
                tint = category.tone.contentColor(),
                background = category.tone.containerColor(),
                size = 42.dp,
                iconSize = OrbitTheme.sizes.iconLg,
            )
            Box(Modifier.weight(1f))
            if (onToggleFavourite != null) {
                OrbitIconButton(
                    icon = if (isFavourite) OrbitIcons.Star else OrbitIcons.StarOutline,
                    contentDescription = if (isFavourite) {
                        "Remove ${tool.name} from favourites"
                    } else {
                        "Add ${tool.name} to favourites"
                    },
                    onClick = onToggleFavourite,
                    size = OrbitButtonSize.Small,
                    tint = if (isFavourite) OrbitTheme.colors.warning else OrbitTheme.colors.textMuted,
                )
            }
        }

        Column(
            modifier = Modifier.padding(top = OrbitTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
        ) {
            OrbitText(
                text = tool.name,
                style = OrbitTheme.typography.h3,
                color = OrbitTheme.colors.textPrimary,
                maxLines = 1,
            )
            OrbitText(
                text = tool.description,
                style = OrbitTheme.typography.bodySmall,
                color = OrbitTheme.colors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = OrbitTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitText(
                text = category.name,
                style = OrbitTheme.typography.overline,
                color = OrbitTheme.colors.textMuted,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            when {
                footnote != null -> OrbitText(
                    text = footnote,
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                    maxLines = 1,
                )

                tool.status == ToolStatus.Beta -> OrbitBadge("Beta", tone = OrbitTone.Accent)
                tool.status == ToolStatus.Planned -> OrbitBadge("Soon", tone = OrbitTone.Neutral)
                tool.status == ToolStatus.NeedsSetup ->
                    OrbitBadge("Setup", tone = OrbitTone.Warning, showDot = true)
            }
        }
    }
}

/** The dense list form of a tool, used in the launcher's list view and search. */
@Composable
fun ToolRow(
    tool: Tool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null,
) {
    val category = ToolRegistry.categoryOf(tool)
    OrbitListItem(
        title = tool.name,
        subtitle = tool.description,
        modifier = modifier,
        onClick = onClick,
        leading = {
            OrbitIconTile(
                icon = tool.icon,
                tint = category.tone.contentColor(),
                background = category.tone.containerColor(),
                size = 38.dp,
                iconSize = OrbitTheme.sizes.iconMd,
            )
        },
        trailing = {
            if (trailingLabel != null) {
                OrbitText(
                    text = trailingLabel,
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                )
            }
            if (tool.status == ToolStatus.Planned) {
                OrbitBadge("Soon", tone = OrbitTone.Neutral)
            }
            OrbitIcon(
                icon = OrbitIcons.ChevronRight,
                contentDescription = null,
                size = OrbitTheme.sizes.iconMd,
                tint = OrbitTheme.colors.textMuted,
            )
        },
    )
}

/** A compact tile for Home's "pinned" and "recent" strips. */
@Composable
fun ToolTile(
    tool: Tool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    val category = ToolRegistry.categoryOf(tool)
    OrbitCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(OrbitTheme.spacing.md),
        contentDescription = tool.name,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
        ) {
            OrbitIconTile(
                icon = tool.icon,
                tint = category.tone.contentColor(),
                background = category.tone.containerColor(),
                size = 38.dp,
                iconSize = OrbitTheme.sizes.iconMd,
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            ) {
                OrbitText(tool.name, style = OrbitTheme.typography.h4, maxLines = 1)
                OrbitText(
                    text = caption ?: category.name,
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}
