package com.dawood.orbit.tools.videodownloader.resolve

import com.dawood.orbit.tools.videodownloader.extractor.StreamExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URLDecoder
import java.util.Locale

/** What a resolved link turned out to be. */
data class ResolvedMedia(
    val mediaUrl: String,
    val title: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val resumable: Boolean,
    /** Poster image for the video, when the page offered one. */
    val thumbnailUrl: String? = null,
    /** Human label such as "1080p" or "160kbps" when known. */
    val qualityLabel: String? = null,
    /** Extractor service name (YouTube, SoundCloud, …) when applicable. */
    val serviceName: String? = null,
)

/** One row inside a playlist before its stream URL is resolved. */
data class PlaylistEntry(
    val url: String,
    val title: String,
    val durationSeconds: Long? = null,
    val thumbnailUrl: String? = null,
    val uploader: String? = null,
)

/** A whole playlist the user can pick from. */
data class ResolvedPlaylist(
    val url: String,
    val title: String,
    val uploader: String? = null,
    val thumbnailUrl: String? = null,
    val entryCount: Long,
    val entries: List<PlaylistEntry>,
    val serviceName: String,
    /** True when the list was capped and more items exist on the site. */
    val truncated: Boolean = false,
)

sealed interface ResolveResult {
    /**
     * Everything downloadable that was found, best first.
     *
     * A page usually holds more than one video, so this is a list even when it
     * has a single entry: the caller shows a picker when there is a choice and
     * goes straight through when there is not.
     */
    data class Success(val candidates: List<ResolvedMedia>) : ResolveResult

    /** A playlist — the UI shows the list and resolves each pick on demand. */
    data class Playlist(val playlist: ResolvedPlaylist) : ResolveResult

    data class Failure(val reason: String) : ResolveResult
}

/**
 * Turns whatever the user pasted into a downloadable media URL — or a playlist.
 *
 * Strategies, tried in order:
 *  1. Bundled extractor (YouTube, SoundCloud, PeerTube, Bandcamp, media.ccc.de)
 *     for signed / per-session streams and native playlists.
 *  2. The link already points at a media file.
 *  3. The link is a web page — parsed for Open Graph, video tags, JSON-LD and
 *     inline media URLs.
 *
 * DRM stays unsupported on purpose.
 */
class MediaResolver(private val client: OkHttpClient = HttpClients.shared) {

    suspend fun resolve(rawUrl: String): ResolveResult = withContext(Dispatchers.IO) {
        val url = normalise(rawUrl)
            ?: return@withContext ResolveResult.Failure("That does not look like a valid link.")

        // A site whose streams are signed per session has to go through the
        // extractor: the page simply does not contain a media URL to find, so
        // parsing it would fail no matter how hard we looked.
        if (StreamExtractor.handles(url)) {
            when (val outcome = StreamExtractor.extract(url)) {
                is StreamExtractor.Outcome.Found ->
                    return@withContext ResolveResult.Success(outcome.candidates)

                is StreamExtractor.Outcome.Playlist ->
                    return@withContext ResolveResult.Playlist(outcome.playlist)

                is StreamExtractor.Outcome.NotSupported ->
                    return@withContext ResolveResult.Failure(outcome.reason)

                is StreamExtractor.Outcome.Failed -> {
                    // Extractors break when a site changes; falling through to
                    // plain page parsing is worth a try before giving up.
                    val fallback = runCatching { resolveFromPage(url) }.getOrNull()
                    if (fallback is ResolveResult.Success) return@withContext fallback
                    return@withContext ResolveResult.Failure(
                        "${outcome.message} This usually means the site changed and the " +
                            "extractor needs updating.",
                    )
                }
            }
        }

        val direct = runCatching { probe(url, referer = null) }.getOrElse { error ->
            return@withContext ResolveResult.Failure(networkMessage(error))
        }

        when {
            direct == null ->
                ResolveResult.Failure("The server did not respond to that link.")

            direct.isMedia ->
                ResolveResult.Success(listOf(direct.toMedia(titleHint = null)))

            direct.isHtml -> resolveFromPage(url)

            else -> ResolveResult.Failure(
                "That link returned ${direct.contentType ?: "an unknown file type"}, " +
                    "which is not audio or video. " +
                    "For signed hosts try one of: ${StreamExtractor.supportedServices().joinToString()}.",
            )
        }
    }

    /**
     * Resolve a single playlist entry into concrete download candidates.
     * Used after the user selects items from a playlist picker.
     */
    suspend fun resolvePlaylistEntry(entryUrl: String): ResolveResult = withContext(Dispatchers.IO) {
        when (val outcome = StreamExtractor.extractStreamUrl(entryUrl)) {
            is StreamExtractor.Outcome.Found -> ResolveResult.Success(outcome.candidates)
            is StreamExtractor.Outcome.Playlist -> ResolveResult.Failure(
                "That entry resolved to another playlist, which is not supported here.",
            )
            is StreamExtractor.Outcome.NotSupported -> ResolveResult.Failure(outcome.reason)
            is StreamExtractor.Outcome.Failed -> ResolveResult.Failure(outcome.message)
        }
    }

    // ── Page parsing ────────────────────────────────────────────────────────

    private suspend fun resolveFromPage(pageUrl: String): ResolveResult {
        val html = runCatching { fetchHtml(pageUrl) }.getOrElse { error ->
            return ResolveResult.Failure(networkMessage(error))
        } ?: return ResolveResult.Failure("Could not read that page.")

        val document = runCatching { Jsoup.parse(html, pageUrl) }.getOrNull()
            ?: return ResolveResult.Failure("Could not read that page.")

        val pageTitle = sequenceOf(
            document.selectFirst("meta[property=og:title]")?.attr("content"),
            document.selectFirst("meta[name=twitter:title]")?.attr("content"),
            document.title(),
        ).firstOrNull { !it.isNullOrBlank() }?.trim()

        val poster = posterFrom(document)
        val candidates = collectCandidates(document, html)
        if (candidates.isEmpty()) {
            val services = StreamExtractor.supportedServices().joinToString()
            return ResolveResult.Failure(
                "No downloadable media was found on that page. " +
                    "Signed hosts that work: $services. " +
                    "HLS (.m3u8) and DRM streams are not supported.",
            )
        }

        val downloadable = candidates.filterNot { isHls(it) }.take(MAX_PROBES)
        val hlsSeen = candidates.any { isHls(it) }

        val probed = coroutineScope {
            downloadable
                .map { candidate ->
                    async(Dispatchers.IO) {
                        runCatching { probe(candidate, referer = pageUrl) }.getOrNull()
                    }
                }
                .awaitAll()
        }

        val media = probed
            .filterNotNull()
            .filter { it.isMedia }
            .distinctBy { it.finalUrl }
            .mapIndexed { index, item ->
                item.toMedia(
                    titleHint = if (index == 0) pageTitle else null,
                    thumbnailUrl = poster,
                )
            }
            .sortedByDescending { it.sizeBytes }

        return when {
            media.isNotEmpty() -> ResolveResult.Success(media)

            hlsSeen -> ResolveResult.Failure(
                "That page streams over HLS (.m3u8). Saving it means downloading and " +
                    "joining hundreds of segments, which this build does not do yet.",
            )

            else -> ResolveResult.Failure(
                "Found media links on that page, but none of them could be downloaded.",
            )
        }
    }

    private fun collectCandidates(document: org.jsoup.nodes.Document, rawHtml: String): List<String> {
        val found = LinkedHashSet<String>()

        fun add(value: String?) {
            val trimmed = value?.trim().orEmpty()
            if (trimmed.isNotEmpty() && trimmed.startsWith("http")) found += trimmed
        }

        listOf(
            "meta[property=og:video:secure_url]",
            "meta[property=og:video:url]",
            "meta[property=og:video]",
            "meta[name=twitter:player:stream]",
            "meta[property=og:audio]",
        ).forEach { selector ->
            document.select(selector).forEach { add(it.absUrl("content").ifEmpty { it.attr("content") }) }
        }

        document.select("video[src]").forEach { add(it.absUrl("src")) }
        document.select("video source[src], audio source[src]").forEach { add(it.absUrl("src")) }
        document.select("audio[src]").forEach { add(it.absUrl("src")) }
        document.select("link[rel=alternate][type^=video]").forEach { add(it.absUrl("href")) }

        document.select("[data-video-src], [data-src], [data-url], [data-file]").forEach { element ->
            listOf("data-video-src", "data-src", "data-url", "data-file").forEach { attribute ->
                val value = element.attr(attribute)
                if (value.isNotBlank() && MEDIA_EXTENSIONS.any { value.substringBefore('?').lowercase(Locale.ROOT).endsWith(it) }) {
                    add(element.absUrl(attribute).ifEmpty { value })
                }
            }
        }
        document.select("a[href]").forEach { anchor ->
            val href = anchor.absUrl("href")
            if (MEDIA_EXTENSIONS.any { href.substringBefore('?').lowercase(Locale.ROOT).endsWith(it) }) add(href)
        }

        JSON_MEDIA_KEY.findAll(rawHtml).forEach { match -> add(unescape(match.groupValues[1])) }
        INLINE_MEDIA_URL.findAll(rawHtml).take(60).forEach { match -> add(unescape(match.value)) }

        return found.toList()
    }

    // ── Probing ─────────────────────────────────────────────────────────────

    private data class Probe(
        val finalUrl: String,
        val contentType: String?,
        val contentLength: Long,
        val acceptsRanges: Boolean,
        val fileNameHint: String?,
    ) {
        val isMedia: Boolean
            get() {
                val type = contentType?.lowercase(Locale.ROOT).orEmpty()
                if (type.startsWith("video/") || type.startsWith("audio/")) return true
                if (type.startsWith("text/") || type.startsWith("application/json")) return false
                if (type.isEmpty() || type.startsWith("application/octet-stream") ||
                    type.startsWith("binary/")
                ) {
                    return MEDIA_EXTENSIONS.any { finalUrl.substringBefore('?').lowercase(Locale.ROOT).endsWith(it) }
                }
                return false
            }

        val isHtml: Boolean
            get() = contentType?.lowercase(Locale.ROOT)?.startsWith("text/html") == true
    }

    private fun probe(url: String, referer: String?): Probe? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", HttpClients.USER_AGENT)
            .header("Range", "bytes=0-0")
            .apply { if (referer != null) header("Referer", referer) }
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) return null
            val contentRange = response.header("Content-Range")
            val total = when {
                contentRange != null -> contentRange.substringAfter('/', "").toLongOrNull() ?: -1L
                else -> response.header("Content-Length")?.toLongOrNull() ?: -1L
            }
            return Probe(
                finalUrl = response.request.url.toString(),
                contentType = response.header("Content-Type")?.substringBefore(';')?.trim(),
                contentLength = total,
                acceptsRanges = response.code == 206 ||
                    response.header("Accept-Ranges")?.contains("bytes") == true,
                fileNameHint = fileNameFromDisposition(response),
            )
        }
    }

    private fun fetchHtml(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", HttpClients.USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            val source = body.source()
            source.request(MAX_HTML_BYTES)
            val available = minOf(source.buffer.size, MAX_HTML_BYTES)
            return source.buffer.readString(available, Charsets.UTF_8)
        }
    }

    private fun Probe.toMedia(titleHint: String?, thumbnailUrl: String? = null): ResolvedMedia {
        val name = fileNameHint
            ?: fileNameFromUrl(finalUrl)
            ?: defaultName(contentType)
        val title = titleHint?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.')
        return ResolvedMedia(
            mediaUrl = finalUrl,
            title = title,
            fileName = sanitiseFileName(ensureExtension(name, contentType)),
            mimeType = contentType ?: "video/mp4",
            sizeBytes = contentLength,
            resumable = acceptsRanges,
            thumbnailUrl = thumbnailUrl,
        )
    }

    private fun posterFrom(document: org.jsoup.nodes.Document): String? = sequenceOf(
        document.selectFirst("meta[property=og:image:secure_url]")?.absUrl("content"),
        document.selectFirst("meta[property=og:image]")?.absUrl("content"),
        document.selectFirst("meta[name=twitter:image]")?.absUrl("content"),
        document.selectFirst("video[poster]")?.absUrl("poster"),
    ).firstOrNull { !it.isNullOrBlank() && it.startsWith("http") }

    private fun normalise(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return withScheme.takeIf { it.contains('.') }
    }

    private fun isHls(url: String): Boolean =
        url.substringBefore('?').lowercase(Locale.ROOT).let { it.endsWith(".m3u8") || it.endsWith(".m3u") }

    private fun unescape(value: String): String =
        value.replace("\\/", "/").replace("&amp;", "&").trim('"', '\'', ' ')

    private fun fileNameFromDisposition(response: Response): String? {
        val header = response.header("Content-Disposition") ?: return null
        val match = DISPOSITION_NAME.find(header) ?: return null
        val raw = match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: return null
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw).trim('"')
    }

    private fun fileNameFromUrl(url: String): String? =
        url.substringBefore('?').substringAfterLast('/').takeIf { it.contains('.') && it.length in 2..120 }

    private fun defaultName(contentType: String?): String = when {
        contentType?.startsWith("audio/") == true -> "audio.m4a"
        else -> "video.mp4"
    }

    private fun ensureExtension(name: String, contentType: String?): String {
        if (name.contains('.')) return name
        val extension = when {
            contentType == null -> "mp4"
            contentType.contains("webm") -> "webm"
            contentType.contains("mpeg") && contentType.startsWith("audio") -> "mp3"
            contentType.startsWith("audio") -> "m4a"
            contentType.contains("quicktime") -> "mov"
            contentType.contains("matroska") -> "mkv"
            else -> "mp4"
        }
        return "$name.$extension"
    }

    private fun sanitiseFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120).ifBlank { "video.mp4" }

    private fun networkMessage(error: Throwable): String = when (error) {
        is IOException -> "Could not reach that link. Check the connection and try again."
        else -> error.message ?: "Something went wrong while reading that link."
    }

    private companion object {
        const val MAX_HTML_BYTES = 2L * 1024 * 1024
        const val MAX_PROBES = 14

        val MEDIA_EXTENSIONS = listOf(
            ".mp4", ".m4v", ".webm", ".mkv", ".mov", ".avi", ".flv", ".ts",
            ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".flac",
        )

        val JSON_MEDIA_KEY = Regex(
            "\"(?:contentUrl|videoUrl|video_url|playbackUrl|file|src)\"\\s*:\\s*\"(https?:[^\"]{8,600})\"",
            RegexOption.IGNORE_CASE,
        )

        val INLINE_MEDIA_URL = Regex(
            "https?://[^\\s\"'<>\\\\]{8,600}?\\.(?:mp4|webm|m4v|mov|mkv|m4a|mp3)" +
                "(?:\\?[^\\s\"'<>\\\\]{0,200})?",
            RegexOption.IGNORE_CASE,
        )

        val DISPOSITION_NAME = Regex(
            "filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?",
            RegexOption.IGNORE_CASE,
        )
    }
}
