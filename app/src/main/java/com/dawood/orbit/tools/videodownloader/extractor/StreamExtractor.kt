package com.dawood.orbit.tools.videodownloader.extractor

import com.dawood.orbit.tools.videodownloader.resolve.PlaylistEntry
import com.dawood.orbit.tools.videodownloader.resolve.ResolvedMedia
import com.dawood.orbit.tools.videodownloader.resolve.ResolvedPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Reads the real stream URLs out of sites that sign them per session.
 *
 * This is what the tool could never do on its own. Sites like YouTube do not
 * put a media URL in the page; they hand the player a signed, short-lived one
 * derived by running their own JavaScript. NewPipeExtractor keeps up with that
 * for a long list of services, which is a full-time job and not one worth
 * reimplementing badly.
 *
 * It is GPL-3.0, which is why this application is GPL-3.0.
 *
 * Supports both single streams and playlists. A playlist is returned as a list
 * of entries (title, url, duration, thumbnail) — the actual stream URL for each
 * entry is resolved only when the user decides to download it, so opening a
 * 200-video playlist stays fast.
 */
object StreamExtractor {

    /** What went wrong, in words worth showing someone. */
    sealed interface Outcome {
        data class Found(
            val candidates: List<ResolvedMedia>,
            val serviceName: String,
        ) : Outcome

        data class Playlist(
            val playlist: ResolvedPlaylist,
            val serviceName: String,
        ) : Outcome

        data class NotSupported(val reason: String) : Outcome
        data class Failed(val message: String) : Outcome
    }

    @Volatile
    private var initialised = false

    private fun ensureInitialised() {
        if (initialised) return
        synchronized(this) {
            if (!initialised) {
                NewPipe.init(OkHttpDownloader())
                initialised = true
            }
        }
    }

    /**
     * True when a service in the list claims this link.
     *
     * Pattern matching only, no network. The lookup throws rather than
     * returning null for an unknown host, which is why this is wrapped.
     */
    fun handles(url: String): Boolean = runCatching {
        ensureInitialised()
        NewPipe.getServiceByUrl(url) != null
    }.getOrDefault(false)

    /**
     * Everything downloadable behind [url] — a single video, or a whole playlist.
     *
     * Muxed streams — video and audio already in one file — come first for
     * single videos, because they are the only ones that play as a single
     * downloaded file. Video-only streams are deliberately left out: they are
     * how the high-resolution options are served, and saving one produces a
     * file with no sound, which would look like a bug rather than a choice.
     */
    suspend fun extract(url: String): Outcome = withContext(Dispatchers.IO) {
        runCatching {
            ensureInitialised()
            val service = runCatching { NewPipe.getServiceByUrl(url) }.getOrNull()
                ?: return@runCatching Outcome.NotSupported(
                    "No bundled extractor recognises that link. " +
                        "Supported services: ${supportedServices().joinToString()}.",
                )

            when (linkKind(service, url)) {
                LinkKind.Playlist -> extractPlaylist(service, url)
                LinkKind.Stream, LinkKind.Other -> extractStream(service, url)
            }
        }.getOrElse { error ->
            Outcome.Failed(error.message ?: "The extractor could not read that link.")
        }
    }

    /**
     * Resolve one playlist entry into downloadable media (best muxed + audio).
     * Called when the user picks items from a playlist, not when the list opens.
     */
    suspend fun extractStreamUrl(url: String): Outcome = withContext(Dispatchers.IO) {
        runCatching {
            ensureInitialised()
            val service = runCatching { NewPipe.getServiceByUrl(url) }.getOrNull()
                ?: return@runCatching Outcome.NotSupported(
                    "No bundled extractor recognises that link.",
                )
            extractStream(service, url)
        }.getOrElse { error ->
            Outcome.Failed(error.message ?: "Could not resolve that video.")
        }
    }

    // ── Single stream ───────────────────────────────────────────────────────

    private fun extractStream(service: StreamingService, url: String): Outcome {
        val info = StreamInfo.getInfo(service, url)
        val thumbnail = info.thumbnails?.maxByOrNull { it.height }?.url

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

        val candidates = muxed + audio
        return if (candidates.isEmpty()) {
            Outcome.NotSupported(
                "${service.serviceInfo.name} served only separate video and audio tracks for " +
                    "that link. Saving one on its own would give a file with no sound, so it " +
                    "is not offered.",
            )
        } else {
            Outcome.Found(candidates, service.serviceInfo.name)
        }
    }

    // ── Playlist ────────────────────────────────────────────────────────────

    private fun extractPlaylist(service: StreamingService, url: String): Outcome {
        val info = PlaylistInfo.getInfo(service, url)
        val entries = mutableListOf<PlaylistEntry>()

        // First page
        info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .forEach { item -> entries += item.toEntry() }

        // Pull further pages so a long playlist is complete, but stop at a
        // hard cap so a 5 000-video list does not hang the UI forever.
        var next = info.nextPage
        var pages = 0
        while (next != null && entries.size < MAX_PLAYLIST_ITEMS && pages < MAX_PLAYLIST_PAGES) {
            val more = PlaylistInfo.getMoreItems(service, url, next)
            more.items
                .filterIsInstance<StreamInfoItem>()
                .forEach { item ->
                    if (entries.size < MAX_PLAYLIST_ITEMS) entries += item.toEntry()
                }
            next = more.nextPage
            pages++
        }

        if (entries.isEmpty()) {
            return Outcome.NotSupported(
                "That playlist looks empty, or every entry was filtered out.",
            )
        }

        val thumbnail = info.thumbnails?.maxByOrNull { it.height }?.url
            ?: entries.firstOrNull()?.thumbnailUrl

        return Outcome.Playlist(
            playlist = ResolvedPlaylist(
                url = info.url ?: url,
                title = info.name.orEmpty().ifBlank { "Playlist" },
                uploader = info.uploaderName,
                thumbnailUrl = thumbnail,
                entryCount = if (info.streamCount > 0) info.streamCount else entries.size.toLong(),
                entries = entries,
                serviceName = service.serviceInfo.name,
                truncated = entries.size >= MAX_PLAYLIST_ITEMS,
            ),
            serviceName = service.serviceInfo.name,
        )
    }

    private fun StreamInfoItem.toEntry(): PlaylistEntry = PlaylistEntry(
        url = url.orEmpty(),
        title = name.orEmpty().ifBlank { "Video" },
        durationSeconds = duration.takeIf { it > 0 },
        thumbnailUrl = thumbnails?.maxByOrNull { it.height }?.url,
        uploader = uploaderName,
    )

    // ── Link kind ───────────────────────────────────────────────────────────

    private enum class LinkKind { Stream, Playlist, Other }

    private fun linkKind(service: StreamingService, url: String): LinkKind = runCatching {
        when (service.getLinkTypeByUrl(url)) {
            StreamingService.LinkType.PLAYLIST -> LinkKind.Playlist
            StreamingService.LinkType.STREAM -> LinkKind.Stream
            else -> {
                // Some hosts put "list=" on a watch URL; treat those as playlists
                // when the path clearly points at a playlist endpoint.
                val lower = url.lowercase()
                when {
                    lower.contains("/playlist") -> LinkKind.Playlist
                    lower.contains("list=") && !lower.contains("watch") -> LinkKind.Playlist
                    else -> LinkKind.Other
                }
            }
        }
    }.getOrDefault(LinkKind.Other)

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Higher is better. Parses "1080p60" into something sortable. */
    private fun resolutionRank(stream: VideoStream): Int {
        val resolution = stream.getResolution().orEmpty()
        val height = resolution.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        val frames = resolution.substringAfter('p', "").toIntOrNull() ?: 0
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

    /** The services the bundled extractor knows about, for the empty state. */
    fun supportedServices(): List<String> = runCatching {
        ensureInitialised()
        ServiceList.all().map { it.serviceInfo.name }
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_PLAYLIST_ITEMS = 300
        const val MAX_PLAYLIST_PAGES = 15
    }
}
