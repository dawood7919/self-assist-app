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
import java.net.URI

object StreamExtractor {

    private const val MAX_PLAYLIST_ITEMS = 300
    private const val MAX_PLAYLIST_PAGES = 15
    private const val MAX_SEARCH_RESULTS = 40

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

    fun handles(url: String): Boolean = runCatching {
        ensureInitialised()
        NewPipe.getServiceByUrl(normaliseUrl(url)) != null
    }.getOrDefault(false)

    suspend fun extract(url: String): Outcome = withContext(Dispatchers.IO) {
        runCatching {
            ensureInitialised()
            val normalised = normaliseUrl(url)
            val service = runCatching { NewPipe.getServiceByUrl(normalised) }.getOrNull()
                ?: return@runCatching Outcome.NotSupported(
                    "No bundled extractor recognises that link. " +
                        "Supported services: ${supportedServices().joinToString()}.",
                )

            when (linkKind(service, normalised)) {
                LinkKind.Playlist -> extractPlaylist(service, normalised)
                LinkKind.Stream, LinkKind.Other -> extractStream(service, normalised)
            }
        }.getOrElse { error ->
            Outcome.Failed(friendlyMessage(error))
        }
    }

    suspend fun extractStreamUrl(url: String): Outcome = withContext(Dispatchers.IO) {
        runCatching {
            ensureInitialised()
            val normalised = normaliseUrl(url)
            val service = runCatching { NewPipe.getServiceByUrl(normalised) }.getOrNull()
                ?: return@runCatching Outcome.NotSupported(
                    "No bundled extractor recognises that link.",
                )
            extractStream(service, normalised)
        }.getOrElse { error ->
            Outcome.Failed(friendlyMessage(error))
        }
    }

    /** YouTube text search → playlist-shaped result for the same picker UI. */
    suspend fun searchYouTube(query: String): Outcome = withContext(Dispatchers.IO) {
        runCatching {
            ensureInitialised()
            val q = query.trim()
            if (q.isEmpty()) return@runCatching Outcome.Failed("Type something to search.")

            val service = ServiceList.YouTube
            val extractor = service.getSearchExtractor(q)
            extractor.fetchPage()

            val entries = mutableListOf<PlaylistEntry>()
            extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .forEach { item ->
                    if (entries.size < MAX_SEARCH_RESULTS) entries += item.toEntry()
                }

            var next = extractor.initialPage.nextPage
            var pages = 0
            while (next != null && entries.size < MAX_SEARCH_RESULTS && pages < 2) {
                val more = extractor.getPage(next)
                more.items.filterIsInstance<StreamInfoItem>().forEach { item ->
                    if (entries.size < MAX_SEARCH_RESULTS) entries += item.toEntry()
                }
                next = more.nextPage
                pages++
            }

            if (entries.isEmpty()) {
                return@runCatching Outcome.Failed("No YouTube results for \"$q\".")
            }

            Outcome.Playlist(
                playlist = ResolvedPlaylist(
                    url = "ytsearch:$q",
                    title = "YouTube · $q",
                    entryCount = entries.size.toLong(),
                    entries = entries,
                    serviceName = "YouTube Search",
                    truncated = entries.size >= MAX_SEARCH_RESULTS,
                ),
                serviceName = "YouTube",
            )
        }.getOrElse { error ->
            Outcome.Failed(friendlyMessage(error))
        }
    }

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

    private fun extractPlaylist(service: StreamingService, url: String): Outcome {
        val mixSeed = youtubeMixSeedVideoId(url)

        val info = runCatching { PlaylistInfo.getInfo(service, url) }.getOrElse { error ->
            if (mixSeed != null) {
                val seedUrl = "https://www.youtube.com/watch?v=$mixSeed"
                return when (val seed = extractStream(service, seedUrl)) {
                    is Outcome.Found -> Outcome.Found(
                        candidates = seed.candidates.map { media ->
                            media.copy(
                                title = "${media.title} (Mix seed — full radio list is not downloadable)",
                            )
                        },
                        serviceName = seed.serviceName,
                    )
                    else -> Outcome.Failed(
                        "YouTube Mix / Radio playlists cannot be downloaded as a full list. " +
                            "Open a normal playlist or paste the individual video link. (${friendlyMessage(error)})",
                    )
                }
            }
            throw error
        }

        val entries = mutableListOf<PlaylistEntry>()

        info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .forEach { item -> entries += item.toEntry() }

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
            if (mixSeed != null) {
                val seedUrl = "https://www.youtube.com/watch?v=$mixSeed"
                return extractStream(service, seedUrl)
            }
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

    private enum class LinkKind { Stream, Playlist, Other }

    private fun linkKind(service: StreamingService, url: String): LinkKind = runCatching {
        when (service.getLinkTypeByUrl(url)) {
            StreamingService.LinkType.PLAYLIST -> LinkKind.Playlist
            StreamingService.LinkType.STREAM -> LinkKind.Stream
            else -> {
                val lower = url.lowercase()
                when {
                    lower.contains("/playlist") -> LinkKind.Playlist
                    lower.contains("list=") && !lower.contains("watch") -> LinkKind.Playlist
                    else -> LinkKind.Other
                }
            }
        }
    }.getOrDefault(LinkKind.Other)

    private fun normaliseUrl(raw: String): String {
        val trimmed = raw.trim()
        return runCatching {
            val uri = URI(trimmed)
            val host = (uri.host ?: "").lowercase()
            val isYoutube = host.contains("youtube.com") || host == "youtu.be"
            if (!isYoutube) return@runCatching trimmed

            val query = parseQuery(uri.rawQuery)
            val path = uri.path.orEmpty()

            if (host == "youtu.be") {
                val id = path.trim('/').substringBefore('?').substringBefore('&')
                if (id.isNotBlank()) {
                    return@runCatching buildYoutubeWatch(id, query["list"])
                }
            }

            val listId = query["list"]
            val videoId = query["v"]
                ?: path.substringAfter("/shorts/", "")
                    .substringBefore('/')
                    .takeIf { path.contains("/shorts/") && it.isNotBlank() }

            if (listId != null && isYoutubeMixListId(listId) && videoId.isNullOrBlank()) {
                val seed = mixSeedFromListId(listId)
                if (seed != null) {
                    return@runCatching buildYoutubeWatch(seed, listId)
                }
            }

            if (path.contains("/playlist") && listId != null) {
                return@runCatching "https://www.youtube.com/playlist?list=$listId"
            }
            if (videoId != null) {
                return@runCatching buildYoutubeWatch(videoId, listId)
            }

            trimmed
                .replace("://m.youtube.com", "://www.youtube.com")
                .replace("://youtube.com", "://www.youtube.com")
        }.getOrDefault(trimmed)
    }

    private fun buildYoutubeWatch(videoId: String, listId: String?): String =
        if (listId.isNullOrBlank()) {
            "https://www.youtube.com/watch?v=$videoId"
        } else {
            "https://www.youtube.com/watch?v=$videoId&list=$listId"
        }

    private fun isYoutubeMixListId(listId: String): Boolean {
        val id = listId.uppercase()
        return id.startsWith("RD") && !id.startsWith("RDMM")
    }

    private fun mixSeedFromListId(listId: String): String? {
        val prefixes = listOf("RDAMVM", "RDEM", "RDCM", "RDMM", "RD")
        for (prefix in prefixes) {
            if (listId.startsWith(prefix, ignoreCase = true) && listId.length > prefix.length) {
                val seed = listId.substring(prefix.length)
                if (seed.length in 6..20) return seed
            }
        }
        return null
    }

    private fun youtubeMixSeedVideoId(url: String): String? {
        val query = runCatching { parseQuery(URI(url).rawQuery) }.getOrDefault(emptyMap())
        val listId = query["list"] ?: return null
        if (!isYoutubeMixListId(listId)) return null
        return query["v"] ?: mixSeedFromListId(listId)
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('&')
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) null
                else part.substring(0, eq) to part.substring(eq + 1)
            }
            .toMap()
    }

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

    private fun friendlyMessage(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("URL not accepted", ignoreCase = true) ->
                "That link was not recognised. For YouTube Mix / Radio, paste a normal " +
                    "playlist link or a single video URL."
            raw.contains("unviewable", ignoreCase = true) ||
                raw.contains("ContentNotAvailable", ignoreCase = true) ->
                "YouTube will not open that playlist for download (common with Mix / Radio). " +
                    "Use a regular playlist or the video itself."
            raw.isBlank() -> "The extractor could not read that link."
            else -> raw
        }
    }

    fun supportedServices(): List<String> = runCatching {
        ensureInitialised()
        ServiceList.all().map { it.serviceInfo.name }
    }.getOrDefault(emptyList())
}
