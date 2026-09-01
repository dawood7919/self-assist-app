package com.dawood.orbit.tools.bookmarks

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitSearchField
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import androidx.compose.ui.platform.LocalClipboardManager
import com.dawood.orbit.core.util.TimeFormat
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolPanel
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace

/**
 * Bookmarks — links kept on the device with a title, a note and tags.
 *
 * The panel is the library, the workspace is the one link you are looking at.
 * Editing writes straight through, because a link has a handful of short
 * fields rather than a body you type into for minutes at a time.
 */
@Composable
fun BookmarksTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val window = LocalOrbitWindow.current
    val repository = remember(context) { BookmarksRepository.get(context) }
    val bookmarks by repository.items.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var composing by remember { mutableStateOf(false) }
    var draftUrl by remember { mutableStateOf("") }
    var draftTitle by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    val visible = remember(bookmarks, query, tagFilter) {
        BookmarkQueries.search(BookmarkQueries.withTag(bookmarks, tagFilter), query)
    }
    val selected = bookmarks.firstOrNull { it.id == selectedId }
    val allTags = remember(bookmarks) { BookmarkQueries.tags(bookmarks) }

    fun openLink(url: String) {
        val opened = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        status = if (opened) "Opened in your browser" else "No app on this device can open that link"
    }

    fun startNew() {
        composing = true
        selectedId = null
        draftUrl = ""
        draftTitle = ""
        status = null
    }

    fun saveNew() {
        val url = draftUrl.trim()
        if (url.isEmpty()) {
            status = "Paste a link first"
            return
        }
        val created = repository.create(
            url = url,
            title = draftTitle,
            description = "",
            tags = listOfNotNull(tagFilter),
        )
        selectedId = created.id
        composing = false
        draftUrl = ""
        draftTitle = ""
        status = "Saved ${created.host}"
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = if (bookmarks.isEmpty()) "No links yet" else "${bookmarks.size} links",
        panel = ToolPanel(title = "Library", icon = OrbitIcons.Bookmark) {
            OrbitSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search links",
            )
            OrbitButton(
                text = "Add link",
                onClick = ::startNew,
                leadingIcon = OrbitIcons.Add,
                size = OrbitButtonSize.Small,
                fullWidth = true,
            )
            if (allTags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                ) {
                    OrbitChip(text = "All", selected = tagFilter == null, onClick = { tagFilter = null })
                    allTags.forEach { tag ->
                        OrbitChip(
                            text = tag,
                            selected = tagFilter == tag,
                            onClick = { tagFilter = if (tagFilter == tag) null else tag },
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            ) {
                visible.forEach { bookmark ->
                    OrbitListItem(
                        title = bookmark.displayTitle,
                        subtitle = "${bookmark.host} · ${TimeFormat.relative(bookmark.createdAt)}",
                        selected = bookmark.id == selected?.id,
                        onClick = {
                            selectedId = bookmark.id
                            composing = false
                            status = null
                        },
                    )
                }
            }
        },
        actions = {
            val current = selected
            if (current != null) {
                OrbitIconButton(
                    icon = OrbitIcons.OpenExternal,
                    contentDescription = "Open link",
                    onClick = { openLink(current.url) },
                )
            }
            OrbitIconButton(OrbitIcons.Add, "Add link", ::startNew)
        },
        menuContent = { dismiss ->
            val current = selected
            OrbitMenuItem("Add link", { dismiss(); startNew() }, icon = OrbitIcons.Add)
            if (current != null) {
                OrbitMenuItem(
                    text = "Copy link",
                    onClick = {
                        dismiss()
                        clipboard.setText(AnnotatedString(current.url))
                        status = "Link copied"
                    },
                    icon = OrbitIcons.Copy,
                )
                OrbitMenuItem(
                    text = "Share",
                    onClick = {
                        dismiss()
                        runCatching {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, current.url)
                                putExtra(Intent.EXTRA_SUBJECT, current.displayTitle)
                            }
                            context.startActivity(
                                Intent.createChooser(send, "Share link")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                    icon = OrbitIcons.Share,
                )
                OrbitMenuItem(
                    text = "Delete link",
                    onClick = {
                        dismiss()
                        repository.remove(current.id)
                        selectedId = null
                        status = "Deleted"
                    },
                    icon = OrbitIcons.Delete,
                    destructive = true,
                )
            }
        },
        bottomBar = if (window.isCompact) {
            {
                ToolStatusLine(
                    text = status ?: "${bookmarks.size} links on this device",
                    modifier = Modifier.weight(1f),
                )
                OrbitButton(text = "Add", onClick = ::startNew, leadingIcon = OrbitIcons.Add)
            }
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(OrbitTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg),
        ) {
            OrbitContentContainer(maxWidth = OrbitTheme.sizes.readingMaxWidth) {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
                    if (composing || (selected == null && bookmarks.isEmpty())) {
                        ToolWorkspace(label = "New link") {
                            OrbitTextField(
                                value = draftUrl,
                                onValueChange = { draftUrl = it },
                                label = "Link",
                                placeholder = "example.com/article",
                                leadingIcon = OrbitIcons.Link,
                                helperText = "https:// is added for you when you leave it out",
                            )
                            OrbitTextField(
                                value = draftTitle,
                                onValueChange = { draftTitle = it },
                                label = "Title",
                                placeholder = "Optional — the domain is used when empty",
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                                OrbitButton(text = "Save link", onClick = ::saveNew, leadingIcon = OrbitIcons.Save)
                                if (bookmarks.isNotEmpty()) {
                                    OrbitButton(
                                        text = "Cancel",
                                        onClick = { composing = false },
                                        variant = OrbitButtonVariant.Ghost,
                                    )
                                }
                            }
                        }
                    }

                    val current = selected
                    if (current != null && !composing) {
                        ToolWorkspace(label = "Link") {
                            OrbitTextField(
                                value = current.title,
                                onValueChange = { repository.update(current.id) { b -> b.copy(title = it) } },
                                label = "Title",
                                placeholder = current.host,
                            )
                            OrbitTextField(
                                value = current.url,
                                onValueChange = {
                                    repository.update(current.id) { b ->
                                        b.copy(url = BookmarkQueries.normaliseUrl(it))
                                    }
                                },
                                label = "Link",
                                leadingIcon = OrbitIcons.Link,
                            )
                            OrbitTextField(
                                value = current.description,
                                onValueChange = { repository.update(current.id) { b -> b.copy(description = it) } },
                                label = "Why you kept it",
                                placeholder = "One line so you remember later",
                                singleLine = false,
                                minLines = 3,
                            )
                            OrbitTextField(
                                value = current.tags.joinToString(", "),
                                onValueChange = {
                                    repository.update(current.id) { b ->
                                        b.copy(tags = BookmarkQueries.parseTags(it))
                                    }
                                },
                                label = "Tags",
                                placeholder = "reading, standards",
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                            ) {
                                OrbitBadge(current.host, tone = OrbitTone.Neutral)
                                current.tags.take(3).forEach { OrbitBadge(it, tone = OrbitTone.Accent) }
                                Box(Modifier.weight(1f))
                                OrbitText(
                                    text = "Saved ${TimeFormat.relative(current.createdAt)}",
                                    style = OrbitTheme.typography.caption,
                                    color = OrbitTheme.colors.textMuted,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                                OrbitButton(
                                    text = "Open",
                                    onClick = { openLink(current.url) },
                                    leadingIcon = OrbitIcons.OpenExternal,
                                )
                                OrbitButton(
                                    text = "Copy",
                                    onClick = {
                                        clipboard.setText(AnnotatedString(current.url))
                                        status = "Link copied"
                                    },
                                    variant = OrbitButtonVariant.Secondary,
                                    leadingIcon = OrbitIcons.Copy,
                                )
                            }
                        }
                    }

                    if (current == null && !composing && bookmarks.isNotEmpty()) {
                        OrbitEmptyState(
                            title = "Pick a link",
                            description = "Choose something from the library, or add a new link.",
                            icon = OrbitIcons.Bookmark,
                            primaryActionLabel = "Add link",
                            onPrimaryAction = ::startNew,
                        )
                    }

                    ToolFooter(
                        text = "Links are stored on this device only. Nothing is fetched from the page, " +
                            "so titles are whatever you type.",
                    )
                }
            }
        }
    }
}
