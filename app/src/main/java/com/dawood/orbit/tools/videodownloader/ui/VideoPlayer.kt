package com.dawood.orbit.tools.videodownloader.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitModal
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.tools.videodownloader.resolve.HttpClients

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

    val player = remember(request.url, request.localPath) {
        // The same browser identity the downloader uses: hosts that refuse an
        // unknown agent for the download refuse it for playback too.
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(HttpClients.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
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
                    .background(OrbitTheme.colors.surfaceSunken),
            ) {
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            this.player = player
                            useController = true
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (request.streaming) {
                OrbitBadge("Playing from the network", tone = OrbitTone.Info)
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
