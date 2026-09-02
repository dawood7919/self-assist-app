package com.dawood.orbit.tools.videodownloader.engine

import android.content.Context
import com.dawood.orbit.tools.videodownloader.data.DownloadRepository
import com.dawood.orbit.tools.videodownloader.model.DownloadItem
import com.dawood.orbit.tools.videodownloader.model.DownloadStatus
import com.dawood.orbit.tools.videodownloader.model.Segment
import com.dawood.orbit.tools.videodownloader.model.SegmentPlan
import com.dawood.orbit.tools.videodownloader.resolve.HttpClients
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLongArray

/**
 * The transfer itself.
 *
 * Two shapes, chosen per file:
 *
 *  - **Segmented.** When the server honours byte ranges and reports a length,
 *    the file is split across several connections that each write straight to
 *    their own offset in one pre-allocated file. This is what actually uses
 *    the available bandwidth: a single TCP stream to a distant CDN is usually
 *    limited by round-trip latency and per-connection shaping long before it
 *    is limited by the phone's link.
 *  - **Single stream.** Everything else — no length, or no range support —
 *    falls back to one sequential connection.
 *
 * Resuming works in both shapes. Segmented resume replays each unfinished
 * range from `start + completed`; single-stream resume uses the length of the
 * partial file. Either way the file's own validator (`ETag`, else
 * `Last-Modified`) goes out as `If-Range`, so a file that changed on the
 * server restarts cleanly instead of being stitched together from two
 * different versions.
 */
internal class DownloadEngine(
    private val context: Context,
    private val repository: DownloadRepository,
    private val client: OkHttpClient = HttpClients.shared,
) {

    /**
     * Runs one download to completion, failure, or cancellation.
     *
     * Cancellation is how pausing works: the coroutine is cancelled, the
     * partial file stays on disk, and the caller records [DownloadStatus.Paused].
     */
    suspend fun run(id: String) = withContext(Dispatchers.IO) {
        val item = repository.get(id) ?: return@withContext
        val partFile = File(item.partPath)
        partFile.parentFile?.mkdirs()

        try {
            val head = probeForPlan(item)
            if (head != null && head.canSegment) {
                runSegmented(id, item, partFile, head)
            } else {
                runSingleStream(id, item, partFile)
            }
        } catch (cancelled: CancellationException) {
            // Pause or shutdown. The partial file is intentionally left alone.
            throw cancelled
        } catch (error: IOException) {
            fail(id, error.message ?: "The connection dropped.")
        } catch (error: Exception) {
            fail(id, error.message ?: "The download failed unexpectedly.")
        }
    }

    // ── Deciding the shape ──────────────────────────────────────────────────

    private data class Head(
        val totalBytes: Long,
        val acceptsRanges: Boolean,
        val etag: String?,
        val lastModified: String?,
    ) {
        val canSegment: Boolean
            get() = acceptsRanges && totalBytes >= SegmentPlan.MIN_SEGMENTED_BYTES
    }

    /**
     * A one-byte ranged GET. It answers the only two questions that decide the
     * shape of the transfer — how big is it, and will you serve ranges — while
     * pulling a single byte.
     */
    private fun probeForPlan(item: DownloadItem): Head? = runCatching {
        val request = baseRequest(item)
            .header("Range", "bytes=0-0")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) return@use null
            val contentRange = response.header("Content-Range")
            val total = when {
                contentRange != null -> contentRange.substringAfter('/', "").toLongOrNull() ?: -1L
                else -> response.header("Content-Length")?.toLongOrNull() ?: -1L
            }
            Head(
                totalBytes = total,
                acceptsRanges = response.code == 206 ||
                    response.header("Accept-Ranges")?.contains("bytes") == true,
                etag = response.header("ETag"),
                lastModified = response.header("Last-Modified"),
            )
        }
    }.getOrNull()

    // ── Segmented transfer ──────────────────────────────────────────────────

    private suspend fun runSegmented(
        id: String,
        item: DownloadItem,
        partFile: File,
        head: Head,
    ) {
        // A saved plan is reused only if it still describes this exact file,
        // and only if the validator has not changed underneath it.
        val validatorChanged = head.etag != null && item.etag != null && head.etag != item.etag
        val savedPlan = if (validatorChanged) emptyList() else item.segments
        val plan = SegmentPlan.restoreOrPlan(savedPlan, head.totalBytes)

        if (validatorChanged || !partFile.exists() || partFile.length() != head.totalBytes) {
            // Pre-allocating means every connection can seek straight to its
            // own offset, and the file never has to be stitched together.
            allocate(partFile, head.totalBytes, keepContents = !validatorChanged && partFile.exists())
        }

        val progress = AtomicLongArray(plan.size)
        plan.forEachIndexed { index, segment -> progress.set(index, segment.completed) }

        repository.update(id) {
            it.copy(
                status = DownloadStatus.Running,
                totalBytes = head.totalBytes,
                downloadedBytes = SegmentPlan.downloadedBytes(plan),
                segments = plan,
                resumable = true,
                errorMessage = null,
                etag = head.etag ?: it.etag,
                lastModified = head.lastModified ?: it.lastModified,
            )
        }

        if (SegmentPlan.isComplete(plan)) {
            finish(id, partFile)
            return
        }

        coroutineScope {
            // The reporter is a separate coroutine so a slow segment cannot
            // hold up progress reporting for the others. It loops forever, so
            // it is cancelled explicitly once the transfers have joined —
            // otherwise this scope would never return.
            val reporter = launch { reportProgress(id, plan, progress, head.totalBytes) }
            try {
                plan.mapIndexedNotNull { index, segment ->
                    if (segment.isDone) {
                        null
                    } else {
                        launch { transferSegment(item, partFile, segment, index, progress) }
                    }
                }.joinAll()
            } finally {
                reporter.cancel()
            }
        }

        val finalSegments = plan.mapIndexed { index, segment ->
            segment.copy(completed = progress.get(index))
        }
        val downloaded = SegmentPlan.downloadedBytes(finalSegments)
        repository.update(id) { it.copy(segments = finalSegments, downloadedBytes = downloaded) }

        if (SegmentPlan.isComplete(finalSegments)) {
            finish(id, partFile)
        } else {
            fail(id, "The transfer stopped before the file was complete. Retry to continue.")
        }
    }

    /** Pulls one byte range into its own slice of the file. */
    private suspend fun transferSegment(
        item: DownloadItem,
        partFile: File,
        segment: Segment,
        index: Int,
        progress: AtomicLongArray,
    ) {
        var completed = progress.get(index)
        var attempt = 0

        while (completed < segment.length) {
            currentCoroutineContext().ensureActive()
            val from = segment.start + completed
            val request = baseRequest(item)
                .header("Range", "bytes=$from-${segment.end}")
                .apply { (item.etag ?: item.lastModified)?.let { header("If-Range", it) } }
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    // Anything but 206 means the server ignored the range and is
                    // about to send the whole file down this one connection,
                    // which would corrupt the slice. Abandoning is correct.
                    if (response.code != 206) {
                        throw IOException("The server stopped honouring byte ranges (HTTP ${response.code}).")
                    }
                    val body = response.body ?: throw IOException("Empty response for one part of the file.")

                    RandomAccessFile(partFile, "rw").use { file ->
                        file.seek(from)
                        val buffer = ByteArray(BUFFER_BYTES)
                        body.byteStream().use { input ->
                            while (completed < segment.length) {
                                currentCoroutineContext().ensureActive()
                                val wanted = minOf(
                                    buffer.size.toLong(),
                                    segment.length - completed,
                                ).toInt()
                                val read = input.read(buffer, 0, wanted)
                                if (read <= 0) break
                                file.write(buffer, 0, read)
                                completed += read
                                progress.set(index, completed)
                            }
                        }
                    }
                }
                if (completed >= segment.length) return
                // The connection ended early — a CDN capping a response, or a
                // dropped socket. Reconnect from where it stopped.
                attempt++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                attempt++
                if (attempt > MAX_SEGMENT_RETRIES) throw error
            }

            if (attempt > MAX_SEGMENT_RETRIES) {
                throw IOException("One part of the file kept failing after $MAX_SEGMENT_RETRIES retries.")
            }
        }
    }

    /** Publishes combined progress and speed on a fixed cadence. */
    private suspend fun reportProgress(
        id: String,
        plan: List<Segment>,
        progress: AtomicLongArray,
        totalBytes: Long,
    ) {
        var lastBytes = SegmentPlan.downloadedBytes(plan)
        var lastAt = System.currentTimeMillis()

        while (true) {
            currentCoroutineContext().ensureActive()
            delay(PUBLISH_INTERVAL_MS)

            var downloaded = 0L
            for (index in 0 until progress.length()) downloaded += progress.get(index)

            val now = System.currentTimeMillis()
            val elapsed = (now - lastAt).coerceAtLeast(1)
            val speed = (downloaded - lastBytes) * 1000 / elapsed

            publish(id, downloaded, totalBytes, speed)
            lastBytes = downloaded
            lastAt = now
        }
    }

    /**
     * Creates the file at its final size so every connection can seek into it.
     * [keepContents] preserves whatever is already there, which is what makes a
     * resume pick up mid-file rather than starting over.
     */
    private fun allocate(partFile: File, totalBytes: Long, keepContents: Boolean) {
        if (!keepContents) partFile.delete()
        RandomAccessFile(partFile, "rw").use { it.setLength(totalBytes) }
    }

    // ── Single stream fallback ──────────────────────────────────────────────

    private suspend fun runSingleStream(id: String, item: DownloadItem, partFile: File) {
        val alreadyOnDisk = if (partFile.exists()) partFile.length() else 0L

        if (item.totalBytes > 0 && alreadyOnDisk >= item.totalBytes) {
            finish(id, partFile)
            return
        }

        val resumeFrom = if (item.resumable) alreadyOnDisk else 0L
        val request = baseRequest(item)
            .apply {
                if (resumeFrom > 0) {
                    header("Range", "bytes=$resumeFrom-")
                    (item.etag ?: item.lastModified)?.let { header("If-Range", it) }
                }
            }
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            when {
                response.code == 416 -> {
                    if (item.totalBytes > 0 && alreadyOnDisk >= item.totalBytes) {
                        finish(id, partFile)
                    } else {
                        partFile.delete()
                        fail(id, "The partial file no longer matches the server. Retry to start again.")
                    }
                    return@use
                }

                !response.isSuccessful -> {
                    fail(id, "The server refused the download (HTTP ${response.code}).")
                    return@use
                }
            }

            val appending = response.code == 206 && resumeFrom > 0
            val startOffset = if (appending) resumeFrom else 0L

            val declaredLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
            val totalBytes = when {
                appending && declaredLength > 0 -> startOffset + declaredLength
                declaredLength > 0 -> declaredLength
                else -> item.totalBytes
            }

            repository.update(id) {
                it.copy(
                    status = DownloadStatus.Running,
                    downloadedBytes = startOffset,
                    totalBytes = totalBytes,
                    segments = emptyList(),
                    errorMessage = null,
                    etag = response.header("ETag") ?: it.etag,
                    lastModified = response.header("Last-Modified") ?: it.lastModified,
                    resumable = it.resumable &&
                        (response.code == 206 || response.header("Accept-Ranges")?.contains("bytes") == true),
                )
            }

            val body = response.body ?: run {
                fail(id, "The server sent an empty response.")
                return@use
            }

            stream(
                id = id,
                source = body.byteStream(),
                partFile = partFile,
                append = appending,
                startOffset = startOffset,
                totalBytes = totalBytes,
            )
            finish(id, partFile)
        }
    }

    private suspend fun stream(
        id: String,
        source: java.io.InputStream,
        partFile: File,
        append: Boolean,
        startOffset: Long,
        totalBytes: Long,
    ) {
        val buffer = ByteArray(BUFFER_BYTES)
        var written = startOffset
        var lastPublishAt = System.currentTimeMillis()
        var bytesSincePublish = 0L

        source.use { input ->
            FileOutputStream(partFile, append).use { output ->
                while (true) {
                    // Cancellation is checked every chunk so pausing feels
                    // immediate rather than waiting for the response to end.
                    currentCoroutineContext().ensureActive()

                    val read = input.read(buffer)
                    if (read <= 0) break

                    output.write(buffer, 0, read)
                    written += read
                    bytesSincePublish += read

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastPublishAt
                    if (elapsed >= PUBLISH_INTERVAL_MS) {
                        publish(id, written, totalBytes, bytesSincePublish * 1000 / elapsed.coerceAtLeast(1))
                        lastPublishAt = now
                        bytesSincePublish = 0L
                    }
                }
                output.flush()
                output.fd.sync()
            }
        }

        publish(id, written, totalBytes, 0L)
    }

    // ── Shared ──────────────────────────────────────────────────────────────

    private fun baseRequest(item: DownloadItem): Request.Builder =
        Request.Builder()
            .url(item.mediaUrl)
            .header("User-Agent", HttpClients.USER_AGENT)
            .header("Accept", "*/*")
            .apply { if (item.sourceUrl != item.mediaUrl) header("Referer", item.sourceUrl) }

    /**
     * Publishes progress without touching the status.
     *
     * This is deliberate and was a real bug: a progress tick that also wrote
     * `Running` would land a fraction of a second after the user tapped pause
     * and put the row straight back to running, so pausing appeared to need
     * two taps. Progress never decides status now — only the service does.
     */
    private fun publish(id: String, downloaded: Long, totalBytes: Long, speed: Long) {
        repository.updateProgressInMemory(id) { item ->
            if (item.status != DownloadStatus.Running) {
                item
            } else {
                item.copy(
                    downloadedBytes = downloaded,
                    totalBytes = if (totalBytes > 0) totalBytes else item.totalBytes,
                    speedBytesPerSecond = speed,
                )
            }
        }
    }

    private fun finish(id: String, partFile: File) {
        val item = repository.get(id) ?: return
        val location = MediaExporter.export(context, partFile, item.fileName, item.mimeType)
        repository.update(id) {
            it.copy(
                status = DownloadStatus.Completed,
                downloadedBytes = if (it.totalBytes > 0) it.totalBytes else it.downloadedBytes,
                completedAt = System.currentTimeMillis(),
                speedBytesPerSecond = 0L,
                errorMessage = null,
                savedLocation = location,
            )
        }
    }

    private fun fail(id: String, message: String) {
        repository.update(id) {
            it.copy(
                status = DownloadStatus.Failed,
                errorMessage = message,
                speedBytesPerSecond = 0L,
            )
        }
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        const val PUBLISH_INTERVAL_MS = 400L
        const val MAX_SEGMENT_RETRIES = 4
    }
}
