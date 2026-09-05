package com.dawood.orbit.tools.videodownloader.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.tools.videodownloader.resolve.HttpClients
import java.io.File
import java.util.Locale

@Composable
fun VideoThumbnail(
    thumbnailUrl: String?,
    localPath: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    ThumbnailImage(
        thumbnailUrl = thumbnailUrl,
        localPath = localPath,
        contentDescription = contentDescription,
        modifier = modifier.size(size).clip(OrbitTheme.radius.shapeMd),
    )
}

/** Full-width 16:9 poster with optional duration badge. */
@Composable
fun VideoThumbnailWide(
    thumbnailUrl: String?,
    localPath: String? = null,
    durationSeconds: Long? = null,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(OrbitTheme.radius.shapeMd),
    ) {
        ThumbnailImage(
            thumbnailUrl = thumbnailUrl,
            localPath = localPath,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
        )
        // Soft bottom gradient so the duration label stays readable
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    ),
                )
                .padding(OrbitTheme.spacing.sm),
        )
        durationSeconds?.takeIf { it > 0 }?.let { sec ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(OrbitTheme.spacing.sm)
                    .background(
                        Color.Black.copy(alpha = 0.75f),
                        shape = OrbitTheme.radius.shapeSm,
                    )
                    .padding(
                        horizontal = OrbitTheme.spacing.xs,
                        vertical = OrbitTheme.spacing.xxs,
                    ),
            ) {
                OrbitText(
                    text = formatDurationLabel(sec),
                    style = OrbitTheme.typography.caption,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ThumbnailImage(
    thumbnailUrl: String?,
    localPath: String?,
    contentDescription: String?,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val localVideo = remember(localPath) {
        localPath?.let { File(it) }?.takeIf { it.isFile && it.length() > 0 }
    }
    val model = thumbnailUrl ?: localVideo

    if (model == null) {
        Box(
            modifier = modifier.background(OrbitTheme.colors.surfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            OrbitIcon(
                icon = OrbitIcons.Video,
                contentDescription = null,
                size = OrbitTheme.sizes.iconXl,
                tint = OrbitTheme.colors.textMuted,
            )
        }
        return
    }

    val loader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    val request = remember(model) {
        ImageRequest.Builder(context)
            .data(model)
            .apply {
                if (model is String) setHeader("User-Agent", HttpClients.USER_AGENT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) allowHardware(true)
            }
            .crossfade(true)
            .build()
    }

    SubcomposeAsyncImage(
        model = request,
        imageLoader = loader,
        contentDescription = contentDescription,
        modifier = modifier.background(OrbitTheme.colors.surfaceSunken),
        contentScale = ContentScale.Crop,
        loading = {
            Box(Modifier.background(OrbitTheme.colors.surfaceSunken))
        },
        error = {
            Box(
                modifier = Modifier.background(OrbitTheme.colors.surfaceSunken),
                contentAlignment = Alignment.Center,
            ) {
                OrbitIcon(
                    icon = OrbitIcons.Video,
                    contentDescription = null,
                    size = OrbitTheme.sizes.iconLg,
                    tint = OrbitTheme.colors.textMuted,
                )
            }
        },
    )
}

private fun formatDurationLabel(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}
