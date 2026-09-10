package com.dawood.orbit.tools.cloudbrowser

import com.dawood.orbit.core.storage.JsonCodec
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON codecs for the Cloud Browser persisted collections.
 *
 * Pure Kotlin with org.json only — the same pattern as TaskCodec. Every
 * decode is tolerant: blank / malformed / non-array input returns an empty
 * list, a malformed row is skipped, and a row with missing fields or unknown
 * enum names falls back to the data class defaults. Decode never throws.
 */

/** Reads an enum by name, falling back to [default] for missing/unknown values. */
private fun <E : Enum<E>> JSONObject.optEnum(key: String, default: E, values: List<E>): E {
    val raw = optString(key, default.name)
    return values.firstOrNull { it.name == raw } ?: default
}

/** Reads a nullable Long: missing, null or non-numeric JSON becomes null. */
private fun JSONObject.optLongOrNull(key: String): Long? {
    if (isNull(key)) return null
    return runCatching { getLong(key) }.getOrNull()
}

/** Decodes a JSON array string into rows, skipping malformed rows, never throwing. */
private fun decodeRows(text: String): List<JSONObject> {
    if (text.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(text)
        (0 until array.length()).mapNotNull { index ->
            runCatching { array.getJSONObject(index) }.getOrNull()
        }
    }.getOrDefault(emptyList())
}

/** Persists [SavedServer] rows. Stores only the key *path*, never key contents. */
object CloudServerCodec : JsonCodec<SavedServer> {

    override fun encode(items: List<SavedServer>): String {
        val array = JSONArray()
        items.forEach { server ->
            array.put(
                JSONObject().apply {
                    put("id", server.id)
                    put("name", server.name)
                    put("host", server.host)
                    put("port", server.port)
                    put("username", server.username)
                    put("authMethod", server.authMethod.name)
                    put("keyPath", server.keyPath ?: JSONObject.NULL)
                    put("protocol", server.protocol.name)
                    put("lastLatencyMs", server.lastLatencyMs ?: JSONObject.NULL)
                },
            )
        }
        return array.toString()
    }

    override fun decode(text: String): List<SavedServer> =
        decodeRows(text).mapNotNull { json ->
            runCatching {
                SavedServer(
                    id = json.optString("id", ""),
                    name = json.optString("name", ""),
                    host = json.optString("host", ""),
                    port = json.optInt("port", 22),
                    username = json.optString("username", ""),
                    authMethod = json.optEnum("authMethod", AuthMethod.SshKey, AuthMethod.entries),
                    keyPath = json.optString("keyPath", null).takeUnless { json.isNull("keyPath") },
                    protocol = json.optEnum("protocol", Protocol.Ssh, Protocol.entries),
                    lastLatencyMs = json.optLongOrNull("lastLatencyMs"),
                )
            }.getOrNull()
        }
}

/** Persists [BrowserSession] rows. */
object CloudSessionCodec : JsonCodec<BrowserSession> {

    override fun encode(items: List<BrowserSession>): String {
        val array = JSONArray()
        items.forEach { session ->
            array.put(
                JSONObject().apply {
                    put("id", session.id)
                    put("serverId", session.serverId)
                    put("browser", session.browser.name)
                    put("profile", session.profile)
                    put("resolution", session.resolution.name)
                    put("quality", session.quality.name)
                    put("frameRate", session.frameRate)
                    put("state", session.state.name)
                    put("startedAtEpochMs", session.startedAtEpochMs)
                    put("lastSeenEpochMs", session.lastSeenEpochMs)
                },
            )
        }
        return array.toString()
    }

    override fun decode(text: String): List<BrowserSession> =
        decodeRows(text).mapNotNull { json ->
            runCatching {
                BrowserSession(
                    id = json.optString("id", ""),
                    serverId = json.optString("serverId", ""),
                    browser = json.optEnum("browser", BrowserKind.Chromium, BrowserKind.entries),
                    profile = json.optString("profile", "Default"),
                    resolution = json.optEnum("resolution", Resolution.P1080, Resolution.entries),
                    quality = json.optEnum("quality", Quality.Balanced, Quality.entries),
                    frameRate = json.optInt("frameRate", 30),
                    state = json.optEnum("state", SessionState.Active, SessionState.entries),
                    startedAtEpochMs = json.optLong("startedAtEpochMs", 0L),
                    lastSeenEpochMs = json.optLong("lastSeenEpochMs", 0L),
                )
            }.getOrNull()
        }
}

/** Persists the single [CloudSettings] row (0..1 rows; see SettingsStore). */
object CloudSettingsCodec : JsonCodec<CloudSettings> {

    override fun encode(items: List<CloudSettings>): String {
        val array = JSONArray()
        items.forEach { settings ->
            array.put(
                JSONObject().apply {
                    put("defaultBrowser", settings.defaultBrowser.name)
                    put("defaultQuality", settings.defaultQuality.name)
                    put("defaultResolution", settings.defaultResolution.name)
                    put("defaultFrameRate", settings.defaultFrameRate)
                    put("hwAccel", settings.hwAccel)
                    put("autoReconnect", settings.autoReconnect)
                    put("keepRunning", settings.keepRunning)
                    put("dataSaver", settings.dataSaver)
                    put("screenshotCacheBytes", settings.screenshotCacheBytes)
                },
            )
        }
        return array.toString()
    }

    override fun decode(text: String): List<CloudSettings> =
        decodeRows(text).mapNotNull { json ->
            runCatching {
                CloudSettings(
                    defaultBrowser = json.optEnum("defaultBrowser", BrowserKind.Chromium, BrowserKind.entries),
                    defaultQuality = json.optEnum("defaultQuality", Quality.Balanced, Quality.entries),
                    defaultResolution = json.optEnum("defaultResolution", Resolution.P1080, Resolution.entries),
                    defaultFrameRate = json.optInt("defaultFrameRate", 30),
                    hwAccel = json.optBoolean("hwAccel", true),
                    autoReconnect = json.optBoolean("autoReconnect", true),
                    keepRunning = json.optBoolean("keepRunning", false),
                    dataSaver = json.optBoolean("dataSaver", false),
                    screenshotCacheBytes = json.optLong("screenshotCacheBytes", CloudSettings().screenshotCacheBytes),
                )
            }.getOrNull()
        }
}

/** Persists [DownloadItem] rows. */
object CloudDownloadCodec : JsonCodec<DownloadItem> {

    override fun encode(items: List<DownloadItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("fileName", item.fileName)
                    put("type", item.type.name)
                    put("sizeBytes", item.sizeBytes)
                    put("downloadedBytes", item.downloadedBytes)
                    put("state", item.state.name)
                    put("sourcePath", item.sourcePath)
                },
            )
        }
        return array.toString()
    }

    override fun decode(text: String): List<DownloadItem> =
        decodeRows(text).mapNotNull { json ->
            runCatching {
                DownloadItem(
                    id = json.optString("id", ""),
                    fileName = json.optString("fileName", ""),
                    type = json.optEnum("type", FileType.Other, FileType.entries),
                    sizeBytes = json.optLong("sizeBytes", 0L),
                    downloadedBytes = json.optLong("downloadedBytes", 0L),
                    state = json.optEnum("state", DownloadState.Downloading, DownloadState.entries),
                    sourcePath = json.optString("sourcePath", ""),
                )
            }.getOrNull()
        }
}
