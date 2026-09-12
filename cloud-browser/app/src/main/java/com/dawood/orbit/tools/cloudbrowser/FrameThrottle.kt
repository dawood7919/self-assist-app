package com.dawood.orbit.tools.cloudbrowser

/**
 * Keep-latest frame gate for the Cloud Browser remote stream.
 *
 * Pure Kotlin with no imports beyond the Kotlin standard library: zero
 * Android / OkHttp / coroutine imports, so this file runs on plain JVM unit
 * tests. Callers drop the frame when [shouldAccept] returns false and keep
 * only the newest accepted frame; there is intentionally no queue, so a slow
 * consumer can never fall behind or grow memory.
 *
 * Thread-safe: [shouldAccept] is synchronized and the last-accept timestamp
 * is volatile, so it may be called from any thread. No Main-thread
 * assumptions are made; the check itself never blocks.
 */
class FrameThrottle(
    val maxFps: Int = 24,
    val minIntervalMs: Long = 0,
    val maxBytes: Int = 4_000_000,
) {

    @Volatile
    private var lastAcceptMs: Long? = null

    /**
     * Returns true when a frame of [bytes] bytes arriving at [nowMs]
     * (millis, same clock for a given instance) may be decoded and shown.
     * Rejects oversize frames (over the bandwidth budget [maxBytes]) and
     * frames arriving sooner than both the [maxFps] rate and [minIntervalMs]
     * after the last ACCEPTED frame. Rejected frames leave no state behind,
     * so the newest frame always wins. The first frame is accepted unless it
     * is oversize. A backwards clock ([nowMs] before the last accept) is
     * treated as a fresh accept.
     */
    @Synchronized
    fun shouldAccept(nowMs: Long, bytes: Int): Boolean {
        if (bytes < 0 || bytes > maxBytes) return false
        val gapMs = effectiveGapMs()
        val last = lastAcceptMs
        if (last != null && nowMs >= last && nowMs - last < gapMs) return false
        lastAcceptMs = nowMs
        return true
    }

    /**
     * Scales [srcW] by [srcH] down (or up) to target width [dstW],
     * preserving the aspect ratio. Returns `Pair(dstW, scaledHeight)` with a
     * height of at least 1. Invalid inputs fall back to height 1 rather than
     * dividing by zero.
     */
    fun scaledSize(srcW: Int, srcH: Int, dstW: Int): Pair<Int, Int> {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0) return Pair(dstW.coerceAtLeast(1), 1)
        val scaledH = ((srcH.toLong() * dstW.toLong()) / srcW.toLong()).toInt().coerceAtLeast(1)
        return Pair(dstW, scaledH)
    }

    private fun effectiveGapMs(): Long {
        val fpsGapMs = if (maxFps > 0) 1000L / maxFps.toLong() else 0L
        return maxOf(minIntervalMs, fpsGapMs)
    }
}
