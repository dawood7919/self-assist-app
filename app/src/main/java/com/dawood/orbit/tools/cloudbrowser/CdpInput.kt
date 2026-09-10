package com.dawood.orbit.tools.cloudbrowser

/**
 * Pure input mapping for the Cloud Browser remote stream.
 *
 * Pure Kotlin with no imports beyond the Kotlin standard library and the
 * sibling [Resolution]/[Quality] models (same package): zero Android /
 * OkHttp / coroutine imports, so this file runs on plain JVM unit tests.
 * Nothing here touches the network or any thread.
 */
object CdpInput {

    /**
     * Friendly key name to Windows virtual-key code, as expected by
     * `Input.dispatchKeyEvent` (`windowsVirtualKeyCode`). Covers the keys
     * the phone overlay can send: Enter, Backspace, Tab, Escape, the four
     * arrows, Delete, and F1 through F12.
     */
    val KEY_TABLE: Map<String, Int> = mapOf(
        "Enter" to 13,
        "Backspace" to 8,
        "Tab" to 9,
        "Escape" to 27,
        "ArrowLeft" to 37,
        "ArrowUp" to 38,
        "ArrowRight" to 39,
        "ArrowDown" to 40,
        "Delete" to 46,
        "F1" to 112,
        "F2" to 113,
        "F3" to 114,
        "F4" to 115,
        "F5" to 116,
        "F6" to 117,
        "F7" to 118,
        "F8" to 119,
        "F9" to 120,
        "F10" to 121,
        "F11" to 122,
        "F12" to 123,
    )

    /**
     * Maps a touch point expressed as fractions of the phone view
     * ([fx], [fy] in 0..1) onto remote pixels for a [w] by [h] target.
     */
    fun pointFromFractions(fx: Float, fy: Float, w: Int, h: Int): Pair<Double, Double> =
        Pair(fx.toDouble() * w.toDouble(), fy.toDouble() * h.toDouble())

    /** Remote desktop size in pixels for a [Resolution] preset. */
    fun remoteSize(resolution: Resolution): Pair<Int, Int> =
        when (resolution) {
            Resolution.P720 -> Pair(1280, 720)
            Resolution.P1080 -> Pair(1920, 1080)
            Resolution.P1440 -> Pair(2560, 1440)
            Resolution.Auto -> Pair(1280, 720)
        }

    /**
     * Screencast tuning for a [Quality] preset as
     * `Triple(maxWidth, quality, everyNthFrame)`: the max frame width in
     * pixels, the JPEG quality, and the frame downsampling factor.
     */
    fun screencastParams(quality: Quality): Triple<Int, Int, Int> =
        when (quality) {
            Quality.Low -> Triple(800, 30, 4)
            Quality.Balanced -> Triple(1280, 60, 2)
            Quality.High -> Triple(1920, 75, 1)
            Quality.Ultra -> Triple(1920, 80, 1)
        }
}
