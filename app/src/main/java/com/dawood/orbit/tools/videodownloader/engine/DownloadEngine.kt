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
 * The transfer itself — segmented multi-connection or single stream.
 * Resuming works in both shapes. Segmented resume replays each unfinished
 * range; single-stream resume uses the on-disk length as the Range start.
 */
internal class DownloadEngine(
    private val context: Context,
    private val repository: DownloadRepository,
    private val client: OkHttpClient = HttpClients.shared,
) {

    suspend fun run(id: String) = withContext(Dispatchers.IO) {
        var item = repository.get(id) ?: return@withContext
        item = StreamUrlRefresher.refresh(repository, item)
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
            throw cancelled
        } catch (error: IOException) {
            fail(id, error.message ?: "The connection dropped.")
        } catch (error: Exception) {
            fail(id, error.message ?: "The download failed unexpectedly.")
        }
    }

    private data class Head(
        val totalBytes: Long,
        val acceptsRanges: Boolean,
        val etag: String?,
        val lastModified: String?,
    ) {
        val canSegment: Boolean
            get() = acceptsRanges && totalBytes >= SegmentPlan.MIN_SEGMENTED_BYTES
    }

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

    private suspend fun runSegmented(
        id: String,
        item: DownloadItem,
        partFile: File,
        head: Head,
    ) {
        val validatorChanged = head.etag != null && item.etag != null && head.etag != item.etag
        val savedPlan = if (validatorChanged) emptyList() else item.segments
        val plan = SegmentPlan.restoreOrPlan(savedPlan, head.totalBytes)

        if (validatorChanged || !partFile.exists() || partFile.length() != head.totalBytes) {
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
            val reporter = launch { reportProgress(id, plan, progress, head.totalBytes) }
            try {
                plan.mapIndexedNotNull { index, segment ->
                    if (segment.isDone) null
                    else launch { transferSegment(item, partFile, segment, index, progress) }
                }.joinAll()
            } finally {
                reporter.cancel()
            }
        }

        val finalPlan = plan.mapIndexed { index, segment ->
            segment.copy(completed = progress.get(index))
        }
        repository.update(id) { it.copy(segments = finalPlan) }

        if (SegmentPlan.isComplete(finalPlan)) {
            finish(id, partFile)
        } else {
            val current = repository.get(id)
            if (current?.status == DownloadStatus.Running) {
                fail(id, "The download stopped before every segment finished.")
            }
        }
    }

    private suspend fun transferSegment(
        item: DownloadItem,
        partFile: File,
        segment: Segment,
        index: Int,
        progress: AtomicLongArray,
    ) {
        var attempt = 0
        while (attempt < MAX_SEGMENT_RETRIES) {
            currentCoroutineContext().ensureActive()
            try {
                val start = segment.start + progress.get(index)
                if (start > segment.end) return
                val request = baseRequest(item)
                    .header("Range", "bytes=$start-${segment.end}")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code != 206 && response.code != 200) {
                        throw IOException("Segment HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IOException("Empty segment body")
                    RandomAccessFile(partFile, "rw").use { raf ->
                        raf.seek(start)
                        val buffer = ByteArray(BUFFER_BYTES)
                        var offset = start
                        body.byteStream().use { input ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read <= 0) break
                                raf.write(buffer, 0, read)
                                offset += read
                                progress.set(index, offset - segment.start)
                            }
                        }
                    }
                }
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                attempt++
                if (attempt >= MAX_SEGMENT_RETRIES) throw error
                delay(300L * attempt)
            }
        }
    }

    private suspend fun reportProgress(
        id: String,
        plan: List<Segment>,
        progress: AtomicLongArray,
        totalBytes: Long,
    ) {
        var lastBytes = 0L
        var lastAt = System.currentTimeMillis()
        while (true) {
            delay(PUBLISH_INTERVAL_MS)
            val downloaded = (0 until plan.size).sumOf { progress.get(it) }
            val now = System.currentTimeMillis()
            val elapsed = (now - lastAt).coerceAtLeast(1L)
            val speed = ((downloaded - lastBytes) * 1000L) / elapsed
            lastBytes = downloaded
            lastAt = now
            publish(id, downloaded, totalBytes, speed)
            if (downloaded >= totalBytes && totalBytes > 0) return
        }
    }

    private suspend fun runSingleStream(id: String, item: DownloadItem, partFile: File) {
        val existing = if (partFile.exists()) partFile.length() else 0L
        repository.update(id) {
            it.copy(
                status = DownloadStatus.Running,
                downloadedBytes = existing,
                resumable = item.resumable,
                errorMessage = null,
            )
        }

        val requestBuilder = baseRequest(item)
        if (existing > 0 && item.resumable) {
            requestBuilder.header("Range", "bytes=$existing-")
            item.etag?.let { requestBuilder.header("If-Range", it) }
                ?: item.lastModified?.let { requestBuilder.header("If-Range", it) }
        }

        client.newCall(requestBuilder.get().build()).execute().use { response ->
            when (response.code) {
                200 -> {
                    if (existing > 0) partFile.delete()
                    writeBody(id, partFile, response.body?.byteStream() ?: throw IOException("Empty body"), append = false)
                }
                206 -> {
                    writeBody(id, partFile, response.body?.byteStream() ?: throw IOException("Empty body"), append = true, startAt = existing)
                }
                416 -> {
                    finish(id, partFile)
                    return
                }
                else -> throw IOException("HTTP ${response.code}")
            }
        }
        finish(id, partFile)
    }

    private suspend fun writeBody(
        id: String,
        partFile: File,
        input: java.io.InputStream,
        append: Boolean,
        startAt: Long = 0L,
    ) {
        var downloaded = startAt
        var lastPublish = System.currentTimeMillis()
        var lastBytes = downloaded
        FileOutputStream(partFile, append).use { output ->
            val buffer = ByteArray(BUFFER_BYTES)
            input.use { stream ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    val now = System.currentTimeMillis()
                    if (now - lastPublish >= PUBLISH_INTERVAL_MS) {
                        val speed = ((downloaded - lastBytes) * 1000L) / (now - lastPublish).coerceAtLeast(1L)
                        publish(id, downloaded, -1L, speed)
                        lastPublish = now
                        lastBytes = downloaded
                    }
                }
            }
        }
        publish(id, downloaded, downloaded, 0L)
    }

    private fun allocate(file: File, size: Long, keepContents: Boolean) {
        if (!keepContents && file.exists()) file.delete()
        RandomAccessFile(file, "rw").use { it.setLength(size) }
    }

    private fun baseRequest(item: DownloadItem): Request.Builder =
        Request.Builder()
            .url(item.mediaUrl)
            .header("User-Agent", HttpClients.USER_AGENT)
            .header("Accept", "*/*")
            .apply { if (item.sourceUrl != item.mediaUrl) header("Referer", item.sourceUrl) }

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
