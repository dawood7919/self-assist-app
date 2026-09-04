package com.dawood.orbit.tools.videodownloader.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitIconTile
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.tools.videodownloader.resolve.HttpClients
import java.io.File

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

/** Full-width 16:9 poster for large result cards. */
@Composable
fun VideoThumbnailWide(
    thumbnailUrl: String?,
    localPath: String? = null,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    ThumbnailImage(
        thumbnailUrl = thumbnailUrl,
        localPath = localPath,
        contentDescription = contentDescription,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(OrbitTheme.radius.shapeMd),
    )
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
