package com.dawood.orbit.tools.videodownloader.extractor

import com.dawood.orbit.tools.videodownloader.resolve.PlaylistEntry
import com.dawood.orbit.tools.videodownloader.resolve.ResolvedMedia
import com.dawood.orbit.tools.videodownloader.resolve.ResolvedPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Reads signed stream URLs via NewPipeExtractor (YouTube and other services).
 * GPL-3.0 — that is why Orbit is GPL-3.0.
 */
object StreamExtractor {

    sealed interface Outcome {
        data class Found(val candidates: List<ResolvedMedia>, val serviceName: String = "") : Outcome
        data class Playlist(val playlist: ResolvedPlaylist) : Outcome
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
        NewPipe.getServiceByUrl(url) != null
    }.getOrDefault(false)

    fun supportedServices(): List<String> = runCatching {
        ensureInitialised()
        ServiceList.all().map { it.serviceInfo.name }
    }.getOrDefault(emptyList())

    suspend fun extract(url: String): Outcome = withContext(Dispatchers.IO) {
        runCatching {
            ensureInitialised()
            val service = runCatching { NewPipe.getServiceByUrl(url) }.getOrNull()
                ?: return@runCatching Outcome.NotSupported(
                    "No extractor knows that host. Signed hosts that work: ${supportedServices().joinToString()}.",
                )
            if (url.contains("list=") || url.contains("/playlist") || url.contains("/channel/") ||
                url.contains("/c/") || url.contains("/user/") || url.contains("/@")
            ) {
                runCatching {
                    val pl = PlaylistInfo.getInfo(service, url)
                    return@runCatching playlistOutcome(service, pl)
                }.getOrNull()?.let { return@runCatching it }
            }
            val info = StreamInfo.getInfo(service, url)
            val candidates = StreamQualities.fromStreamInfo(service, info)
            if (candidates.isEmpty()) {
                Outcome.NotSupported("${service.serviceInfo.name} returned no usable streams.")
            } else {
                Outcome.Found(candidates, service.serviceInfo.name)
            }
        }.getOrElse { error ->
            Outcome.Failed(error.message ?: "The extractor could not read that link.")
        }
    }

    suspend fun extractStreamUrl(url: String): Outcome = withContext(Dispatchers.IO) {
        runCatching {
            ensureInitialised()
            val service = runCatching { NewPipe.getServiceByUrl(url) }.getOrNull()
                ?: return@runCatching Outcome.NotSupported("Unsupported host.")
            val info = StreamInfo.getInfo(service, url)
            val candidates = StreamQualities.fromStreamInfo(service, info)
            if (candidates.isEmpty()) {
                Outcome.NotSupported("No streams.")
            } else {
                Outcome.Found(candidates, service.serviceInfo.name)
            }
        }.getOrElse { error ->
            Outcome.Failed(error.message ?: "Could not resolve stream.")
        }
    }

    suspend fun searchYouTube(query: String): Outcome = withContext(Dispatchers.IO) {
        runCatching {
            ensureInitialised()
            val service = ServiceList.YouTube
            val search = SearchInfo.getInfo(
                service,
                service.searchQHFactory.fromQuery(query, listOf("videos"), ""),
            )
            val entries = search.relatedItems.mapNotNull { item ->
                val itemUrl = item.url ?: return@mapNotNull null
                PlaylistEntry(
                    url = itemUrl,
                    title = item.name.orEmpty().ifBlank { "Video" },
                    thumbnailUrl = item.thumbnails?.maxByOrNull { it.height }?.url,
                    uploader = null,
                    durationSeconds = null,
                )
            }
            if (entries.isEmpty()) {
                Outcome.Failed("No results for \"$query\".")
            } else {
                Outcome.Playlist(
                    ResolvedPlaylist(
                        title = "Search: $query",
                        url = "https://www.youtube.com/results?search_query=${query.replace(" ", "+")}",
                        serviceName = "YouTube",
                        thumbnailUrl = entries.firstOrNull()?.thumbnailUrl,
                        uploader = null,
                        entryCount = entries.size.toLong(),
                        entries = entries,
                        truncated = false,
                    ),
                )
            }
        }.getOrElse { error ->
            Outcome.Failed(error.message ?: "YouTube search failed.")
        }
    }

    private fun playlistOutcome(
        service: org.schabi.newpipe.extractor.StreamingService,
        pl: PlaylistInfo,
    ): Outcome {
        val entries = pl.relatedItems.mapNotNull { item ->
            val itemUrl = item.url ?: return@mapNotNull null
            PlaylistEntry(
                url = itemUrl,
                title = item.name.orEmpty().ifBlank { "Video" },
                thumbnailUrl = item.thumbnails?.maxByOrNull { it.height }?.url,
                uploader = null,
                durationSeconds = null,
            )
        }
        if (entries.isEmpty()) {
            return Outcome.Failed("Playlist has no videos.")
        }
        val count = when {
            pl.streamCount > 0 -> pl.streamCount
            else -> entries.size.toLong()
        }
        return Outcome.Playlist(
            ResolvedPlaylist(
                title = pl.name.orEmpty().ifBlank { "Playlist" },
                url = pl.url.orEmpty(),
                serviceName = service.serviceInfo.name,
                thumbnailUrl = pl.thumbnails?.maxByOrNull { it.height }?.url
                    ?: entries.firstOrNull()?.thumbnailUrl,
                uploader = pl.uploaderName,
                entryCount = count,
                entries = entries,
                truncated = count > entries.size,
            ),
        )
    }
}
