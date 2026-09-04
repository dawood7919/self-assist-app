package com.dawood.orbit.tools.videodownloader.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.tools.videodownloader.resolve.HttpClients
import com.dawood.orbit.tools.videodownloader.resolve.ResolvedMedia
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val SEEK_MS = 10_000L
private const val SEEK_LONG_MS = 30_000L

/**
 * Immersive fullscreen player (watch surface only):
 * play/pause, ±10s / ±30s, quality pick, progress.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerModal(
    request: PlaybackRequest?,
    onDismiss: () -> Unit,
) {
    if (request == null) return

    val context = LocalContext.current
    var error by remember(request.url) { mutableStateOf<String?>(null) }
    var controlsVisible by remember(request.url) { mutableStateOf(true) }
    var isPlaying by remember(request.url) { mutableStateOf(true) }
    var positionMs by remember(request.url) { mutableLongStateOf(0L) }
    var durationMs by remember(request.url) { mutableLongStateOf(0L) }

    val qualities = remember(request) {
        request.qualities.ifEmpty {
            listOf(
                ResolvedMedia(
                    mediaUrl = request.localPath ?: request.url,
                    title = request.title,
                    fileName = "video.mp4",
                    mimeType = "video/mp4",
                    sizeBytes = -1L,
                    resumable = true,
                    qualityLabel = "Default",
                ),
            )
        }
    }
    var selectedQuality by remember(request.url) {
        mutableStateOf(qualities.first())
    }
    var qualityMenuOpen by remember { mutableStateOf(false) }

    val player = remember(request.url, request.localPath) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(HttpClients.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setSeekBackIncrementMs(SEEK_LONG_MS)
            .setSeekForwardIncrementMs(SEEK_LONG_MS)
            .build()
            .apply {
                val uri = request.localPath ?: selectedQuality.mediaUrl
                setMediaItem(MediaItem.fromUri(uri))
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(exception: PlaybackException) {
                error = exception.errorCodeName
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0 } ?: 0L
            delay(250)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3200)
            controlsVisible = false
            qualityMenuOpen = false
        }
    }

    fun switchQuality(media: ResolvedMedia) {
        if (request.localPath != null) return // local file has one track
        val pos = player.currentPosition
        val wasPlaying = player.isPlaying
        selectedQuality = media
        player.setMediaItem(MediaItem.fromUri(media.mediaUrl))
        player.prepare()
        player.seekTo(pos)
        player.playWhenReady = wasPlaying
        qualityMenuOpen = false
        error = null
    }

    val activity = remember(context) { context.findActivity() }

    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation
        val controller = activity?.window?.let { window ->
            WindowInsetsControllerCompat(window, window.decorView)
        }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        controller?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, true) }
            previousOrientation?.let { activity.requestedOrientation = it }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            (dialogView.parent as? DialogWindowProvider)?.window?.apply {
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                )
                addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            }
            onDispose { }
        }

        BackHandler(onBack = onDismiss)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    controlsVisible = !controlsVisible
                    if (!controlsVisible) qualityMenuOpen = false
                },
        ) {
            PlayerSurface(player = player, modifier = Modifier.fillMaxSize())

            if (controlsVisible) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(OrbitTheme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                ) {
                    OrbitIconButton(
                        icon = OrbitIcons.Close,
                        contentDescription = "Close",
                        onClick = onDismiss,
                        tint = Color.White,
                    )
                    OrbitText(
                        text = request.title,
                        style = OrbitTheme.typography.h4,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (qualities.size > 1 && request.localPath == null) {
                        OrbitButton(
                            text = selectedQuality.qualityLabel ?: "Quality",
                            onClick = { qualityMenuOpen = !qualityMenuOpen },
                            variant = OrbitButtonVariant.Ghost,
                            size = OrbitButtonSize.Small,
                        )
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OrbitIconButton(
                        icon = OrbitIcons.Replay30,
                        contentDescription = "Back 30s",
                        onClick = {
                            player.seekTo((player.currentPosition - SEEK_LONG_MS).coerceAtLeast(0))
                        },
                        tint = Color.White,
                    )
                    OrbitIconButton(
                        icon = OrbitIcons.Replay30,
                        contentDescription = "Back 10s",
                        onClick = {
                            player.seekTo((player.currentPosition - SEEK_MS).coerceAtLeast(0))
                        },
                        tint = Color.White,
                    )
                    OrbitIconButton(
                        icon = if (isPlaying) OrbitIcons.Pause else OrbitIcons.Play,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        onClick = { if (player.isPlaying) player.pause() else player.play() },
                        tint = Color.White,
                    )
                    OrbitIconButton(
                        icon = OrbitIcons.Forward30,
                        contentDescription = "Forward 10s",
                        onClick = {
                            val d = player.duration
                            val t = player.currentPosition + SEEK_MS
                            player.seekTo(if (d > 0) t.coerceAtMost(d) else t)
                        },
                        tint = Color.White,
                    )
                    OrbitIconButton(
                        icon = OrbitIcons.Forward30,
                        contentDescription = "Forward 30s",
                        onClick = {
                            val d = player.duration
                            val t = player.currentPosition + SEEK_LONG_MS
                            player.seekTo(if (d > 0) t.coerceAtMost(d) else t)
                        },
                        tint = Color.White,
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(OrbitTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                ) {
                    if (request.streaming) {
                        OrbitBadge("Streaming", tone = OrbitTone.Info)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                    ) {
                        OrbitText(
                            text = formatPlayerTime(positionMs),
                            style = OrbitTheme.typography.caption,
                            color = Color.White,
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = OrbitTheme.spacing.sm)
                                .background(Color.White.copy(alpha = 0.25f)),
                        ) {
                            val progress = if (durationMs > 0) {
                                (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .padding(vertical = OrbitTheme.spacing.xxs)
                                    .background(Color.White),
                            )
                        }
                        OrbitText(
                            text = if (durationMs > 0) formatPlayerTime(durationMs) else "--:--",
                            style = OrbitTheme.typography.caption,
                            color = Color.White,
                        )
                    }
                }

                if (qualityMenuOpen && qualities.size > 1) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = OrbitTheme.spacing.xxl, end = OrbitTheme.spacing.md)
                            .background(Color.Black.copy(alpha = 0.85f))
                            .padding(OrbitTheme.spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                    ) {
                        qualities.forEach { q ->
                            val label = q.qualityLabel ?: q.mimeType
                            val selected = q.mediaUrl == selectedQuality.mediaUrl
                            OrbitButton(
                                text = if (selected) "● $label" else label,
                                onClick = { switchQuality(q) },
                                variant = if (selected) {
                                    OrbitButtonVariant.Primary
                                } else {
                                    OrbitButtonVariant.Ghost
                                },
                                size = OrbitButtonSize.Small,
                            )
                        }
                    }
                }
            }

            error?.let { code ->
                OrbitText(
                    text = "Playback failed ($code)",
                    style = OrbitTheme.typography.bodySmall,
                    color = OrbitTheme.colors.error,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(OrbitTheme.spacing.lg),
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerSurface(player: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        onRelease = { view -> view.player = null },
        modifier = modifier,
    )
}

private fun formatPlayerTime(ms: Long): String {
    val totalSec = TimeUnit.MILLISECONDS.toSeconds(ms).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

private fun android.content.Context.findActivity(): Activity? {
    var current: android.content.Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
