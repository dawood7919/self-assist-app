package com.dawood.orbit.tools.cloudbrowser.real

import com.dawood.orbit.tools.cloudbrowser.CdpEvent
import com.dawood.orbit.tools.cloudbrowser.CdpMessages
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * Blocking OkHttp WebSocket client for one CDP session (a target or the
 * browser endpoint).
 *
 * Blocking contract: [connectBlocking] and [sendAndAwait] block on network
 * I/O and MUST be called from a background thread (callers already run on
 * Dispatchers.IO). No method posts to Main, touches the UI, or launches
 * coroutines. `sendAndAwait` is thread-safe from any thread: responses are
 * routed by id through a [ConcurrentHashMap] of latch + result slots, so
 * concurrent callers with distinct ids never mix replies up.
 *
 * Incoming text messages carrying an `id` complete the matching pending
 * [sendAndAwait]; messages carrying a `method` are parsed with
 * [CdpMessages.parseEvent] and delivered to the [setEventListener] listener
 * on OkHttp's reader thread (the listener must therefore be fast and never
 * touch the UI directly). Unknown ids (late replies after a timeout) are
 * dropped. Nothing here rewrites the WebSocket URL host: mapping the remote
 * DevTools port to the SSH tunnel's local port is the CALLER's job
 * (see `ChromiumManager`, which returns already-rewritten URLs).
 */
class CdpClient(private val okHttp: OkHttpClient) {

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var eventListener: ((CdpEvent) -> Unit)? = null

    private val pending = ConcurrentHashMap<Int, PendingSlot>()

    /** Sets the event listener; replaces any previous one. Never blocks. */
    fun setEventListener(listener: (CdpEvent) -> Unit) {
        eventListener = listener
    }

    /**
     * Blocking: opens the WebSocket to [wsUrl] and returns once the
     * handshake completes. Must be called on a background thread. Fails when
     * the URL is invalid, the connect fails, or [timeoutMs] elapses.
     * Replaces any previous connection.
     */
    fun connectBlocking(wsUrl: String, timeoutMs: Long = 10_000): Result<Unit> {
        val openLatch = CountDownLatch(1)
        val openError = AtomicReference<Throwable>(null)
        val request = try {
            Request.Builder().url(wsUrl).build()
        } catch (e: Exception) {
            return Result.failure(Exception("Invalid CDP WebSocket URL: ${e.message}"))
        }
        try {
            socket?.close(1000, "reconnect")
        } catch (_: Exception) {
        }
        socket = null
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                openLatch.countDown()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                try {
                    webSocket.close(1000, null)
                } catch (_: Exception) {
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket === webSocket) socket = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                openError.compareAndSet(null, t)
                openLatch.countDown()
                if (socket === webSocket) socket = null
                failAll(t)
            }
        }
        val ws = try {
            okHttp.newWebSocket(request, listener)
        } catch (e: Exception) {
            return Result.failure(Exception("CDP connect failed: ${e.message}"))
        }
        socket = ws
        val opened = try {
            openLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            if (socket === ws) socket = null
            return Result.failure(Exception("CDP connect interrupted."))
        }
        if (!opened) {
            try {
                ws.cancel()
            } catch (_: Exception) {
            }
            if (socket === ws) socket = null
            return Result.failure(Exception("CDP connect timed out after ${timeoutMs}ms: $wsUrl"))
        }
        openError.get()?.let { t ->
            if (socket === ws) socket = null
            return Result.failure(Exception("CDP connect failed: ${t.message}"))
        }
        return Result.success(Unit)
    }

    /**
     * Blocking: sends [json] (built by [CdpMessages] with the given [id])
     * and awaits its `id` response. Must be called on a background thread.
     * Thread-safe from any thread. Fails when not connected, when the send
     * is rejected, on timeout ([timeoutMs]), when the connection drops while
     * waiting, or when the browser answers with a CDP `error` object (the
     * error JSON is quoted in the failure message). Success holds the
     * `result` object (empty when the browser sent none).
     */
    fun sendAndAwait(json: String, id: Int, timeoutMs: Long = 10_000): Result<JSONObject> {
        val ws = socket
            ?: return Result.failure(Exception("Not connected — call connectBlocking() first."))
        val slot = PendingSlot()
        pending[id] = slot
        try {
            val sent = try {
                ws.send(json)
            } catch (e: Exception) {
                return Result.failure(Exception("CDP send failed (id=$id): ${e.message}"))
            }
            if (!sent) {
                return Result.failure(Exception("CDP send rejected (id=$id): socket is closing."))
            }
            val done = try {
                slot.latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return Result.failure(Exception("Interrupted awaiting CDP response (id=$id)."))
            }
            if (!done) {
                return Result.failure(Exception("Timed out after ${timeoutMs}ms awaiting CDP response (id=$id)."))
            }
            slot.failure?.let { t ->
                return Result.failure(Exception("Connection lost awaiting CDP response (id=$id): ${t.message}"))
            }
            slot.error?.let { error ->
                return Result.failure(Exception("CDP error (id=$id): $error"))
            }
            return Result.success(slot.result ?: JSONObject())
        } finally {
            pending.remove(id, slot)
        }
    }

    /** Closes the socket and fails all pending waits. Never throws. */
    fun close() {
        val ws = socket
        socket = null
        if (ws != null) {
            try {
                ws.close(1000, "bye")
            } catch (_: Exception) {
            }
        }
        failAll(Exception("CdpClient is closed."))
    }

    private fun handleMessage(text: String) {
        val obj = try {
            JSONObject(text)
        } catch (_: Exception) {
            return
        }
        if (!obj.isNull("id")) {
            val id = obj.optInt("id", Int.MIN_VALUE)
            if (id == Int.MIN_VALUE) return
            pending[id]?.let { slot ->
                slot.result = obj.optJSONObject("result")
                slot.error = obj.optJSONObject("error")
                slot.latch.countDown()
            }
            return
        }
        val event = try {
            CdpMessages.parseEvent(text)
        } catch (_: Exception) {
            null
        } ?: return
        try {
            eventListener?.invoke(event)
        } catch (_: Exception) {
        }
    }

    private fun failAll(t: Throwable) {
        for (slot in pending.values) {
            slot.failure = t
            slot.latch.countDown()
        }
    }

    private class PendingSlot {
        val latch = CountDownLatch(1)

        @Volatile
        var result: JSONObject? = null

        @Volatile
        var error: JSONObject? = null

        @Volatile
        var failure: Throwable? = null
    }
}
