package com.dawood.orbit.tools.cloudbrowser

import org.json.JSONObject

/**
 * Builders and parsers for the Chrome DevTools Protocol (CDP) messages used
 * by the Cloud Browser remote stream.
 *
 * Pure Kotlin with org.json only: zero Android / OkHttp / coroutines imports,
 * so this file runs on plain JVM unit tests as well as on device. Nothing
 * here touches the network or any thread: callers own threading, and the
 * blocking WebSocket work lives in
 * `com.dawood.orbit.tools.cloudbrowser.real.CdpClient`.
 *
 * Every builder returns a JSON String envelope of the form
 * `{"id":...,"method":...,"params":{...}}` ready to send over the target or
 * browser WebSocket. Ids come from [nextId]; a caller that manages its own
 * ids may pass any Int explicitly instead.
 *
 * Parsing never throws: [parseResponse] falls back to id -1 and
 * [parseEvent] falls back to null on malformed input.
 */
object CdpMessages {

    // CDP Input.dispatchMouseEvent / KeyEvent modifier bitmask.
    const val MOD_NONE = 0
    const val MOD_ALT = 1
    const val MOD_CTRL = 2
    const val MOD_META = 4
    const val MOD_SHIFT = 8

    private val idCounter = java.util.concurrent.atomic.AtomicInteger(0)

    /** Thread-safe monotonically increasing CDP message id, starting at 1. */
    fun nextId(): Int = idCounter.incrementAndGet()

    /** Navigates the target to [url] (`Page.navigate`). */
    fun navigate(id: Int, url: String): String =
        envelope(
            id = id,
            method = "Page.navigate",
            params = JSONObject().put("url", url),
        )

    /** Reloads the current page (`Page.reload`). */
    fun reload(id: Int, ignoreCache: Boolean = false): String =
        envelope(
            id = id,
            method = "Page.reload",
            params = JSONObject().put("ignoreCache", ignoreCache),
        )

    /** Stops an in-flight load (`Page.stopLoading`). */
    fun stopLoading(id: Int): String =
        envelope(
            id = id,
            method = "Page.stopLoading",
            params = JSONObject(),
        )

    /** Captures one JPEG still from the target (`Page.captureScreenshot`). */
    fun captureScreenshot(id: Int, quality: Int = 70): String =
        envelope(
            id = id,
            method = "Page.captureScreenshot",
            params = JSONObject()
                .put("format", "jpeg")
                .put("quality", quality),
        )

    /** Starts the frame stream (`Page.startScreencast`). */
    fun startScreencast(id: Int, w: Int, h: Int, quality: Int, everyNth: Int): String =
        envelope(
            id = id,
            method = "Page.startScreencast",
            params = JSONObject()
                .put("format", "jpeg")
                .put("quality", quality)
                .put("maxWidth", w)
                .put("maxHeight", h)
                .put("everyNthFrame", everyNth),
        )

    /** Acknowledges one screencast frame so the browser keeps sending. */
    fun screencastAck(id: Int, sessionId: Int): String =
        envelope(
            id = id,
            method = "Page.screencastFrameAck",
            params = JSONObject().put("sessionId", sessionId),
        )

    /** Stops the frame stream (`Page.stopScreencast`). */
    fun stopScreencast(id: Int): String =
        envelope(
            id = id,
            method = "Page.stopScreencast",
            params = JSONObject(),
        )

    /**
     * Dispatches a mouse event (`Input.dispatchMouseEvent`).
     *
     * [type] is one of `mousePressed`, `mouseReleased`, `mouseMoved` or
     * `mouseWheel`. Optional fields are omitted when null/default: pass
     * [button] (`left`/`right`/`middle`) plus [clickCount] for clicks,
     * [deltaX]/[deltaY] for wheel scrolls, [modifiers] for Ctrl+wheel zoom
     * ([MOD_CTRL]), and [buttons] as the bitmask of currently held buttons.
     */
    fun mouse(
        id: Int,
        type: String,
        x: Double,
        y: Double,
        button: String? = null,
        clickCount: Int? = null,
        deltaX: Double? = null,
        deltaY: Double? = null,
        modifiers: Int? = null,
        buttons: Int? = null,
        deltaMode: Int? = null,
    ): String {
        val params = JSONObject()
            .put("type", type)
            .put("x", x)
            .put("y", y)
        if (button != null) params.put("button", button)
        if (clickCount != null) params.put("clickCount", clickCount)
        if (deltaX != null) params.put("deltaX", deltaX)
        if (deltaY != null) params.put("deltaY", deltaY)
        if (modifiers != null && modifiers != 0) params.put("modifiers", modifiers)
        if (buttons != null) params.put("buttons", buttons)
        if (deltaMode != null) params.put("deltaMode", deltaMode)
        return envelope(id = id, method = "Input.dispatchMouseEvent", params = params)
    }

    /**
     * Dispatches a key press (`Input.dispatchKeyEvent`). Pass [modifiers]
     * (e.g. [MOD_CTRL]) for chords such as Ctrl+`+`. Pass [text] for
     * printable keys so the character is committed.
     */
    fun keyDown(
        id: Int,
        windowsCode: Int,
        key: String,
        code: String,
        text: String? = null,
        modifiers: Int? = null,
    ): String {
        val params = JSONObject()
            .put("type", "keyDown")
            .put("windowsVirtualKeyCode", windowsCode)
            .put("key", key)
            .put("code", code)
        if (text != null) params.put("text", text)
        if (modifiers != null && modifiers != 0) params.put("modifiers", modifiers)
        return envelope(id = id, method = "Input.dispatchKeyEvent", params = params)
    }

    /** Dispatches a key release (`Input.dispatchKeyEvent`). */
    fun keyUp(
        id: Int,
        windowsCode: Int,
        key: String,
        code: String,
        modifiers: Int? = null,
    ): String {
        val params = JSONObject()
            .put("type", "keyUp")
            .put("windowsVirtualKeyCode", windowsCode)
            .put("key", key)
            .put("code", code)
        if (modifiers != null && modifiers != 0) params.put("modifiers", modifiers)
        return envelope(
            id = id,
            method = "Input.dispatchKeyEvent",
            params = params,
        )
    }

    /** Inserts text as if typed (`Input.insertText`). */
    fun insertText(id: Int, text: String): String =
        envelope(
            id = id,
            method = "Input.insertText",
            params = JSONObject().put("text", text),
        )

    /** Opens a new page target (`Target.createTarget`). Sent on the browser session. */
    fun createTarget(id: Int, url: String, w: Int, h: Int): String =
        envelope(
            id = id,
            method = "Target.createTarget",
            params = JSONObject()
                .put("url", url)
                .put("width", w)
                .put("height", h),
            )

    /** Closes a page target (`Target.closeTarget`). Sent on the browser session. */
    fun closeTarget(id: Int, targetId: String): String =
        envelope(
            id = id,
            method = "Target.closeTarget",
            params = JSONObject().put("targetId", targetId),
        )

    /** Brings a page target to front (`Target.activateTarget`). */
    fun activateTarget(id: Int, targetId: String): String =
        envelope(
            id = id,
            method = "Target.activateTarget",
            params = JSONObject().put("targetId", targetId),
        )

    /**
     * Returns the browser window id and bounds of the window hosting
     * [targetId] (`Browser.getWindowForTarget`). Send on the browser session.
     */
    fun getWindowForTarget(id: Int, targetId: String): String =
        envelope(
            id = id,
            method = "Browser.getWindowForTarget",
            params = JSONObject().put("targetId", targetId),
        )

    /** Resizes/positions a browser window (`Browser.setWindowBounds`). */
    fun setWindowBounds(
        id: Int,
        windowId: Int,
        width: Int,
        height: Int,
        windowState: String = "normal",
    ): String =
        envelope(
            id = id,
            method = "Browser.setWindowBounds",
            params = JSONObject()
                .put("windowId", windowId)
                .put(
                    "bounds",
                    JSONObject()
                        .put("windowState", windowState)
                        .put("width", width)
                        .put("height", height),
                ),
        )

    /** Reads the in-tab navigation history (`Page.getNavigationHistory`). */
    fun getNavigationHistory(id: Int): String =
        envelope(
            id = id,
            method = "Page.getNavigationHistory",
            params = JSONObject(),
        )

    /** Evaluates a JS expression in the target (`Runtime.evaluate`). */
    fun evaluate(id: Int, expr: String): String =
        envelope(
            id = id,
            method = "Runtime.evaluate",
            params = JSONObject().put("expression", expr),
        )

    /**
     * Evaluates a JS expression and returns the value directly
     * (`returnByValue: true`, `awaitPromise: true`), so page-state probes do
     * not have to unpack remote objects.
     */
    fun evaluateValue(id: Int, expr: String, awaitPromise: Boolean = true): String =
        envelope(
            id = id,
            method = "Runtime.evaluate",
            params = JSONObject()
                .put("expression", expr)
                .put("returnByValue", true)
                .put("awaitPromise", awaitPromise),
        )

    /**
     * Parses a CDP response (a message carrying an `id`). Never throws:
     * malformed input yields `CdpResponse(-1, null, null)`.
     */
    fun parseResponse(json: String): CdpResponse {
        return try {
            val obj = JSONObject(json)
            CdpResponse(
                id = obj.optInt("id", -1),
                result = obj.optJSONObject("result"),
                error = obj.optJSONObject("error"),
            )
        } catch (_: Exception) {
            CdpResponse(id = -1, result = null, error = null)
        }
    }

    /**
     * Parses a CDP event (a message carrying a `method`). Never throws.
     * Returns null for responses (messages with an `id` and no `method`)
     * and for malformed input.
     */
    fun parseEvent(json: String): CdpEvent? {
        return try {
            val obj = JSONObject(json)
            if (obj.isNull("method")) return null
            val method = obj.optString("method", "")
            if (method.isEmpty()) return null
            when (method) {
                "Page.screencastFrame" -> {
                    val params = obj.optJSONObject("params") ?: return null
                    val sessionId = params.optInt("sessionId", -1)
                    val data = params.optString("data", "")
                    if (sessionId < 0 || data.isEmpty()) return null
                    val meta = params.optJSONObject("metadata")
                    CdpEvent.ScreencastFrame(
                        sessionId = sessionId,
                        dataB64 = data,
                        deviceWidth = meta?.optInt("deviceWidth", 0) ?: 0,
                        deviceHeight = meta?.optInt("deviceHeight", 0) ?: 0,
                        pageScaleFactor = meta?.optDouble("pageScaleFactor", 1.0) ?: 1.0,
                        scrollOffsetX = meta?.optDouble("scrollOffsetX", 0.0) ?: 0.0,
                        scrollOffsetY = meta?.optDouble("scrollOffsetY", 0.0) ?: 0.0,
                    )
                }
                "Page.frameNavigated" -> {
                    val params = obj.optJSONObject("params") ?: return null
                    val frame = params.optJSONObject("frame") ?: return null
                    val url = frame.optString("url", "")
                    val name = frame.optString("name", "")
                    val isMain = frame.isNull("parentId") || !frame.has("parentId")
                    CdpEvent.FrameNavigated(url = url, title = name, isMainFrame = isMain)
                }
                "Page.frameStartedLoading" -> CdpEvent.LoadStateChanged(loading = true)
                "Page.frameStoppedLoading", "Page.loadEventFired" ->
                    CdpEvent.LoadStateChanged(loading = false)
                else -> CdpEvent.Ignored(method = method)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses the result object of `Page.getNavigationHistory`. Never throws;
     * malformed input yields an empty history (index 0, no entries).
     */
    fun parseHistory(result: JSONObject?): HistorySnapshot {
        if (result == null) return HistorySnapshot()
        val entries = result.optJSONArray("entries")
        val out = ArrayList<HistoryEntry>()
        if (entries != null) {
            for (i in 0 until entries.length()) {
                val row = entries.optJSONObject(i) ?: continue
                out.add(
                    HistoryEntry(
                        id = row.optInt("id", -1),
                        url = row.optString("url", ""),
                        title = row.optString("title", ""),
                    ),
                )
            }
        }
        val idx = result.optInt("currentIndex", -1)
        return HistorySnapshot(
            currentIndex = idx,
            entries = out,
        )
    }

    /**
     * Parses the result object of a `Runtime.evaluate(returnByValue=true)`
     * call whose expression returned [PageInfo] JSON. Never throws.
     */
    fun parsePageInfo(result: JSONObject?): PageInfo? {
        if (result == null) return null
        val value = result.optJSONObject("result")?.opt("value") ?: return null
        val json = value as? String ?: return null
        return try {
            val obj = JSONObject(json)
            PageInfo(
                url = obj.optString("url", ""),
                title = obj.optString("title", ""),
                zoomPct = (obj.optDouble("zoom", 1.0) * 100.0).toInt().coerceIn(25, 500),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun envelope(id: Int, method: String, params: JSONObject): String =
        JSONObject()
            .put("id", id)
            .put("method", method)
            .put("params", params)
            .toString()
}

/** A parsed CDP response: the reply to one request id. */
data class CdpResponse(
    val id: Int,
    val result: JSONObject?,
    val error: JSONObject?,
)

/** A parsed CDP event: an unsolicited `method` message from the browser. */
sealed interface CdpEvent {
    /**
     * One `Page.screencastFrame`: JPEG bytes as base64 in [dataB64], plus
     * the frame's own metadata (the coded frame may be downscaled and
     * zoomed relative to the CSS viewport).
     */
    data class ScreencastFrame(
        val sessionId: Int,
        val dataB64: String,
        val deviceWidth: Int = 0,
        val deviceHeight: Int = 0,
        val pageScaleFactor: Double = 1.0,
        val scrollOffsetX: Double = 0.0,
        val scrollOffsetY: Double = 0.0,
    ) : CdpEvent

    /** A frame finished navigating (main-frame navigations drive the URL bar). */
    data class FrameNavigated(
        val url: String,
        val title: String = "",
        val isMainFrame: Boolean = true,
    ) : CdpEvent

    /** Page load started ([loading] = true) or settled (false). */
    data class LoadStateChanged(val loading: Boolean) : CdpEvent

    /** Any event the stream does not consume, kept for diagnostics. */
    data class Ignored(val method: String) : CdpEvent
}
