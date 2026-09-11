package com.dawood.orbit.tools.cloudbrowser

/**
 * Real input surface onto a remote browser session. Every method is fire and
 * forget from the UI thread: implementations marshal blocking CDP work onto
 * [kotlinx.coroutines.Dispatchers.IO] and surface failures through the
 * browser status channel rather than crashing the viewport.
 *
 * Coordinates are 0..1 fractions of the remote viewport so the UI never has
 * to know the remote resolution.
 */
interface BrowserInput {

    /** Full press/release click (and double/triple via [clickCount]). */
    fun click(fx: Float, fy: Float, button: RemoteMouseButton, clickCount: Int = 1)

    /** Press a mouse button; pair with [move] and [release] for a drag. */
    fun press(fx: Float, fy: Float, button: RemoteMouseButton)

    fun move(fx: Float, fy: Float)

    fun release(fx: Float, fy: Float, button: RemoteMouseButton)

    /** Move the trackpad cursor by fractions of the remote viewport. */
    fun relativeMove(dfx: Float, dfy: Float)

    /** Wheel scroll in REMOTE CSS pixels; ctrlKey turns it into zoom. */
    fun wheel(fx: Float, fy: Float, deltaXPx: Double, deltaYPx: Double, ctrlKey: Boolean = false)

    // ── Real multitouch (mobile emulation); fractions of the frame ─────

    /** One or more fingers land. Points carry stable finger ids. */
    fun touchStart(points: List<TouchPointFraction>)

    /** Active fingers move to new fractions. */
    fun touchMove(points: List<TouchPointFraction>)

    /** Fingers lift (released points included at their final spot). */
    fun touchEnd(points: List<TouchPointFraction>)

    /** Zoom one notch in/out around [fx],[fy]; the label updates itself. */
    fun zoom(steps: Int, fx: Float = 0.5f, fy: Float = 0.5f)

    fun resetZoom()

    /** Type literal text into the focused element. */
    fun typeText(text: String)

    /** Press and release a named key (Enter, Backspace, Tab, ArrowLeft…). */
    fun pressKey(label: String)
}
