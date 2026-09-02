package com.dawood.orbit.tools.videodownloader.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.media3.ui.PlayerView
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitModal
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.tools.videodownloader.resolve.HttpClients

/** How far the skip buttons jump. */
private const val SEEK_STEP_MS = 30_000L

/**
 * Plays a video without leaving the app.
 *
 * A download that is still running is played from its source URL rather than
 * from the file on disk. That is not a shortcut: the segmented transfer writes
 * several parts of the file at once, so the bytes on disk are full of holes
 * until every connection finishes and nothing could play them. Streaming the
 * same URL gives the user what they actually want — watch it now — while the
 * download carries on underneath.
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
    var fullscreen by remember(request.url) { mutableStateOf(false) }

    val player = remember(request.url, request.localPath) {
        // The same browser identity the downloader uses: hosts that refuse an
        // unknown agent for the download refuse it for playback too.
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(HttpClients.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            // These drive the skip buttons the controls already know how to
            // draw, so the step is set once here rather than wired by hand.
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(request.localPath ?: request.url))
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(exception: PlaybackException) {
                error = exception.errorCodeName
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    if (fullscreen) {
        FullscreenPlayer(
            player = player,
            onExit = { fullscreen = false },
        )
        return
    }

    OrbitModal(
        visible = true,
        onDismiss = onDismiss,
        title = request.title,
        description = if (request.streaming) {
            "Streaming from the source while the download continues."
        } else {
            null
        },
        width = OrbitTheme.sizes.readingMaxWidth,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(OrbitTheme.radius.shapeMd)
                    .background(Color.Black),
            ) {
                PlayerSurface(player = player, modifier = Modifier.fillMaxSize())

                OrbitIconButton(
                    icon = OrbitIcons.Fullscreen,
                    contentDescription = "Full screen",
                    onClick = { fullscreen = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(OrbitTheme.spacing.sm),
                    tint = Color.White,
                )
            }

            // Explicit skip controls rather than relying on whatever the
            // built-in controller chooses to draw, and reachable without
            // waking the overlay first.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            ) {
                OrbitIconButton(
                    icon = OrbitIcons.Replay30,
                    contentDescription = "Back 30 seconds",
                    onClick = { player.seekBack() },
                )
                OrbitIconButton(
                    icon = OrbitIcons.Forward30,
                    contentDescription = "Forward 30 seconds",
                    onClick = { player.seekForward() },
                )
                Box(Modifier.weight(1f))
                if (request.streaming) {
                    OrbitBadge("Playing from the network", tone = OrbitTone.Info)
                }
            }

            error?.let { code ->
                OrbitText(
                    text = "Playback failed ($code). Some hosts allow the file to be downloaded " +
                        "but refuse to stream it, and a partly-downloaded file cannot be played " +
                        "until it finishes.",
                    style = OrbitTheme.typography.bodySmall,
                    color = OrbitTheme.colors.error,
                )
            }
        }
    }
}

/**
 * The same player, filling the screen.
 *
 * The activity is put into landscape and the system bars are hidden for the
 * duration, then both are put back exactly as they were — leaving an app
 * locked in landscape after a video is a bug people notice immediately.
 */
@OptIn(UnstableApi::class)
@Composable
private fun FullscreenPlayer(
    player: ExoPlayer,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
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
        onDismissRequest = onExit,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        BackHandler(onBack = onExit)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            PlayerSurface(player = player, modifier = Modifier.fillMaxSize())

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(OrbitTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            ) {
                OrbitIconButton(
                    icon = OrbitIcons.Replay30,
                    contentDescription = "Back 30 seconds",
                    onClick = { player.seekBack() },
                    tint = Color.White,
                )
                OrbitIconButton(
                    icon = OrbitIcons.Forward30,
                    contentDescription = "Forward 30 seconds",
                    onClick = { player.seekForward() },
                    tint = Color.White,
                )
                OrbitIconButton(
                    icon = OrbitIcons.FullscreenExit,
                    contentDescription = "Leave full screen",
                    onClick = onExit,
                    tint = Color.White,
                )
            }
        }
    }
}

/**
 * The video surface itself.
 *
 * `setPlayer(null)` on dispose matters: two views attached to one player leave
 * the old one holding a surface, which shows as a black rectangle after
 * leaving full screen.
 */
@OptIn(UnstableApi::class)
@Composable
private fun PlayerSurface(player: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = true
                setShowRewindButton(true)
                setShowFastForwardButton(true)
                setShowNextButton(false)
                setShowPreviousButton(false)
                setShowSubtitleButton(false)
                controllerShowTimeoutMs = 2500
            }
        },
        onRelease = { view -> view.player = null },
        modifier = modifier,
    )
}

private fun android.content.Context.findActivity(): Activity? {
    var current: android.content.Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
