package com.dawood.orbit.tools.videodownloader.resolve

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

    data class Failure(val reason: String) : ResolveResult
}

/**
 * Turns whatever the user pasted into a downloadable media URL.
 *
 * Two strategies, tried in order:
 *  1. The link already points at a media file — the common case for direct CDN
 *     links, course platforms and podcast feeds.
 *  2. The link is a web page — the page is parsed for the media it embeds
 *     (Open Graph video tags, `<video>`/`<source>`, JSON-LD `contentUrl`, and
 *     finally media URLs appearing in inline scripts).
 *
 * Sites that hide their streams behind signed, per-session APIs — YouTube being
 * the obvious one — need a dedicated extractor and are reported as unsupported
 * rather than failing obscurely.
 */
class MediaResolver(private val client: OkHttpClient = HttpClients.shared) {

    suspend fun resolve(rawUrl: String): ResolveResult = withContext(Dispatchers.IO) {
        val url = normalise(rawUrl)
            ?: return@withContext ResolveResult.Failure("That does not look like a valid link.")

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
                    "which is not audio or video.",
            )
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

        val candidates = collectCandidates(document, html)
        if (candidates.isEmpty()) {
            return ResolveResult.Failure(
                "No downloadable media was found on that page. Sites that sign their " +
                    "video URLs per session (YouTube and similar) need a dedicated " +
                    "extractor, which this build does not include yet.",
            )
        }

        val downloadable = candidates.filterNot { isHls(it) }.take(MAX_PROBES)
        val hlsSeen = candidates.any { isHls(it) }

        // Probed in parallel: a page with a dozen candidates would otherwise
        // take a dozen round trips before showing the user anything.
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
                // Only the first gets the page title; the rest would all end up
                // with the same name and overwrite each other in the queue.
                item.toMedia(titleHint = if (index == 0) pageTitle else null)
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

        // Open Graph and Twitter cards are the most reliable, so they go first.
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

        // Lazy players keep the real file on a data attribute until playback.
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

        // JSON-LD and inline players keep the real file in a plain string.
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
                // Many CDNs serve media as a generic binary stream; fall back to
                // the file extension rather than refusing a perfectly good file.
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

    /**
     * A one-byte ranged GET. It reveals the content type, the total size and
     * whether the server honours ranges — the three things a resumable
     * download needs — without pulling the file.
     */
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
            // Cap the read: a page big enough to blow memory is not a page that
            // is going to yield a clean media link anyway.
            val source = body.source()
            source.request(MAX_HTML_BYTES)
            val available = minOf(source.buffer.size, MAX_HTML_BYTES)
            return source.buffer.readString(available, Charsets.UTF_8)
        }
    }

    private fun Probe.toMedia(titleHint: String?): ResolvedMedia {
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
        )
    }

    // ── Small helpers ───────────────────────────────────────────────────────

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

        /** Probing is a network round trip each, so the list is capped. */
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
