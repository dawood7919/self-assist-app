package com.dawood.orbit.tools.videodownloader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitCheckbox
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitProgressBar
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.tools.videodownloader.model.QualityPreference
import com.dawood.orbit.tools.videodownloader.resolve.PlaylistEntry
import java.util.Locale

@Composable
internal fun PlaylistPicker(
    state: ResolveUiState.Playlist,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onQuality: (QualityPreference) -> Unit,
    onDownloadSelected: () -> Unit,
    onPlayEntry: (PlaylistEntry) -> Unit,
    onFilter: (String) -> Unit,
    onMinDuration: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val playlist = state.playlist
    val visible = state.visibleEntries
    val selected = state.selectedUrls.size
    val total = playlist.entries.size

    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
        OrbitCard(color = OrbitTheme.colors.surfaceElevated) {
            Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
                    ) {
                        OrbitText(
                            text = playlist.title,
                            style = OrbitTheme.typography.h3,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        OrbitText(
                            text = buildString {
                                append(playlist.serviceName)
                                playlist.uploader?.takeIf { it.isNotBlank() }?.let {
                                    append(" · "); append(it)
                                }
                                append(" · ${playlist.entryCount} videos")
                                if (playlist.truncated) append(" (capped)")
                            },
                            style = OrbitTheme.typography.caption,
                            color = OrbitTheme.colors.textMuted,
                            maxLines = 2,
                        )
                    }
                    OrbitIconButton(
                        icon = OrbitIcons.Close,
                        contentDescription = "Dismiss",
                        onClick = onDismiss,
                        size = OrbitButtonSize.Small,
                        enabled = !state.enqueueing,
                    )
                }

                OrbitTextField(
                    value = state.filter,
                    onValueChange = onFilter,
                    label = "Filter",
                    placeholder = "Title or channel…",
                    leadingIcon = OrbitIcons.Search,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                ) {
                    listOf(0L to "Any", 60L to ">1m", 300L to ">5m", 1200L to ">20m").forEach { (sec, label) ->
                        val on = state.minDurationSec == sec
                        OrbitButton(
                            text = label,
                            onClick = { onMinDuration(sec) },
                            variant = if (on) OrbitButtonVariant.Primary else OrbitButtonVariant.Ghost,
                            size = OrbitButtonSize.Small,
                            enabled = !state.enqueueing,
                        )
                    }
                }

                OrbitText(
                    text = "Quality for all",
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                ) {
                    QualityPreference.entries.forEach { pref ->
                        val selectedQ = state.quality == pref
                        OrbitButton(
                            text = pref.label,
                            onClick = { onQuality(pref) },
                            variant = if (selectedQ) OrbitButtonVariant.Primary else OrbitButtonVariant.Ghost,
                            size = OrbitButtonSize.Small,
                            enabled = !state.enqueueing,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OrbitBadge(
                        text = "$selected selected · ${visible.size}/$total shown",
                        tone = if (selected > 0) OrbitTone.Accent else OrbitTone.Neutral,
                        showDot = true,
                    )
                    Box(Modifier.weight(1f))
                    OrbitButton(
                        text = "All",
                        onClick = onSelectAll,
                        variant = OrbitButtonVariant.Ghost,
                        size = OrbitButtonSize.Small,
                        enabled = !state.enqueueing,
                    )
                    OrbitButton(
                        text = "None",
                        onClick = onClearSelection,
                        variant = OrbitButtonVariant.Ghost,
                        size = OrbitButtonSize.Small,
                        enabled = !state.enqueueing,
                    )
                }

                OrbitButton(
                    text = if (state.enqueueing) {
                        "Adding ${state.enqueueProgress}/${state.enqueueTotal}…"
                    } else {
                        "Download selected ($selected)"
                    },
                    onClick = onDownloadSelected,
                    leadingIcon = OrbitIcons.Download,
                    enabled = selected > 0 && !state.enqueueing,
                    loading = state.enqueueing,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.enqueueing && state.enqueueTotal > 0) {
                    OrbitProgressBar(
                        progress = state.enqueueProgress.toFloat() / state.enqueueTotal,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        visible.forEach { entry ->
            PlaylistEntryCard(
                entry = entry,
                selected = entry.url in state.selectedUrls,
                enabled = !state.enqueueing,
                onToggle = { onToggle(entry.url) },
                onPlay = { onPlayEntry(entry) },
            )
        }
    }
}

@Composable
private fun PlaylistEntryCard(
    entry: PlaylistEntry,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onPlay: () -> Unit,
) {
    OrbitCard(
        color = if (selected) {
            OrbitTheme.colors.surfaceSelected
        } else {
            OrbitTheme.colors.surfaceElevated
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled, onClick = onToggle),
            ) {
                VideoThumbnailWide(
                    thumbnailUrl = entry.thumbnailUrl,
                    durationSeconds = entry.durationSeconds,
                    contentDescription = entry.title,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            ) {
                OrbitCheckbox(
                    checked = selected,
                    onCheckedChange = if (enabled) {{ onToggle() }} else null,
                    enabled = enabled,
                )
                Column(modifier = Modifier.weight(1f)) {
                    OrbitText(
                        text = entry.title,
                        style = OrbitTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = buildString {
                        entry.uploader?.takeIf { it.isNotBlank() }?.let { append(it) }
                        entry.durationSeconds?.let { seconds ->
                            if (isNotEmpty()) append(" · ")
                            append(formatDuration(seconds))
                        }
                    }.ifBlank { "Video" }
                    OrbitText(
                        text = meta,
                        style = OrbitTheme.typography.caption,
                        color = OrbitTheme.colors.textMuted,
                        maxLines = 1,
                    )
                }
                OrbitButton(
                    text = "Play",
                    onClick = onPlay,
                    variant = OrbitButtonVariant.Ghost,
                    size = OrbitButtonSize.Small,
                    leadingIcon = OrbitIcons.Play,
                    enabled = enabled,
                )
            }
        }
    }
}

internal fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}
