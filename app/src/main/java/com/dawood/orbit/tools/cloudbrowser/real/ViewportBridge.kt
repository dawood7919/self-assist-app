package com.dawood.orbit.tools.cloudbrowser.real

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Latest-only holder for live remote-browser screencast frames.
 *
 * The REAL backend posts decoded JPEG bytes per session id via [post]; the
 * [frames] StateFlow is conflated by construction (a StateFlow only ever
 * holds the latest value), so slow collectors never queue stale frames and
 * the UI always renders the newest one. At most [maxSessions] session ids
 * are kept; posting for a new id while full drops the eldest entry first.
 *
 * Decoding stays in one place: [decodeLatest] turns the newest bytes for a
 * session into an [ImageBitmap], returning null for missing or corrupt data
 * instead of throwing. Bitmap work happens here — never in the API layer —
 * so callers can invoke it from any thread that is allowed to decode.
 *
 * This class owns no threads and performs no I/O: the tool calls [post]
 * from the API frame callback and reads [frames]/[decodeLatest] from the UI.
 */
class ViewportBridge(private val maxSessions: Int = 5) {

    private val capacity: Int = maxSessions.coerceAtLeast(1)

    private val _frames = MutableStateFlow<Map<String, ByteArray>>(emptyMap())

    /** Latest JPEG bytes per live session id; conflated, newest write wins. */
    val frames: StateFlow<Map<String, ByteArray>> = _frames.asStateFlow()

    /**
     * Stores [bytes] as the latest frame for session [id]. Empty payloads
     * are ignored. Never throws.
     */
    fun post(id: String, bytes: ByteArray) {
        if (id.isBlank() || bytes.isEmpty()) return
        _frames.update { current ->
            if (current.containsKey(id)) {
                current + (id to bytes)
            } else if (current.size < capacity) {
                current + (id to bytes)
            } else {
                val drop = current.size - capacity + 1
                current.entries.drop(drop).associate { it.key to it.value } + (id to bytes)
            }
        }
    }

    /** Returns the latest raw JPEG bytes for session [id], or null. */
    fun latest(id: String): ByteArray? = _frames.value[id]

    /** True when at least one frame for session [id] is held. */
    fun hasFrame(id: String): Boolean = _frames.value.containsKey(id)

    /**
     * Decodes the latest frame for session [id] into an [ImageBitmap].
     * Returns null for unknown ids, empty payloads, undecodable bytes, or
     * when the platform refuses the allocation. Never throws.
     */
    fun decodeLatest(id: String): ImageBitmap? {
        val bytes = _frames.value[id] ?: return null
        if (bytes.isEmpty()) return null
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /** Drops the frame held for session [id], if any. Never throws. */
    fun clear(id: String) {
        _frames.update { current -> current - id }
    }

    /** Drops every held frame. Never throws. */
    fun clearAll() {
        _frames.update { emptyMap() }
    }
}
