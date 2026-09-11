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
 * coordinates are 0..1 fractions OF THE REMOTE VIEWPORT (the coded frame
 * fills the gesture box edge to edge in mobile mode, so a fraction maps
 * 1:1).
 */
class BrowserGestures(
    // Mouse-style semantics (Pointer mode, and desktop mode).
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
    // Native touch semantics (mobile emulation): one finger down/move/up as
    // real Input.dispatchTouchEvent sequences. Tap, double-tap zoom, long
    // press, fling scrolling and inner-container scroll are all performed by
    // the REMOTE browser itself.
    // Lists of pressed/lifted fingers; pointerId carries through to CDP so
    // lifting one finger while another stays down targets the right one.
    val onTouchStart: (List<GestureFinger>) -> Unit = {},
    val onTouchMove: (List<GestureFinger>) -> Unit = {},
    val onTouchEnd: (List<GestureFinger>) -> Unit = {},
)

data class GestureFinger(val pointerId: Int, val pos: Offset)

private const val LONG_PRESS_MS = 450L
private const val DOUBLE_TAP_MS = 280L
private const val DOUBLE_TAP_SLOP_PX = 48f
private const val TWO_FINGER_TAP_MS = 260L
private const val ZOOM_RATIO_PER_STEP = 1.12f

/**
 * Touch surface for the cloud browser.
 *
 * Native touch ([touchNative] = true, the default in Mobile mode):
 *  - one finger = real remote touch: tap, double tap, long press, press-and
 *    drag scrolling and fling are all performed by the remote browser;
 *  - pinch = genuine page-zoom notches anchored at the focal point;
 *  - two-finger pan while zoomed scrolls; two-finger tap = right click.
 *
 * Mouse direct (Desktop mode):
 *  - tap = left click, second tap = double click, hold = right click,
 *    drag = left-button drag, two-finger drag = wheel.
 *
 * Trackpad ([BrowserInteraction.Trackpad], the optional Pointer mode):
 *  - one-finger drag moves a floating cursor; tap clicks under the cursor.
 *
 * Events are observed on the Initial pass so the outer scroll chrome can
 * never steal the viewport's gestures.
 */
fun Modifier.browserGestures(
    mode: BrowserInteraction,
    gestures: BrowserGestures,
    touchNative: Boolean,
): Modifier = this.pointerInput(gestures, mode, touchNative) {
    var lastTapTime = 0L
    var lastTapPoint: Offset? = null
    val trackpadTapTime = longArrayOf(0L)
    val slop = viewConfiguration.touchSlop

    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val size = size
        if (size.width == 0 || size.height == 0) return@awaitEachGesture

        // Native mobile: every Compose pointer is a remote CDP finger. The
        // remote browser itself performs tap/double-tap/long-press/fling and
        // two-finger pinch; we only forward fingers with stable ids.
        if (touchNative && mode == BrowserInteraction.Direct) {
            val active = linkedMapOf<Int, Offset>()
            // Compose pointer ids are Long; CDP touch ids are integers.
            fun pidOf(c: androidx.compose.ui.input.pointer.PointerInputChange) =
                c.id.value.toInt()
            active[first.id.value.toInt()] = first.position
            fun frac(o: Offset) = Offset(
                (o.x / size.width.toFloat()).coerceIn(0f, 1f),
                (o.y / size.height.toFloat()).coerceIn(0f, 1f),
            )
            fun fingers() = active.entries.map {
                GestureFinger(it.key, frac(it.value))
            }
            gestures.onTouchStart(listOf(GestureFinger(first.id.value.toInt(), frac(first.position))))
            while (active.isNotEmpty()) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                var added = false
                val lifted = mutableListOf<GestureFinger>()
                ev.changes.fastForEach { c ->
                    val pid = pidOf(c)
                    when {
                        c.pressed && !active.containsKey(pid) -> {
                            active[pid] = c.position
                            added = true
                        }
                        c.pressed -> active[pid] = c.position
                        active.containsKey(pid) -> {
                            lifted += GestureFinger(pid, frac(c.position))
                            active.remove(pid)
                        }
                    }
                }
                when {
                    added -> gestures.onTouchStart(fingers())
                    active.isNotEmpty() -> gestures.onTouchMove(fingers())
                }
                if (lifted.isNotEmpty()) gestures.onTouchEnd(lifted)
            }
            return@awaitEachGesture
        }

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

        // Native single finger: forward the full touch sequence. The first
        // down is already in hand; report it immediately so long-press and
        // fast taps behave exactly like a physical screen.
        if (touchNative && mode == BrowserInteraction.Direct) {
            gestures.onTouchStart(listOf(GestureFinger(0, fractionAt(start))))
        }

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
                if (modeOne && !longPressFired && !movedFar &&
                    !(touchNative && mode == BrowserInteraction.Direct)
                ) {
                    longPressFired = true
                    gestures.onLongTap(fractionAt(last))
                }
                null
            }

            if (event != null) {
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2 && modeOne) {
                    modeOne = false
                    // In native touch, lift the single finger cleanly before
                    // switching to pinch/zoom (which is CDP page zoom).
                    if (touchNative && mode == BrowserInteraction.Direct) {
                        gestures.onTouchEnd(listOf(GestureFinger(0, fractionAt(last))))
                    }
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
                            if (touchNative && mode == BrowserInteraction.Direct) {
                                gestures.onTouchEnd(listOf(GestureFinger(0, fractionAt(up))))
                                return@awaitEachGesture
                            }
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
                            if (touchNative && mode == BrowserInteraction.Direct) {
                                // Forward every move: the remote browser
                                // owns scrolling, fling and sliders.
                                if (dist > 1f) gestures.onTouchMove(listOf(GestureFinger(0, fractionAt(p))))
                                last = p
                                if (dist > slop) movedFar = true
                            } else if (mode == BrowserInteraction.Trackpad) {
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
                    // ── Two-finger: pinch zoom + pan + tap ──
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
