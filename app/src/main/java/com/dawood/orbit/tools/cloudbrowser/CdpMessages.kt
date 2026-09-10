package com.dawood.orbit.tools.cloudbrowser

import org.json.JSONObject

/**
 * Builders and parsers for the Chrome DevTools Protocol (CDP) messages used
 * by the Cloud Browser remote stream.
 *
 * Pure Kotlin with org.json only: zero Android / OkHttp / coroutine imports,
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

    /** Captures one JPEG still from the target (`Page.captureScreenshot`). */
    fun captureScreenshot(id: Int, quality: Int = 60): String =
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
     * `mouseWheel`. Optional fields are omitted from the JSON when null:
     * pass [button] (`left`/`right`/`middle`) plus [clickCount] for clicks,
     * and [deltaX]/[deltaY] for wheel scrolls.
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
    ): String {
        val params = JSONObject()
            .put("type", type)
            .put("x", x)
            .put("y", y)
        if (button != null) params.put("button", button)
        if (clickCount != null) params.put("clickCount", clickCount)
        if (deltaX != null) params.put("deltaX", deltaX)
        if (deltaY != null) params.put("deltaY", deltaY)
        return envelope(id = id, method = "Input.dispatchMouseEvent", params = params)
    }

    /**
     * Dispatches a key press (`Input.dispatchKeyEvent` with type `keyDown`).
     * Pass [text] for printable keys so the character is committed.
     */
    fun keyDown(id: Int, windowsCode: Int, key: String, code: String, text: String? = null): String {
        val params = JSONObject()
            .put("type", "keyDown")
            .put("windowsVirtualKeyCode", windowsCode)
            .put("key", key)
            .put("code", code)
        if (text != null) params.put("text", text)
        return envelope(id = id, method = "Input.dispatchKeyEvent", params = params)
    }

    /** Dispatches a key release (`Input.dispatchKeyEvent` with type `keyUp`). */
    fun keyUp(id: Int, windowsCode: Int, key: String, code: String): String =
        envelope(
            id = id,
            method = "Input.dispatchKeyEvent",
            params = JSONObject()
                .put("type", "keyUp")
                .put("windowsVirtualKeyCode", windowsCode)
                .put("key", key)
                .put("code", code),
        )

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

    /** Evaluates a JS expression in the target (`Runtime.evaluate`). */
    fun evaluate(id: Int, expr: String): String =
        envelope(
            id = id,
            method = "Runtime.evaluate",
            params = JSONObject().put("expression", expr),
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
            if (method == "Page.screencastFrame") {
                val params = obj.optJSONObject("params") ?: return null
                val sessionId = params.optInt("sessionId", -1)
                val data = params.optString("data", "")
                if (sessionId < 0 || data.isEmpty()) return null
                CdpEvent.ScreencastFrame(sessionId = sessionId, dataB64 = data)
            } else {
                CdpEvent.Ignored(method = method)
            }
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
    /** One `Page.screencastFrame`: JPEG bytes as base64 in [dataB64]. */
    data class ScreencastFrame(val sessionId: Int, val dataB64: String) : CdpEvent

    /** Any event the stream does not consume, kept for diagnostics. */
    data class Ignored(val method: String) : CdpEvent
}
