package com.dawood.orbit.tools.cloudbrowser

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

enum class MouseTool {
    Pointer,
    RightClick,
    Drag,
    Scroll,
}

@Composable
fun MousePad(
    api: CloudBrowserApi,
    tool: MouseTool,
    remoteWidth: Float,
    remoteHeight: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offsetYPx = with(density) { 56.dp.toPx() }

    var padSize by remember { mutableStateOf(IntSize.Zero) }
    var cursor by remember { mutableStateOf<Offset?>(null) }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var lastMoveJob by remember { mutableStateOf<Job?>(null) }
    var lastSentX by remember { mutableFloatStateOf(-1f) }
    var lastSentY by remember { mutableFloatStateOf(-1f) }

    fun mapToRemote(cursorPos: Offset): Pair<Float, Float> {
        val w = padSize.width.coerceAtLeast(1).toFloat()
        val h = padSize.height.coerceAtLeast(1).toFloat()
        val rw = max(remoteWidth, 1f)
        val rh = max(remoteHeight, 1f)
        val x = (cursorPos.x / w * rw).coerceIn(0f, rw)
        val y = (cursorPos.y / h * rh).coerceIn(0f, rh)
        return x to y
    }

    fun sendMouse(
        type: String,
        cursorPos: Offset,
        button: String = "left",
        deltaX: Float = 0f,
        deltaY: Float = 0f,
    ) {
        val (rx, ry) = mapToRemote(cursorPos)
        if (type == "move") {
            if (kotlin.math.abs(rx - lastSentX) < 1f && kotlin.math.abs(ry - lastSentY) < 1f) return
            lastSentX = rx
            lastSentY = ry
            lastMoveJob?.cancel()
            lastMoveJob = scope.launch {
                withContext(Dispatchers.IO) { api.mouse(type = "move", x = rx, y = ry) }
            }
            return
        }
        scope.launch {
            withContext(Dispatchers.IO) {
                api.mouse(type = type, x = rx, y = ry, button = button, deltaX = deltaX, deltaY = deltaY)
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { padSize = it }
            .then(
                if (enabled) {
                    Modifier.pointerInput(tool, remoteWidth, remoteHeight, padSize) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val start = down.position
                            val cursorStart = Offset(start.x, start.y - offsetYPx)
                            finger = start
                            cursor = cursorStart
                            sendMouse("move", cursorStart)
                            if (tool == MouseTool.Drag) {
                                sendMouse("down", cursorStart, button = "left")
                            }
                            var lastPos = start
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    val endCursor = Offset(change.position.x, change.position.y - offsetYPx)
                                    cursor = endCursor
                                    when (tool) {
                                        MouseTool.Pointer -> {
                                            val dist = (change.position - start).getDistance()
                                            if (dist < 18f) sendMouse("click", endCursor, button = "left")
                                            else sendMouse("move", endCursor)
                                        }
                                        MouseTool.RightClick -> sendMouse("click", endCursor, button = "right")
                                        MouseTool.Drag -> sendMouse("up", endCursor, button = "left")
                                        MouseTool.Scroll -> Unit
                                    }
                                    finger = null
                                    break
                                }
                                change.consume()
                                val pos = change.position
                                val cur = Offset(pos.x, pos.y - offsetYPx)
                                finger = pos
                                cursor = cur
                                when (tool) {
                                    MouseTool.Scroll -> {
                                        val dy = (lastPos.y - pos.y) * 2.5f
                                        val dx = (lastPos.x - pos.x) * 2.5f
                                        if (kotlin.math.abs(dy) > 2f || kotlin.math.abs(dx) > 2f) {
                                            sendMouse("wheel", cur, deltaX = dx, deltaY = dy)
                                        }
                                    }
                                    else -> sendMouse("move", cur)
                                }
                                lastPos = pos
                            }
                        }
                    }
                } else Modifier,
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = cursor
            if (c != null) {
                drawCircle(Color.Black.copy(alpha = 0.35f), radius = 16f, center = c + Offset(2f, 3f))
                val path = Path().apply {
                    moveTo(c.x, c.y)
                    lineTo(c.x + 14f, c.y + 12f)
                    lineTo(c.x + 7f, c.y + 12f)
                    lineTo(c.x + 11f, c.y + 22f)
                    lineTo(c.x + 7f, c.y + 24f)
                    lineTo(c.x + 3f, c.y + 14f)
                    lineTo(c.x, c.y + 18f)
                    close()
                }
                drawPath(path, Color.White)
                drawPath(path, Color(0xFF1A73E8), style = Stroke(width = 2f))
                drawCircle(Color(0xFF1A73E8), radius = 3.5f, center = c)
            }
            val f = finger
            if (f != null) {
                drawCircle(Color.White.copy(alpha = 0.25f), radius = 22f, center = f, style = Stroke(width = 2f))
            }
        }
    }
}
