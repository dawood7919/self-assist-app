package com.dawood.orbit.core.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.dawood.orbit.core.designsystem.theme.OrbitTheme

/**
 * A fixed-column grid that composes eagerly.
 *
 * Home and the tool launcher place grids inside a scrolling column, where a
 * lazy grid cannot measure itself. Cells are equal width by construction, which
 * is what keeps card sizes uniform instead of ragged.
 */
@Composable
fun <T> OrbitGrid(
    items: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = OrbitTheme.spacing.md,
    verticalSpacing: Dp = OrbitTheme.spacing.md,
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    val safeColumns = columns.coerceAtLeast(1)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
    ) {
        items.chunked(safeColumns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                verticalAlignment = Alignment.Top,
            ) {
                rowItems.forEach { item ->
                    Box(Modifier.weight(1f)) { itemContent(item) }
                }
                // Keep the last row's cells the same width as every other row.
                repeat(safeColumns - rowItems.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}
