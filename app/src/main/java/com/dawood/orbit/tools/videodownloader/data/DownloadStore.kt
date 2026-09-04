package com.dawood.orbit.tools.videodownloader.data

import android.content.Context
import com.dawood.orbit.tools.videodownloader.model.DownloadItem
import com.dawood.orbit.tools.videodownloader.model.DownloadStatus
import com.dawood.orbit.tools.videodownloader.model.Segment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Disk persistence for the download queue.
 *
 * Deliberately a plain JSON file rather than a database: the queue is small,
 * and keeping it behind this narrow interface means moving to Room later
 * touches this file and nothing else.
 */
internal class DownloadStore(context: Context) {

    private val file = File(context.filesDir, "downloads.json")

    @Synchronized
    fun load(): List<DownloadItem> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { index ->
                runCatching { array.getJSONObject(index).toItem() }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(items: List<DownloadItem>) {
        runCatching {
            val array = JSONArray()
            items.forEach { array.put(it.toJson()) }
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(array.toString())
            temp.renameTo(file)
        }
    }

    private fun DownloadItem.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("sourceUrl", sourceUrl)
        put("mediaUrl", mediaUrl)
        put("title", title)
        put("fileName", fileName)
        put("mimeType", mimeType)
        put("partPath", partPath)
        put("totalBytes", totalBytes)
        put("downloadedBytes", downloadedBytes)
        put("status", if (status == DownloadStatus.Running || status == DownloadStatus.Resolving) {
            DownloadStatus.Paused.name
        } else {
            status.name
        })
        put("errorMessage", errorMessage ?: JSONObject.NULL)
        put("etag", etag ?: JSONObject.NULL)
        put("lastModified", lastModified ?: JSONObject.NULL)
        put("resumable", resumable)
        put("createdAt", createdAt)
        put("completedAt", completedAt ?: JSONObject.NULL)
        put("savedLocation", savedLocation ?: JSONObject.NULL)
        put("thumbnailUrl", thumbnailUrl ?: JSONObject.NULL)
        put("playlistGroupId", playlistGroupId ?: JSONObject.NULL)
        put("playlistTitle", playlistTitle ?: JSONObject.NULL)
        put("qualityLabel", qualityLabel ?: JSONObject.NULL)
        put(
            "segments",
            JSONArray().apply {
                segments.forEach { segment ->
                    put(
                        JSONObject().apply {
                            put("start", segment.start)
                            put("end", segment.end)
                            put("completed", segment.completed)
                        },
                    )
                }
            },
        )
    }

    private fun JSONObject.toItem(): DownloadItem = DownloadItem(
        id = getString("id"),
        sourceUrl = getString("sourceUrl"),
        mediaUrl = optString("mediaUrl", ""),
        title = optString("title", "Untitled"),
        fileName = optString("fileName", "video.mp4"),
        mimeType = optString("mimeType", "video/mp4"),
        partPath = optString("partPath", ""),
        totalBytes = optLong("totalBytes", -1L),
        downloadedBytes = optLong("downloadedBytes", 0L),
        status = runCatching { DownloadStatus.valueOf(optString("status")) }
            .getOrDefault(DownloadStatus.Paused),
        errorMessage = optStringOrNull("errorMessage"),
        etag = optStringOrNull("etag"),
        lastModified = optStringOrNull("lastModified"),
        resumable = optBoolean("resumable", true),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        completedAt = if (isNull("completedAt")) null else optLong("completedAt"),
        savedLocation = optStringOrNull("savedLocation"),
        thumbnailUrl = optStringOrNull("thumbnailUrl"),
        playlistGroupId = optStringOrNull("playlistGroupId"),
        playlistTitle = optStringOrNull("playlistTitle"),
        qualityLabel = optStringOrNull("qualityLabel"),
        segments = optJSONArray("segments")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                runCatching {
                    val json = array.getJSONObject(index)
                    Segment(
                        start = json.getLong("start"),
                        end = json.getLong("end"),
                        completed = json.optLong("completed", 0L),
                    )
                }.getOrNull()
            }
        }.orEmpty(),
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
}
