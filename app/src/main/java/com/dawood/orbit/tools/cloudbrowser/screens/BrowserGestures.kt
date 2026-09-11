package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import com.dawood.orbit.tools.cloudbrowser.BrowserInteraction
import com.dawood.orbit.tools.cloudbrowser.CdpInput
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Callbacks from the phone viewport onto the remote browser. All point
 * coordinates are 0..1 fractions OF THE REMOTE VIEWPORT (the gesture box is
 * already letterbox-sized to the coded frame, so a fraction maps 1:1).
 */
class BrowserGestures(
    val onTap: (Offset) -> Unit,
    val onDoubleTap: (Offset) -> Unit,
    val onLongTap: (Offset) -> Unit,
    val onPressStart: (Offset) -> Unit,
    val onDragging: (Offset) -> Unit,
    val onPressEnd: (Offset) -> Unit,
    val onTrackpadMove: (dfx: Float, dfy: Float) -> Unit,
    val onTrackpadTap: () -> Unit,
    val onTrackpadDoubleTap: () -> Unit,
    val onTwoFingerTap: (Offset) -> Unit,
    val onScroll: (fx: Float, fy: Float, dxPhonePx: Float, dyPhonePx: Float) -> Unit,
    val onZoom: (fx: Float, fy: Float, steps: Int) -> Unit,
)

private const val LONG_PRESS_MS = 450L
private const val DOUBLE_TAP_MS = 280L
private const val DOUBLE_TAP_SLOP_PX = 48f
private const val TWO_FINGER_TAP_MS = 260L
private const val ZOOM_RATIO_PER_STEP = 1.12f

/**
 * Touch surface that makes the remote DESKTOP browser behave like a normal
 * phone browser while still exposing a full mouse:
 *
 * Direct mode ([BrowserInteraction.Direct]):
 *  - tap = left click at that point
 *  - quick second tap = double click
 *  - press and hold = right click
 *  - press, hold and drag = left-button drag (text selection, sliders, maps)
 *  - two-finger drag = wheel scroll
 *  - pinch open/closed = browser zoom (Ctrl+wheel on the remote)
 *  - two-finger tap = right click
 *
 * Trackpad mode ([BrowserInteraction.Trackpad]):
 *  - one-finger drag = move a floating cursor (like a laptop touchpad)
 *  - one-finger tap = click under the cursor (double tap = double click)
 *  - two-finger drag / pinch behave the same as Direct
 *
 * Events are observed on the Initial pass so the outer scroll chrome can
 * never steal the viewport's gestures.
 */
fun Modifier.browserGestures(
    mode: BrowserInteraction,
    gestures: BrowserGestures,
): Modifier = this.pointerInput(gestures) {
    var lastTapTime = 0L
    var lastTapPoint: Offset? = null
    val trackpadTapTime = longArrayOf(0L)
    val slop = viewConfiguration.touchSlop

    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val size = size
        if (size.width == 0 || size.height == 0) return@awaitEachGesture

        var modeOne = true
        var longPressFired = false
        var dragging = false
        var movedFar = false
        val start = first.position
        var last = start
        val downTime = System.currentTimeMillis()

        // Two-finger state.
        var twoStart = 0L
        var twoMoved = false
        var spanAtStep = 0f
        var lastFocal: Offset? = null

        fun fractionAt(p: Offset): Offset =
            Offset(
                (p.x / size.width.toFloat()).coerceIn(0f, 1f),
                (p.y / size.height.toFloat()).coerceIn(0f, 1f),
            )

        fun endOneFingerDrag(at: Offset) {
            if (dragging) gestures.onPressEnd(fractionAt(at))
            dragging = false
        }

        while (true) {
            val remaining = LONG_PRESS_MS - (System.currentTimeMillis() - downTime)
            val event = withTimeoutOrNull(
                if (modeOne && !longPressFired && !dragging && remaining > 0) remaining else 10_000L,
            ) {
                awaitPointerEvent(PointerEventPass.Initial)
            } ?: run {
                // Idle long-press deadline elapsed with one finger held.
                if (modeOne && !longPressFired && !movedFar) {
                    longPressFired = true
                    gestures.onLongTap(fractionAt(last))
                }
                null
            }

            if (event != null) {
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2 && modeOne) {
                    modeOne = false
                    endOneFingerDrag(last)
                    twoStart = System.currentTimeMillis()
                    twoMoved = false
                    spanAtStep = currentSpan(event)
                    lastFocal = currentFocal(event)
                }

                if (modeOne) {
                    event.changes.fastForEach { change ->
                        if (!change.pressed) {
                            // Finger up -> end of the one-finger gesture.
                            val up = change.position
                            endOneFingerDrag(up)
                            // A tap is a release without leaving touch slop;
                            // speed matters only for the double-tap window.
                            val withinSlop = (up - start).getDistance() < slop * 1.6f
                            if (!longPressFired && !movedFar && withinSlop) {
                                val f = fractionAt(up)
                                val now = System.currentTimeMillis()
                                val prev = lastTapPoint
                                if (mode == BrowserInteraction.Trackpad) {
                                    if (now - trackpadTapTime[0] < DOUBLE_TAP_MS) {
                                        gestures.onTrackpadDoubleTap()
                                        trackpadTapTime[0] = 0L
                                    } else {
                                        gestures.onTrackpadTap()
                                        trackpadTapTime[0] = now
                                    }
                                } else if (prev != null && now - lastTapTime < DOUBLE_TAP_MS &&
                                    (prev - up).getDistance() < DOUBLE_TAP_SLOP_PX
                                ) {
                                    gestures.onDoubleTap(f)
                                    lastTapPoint = null
                                    lastTapTime = 0L
                                } else {
                                    gestures.onTap(f)
                                    lastTapPoint = up
                                    lastTapTime = now
                                }
                            }
                            return@awaitEachGesture
                        }
                        if (change.positionChanged()) {
                            val p = change.position
                            val dist = (p - start).getDistance()
                            if (mode == BrowserInteraction.Trackpad) {
                                val dfx = CdpInput.trackpadFractionDelta(p.x - last.x, size.width.toFloat())
                                val dfy = CdpInput.trackpadFractionDelta(p.y - last.y, size.height.toFloat())
                                if (dfx != 0f || dfy != 0f) gestures.onTrackpadMove(dfx, dfy)
                                last = p
                                // Small jitter under touch slop still counts
                                // as a tap; only a real drag cancels it.
                                if (dist > slop) movedFar = true
                            } else if (!longPressFired) {
                                if (dist > slop && !dragging) {
                                    dragging = true
                                    movedFar = true
                                    gestures.onPressStart(fractionAt(start))
                                }
                                if (dragging) gestures.onDragging(fractionAt(p))
                                last = p
                            }
                        }
                    }
                } else {
                    // ── Two-finger: pinch zoom + wheel scroll + tap ──
                    val now = System.currentTimeMillis()
                    event.changes.fastForEach { change ->
                        if (change.positionChanged()) twoMoved = true
                    }
                    val downCount = event.changes.count { it.pressed }
                    if (downCount < 2) {
                        // Only BOTH fingers lifting counts as a two-finger tap;
                        // a single finger lifting just ends the gesture.
                        val quick = now - twoStart < TWO_FINGER_TAP_MS
                        if (downCount == 0 && quick && !twoMoved) {
                            val focal = lastFocal ?: currentFocal(event)
                            gestures.onTwoFingerTap(fractionAt(focal))
                        }
                        endOneFingerDrag(last)
                        return@awaitEachGesture
                    }
                    val focal = currentFocal(event)
                    val previousFocal = lastFocal
                    if (previousFocal != null) {
                        val panX = focal.x - previousFocal.x
                        val panY = focal.y - previousFocal.y
                        if (abs(panX) > 0.5f || abs(panY) > 0.5f) {
                            val ff = fractionAt(focal)
                            gestures.onScroll(ff.x, ff.y, panX, panY)
                        }
                    }
                    lastFocal = focal
                    val span = currentSpan(event)
                    if (spanAtStep > 1f && span > 1f) {
                        while (span >= spanAtStep * ZOOM_RATIO_PER_STEP) {
                            val ff = fractionAt(focal)
                            gestures.onZoom(ff.x, ff.y, +1)
                            spanAtStep *= ZOOM_RATIO_PER_STEP
                        }
                        while (span <= spanAtStep / ZOOM_RATIO_PER_STEP) {
                            val ff = fractionAt(focal)
                            gestures.onZoom(ff.x, ff.y, -1)
                            spanAtStep /= ZOOM_RATIO_PER_STEP
                        }
                    }
                }
            }

            if (event == null) continue
            // No pointers remain -> gesture over.
            val anyPressed = event.changes.fastAny { it.pressed }
            if (!anyPressed) return@awaitEachGesture
        }
    }
}

private fun currentSpan(
    event: androidx.compose.ui.input.pointer.PointerEvent,
): Float {
    val down = event.changes.filter { it.pressed }.map { it.position }
    if (down.size < 2) return 0f
    return hypot(down[0].x - down[1].x, down[0].y - down[1].y)
}

private fun currentFocal(event: androidx.compose.ui.input.pointer.PointerEvent): Offset {
    val down = event.changes.filter { it.pressed }.map { it.position }
    if (down.isEmpty()) return Offset.Zero
    return Offset(
        down.map { it.x }.average().toFloat(),
        down.map { it.y }.average().toFloat(),
    )
}


