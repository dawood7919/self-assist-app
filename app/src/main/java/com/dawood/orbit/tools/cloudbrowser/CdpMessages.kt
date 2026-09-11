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
     * Activates this target's page. Required in headful Chrome (e.g. under
     * Xvfb): without it a freshly created background tab answers
     * `Page.startScreencast` with "Not attached to an active page".
     */
    fun bringToFront(id: Int): String =
        envelope(
            id = id,
            method = "Page.bringToFront",
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
        eventType: String = "keyDown",
    ): String {
        val params = JSONObject()
            .put("type", eventType)
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

    /**
     * Sets the pinch-style page scale (`Emulation.setPageScaleFactor`).
     * Verified against real headless Chrome: this is the zoom path that
     * actually moves `visualViewport.scale` there (browser accelerators do
     * not). Desktop pages clamp it to a 1.0 minimum, so sub-100% zoom uses
     * CSS zoom instead.
     */
    fun setPageScaleFactor(id: Int, factor: Double): String =
        envelope(
            id = id,
            method = "Emulation.setPageScaleFactor",
            params = JSONObject().put("pageScaleFactor", factor),
        )

    /**
     * Turns the tab into a phone (or a desktop window) at the CDP level.
     * This is NOT a CSS transform: the website actually re-lays out at
     * [cssWidth] x [cssHeight] CSS pixels at [deviceScaleFactor], with real
     * touch events when [mobile] is true. The screencast then codes exactly
     * the phone-sized viewport, so frames fill the screen edge to edge.
     */
    fun setDeviceMetrics(
        id: Int,
        cssWidth: Int,
        cssHeight: Int,
        deviceScaleFactor: Double,
        mobile: Boolean,
    ): String =
        envelope(
            id = id,
            method = "Emulation.setDeviceMetricsOverride",
            params = JSONObject()
                .put("width", cssWidth)
                .put("height", cssHeight)
                .put("deviceScaleFactor", deviceScaleFactor)
                .put("mobile", mobile),
        )

    /** Reverts to the real (host) viewport after a device override. */
    fun clearDeviceMetrics(id: Int): String =
        envelope(
            id = id,
            method = "Emulation.clearDeviceMetricsOverride",
            params = JSONObject(),
        )

    /** Enables/disables synthetic touch (and touch event synthesis). */
    fun setTouchEmulation(id: Int, enabled: Boolean, maxTouchPoints: Int = 1): String =
        envelope(
            id = id,
            method = "Emulation.setTouchEmulationEnabled",
            params = JSONObject()
                .put("enabled", enabled)
                // Chrome rejects maxTouchPoints=0 ("must be between 1 and 16"),
                // so never send it while disabling.
                .apply { if (enabled) put("maxTouchPoints", maxTouchPoints.coerceIn(1, 16)) },
        )

    /**
     * Mobile-mode input: one or more real fingers. [type] is touchStart,
     * touchMove, touchEnd or touchCancel. Each point is `id` + CSS x/y;
     * lifted fingers are omitted (touchEnd sends the released ids only).
     */
    fun touchEvent(
        id: Int,
        type: String,
        points: List<TouchPoint>,
    ): String {
        val arr = org.json.JSONArray()
        points.forEach { p ->
            arr.put(
                JSONObject()
                    .put("x", p.x)
                    .put("y", p.y)
                    .put("id", p.id)
                    .put("radiusX", 1.0)
                    .put("radiusY", 1.0)
                    .put("force", if (type == "touchEnd") 0.0 else 1.0),
            )
        }
        return envelope(
            id = id,
            method = "Input.dispatchTouchEvent",
            params = JSONObject()
                .put("type", type)
                .put("touchPoints", arr),
        )
    }

    /** One touch finger for [touchEvent], in emulated CSS viewport pixels. */
    data class TouchPoint(val id: Int, val x: Double, val y: Double)

    /**
     * Anchored pinch gesture around ([x],[y]); [scaleFactor] is absolute
     * (1.0 = identity). Probe-verified on real headless Chrome to move
     * visualViewport.scale (used for smooth zoom-in around the focal point).
     */
    fun pinchGesture(
        id: Int,
        x: Double,
        y: Double,
        scaleFactor: Double,
        relativeSpeed: Int = 400,
    ): String =
        envelope(
            id = id,
            method = "Input.synthesizePinchGesture",
            params = JSONObject()
                .put("x", x)
                .put("y", y)
                .put("scaleFactor", scaleFactor)
                .put("relativeSpeed", relativeSpeed)
                .put("gestureSourceType", "touch"),
        )

    /**
     * Overrides the User-Agent string (and the client hints platform) so
     * sites serve mobile/desktop markup. Requires the emulation override to
     * be active; [platform] is the client-hints platformFormFactor hint.
     */
    fun setUserAgent(id: Int, userAgent: String, platform: String? = null, mobile: Boolean): String {
        val brands = org.json.JSONArray()
        listOf("Google Chrome" to "124", "Chromium" to "124", "Not-A.Brand" to "99").forEach {
            brands.put(JSONObject().put("brand", it.first).put("version", it.second))
        }
        return envelope(
            id = id,
            method = "Network.setUserAgentOverride",
            params = JSONObject()
                .put("userAgent", userAgent)
                .put(
                    "userAgentMetadata",
                    JSONObject()
                        .put("platform", platform ?: if (mobile) "Android" else "Linux")
                        .put("platformVersion", if (mobile) "14.0.0" else "6.5.0")
                        .put("architecture", if (mobile) "arm" else "x86")
                        .put("model", if (mobile) "Pixel 8" else "")
                        .put("mobile", mobile)
                        .put("brands", brands)
                        .put("fullVersionList", brands),
                ),
        )
    }

    /** Enables the Network domain (needed before UA overrides on some builds). */
    fun networkEnable(id: Int): String =
        envelope(
            id = id,
            method = "Network.enable",
            params = JSONObject(),
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
                    // CDP nests the viewport size and scroll offset inside
                    // metadata.deviceSize / metadata.scrollOffset; fall back to
                    // flat keys for robustness across Chrome revisions.
                    val deviceSize = meta?.optJSONObject("deviceSize")
                    val scroll = meta?.optJSONObject("scrollOffset")
                    CdpEvent.ScreencastFrame(
                        sessionId = sessionId,
                        dataB64 = data,
                        deviceWidth = deviceSize?.optInt("width")
                            ?: meta?.optInt("deviceWidth", 0) ?: 0,
                        deviceHeight = deviceSize?.optInt("height")
                            ?: meta?.optInt("deviceHeight", 0) ?: 0,
                        pageScaleFactor = meta?.optDouble("pageScaleFactor", 1.0) ?: 1.0,
                        scrollOffsetX = scroll?.optDouble("x")
                            ?: meta?.optDouble("scrollOffsetX", 0.0) ?: 0.0,
                        scrollOffsetY = scroll?.optDouble("y")
                            ?: meta?.optDouble("scrollOffsetY", 0.0) ?: 0.0,
                        offsetTop = meta?.optDouble("offsetTop", 0.0) ?: 0.0,
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
                inputFocused = obj.optBoolean("focused", false),
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
        val offsetTop: Double = 0.0,
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
