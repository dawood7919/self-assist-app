package com.dawood.orbit.tools.cloudbrowser.real

import com.dawood.orbit.tools.cloudbrowser.CdpMessages
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lifecycle owner for the headless Chromium on the VPS, reached through the
 * SSH tunnel's local port.
 *
 * Blocking contract: EVERY method below performs network I/O over SSH or
 * HTTP and MUST be called from a background thread (callers already run on
 * Dispatchers.IO). No method posts to Main, touches the UI, or launches
 * coroutines. HTTP uses [HttpURLConnection] only — no new dependencies.
 *
 * URL contract: every [CdpTarget.wsUrl] and [browserWsUrl] result returned
 * here is already rewritten to `127.0.0.1:[localPort]`, so the caller can
 * hand it straight to the [cdpFactory] client without any host fixup.
 * ([CdpClient] itself never rewrites hosts.)
 *
 * Target creation prefers the plain DevTools HTTP endpoint
 * (`PUT /json/new?url=...`, then a `/json/list` match for the WebSocket
 * URL) and falls back to a browser-level `Target.createTarget` round trip
 * over a short-lived [CdpClient] from [cdpFactory] when HTTP cannot supply
 * the WebSocket URL. Closing prefers `GET /json/close/[id]` with the same
 * WS fallback, and treats "already gone" (HTTP 404 / CDP "No target") as
 * success so cleanup stays idempotent.
 */
class ChromiumManager(
    private val ssh: SshManager,
    private val cdpFactory: (String) -> CdpClient,
) {

    /**
     * Blocking: resizes the browser window hosting [targetId] to exactly
     * [w] x [h] CSS pixels (so the 720p/1080p/1440p presets are real, not
     * just screencast scaling). Must be called on a background thread. Uses
     * the browser-level WebSocket (`Browser.getWindowForTarget` +
     * `Browser.setWindowBounds`).
     */
    fun resizeWindow(localPort: Int, targetId: String, w: Int, h: Int): Result<Unit> {
        val browser = browserWsUrl(localPort)
        if (browser.isFailure) {
            // Non-fatal: creation already works at the service default size;
            // a failed resize must not abort the session launch.
            return Result.success(Unit)
        }
        val client = try {
            cdpFactory(browser.getOrThrow())
        } catch (e: Exception) {
            return Result.success(Unit)
        }
        try {
            val connected = client.connectBlocking(browser.getOrThrow(), WS_TIMEOUT_MS)
            if (connected.isFailure) return Result.success(Unit)
            val getId = CdpMessages.nextId()
            val got = client.sendAndAwait(CdpMessages.getWindowForTarget(getId, targetId), getId, WS_TIMEOUT_MS)
            if (got.isFailure) return Result.success(Unit)
            val windowId = got.getOrNull()?.optInt("windowId", -1)?.takeIf { it != null && it >= 0 }
                ?: return Result.success(Unit)
            val setId = CdpMessages.nextId()
            client.sendAndAwait(CdpMessages.setWindowBounds(setId, windowId, w, h), setId, WS_TIMEOUT_MS)
            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.success(Unit)
        } finally {
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Blocking: returns the browser-level CDP WebSocket URL via
     * `GET /json/version` on `127.0.0.1:[localPort]` (5 s timeouts),
     * rewritten to the tunnel's local port. Must be called on a background
     * thread.
     */
    fun browserWsUrl(localPort: Int): Result<String> {
        if (localPort <= 0) {
            return Result.failure(Exception("Invalid localPort: $localPort"))
        }
        val body = httpCall("http://127.0.0.1:$localPort/json/version", "GET", HTTP_TIMEOUT_MS)
        if (body.isFailure) {
            return Result.failure(
                Exception(
                    "DevTools /json/version unreachable on 127.0.0.1:$localPort: " +
                        "${body.exceptionOrNull()?.message}",
                ),
            )
        }
        return try {
            val wsUrl = JSONObject(body.getOrNull() ?: "{}").optString("webSocketDebuggerUrl", "")
            if (wsUrl.isBlank()) {
                Result.failure(Exception("DevTools /json/version has no webSocketDebuggerUrl."))
            } else {
                Result.success(rewriteWsUrl(wsUrl, localPort))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse /json/version: ${e.message}"))
        }
    }

    /**
     * Blocking: lists the page targets visible on `GET /json/list`.
     * Must be called on a background thread. Rows without an id or without
     * a `webSocketDebuggerUrl` (browser/version pseudo-targets) are
     * skipped. All returned URLs are rewritten to [localPort].
     */
    fun listTargets(localPort: Int): Result<List<CdpTarget>> {
        if (localPort <= 0) {
            return Result.failure(Exception("Invalid localPort: $localPort"))
        }
        val body = httpCall("http://127.0.0.1:$localPort/json/list", "GET", HTTP_TIMEOUT_MS)
        if (body.isFailure) {
            return Result.failure(
                Exception("DevTools /json/list unreachable: ${body.exceptionOrNull()?.message}"),
            )
        }
        return try {
            val array = JSONArray(body.getOrNull() ?: "[]")
            val out = mutableListOf<CdpTarget>()
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                val id = row.optString("id", "")
                val wsUrl = row.optString("webSocketDebuggerUrl", "")
                if (id.isBlank() || wsUrl.isBlank()) continue
                out.add(CdpTarget(id = id, wsUrl = rewriteWsUrl(wsUrl, localPort), url = row.optString("url", "")))
            }
            Result.success(out.toList())
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse /json/list: ${e.message}"))
        }
    }

    /**
     * Blocking: opens [url] in a new page target. Must be called on a
     * background thread. Tries `PUT /json/new?url=...` first (matching
     * `/json/list` for the WebSocket URL when the PUT body omits it);
     * [w]/[h] are the requested page size and are sent on the browser-WS
     * fallback path (`Target.createTarget`), since the HTTP endpoint sizes
     * by the browser window.
     */
    fun createTarget(localPort: Int, url: String, w: Int, h: Int): Result<CdpTarget> {
        if (localPort <= 0) {
            return Result.failure(Exception("Invalid localPort: $localPort"))
        }
        if (url.isBlank()) {
            return Result.failure(Exception("Blank url."))
        }
        val encoded = try {
            URLEncoder.encode(url, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            return Result.failure(Exception("Failed to encode url: ${e.message}"))
        }
        val put = httpCall("http://127.0.0.1:$localPort/json/new?url=$encoded", "PUT", HTTP_TIMEOUT_MS)
        var httpDetail: String? = put.exceptionOrNull()?.message
        if (put.isSuccess) {
            try {
                val row = JSONObject(put.getOrNull() ?: "{}")
                val id = row.optString("id", "")
                val wsUrl = row.optString("webSocketDebuggerUrl", "")
                if (id.isNotBlank() && wsUrl.isNotBlank()) {
                    val actualUrl = row.optString("url", url)
                    return Result.success(
                        CdpTarget(id = id, wsUrl = rewriteWsUrl(wsUrl, localPort), url = actualUrl),
                    )
                }
                if (id.isNotBlank()) {
                    listTargets(localPort).getOrNull()?.firstOrNull { it.id == id }?.let { match ->
                        return Result.success(match)
                    }
                    httpDetail = "PUT /json/new returned no webSocketDebuggerUrl for $id."
                } else {
                    httpDetail = "PUT /json/new returned no target id."
                }
            } catch (e: Exception) {
                httpDetail = "Failed to parse /json/new: ${e.message}"
            }
        }
        return createTargetViaBrowser(localPort, url, w, h, httpDetail)
    }

    /**
     * Blocking: closes [targetId], idempotently. Must be called on a
     * background thread. Tries `GET /json/close/[targetId]` first (HTTP 404
     * means already gone and counts as success), then the browser-WS
     * `Target.closeTarget` fallback.
     */
    fun closeTarget(localPort: Int, targetId: String): Result<Unit> {
        if (localPort <= 0) {
            return Result.failure(Exception("Invalid localPort: $localPort"))
        }
        if (targetId.isBlank()) {
            return Result.failure(Exception("Blank targetId."))
        }
        val close = httpCall("http://127.0.0.1:$localPort/json/close/$targetId", "GET", HTTP_TIMEOUT_MS)
        if (close.isSuccess) return Result.success(Unit)
        val detail = close.exceptionOrNull()?.message ?: ""
        if (detail.contains("code 404")) return Result.success(Unit)
        return closeTargetViaBrowser(localPort, targetId)
    }

    // ---- internals (all blocking; caller thread only) ----

    private fun createTargetViaBrowser(
        localPort: Int,
        url: String,
        w: Int,
        h: Int,
        httpDetail: String?,
    ): Result<CdpTarget> {
        val browser = browserWsUrl(localPort)
        if (browser.isFailure) {
            return Result.failure(
                Exception(
                    "createTarget failed over HTTP ($httpDetail) and the browser " +
                        "endpoint is unreachable: ${browser.exceptionOrNull()?.message}",
                ),
            )
        }
        val browserUrl = browser.getOrNull() ?: ""
        val client = try {
            cdpFactory(browserUrl)
        } catch (e: Exception) {
            return Result.failure(Exception("createTarget failed: ${e.message}"))
        }
        try {
            val connected = client.connectBlocking(browserUrl, WS_TIMEOUT_MS)
            if (connected.isFailure) {
                return Result.failure(
                    Exception("createTarget WS connect failed: ${connected.exceptionOrNull()?.message}"),
                )
            }
            val id = CdpMessages.nextId()
            val sent = client.sendAndAwait(CdpMessages.createTarget(id, url, w, h), id, WS_TIMEOUT_MS)
            if (sent.isFailure) {
                return Result.failure(
                    Exception("Target.createTarget failed: ${sent.exceptionOrNull()?.message}"),
                )
            }
            val targetId = try {
                sent.getOrNull()?.optString("targetId", "") ?: ""
            } catch (e: Exception) {
                return Result.failure(Exception("Failed to parse Target.createTarget: ${e.message}"))
            }
            if (targetId.isBlank()) {
                return Result.failure(Exception("Target.createTarget returned no targetId."))
            }
            listTargets(localPort).getOrNull()?.firstOrNull { it.id == targetId }?.let { match ->
                return Result.success(match)
            }
            return Result.failure(Exception("Target $targetId created but missing from /json/list."))
        } finally {
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun closeTargetViaBrowser(localPort: Int, targetId: String): Result<Unit> {
        val browser = browserWsUrl(localPort)
        if (browser.isFailure) {
            return Result.failure(
                Exception(
                    "closeTarget failed over HTTP and the browser endpoint is " +
                        "unreachable: ${browser.exceptionOrNull()?.message}",
                ),
            )
        }
        val browserUrl = browser.getOrNull() ?: ""
        val client = try {
            cdpFactory(browserUrl)
        } catch (e: Exception) {
            return Result.failure(Exception("closeTarget failed: ${e.message}"))
        }
        try {
            val connected = client.connectBlocking(browserUrl, WS_TIMEOUT_MS)
            if (connected.isFailure) {
                return Result.failure(
                    Exception("closeTarget WS connect failed: ${connected.exceptionOrNull()?.message}"),
                )
            }
            val id = CdpMessages.nextId()
            val sent = client.sendAndAwait(CdpMessages.closeTarget(id, targetId), id, WS_TIMEOUT_MS)
            if (sent.isSuccess) return Result.success(Unit)
            val detail = sent.exceptionOrNull()?.message ?: ""
            if (detail.contains("No target")) return Result.success(Unit)
            return Result.failure(Exception("Target.closeTarget failed: $detail"))
        } finally {
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun httpCall(urlText: String, method: String, timeoutMs: Int): Result<String> {
        var connection: HttpURLConnection? = null
        return try {
            val opened = java.net.URL(urlText).openConnection() as HttpURLConnection
            connection = opened
            opened.connectTimeout = timeoutMs
            opened.readTimeout = timeoutMs
            opened.requestMethod = method
            opened.doInput = true
            opened.instanceFollowRedirects = false
            opened.connect()
            val code = try {
                opened.responseCode
            } catch (e: Exception) {
                return Result.failure(Exception("DevTools HTTP $method failed: ${e.message}"))
            }
            if (code in 200..299) {
                Result.success(readBody(opened))
            } else {
                val err = try {
                    readBody(opened)
                } catch (_: Exception) {
                    ""
                }
                Result.failure(Exception("DevTools HTTP $method failed (code $code): ${err.take(200)}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("DevTools HTTP $method $urlText failed: ${e.message}"))
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun readBody(connection: HttpURLConnection): String {
        val code = try {
            connection.responseCode
        } catch (_: Exception) {
            -1
        }
        val stream = try {
            if (code in 200..299) connection.inputStream else connection.errorStream
        } catch (_: Exception) {
            null
        } ?: return ""
        stream.use { input ->
            val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
            val out = StringBuilder()
            val buf = CharArray(4096)
            while (true) {
                val read = reader.read(buf)
                if (read < 0) break
                out.append(buf, 0, read)
                if (out.length > MAX_BODY_CHARS) break
            }
            return out.toString()
        }
    }

    /**
     * Rewrites a DevTools WebSocket URL to the SSH tunnel: host becomes
     * 127.0.0.1 and the port becomes [localPort], preserving scheme, path
     * and query. Falls back to the original string when unparseable.
     */
    private fun rewriteWsUrl(wsUrl: String, localPort: Int): String {
        return try {
            val uri = java.net.URI(wsUrl)
            java.net.URI(
                uri.scheme,
                uri.userInfo,
                "127.0.0.1",
                localPort,
                uri.path,
                uri.query,
                uri.fragment,
            ).toString()
        } catch (_: Exception) {
            wsUrl
        }
    }

    private companion object {
        const val HTTP_TIMEOUT_MS = 5_000
        const val WS_TIMEOUT_MS = 10_000L
        const val MAX_BODY_CHARS = 512_000
    }
}

/** One DevTools page target: [wsUrl] is already rewritten to the tunnel port. */
data class CdpTarget(
    val id: String = "",
    val wsUrl: String = "",
    val url: String = "",
)
