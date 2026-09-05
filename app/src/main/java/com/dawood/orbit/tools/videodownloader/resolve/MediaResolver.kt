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
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

data class ResolvedMedia(
    val mediaUrl: String,
    val title: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val resumable: Boolean,
    val thumbnailUrl: String? = null,
    val qualityLabel: String? = null,
    val serviceName: String? = null,
    /** When set, [mediaUrl] is video-only and this audio track is used for playback merge. */
    val audioUrl: String? = null,
    /** True when the stream has no audio track in the same file. */
    val videoOnly: Boolean = false,
)

data class PlaylistEntry(
    val url: String,
    val title: String,
    val durationSeconds: Long? = null,
    val thumbnailUrl: String? = null,
    val uploader: String? = null,
)

data class ResolvedPlaylist(
    val url: String,
    val title: String,
    val uploader: String? = null,
    val thumbnailUrl: String? = null,
    val entryCount: Long,
    val entries: List<PlaylistEntry>,
    val serviceName: String,
    val truncated: Boolean = false,
)

sealed interface ResolveResult {
    data class Success(val candidates: List<ResolvedMedia>) : ResolveResult
    data class Playlist(val playlist: ResolvedPlaylist) : ResolveResult
    data class Failure(val reason: String) : ResolveResult
}

class MediaResolver(private val client: OkHttpClient = HttpClients.shared) {

    suspend fun resolve(rawUrl: String): ResolveResult = withContext(Dispatchers.IO) {
        val url = normalise(rawUrl)
            ?: return@withContext ResolveResult.Failure("That does not look like a valid link.")

        if (StreamExtractor.handles(url)) {
            when (val outcome = StreamExtractor.extract(url)) {
                is StreamExtractor.Outcome.Found ->
                    return@withContext ResolveResult.Success(outcome.candidates)

                is StreamExtractor.Outcome.Playlist ->
                    return@withContext ResolveResult.Playlist(outcome.playlist)

                is StreamExtractor.Outcome.NotSupported ->
                    return@withContext ResolveResult.Failure(outcome.reason)

                is StreamExtractor.Outcome.Failed -> {
                    val fallback = runCatching { resolveFromPage(url) }.getOrNull()
                    if (fallback is ResolveResult.Success || fallback is ResolveResult.Playlist) {
                        return@withContext fallback
                    }
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

    suspend fun resolvePlaylistEntry(entryUrl: String): ResolveResult = withContext(Dispatchers.IO) {
        val url = normalise(entryUrl)
            ?: return@withContext ResolveResult.Failure("Invalid entry link.")

        if (StreamExtractor.handles(url)) {
            when (val outcome = StreamExtractor.extractStreamUrl(url)) {
                is StreamExtractor.Outcome.Found -> return@withContext ResolveResult.Success(outcome.candidates)
                is StreamExtractor.Outcome.Playlist -> return@withContext ResolveResult.Failure(
                    "That entry resolved to another playlist.",
                )
                is StreamExtractor.Outcome.NotSupported -> { }
                is StreamExtractor.Outcome.Failed -> { }
            }
        }

        when (val page = resolveFromPage(url)) {
            is ResolveResult.Success -> page
            is ResolveResult.Playlist -> ResolveResult.Failure(
                "That page is another listing, not a single video.",
            )
            is ResolveResult.Failure -> page
        }
    }

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

        if (media.isNotEmpty()) return ResolveResult.Success(media)

        val listing = collectVideoPageLinks(document, pageUrl)
        if (listing.isNotEmpty()) {
            return ResolveResult.Playlist(
                ResolvedPlaylist(
                    url = pageUrl,
                    title = pageTitle?.ifBlank { null } ?: hostOf(pageUrl) ?: "Videos",
                    thumbnailUrl = poster,
                    entryCount = listing.size.toLong(),
                    entries = listing,
                    serviceName = hostOf(pageUrl) ?: "Web",
                    truncated = listing.size >= MAX_LISTING_ENTRIES,
                ),
            )
        }

        return when {
            hlsSeen -> ResolveResult.Failure(
                "That page streams over HLS (.m3u8). Saving it means downloading and " +
                    "joining hundreds of segments, which this build does not do yet.",
            )
            candidates.isNotEmpty() -> ResolveResult.Failure(
                "Found media links on that page, but none of them could be downloaded.",
            )
            else -> ResolveResult.Failure(
                "No downloadable media or video list was found on that page. " +
                    "Signed hosts that work: ${StreamExtractor.supportedServices().joinToString()}.",
            )
        }
    }

    private fun collectVideoPageLinks(
        document: org.jsoup.nodes.Document,
        pageUrl: String,
    ): List<PlaylistEntry> {
        val pageHost = hostOf(pageUrl) ?: return emptyList()
        val seen = LinkedHashSet<String>()
        val entries = mutableListOf<PlaylistEntry>()

        document.select("a[href]").forEach { anchor ->
            if (entries.size >= MAX_LISTING_ENTRIES) return@forEach
            val href = anchor.absUrl("href").ifBlank { return@forEach }
            if (hostOf(href)?.equals(pageHost, ignoreCase = true) != true) return@forEach
            if (!looksLikeVideoPage(href)) return@forEach
            val clean = href.substringBefore('#').substringBefore('?')
            if (!seen.add(clean)) return@forEach

            val title = sequenceOf(
                anchor.attr("title"),
                anchor.selectFirst("img[alt]")?.attr("alt"),
                anchor.text(),
            ).firstOrNull { !it.isNullOrBlank() && it.trim().length in 3..200 }
                ?.trim()
                ?: clean.substringAfterLast('/').ifBlank { "Video" }

            val thumb = anchor.selectFirst("img[src]")?.absUrl("src")
                ?.takeIf { it.startsWith("http") }

            entries += PlaylistEntry(url = clean, title = title, thumbnailUrl = thumb)
        }
        return entries
    }

    private fun looksLikeVideoPage(url: String): Boolean {
        val path = runCatching { URI(url).path.orEmpty().lowercase(Locale.ROOT) }
            .getOrDefault(url.lowercase(Locale.ROOT))
        if (path.length < 6) return false
        val blocked = listOf(
            "/login", "/register", "/search", "/categories", "/category",
            "/tags", "/tag/", "/models", "/channels", "/network",
            "/networks", "/pornstar", "/pornstars", "/page/", "/about",
            "/contact", "/privacy", "/terms", "/cdn-cgi", "/static",
            "/css", "/js/", "/images/", "/img/", "/fonts/",
        )
        if (blocked.any { path.contains(it) } && !VIDEO_PATH_HINT.any { path.contains(it) }) {
            return false
        }
        return VIDEO_PATH_HINT.any { path.contains(it) } ||
            path.substringAfterLast('/').let { slug ->
                slug.length >= 12 && slug.contains('-') && !slug.contains('.')
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
            document.select(selector).forEach {
                add(it.absUrl("content").ifEmpty { it.attr("content") })
            }
        }
        document.select("video[src]").forEach { add(it.absUrl("src")) }
        document.select("video source[src], audio source[src]").forEach { add(it.absUrl("src")) }
        document.select("audio[src]").forEach { add(it.absUrl("src")) }
        document.select("iframe[src]").forEach { add(it.absUrl("src")) }
        document.select("[data-video-src], [data-src], [data-url], [data-file], [data-mp4], [data-video]").forEach { element ->
            listOf("data-video-src", "data-src", "data-url", "data-file", "data-mp4", "data-video").forEach { attribute ->
                val value = element.attr(attribute)
                if (value.isNotBlank()) add(element.absUrl(attribute).ifEmpty { value })
            }
        }
        document.select("a[href]").forEach { anchor ->
            val href = anchor.absUrl("href")
            if (MEDIA_EXTENSIONS.any { href.substringBefore('?').lowercase(Locale.ROOT).endsWith(it) }) add(href)
        }
        JSON_MEDIA_KEY.findAll(rawHtml).forEach { match -> add(unescape(match.groupValues[1])) }
        INLINE_MEDIA_URL.findAll(rawHtml).take(80).forEach { match -> add(unescape(match.value)) }
        return found.toList()
    }

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
                if (type.isEmpty() || type.startsWith("application/octet-stream") || type.startsWith("binary/")) {
                    return MEDIA_EXTENSIONS.any {
                        finalUrl.substringBefore('?').lowercase(Locale.ROOT).endsWith(it)
                    }
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
        val name = fileNameHint ?: fileNameFromUrl(finalUrl) ?: defaultName(contentType)
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
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        return withScheme.takeIf { it.contains('.') }
    }

    private fun hostOf(url: String): String? =
        runCatching { URI(url).host?.lowercase(Locale.ROOT) }.getOrNull()

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

    private fun defaultName(contentType: String?): String =
        if (contentType?.startsWith("audio/") == true) "audio.m4a" else "video.mp4"

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
        const val MAX_LISTING_ENTRIES = 80
        val VIDEO_PATH_HINT = listOf(
            "/videos/", "/video/", "/watch/", "/v/", "/embed/",
            "/movie/", "/movies/", "/clip/", "/clips/", "/play/", "/view/",
        )
        val MEDIA_EXTENSIONS = listOf(
            ".mp4", ".m4v", ".webm", ".mkv", ".mov", ".avi", ".flv", ".ts",
            ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".flac",
        )
        val JSON_MEDIA_KEY = Regex(
            "\"(?:contentUrl|videoUrl|video_url|playbackUrl|file|src|source)\"\\s*:\\s*\"(https?:[^\"]{8,600})\"",
            RegexOption.IGNORE_CASE,
        )
        val INLINE_MEDIA_URL = Regex(
            "https?://[^\\s\"'<>\\\\]{8,600}?\\.(?:mp4|webm|m4v|mov|mkv|m4a|mp3)(?:\\?[^\\s\"'<>\\\\]{0,200})?",
            RegexOption.IGNORE_CASE,
        )
        val DISPOSITION_NAME = Regex(
            "filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?",
            RegexOption.IGNORE_CASE,
        )
    }
}
