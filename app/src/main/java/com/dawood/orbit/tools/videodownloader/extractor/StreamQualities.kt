package com.dawood.orbit.tools.videodownloader.extractor

import com.dawood.orbit.tools.videodownloader.resolve.ResolvedMedia
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

internal object StreamQualities {

    fun fromStreamInfo(service: StreamingService, info: StreamInfo): List<ResolvedMedia> {
        val thumbnail = info.thumbnails?.maxByOrNull { it.height }?.url
        val bestAudio = info.audioStreams.maxByOrNull { it.averageBitrate }

        val muxed = info.videoStreams
            .filterNot { it.isVideoOnly }
            .distinctBy { it.content }
            .sortedByDescending { resolutionRank(it) }
            .map { stream ->
                ResolvedMedia(
                    mediaUrl = stream.content,
                    title = info.name.orEmpty().ifBlank { "Video" },
                    fileName = fileName(info.name, stream.format?.suffix ?: "mp4", stream.getResolution()),
                    mimeType = stream.format?.mimeType ?: "video/mp4",
                    sizeBytes = -1L,
                    resumable = true,
                    thumbnailUrl = thumbnail,
                    qualityLabel = stream.getResolution().orEmpty().ifBlank { null },
                    serviceName = service.serviceInfo.name,
                    videoOnly = false,
                )
            }

        val videoOnly = info.videoStreams
            .filter { it.isVideoOnly }
            .distinctBy { "${it.getResolution()}|${it.format?.mimeType}" }
            .sortedByDescending { resolutionRank(it) }
            .map { stream ->
                val res = stream.getResolution().orEmpty().ifBlank { "video" }
                ResolvedMedia(
                    mediaUrl = stream.content,
                    title = info.name.orEmpty().ifBlank { "Video" },
                    fileName = fileName(info.name, stream.format?.suffix ?: "mp4", res),
                    mimeType = stream.format?.mimeType ?: "video/mp4",
                    sizeBytes = -1L,
                    resumable = true,
                    thumbnailUrl = thumbnail,
                    qualityLabel = res,
                    serviceName = service.serviceInfo.name,
                    audioUrl = bestAudio?.content,
                    videoOnly = true,
                )
            }

        val audio = info.audioStreams
            .distinctBy { it.content }
            .sortedByDescending { it.averageBitrate }
            .take(2)
            .map { stream ->
                ResolvedMedia(
                    mediaUrl = stream.content,
                    title = "${info.name.orEmpty().ifBlank { "Audio" }} (audio only)",
                    fileName = fileName(info.name, stream.format?.suffix ?: "m4a", audioLabel(stream)),
                    mimeType = stream.format?.mimeType ?: "audio/mp4",
                    sizeBytes = -1L,
                    resumable = true,
                    thumbnailUrl = thumbnail,
                    qualityLabel = audioLabel(stream),
                    serviceName = service.serviceInfo.name,
                )
            }

        return muxed + videoOnly + audio
    }

    private fun resolutionRank(stream: VideoStream): Int {
        val resolution = stream.getResolution().orEmpty()
        val height = resolution.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        val frames = resolution.substringAfter("p", "").toIntOrNull() ?: 0
        return height * 100 + frames
    }

    private fun audioLabel(stream: AudioStream): String {
        val bitrate = stream.averageBitrate
        return if (bitrate > 0) "${bitrate}kbps" else "audio"
    }

    private fun fileName(title: String?, extension: String, quality: String?): String {
        val base = title.orEmpty()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(100)
            .ifBlank { "video" }
        val suffix = quality?.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
        return "$base$suffix.$extension"
    }
}
