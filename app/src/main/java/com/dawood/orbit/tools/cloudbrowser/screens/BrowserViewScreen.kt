package com.dawood.orbit.tools.cloudbrowser.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dawood.orbit.tools.cloudbrowser.BrowserInteraction
import com.dawood.orbit.tools.cloudbrowser.BrowserInput
import com.dawood.orbit.tools.cloudbrowser.CdpInput
import com.dawood.orbit.tools.cloudbrowser.CloudColors
import com.dawood.orbit.tools.cloudbrowser.CloudSmall
import com.dawood.orbit.tools.cloudbrowser.PageInfo
import com.dawood.orbit.tools.cloudbrowser.Quality
import com.dawood.orbit.tools.cloudbrowser.RemoteMouseButton
import com.dawood.orbit.tools.cloudbrowser.real.LiveFrame
import kotlin.math.roundToInt

/** Everything the live browser view needs, pushed from the engine. */
data class BrowserViewState(
    val frame: LiveFrame?,
    val page: PageInfo,
    val zoomPct: Int,
    val interaction: BrowserInteraction,
    val quality: Quality,
    val busy: String?,
    val error: String?,
)

/** Toolbar / viewport actions. Input gestures are funneled through [input]. */
class BrowserViewActions(
    val onNavigate: (String) -> Unit,
    val onReload: () -> Unit,
    val onStop: () -> Unit,
    val onBack: () -> Unit,
    val onForward: () -> Unit,
    val onHome: () -> Unit,
    val onZoomSteps: (Int) -> Unit,
    val onResetZoom: () -> Unit,
    val onScreenshot: () -> Unit,
    val onQualityChange: (Quality) -> Unit,
    val onRetry: () -> Unit,
    val onExit: () -> Unit,
    val input: BrowserInput,
)

private val PanelShape = RoundedCornerShape(8.dp)

/**
 * The actual remote browser: live coded viewport filling the screen, real
 * address bar, navigation buttons, mouse/touch gestures and zoom controls.
 */
@Composable
internal fun BrowserViewScreen(state: BrowserViewState, actions: BrowserViewActions) {
    var mode by remember { mutableStateOf(state.interaction) }
    var keyboardOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CloudColors.Bg),
    ) {
        BrowserToolbar(state, actions, onMenu = { menuOpen = true })
        LoadingStrip(visible = state.page.loading && state.busy == null)
        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            ViewportArea(state, actions, mode)
            if (state.error != null) {
                ErrorBanner(state.error, onRetry = actions.onRetry, onExit = actions.onExit)
            }
        }
        if (keyboardOpen) {
            KeyboardPanel(onType = actions.input::typeText, onKey = actions.input::pressKey)
        }
        BottomBar(
            zoomPct = state.zoomPct,
            mode = mode,
            onToggleMode = {
                mode = if (mode == BrowserInteraction.Direct) {
                    BrowserInteraction.Trackpad
                } else {
                    BrowserInteraction.Direct
                }
            },
            onKeyboard = { keyboardOpen = !keyboardOpen },
            onZoom = actions.onZoomSteps,
            onResetZoom = actions.onResetZoom,
        )
    }

    if (menuOpen) {
        MenuSheet(
            quality = state.quality,
            onScreenshot = {
                menuOpen = false
                actions.onScreenshot()
            },
            onQuality = {
                menuOpen = false
                actions.onQualityChange(it)
            },
            onDismiss = { menuOpen = false },
        )
    }
}

// ----------------------------------------------------------------------
// Toolbar
// ----------------------------------------------------------------------

@Composable
private fun BrowserToolbar(state: BrowserViewState, actions: BrowserViewActions, onMenu: () -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(state.page.url)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(state.page.url) {
        if (!focused) text = TextFieldValue(state.page.url)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CloudColors.Panel)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarGlyph("←", "Back", enabled = state.page.canGoBack, onClick = actions.onBack)
        BarGlyph("→", "Forward", enabled = state.page.canGoForward, onClick = actions.onForward)
        if (state.page.loading) {
            BarGlyph("✕", "Stop", enabled = true, onClick = actions.onStop)
        } else {
            BarGlyph("↻", "Reload", enabled = true, onClick = actions.onReload)
        }
        BarGlyph("⌂", "Home", enabled = true, onClick = actions.onHome)
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .background(CloudColors.Bg, PanelShape)
                .border(1.dp, CloudColors.Line, PanelShape)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CloudSmall(SecurityGlyph(state.page.url), color = CloudColors.Dim)
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = CloudColors.Text, fontSize = 13.sp),
                cursorBrush = SolidColor(CloudColors.Blue),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { actions.onNavigate(text.text.trim()) }),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        BarGlyph("⋯", "Menu", enabled = true, onClick = onMenu)
        BarGlyph("✕", "Exit browser", enabled = true, onClick = actions.onExit)
    }
}

private fun SecurityGlyph(url: String): String = when {
    url.startsWith("https://") -> "🔒"
    url.startsWith("http://") -> "⚠️"
    else -> "🌐"
}

@Composable
private fun BarGlyph(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CloudSmall(symbol, color = if (enabled) CloudColors.Text else CloudColors.Dim)
    }
}

@Composable
private fun LoadingStrip(visible: Boolean) {
    val transition = rememberInfiniteTransition(label = "loading")
    val progress by transition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "bar",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(CloudColors.Line),
    ) {
        if (visible) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.25f)
                    .graphicsLayer { translationX = progress * size.width }
                    .background(CloudColors.Blue),
            )
        }
    }
}

// ----------------------------------------------------------------------
// Viewport with letterboxed coded frame and gesture surface
// ----------------------------------------------------------------------

@Composable
private fun ViewportArea(
    state: BrowserViewState,
    actions: BrowserViewActions,
    mode: BrowserInteraction,
) {
    val frame = state.frame
    val bitmap = remember(frame?.epochMs ?: -1L) { frame?.bytes?.decodeJpeg() }
    when {
        state.busy != null -> BusyState(state.busy)
        frame == null || bitmap == null -> BusyState("Waiting for the remote picture…")
        else -> LiveViewport(frame, bitmap, actions, mode)
    }
}

@Composable
private fun BusyState(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CloudSmall("🌐", color = CloudColors.Text)
        Spacer(Modifier.height(12.dp))
        CloudSmall(text, color = CloudColors.Dim)
    }
}

@Composable
private fun LiveViewport(
    frame: LiveFrame,
    bitmap: ImageBitmap,
    actions: BrowserViewActions,
    mode: BrowserInteraction,
) {
    // Mutable refs read from inside (stable) gesture callbacks so taps always
    // see the latest cursor position and content box size.
    val refs = remember(frame.targetId) { GestureRefs() }
    var contentSize by remember(frame.targetId) { mutableStateOf(IntSize.Zero) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val containerRatio = maxWidth / maxHeight
        val frameRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val contentModifier = if (frameRatio > containerRatio) {
            Modifier.fillMaxWidth().aspectRatio(frameRatio)
        } else {
            Modifier.fillMaxHeight().aspectRatio(frameRatio)
        }
        val input = actions.input
        val gestures = remember(frame.targetId, mode) {
            BrowserGestures(
                onTap = { if (mode == BrowserInteraction.Direct) input.click(it.x, it.y, RemoteMouseButton.Left) },
                onDoubleTap = {
                    if (mode == BrowserInteraction.Direct) {
                        input.click(it.x, it.y, RemoteMouseButton.Left, clickCount = 2)
                    }
                },
                onLongTap = {
                    if (mode == BrowserInteraction.Direct) input.click(it.x, it.y, RemoteMouseButton.Right)
                },
                onPressStart = { input.press(it.x, it.y, RemoteMouseButton.Left) },
                onDragging = { input.move(it.x, it.y) },
                onPressEnd = { input.release(it.x, it.y, RemoteMouseButton.Left) },
                onTrackpadMove = { dx, dy ->
                    refs.cursor = Offset(
                        (refs.cursor.x + dx).coerceIn(0f, 1f),
                        (refs.cursor.y + dy).coerceIn(0f, 1f),
                    )
                    input.relativeMove(dx, dy)
                },
                onTrackpadTap = {
                    input.click(refs.cursor.x, refs.cursor.y, RemoteMouseButton.Left)
                },
                onTrackpadDoubleTap = {
                    input.click(refs.cursor.x, refs.cursor.y, RemoteMouseButton.Left, clickCount = 2)
                },
                onTwoFingerTap = { input.click(it.x, it.y, RemoteMouseButton.Right) },
                onScroll = { fx, fy, dxPhone, dyPhone ->
                    // wheelDeltaPixels already maps phone px to remote CSS px;
                    // negated so the content follows the finger (natural
                    // trackpad direction: swipe up scrolls the page down).
                    val cssX = -CdpInput.wheelDeltaPixels(dxPhone)
                    val cssY = -CdpInput.wheelDeltaPixels(dyPhone)
                    input.wheel(fx, fy, cssX, cssY)
                },
                onZoom = { fx, fy, steps -> input.zoom(steps, fx, fy) },
            )
        }
        Box(
            modifier = contentModifier
                .onSizeChanged { refs.size = it }
                .browserGestures(mode, gestures),
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = "Remote browser viewport",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            if (mode == BrowserInteraction.Trackpad) {
                CursorDot(refs.cursor, refs.size)
            }
        }
    }
}

/** Stable mutable holder shared between the gesture detector and overlay. */
private class GestureRefs {
    var cursor by mutableStateOf(Offset(0.5f, 0.5f))
    var size by mutableStateOf(IntSize.Zero)
}

@Composable
private fun CursorDot(position: Offset, contentSize: IntSize) {
    val diameter = 18.dp
    Box(
        modifier = Modifier
            .offset {
                val px = diameter.toPx()
                IntOffset(
                    (position.x * contentSize.width - px / 2f).roundToInt(),
                    (position.y * contentSize.height - px / 2f).roundToInt(),
                )
            }
            .size(diameter)
            .background(Color.White.copy(alpha = 0.25f), CircleShape)
            .border(1.5.dp, Color.White, CircleShape),
    )
}


// ----------------------------------------------------------------------
// Bottom control bar + keyboard panel
// ----------------------------------------------------------------------

@Composable
private fun BottomBar(
    zoomPct: Int,
    mode: BrowserInteraction,
    onToggleMode: () -> Unit,
    onKeyboard: () -> Unit,
    onZoom: (Int) -> Unit,
    onResetZoom: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CloudColors.Panel)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BarGlyph(
            if (mode == BrowserInteraction.Direct) "👆" else "🖱️",
            "Toggle touch / trackpad mode",
            enabled = true,
            onClick = onToggleMode,
        )
        BarGlyph("⌨️", "Keyboard", enabled = true, onClick = onKeyboard)
        BarGlyph("−", "Zoom out", enabled = true) { onZoom(-1) }
        Box(
            modifier = Modifier
                .widthIn(min = 52.dp)
                .clickable(onClick = onResetZoom)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            CloudSmall("$zoomPct%", color = CloudColors.Text)
        }
        BarGlyph("+", "Zoom in", enabled = true) { onZoom(+1) }
    }
}

@Composable
private fun KeyboardPanel(onType: (String) -> Unit, onKey: (String) -> Unit) {
    var field by remember { mutableStateOf(TextFieldValue("")) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CloudColors.Panel)
            .padding(8.dp),
    ) {
        BasicTextField(
            value = field,
            onValueChange = { new ->
                val added = new.text.removePrefix(field.text)
                if (added.isNotEmpty()) onType(added)
                // The field stays empty: keystrokes go straight to the page.
                field = TextFieldValue("")
            },
            singleLine = true,
            textStyle = TextStyle(color = CloudColors.Text, fontSize = 13.sp),
            cursorBrush = SolidColor(CloudColors.Blue),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onKey("Enter") }),
            modifier = Modifier
                .fillMaxWidth()
                .background(CloudColors.Bg, PanelShape)
                .border(1.dp, CloudColors.Line, PanelShape)
                .padding(10.dp),
            decorationBox = { inner ->
                if (field.text.isEmpty()) {
                    CloudSmall("Type here — keys go to the remote page ⌨️", color = CloudColors.Dim)
                }
                inner()
            },
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Esc", "Tab", "ArrowLeft", "ArrowUp", "ArrowDown", "ArrowRight", "Backspace").forEach { key ->
                KeyButton(key) { onKey(key) }
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(CloudColors.Bg, PanelShape)
            .border(1.dp, CloudColors.Line, PanelShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        CloudSmall(prettyKey(label), color = CloudColors.Text)
    }
}

private fun prettyKey(label: String): String = when (label) {
    "ArrowLeft" -> "←"
    "ArrowRight" -> "→"
    "ArrowUp" -> "↑"
    "ArrowDown" -> "↓"
    "Backspace" -> "⌫"
    else -> label
}

// ----------------------------------------------------------------------
// Error / menu
// ----------------------------------------------------------------------

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit, onExit: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .background(CloudColors.Panel, PanelShape)
                .border(1.dp, CloudColors.Line, PanelShape)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CloudSmall("⚠️", color = CloudColors.Text)
            Spacer(Modifier.height(8.dp))
            CloudSmall(message, color = CloudColors.Text)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallAction("Retry", onRetry)
                SmallAction("Back", onExit)
            }
        }
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(CloudColors.Bg, PanelShape)
            .border(1.dp, CloudColors.Blue, PanelShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        CloudSmall(label, color = CloudColors.Blue)
    }
}

@Composable
private fun MenuSheet(
    quality: Quality,
    onScreenshot: () -> Unit,
    onQuality: (Quality) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CloudColors.Panel)
                .padding(16.dp),
        ) {
            CloudSmall("Picture quality", color = CloudColors.Dim)
            Spacer(Modifier.height(8.dp))
            Quality.entries.forEach { q ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onQuality(q) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloudSmall(if (q == quality) "●" else "○", color = CloudColors.Blue)
                    Spacer(Modifier.width(10.dp))
                    CloudSmall(q.name, color = CloudColors.Text)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onScreenshot)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CloudSmall("📸", color = CloudColors.Text)
                Spacer(Modifier.width(10.dp))
                CloudSmall("Save screenshot to Pictures", color = CloudColors.Text)
            }
        }
    }
}

// ----------------------------------------------------------------------
// Helpers
// ----------------------------------------------------------------------

private fun ByteArray.decodeJpeg(): ImageBitmap? =
    runCatching {
        BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap()
    }.getOrNull()
