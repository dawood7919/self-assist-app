package com.dawood.orbit.tools.videodownloader.engine

import android.content.Context
import com.dawood.orbit.tools.videodownloader.data.DownloadRepository
import com.dawood.orbit.tools.videodownloader.model.DownloadItem
import com.dawood.orbit.tools.videodownloader.model.DownloadStatus
import com.dawood.orbit.tools.videodownloader.resolve.HttpClients
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * The transfer itself.
 *
 * Resuming is the whole point of this class, so it is built around HTTP range
 * requests: the length of the partial file on disk is the offset to ask for,
 * and the file's own validator (`ETag`, else `Last-Modified`) is sent as
 * `If-Range` so a file that changed on the server restarts cleanly instead of
 * being silently stitched together from two different versions.
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

        val alreadyOnDisk = if (partFile.exists()) partFile.length() else 0L

        // The server said how big the file is and we already have that much:
        // nothing to transfer, just publish it.
        if (item.totalBytes > 0 && alreadyOnDisk >= item.totalBytes) {
            finish(id, partFile)
            return@withContext
        }

        val resumeFrom = if (item.resumable) alreadyOnDisk else 0L
        val request = buildRequest(item, resumeFrom)

        try {
            client.newCall(request).execute().use { response ->
                when {
                    // Asked for a range past the end of the file. Either we
                    // already have everything, or the partial file is stale.
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

                // 206 means the range was honoured and we append. Anything else
                // means the server sent the whole file regardless, so the
                // partial data is worthless and the file restarts at zero.
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
        } catch (cancelled: CancellationException) {
            // Pause or shutdown. The partial file is intentionally left alone.
            throw cancelled
        } catch (error: IOException) {
            fail(id, error.message ?: "The connection dropped.")
        } catch (error: Exception) {
            fail(id, error.message ?: "The download failed unexpectedly.")
        }
    }

    private fun buildRequest(item: DownloadItem, resumeFrom: Long): Request {
        val builder = Request.Builder()
            .url(item.mediaUrl)
            .header("User-Agent", HttpClients.USER_AGENT)
            .header("Accept", "*/*")
            .apply {
                if (item.sourceUrl != item.mediaUrl) header("Referer", item.sourceUrl)
            }

        if (resumeFrom > 0) {
            builder.header("Range", "bytes=$resumeFrom-")
            // Without a validator the server cannot tell us the file changed,
            // so a resume could append new bytes onto old ones.
            (item.etag ?: item.lastModified)?.let { builder.header("If-Range", it) }
        }
        return builder.get().build()
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
                        val speed = bytesSincePublish * 1000 / elapsed.coerceAtLeast(1)
                        val snapshot = written
                        repository.updateProgressInMemory(id) {
                            it.copy(
                                downloadedBytes = snapshot,
                                totalBytes = totalBytes,
                                speedBytesPerSecond = speed,
                                status = DownloadStatus.Running,
                            )
                        }
                        lastPublishAt = now
                        bytesSincePublish = 0L
                    }
                }
                output.flush()
                output.fd.sync()
            }
        }

        val finalBytes = written
        repository.updateProgressInMemory(id) {
            it.copy(downloadedBytes = finalBytes, speedBytesPerSecond = 0L)
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
    }
}
