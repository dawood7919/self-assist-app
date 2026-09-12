package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.dawood.orbit.tools.cloudbrowser.BookmarkEntry
import com.dawood.orbit.tools.cloudbrowser.BrowserLinks
import com.dawood.orbit.tools.cloudbrowser.CloudBody
import com.dawood.orbit.tools.cloudbrowser.CloudCard
import com.dawood.orbit.tools.cloudbrowser.CloudColors
import com.dawood.orbit.tools.cloudbrowser.CloudField
import com.dawood.orbit.tools.cloudbrowser.CloudOutlineButton
import com.dawood.orbit.tools.cloudbrowser.CloudPill
import com.dawood.orbit.tools.cloudbrowser.CloudSmall
import com.dawood.orbit.tools.cloudbrowser.CloudSpacing
import com.dawood.orbit.tools.cloudbrowser.CloudTitle
import com.dawood.orbit.tools.cloudbrowser.BrowserHistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Standalone-app extras screen: saved bookmarks plus auto-recorded history.
 * Tapping a row hands the URL back to the tool, which opens it in the
 * currently active remote tab (or offers to launch one).
 */
@Composable
fun BookmarksScreen(
    bookmarks: List<BookmarkEntry>,
    history: List<BrowserHistoryEntry>,
    onOpen: (String) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onDeleteHistoryEntry: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadMd)) {
        CloudTitle(text = if (tab == 0) "Bookmarks" else "History")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            CloudPill("Saved", tab == 0, { tab = 0 }, Modifier.weight(1f))
            CloudPill("History", tab == 1, { tab = 1 }, Modifier.weight(1f))
        }

        CloudField(
            value = query,
            onValueChange = { query = it },
            placeholder = if (tab == 0) "Search bookmarks" else "Search history",
            keyboardOptions = KeyboardOptions.Default,
        )

        if (tab == 0) {
            val visible = BrowserLinks.searchBookmarks(bookmarks, query)
            if (visible.isEmpty()) {
                EmptyNote(
                    if (query.isBlank()) {
                        "No bookmarks yet. Open a page in the browser and use the star to save it."
                    } else {
                        "No bookmarks match \"$query\"."
                    },
                )
            }
            visible.forEach { entry ->
                LinkRow(
                    title = BrowserLinks.displayTitle(entry),
                    subtitle = entry.url,
                    trailing = "★",
                    onOpen = { onOpen(entry.url) },
                    onDelete = { onDeleteBookmark(entry.id) },
                    deleteLabel = "Remove bookmark",
                )
            }
        } else {
            val visible = BrowserLinks.searchHistory(history, query)
            if (visible.isEmpty()) {
                EmptyNote(
                    if (query.isBlank()) {
                        "Pages you visit in the cloud browser will show up here, on this device only."
                    } else {
                        "No history matches \"$query\"."
                    },
                )
            } else {
                CloudOutlineButton(text = "Clear history", onClick = onClearHistory)
                val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
                visible.forEach { entry ->
                    LinkRow(
                        title = BrowserLinks.displayTitle(entry),
                        subtitle = entry.url,
                        meta = dateFormat.format(Date(entry.visitedAt)),
                        trailing = "🕘",
                        onOpen = { onOpen(entry.url) },
                        onDelete = { onDeleteHistoryEntry(entry.id) },
                        deleteLabel = "Remove from history",
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    CloudCard {
        CloudSmall(text = text, color = CloudColors.Dim)
    }
}

@Composable
private fun LinkRow(
    title: String,
    subtitle: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    deleteLabel: String,
    trailing: String,
    meta: String? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CloudColors.CardRadius))
            .background(CloudColors.Panel)
            .border(
                CloudSpacing.BorderWidth,
                CloudColors.Line,
                RoundedCornerShape(CloudColors.CardRadius),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = title, role = Role.Button, onClick = onOpen)
                .padding(CloudColors.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            CloudBody(text = trailing)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadXs),
            ) {
                CloudBody(text = title, bold = true)
                CloudSmall(text = subtitle)
                if (meta != null) CloudSmall(text = meta)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(CloudColors.PillRadius))
                    .clickable(onClickLabel = deleteLabel, role = Role.Button, onClick = onDelete)
                    .padding(CloudSpacing.PadSm),
            ) {
                CloudSmall(text = "✕", align = TextAlign.Center)
            }
        }
    }
}
