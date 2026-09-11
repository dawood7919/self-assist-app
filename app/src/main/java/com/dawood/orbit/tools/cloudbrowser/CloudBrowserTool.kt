package com.dawood.orbit.tools.cloudbrowser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitModal
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine.SessionAction
import com.dawood.orbit.tools.cloudbrowser.screens.ActiveSessionScreen
import com.dawood.orbit.tools.cloudbrowser.screens.BrowserViewActions
import com.dawood.orbit.tools.cloudbrowser.screens.BrowserViewScreen
import com.dawood.orbit.tools.cloudbrowser.screens.BrowserViewState
import com.dawood.orbit.tools.cloudbrowser.screens.ConnectScreen
import com.dawood.orbit.tools.cloudbrowser.screens.DownloadsScreen
import com.dawood.orbit.tools.cloudbrowser.screens.FilesScreen
import com.dawood.orbit.tools.cloudbrowser.screens.HomeScreen
import com.dawood.orbit.tools.cloudbrowser.screens.IntroScreen
import com.dawood.orbit.tools.cloudbrowser.screens.MonitorScreen
import com.dawood.orbit.tools.cloudbrowser.screens.NewSessionScreen
import com.dawood.orbit.tools.cloudbrowser.screens.SessionsScreen
import com.dawood.orbit.tools.cloudbrowser.screens.SettingsScreen
import com.dawood.orbit.tools.cloudbrowser.real.CdpClient
import com.dawood.orbit.tools.cloudbrowser.real.ChromiumManager
import com.dawood.orbit.tools.cloudbrowser.real.EphemeralCredentials
import com.dawood.orbit.tools.cloudbrowser.real.RealVpsApi
import com.dawood.orbit.tools.cloudbrowser.real.SavedCredentialStore
import com.dawood.orbit.tools.cloudbrowser.real.SshManager
import com.dawood.orbit.tools.cloudbrowser.real.ViewportBridge
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolShell
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** Tool-owned file dialog: rename / move prompts or an honest notice. */
private sealed interface FileDialog {
    data class Rename(val path: String) : FileDialog
    data class Move(val path: String) : FileDialog
    data class Notice(val title: String, val message: String) : FileDialog
}

/** First-connection TOFU prompt for an unseen SSH host key. */
private data class PendingHostKey(
    val server: SavedServer,
    val host: String,
    val port: Int,
    val fingerprint: String,
)

/** Chrome provisioning overlay state. */
private data class ProvisionState(
    val stage: String,
    val failed: String? = null,
)

/** The page every fresh browser window lands on. */
private const val HOME_URL = "https://www.google.com/webhp?hl=en"
private const val PAGE_POLL_MS = 1_500L

/**
 * Cloud Browser entry: owns all tool state and the blocking [VpsApi] calls.
 *
 * Live backend: [RealVpsApi] talks to the VPS over SSH and drives headless
 * Chrome through an SSH tunnel, posting screencast frames into the viewport
 * bridge. The BrowserView route renders full-bleed (outside the scrolling
 * chrome) because it IS the browser: live viewport, address bar, real mouse
 * gestures and zoom. Every blocking call runs on Dispatchers.IO.
 */
@Composable
fun CloudBrowserTool(tool: Tool, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val ssh = remember(appContext) { SshManager(appContext) }
    // Encrypted, on-device SSH password vault so users do not retype the
    // VPS password on every launch.
    val credentialStore = remember(appContext) { SavedCredentialStore(appContext) }
    val okHttp = remember {
        // WebSocket pings keep the CDP socket (tunnelled over SSH) alive
        // during quiet spells so the reader never dies of an idle timeout.
        OkHttpClient.Builder()
            .pingInterval(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
    val viewportBridge = remember { ViewportBridge() }
    // States the CDP reader-thread callbacks update: declared before the API so
    // the frame/page lambdas capture stable MutableState references.
    val activeSessionIdState = remember { mutableStateOf<String?>(null) }
    val pageState = remember { mutableStateOf(PageInfo()) }
    var activeSessionId by activeSessionIdState
    var page by pageState
    val api = remember(appContext) {
        RealVpsApi(
            appCtx = appContext,
            ssh = ssh,
            chromium = ChromiumManager(ssh) { CdpClient(okHttp) },
            okHttp = okHttp,
            onFrame = { sessionId, frame -> viewportBridge.post(sessionId, frame) },
            onPageEvent = { sessionId, event ->
                // Runs on the CDP reader thread; snapshot state writes are
                // thread-safe and the UI only collects on the main thread.
                if (sessionId == activeSessionIdState.value) {
                    val current = pageState.value
                    pageState.value = when (event) {
                        is BrowserPageEvent.Loading -> current.copy(loading = event.loading)
                        is BrowserPageEvent.Navigated -> current.copy(url = event.url)
                    }
                }
            },
        )
    }
    val isDemo = false

    val nav = remember { CloudNavState() }
    var route by remember { mutableStateOf(nav.current) }

    val serverStore = remember(context) { ServerStore.get(context) }
    val settingsStore = remember(context) { SettingsStore.get(context) }
    val servers by serverStore.items.collectAsStateWithLifecycle()
    val settingsRows by settingsStore.items.collectAsStateWithLifecycle()
    val server = servers.firstOrNull()
    val settings = settingsRows.firstOrNull() ?: CloudSettings()

    var connectionState by remember { mutableStateOf(ConnectionState.Disconnected) }
    var latencyMs by remember { mutableStateOf<Long?>(null) }
    var sessions by remember { mutableStateOf(emptyList<BrowserSession>()) }
    var metrics by remember { mutableStateOf<VpsMetrics?>(null) }
    var currentPath by remember { mutableStateOf("/home/user") }
    var files by remember { mutableStateOf(emptyList<RemoteFile>()) }
    var downloads by remember { mutableStateOf(emptyList<DownloadItem>()) }
    var streamConfig by remember { mutableStateOf(StreamConfig()) }
    var browserBusy by remember { mutableStateOf<String?>(null) }
    var browserError by remember { mutableStateOf<String?>(null) }
    var browserMode by remember { mutableStateOf(BrowserMode.Mobile) }
    var reconnecting by remember { mutableStateOf(false) }
    // Last viewport we actually applied remotely (dedupes measure callbacks).
    var appliedViewportKey by remember { mutableStateOf("") }
    var pendingHostKey by remember { mutableStateOf<PendingHostKey?>(null) }
    var provision by remember { mutableStateOf<ProvisionState?>(null) }
    var prepareJob by remember { mutableStateOf<Job?>(null) }
    val initialNavDone = remember { mutableSetOf<String>() }
    var downloadFilterIndex by rememberSaveable { mutableIntStateOf(0) }
    var fileDialog by remember { mutableStateOf<FileDialog?>(null) }
    var dialogInput by rememberSaveable { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var demoNotice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val framesBySession by viewportBridge.frames.collectAsStateWithLifecycle()
    val activeFrame = activeSessionId?.let { framesBySession[it] }

    fun io(block: suspend () -> Unit): Job = scope.launch(Dispatchers.IO) { block() }

    /**
     * Maps raw exception text to human connection/browser language. Internal
     * strings ("connectBlocking()", "Not connected — call connect()") must
     * never reach users; they look like broken diagnostics instead of a
     * browser.
     */
    fun friendlyCloudError(raw: String?): String {
        val m = raw.orEmpty()
        return when {
            m.contains("connectBlocking", ignoreCase = true) ||
                m.contains("Not connected", ignoreCase = true) ||
                m.contains("call connect()", ignoreCase = true) ->
                "The link to your server dropped. Reconnect and reopen the browser."
            m.contains("channel is closed", ignoreCase = true) ||
                m.contains("socket closed", ignoreCase = true) ||
                m.contains("Websocket", ignoreCase = true) ||
                m.contains("web socket", ignoreCase = true) ||
                m.contains("timeout", ignoreCase = true) ->
                "The browser connection stalled. Check your network and tap retry."
            m.contains("ECONNREFUSED", ignoreCase = true) ||
                m.contains("refused", ignoreCase = true) ->
                "Chrome on the server is not accepting connections yet — tap retry in a few seconds."
            m.isBlank() -> "Something went wrong with the cloud browser. Please retry."
            else -> m.take(220)
        }
    }

    /** True for dropped-link errors that should trigger auto-heal. */
    fun isDroppedLink(raw: String?): Boolean {
        val m = raw.orEmpty()
        return m.contains("connectBlocking", ignoreCase = true) ||
            m.contains("Not connected", ignoreCase = true) ||
            m.contains("channel is closed", ignoreCase = true) ||
            m.contains("socket closed", ignoreCase = true)
    }

    /**
     * Viewport measured from the device: physical content area (screen minus
     * toolbar/bottom bar) plus density. The BrowserView reports exact dims
     * once composed; this is the launch-time estimate.
     */
    fun launchViewport(mode: BrowserMode): ViewportGeometry {
        val dm = context.resources.displayMetrics
        val toolbarPx = (56f * dm.density).toInt()
        val bottomPx = (44f * dm.density).toInt()
        val physW = dm.widthPixels.coerceAtLeast(360)
        val physH = (dm.heightPixels - toolbarPx - bottomPx).coerceAtLeast(640)
        return CdpInput.emulatedViewport(physW, physH, dm.density, mode)
    }

    /**
     * Best-effort recovery of a dead browser link without user action:
     * relaunches the remote target (SSH control connection self-heals inside
     * SshManager) and returns to the last URL.
     */
    fun relaunchBrowser() {
        if (reconnecting) return
        val currentServer = server ?: return
        reconnecting = true
        scope.launch(Dispatchers.IO) {
            val oldId = activeSessionId
            val lastUrl = page.url.takeIf { it.isNotBlank() } ?: HOME_URL
            try {
                oldId?.let { runCatching { api.closeSession(it) } }
                val geo = launchViewport(browserMode)
                val launched = api.launchSession(
                    serverId = currentServer.id,
                    browser = BrowserKind.Chrome,
                    profile = "Default",
                    resolution = streamConfig.resolution,
                    quality = streamConfig.quality,
                    frameRate = streamConfig.frameRate,
                    timeoutSecs = 60,
                    mode = browserMode,
                    cssWidth = geo.cssWidth,
                    cssHeight = geo.cssHeight,
                    deviceScaleFactor = geo.deviceScaleFactor,
                )
                launched.onSuccess { s ->
                    activeSessionId = s.id
                    initialNavDone.add(s.id)
                    api.navigate(s.id, lastUrl)
                    reconnecting = false
                    browserError = null
                }.onFailure {
                    reconnecting = false
                    browserError = friendlyCloudError(it.message)
                }
            } finally {
                reconnecting = false
            }
        }
    }

    fun refreshSessions() {
        scope.launch {
            val current = sessions
            val result = withContext(Dispatchers.IO) { api.listSessions() }
            sessions = result.getOrDefault(current)
        }
    }

    fun refreshMetrics() {
        val id = server?.id ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) { api.pollMetrics(id) }
            result.onSuccess { metrics = it }
        }
    }

    fun refreshFiles() {
        val path = currentPath
        scope.launch {
            val result = withContext(Dispatchers.IO) { api.listFiles(path) }
            files = result.getOrDefault(emptyList())
        }
    }

    fun refreshDownloads() {
        scope.launch {
            val current = downloads
            val result = withContext(Dispatchers.IO) { api.listDownloads() }
            downloads = result.getOrDefault(current)
        }
    }

    LaunchedEffect(Unit) {
        refreshSessions()
        refreshFiles()
        refreshDownloads()
    }

    // While the browser is on screen, poll page info (url/title/zoom/history)
    // so the toolbar reflects anything happening on the remote side.
    LaunchedEffect(route, activeSessionId) {
        val id = activeSessionId
        if (route == CloudRoute.BrowserView && id != null) {
            while (true) {
                val result = withContext(Dispatchers.IO) { api.pageInfo(id) }
                result.onSuccess { info ->
                    // Preserve the live loading flag (driven by CDP events).
                    val loading = page.loading
                    page = info.copy(loading = loading)
                }
                delay(PAGE_POLL_MS)
            }
        }
    }

    // Pause the stream only when LEAVING the browser route; resume when it
    // comes forward. Opening a session while still on Home must not pause the
    // stream that the browser route is about to show.
    var previousRoute by remember { mutableStateOf(route) }
    LaunchedEffect(route, activeSessionId) {
        val id = activeSessionId
        if (id != null && route == CloudRoute.BrowserView) {
            withContext(Dispatchers.IO) {
                api.resumeSession(id)
                api.pageInfo(id).onSuccess { page = it }
            }
        } else if (id != null && previousRoute == CloudRoute.BrowserView &&
            route != CloudRoute.BrowserView
        ) {
            withContext(Dispatchers.IO) { api.pauseSession(id) }
        }
        previousRoute = route
    }

    fun go(next: CloudRoute) {
        nav.navigate(next)
        route = nav.current
        demoNotice = null
        when (next) {
            CloudRoute.Sessions, CloudRoute.ActiveSession -> refreshSessions()
            CloudRoute.Monitor -> refreshMetrics()
            CloudRoute.Files -> refreshFiles()
            CloudRoute.Downloads -> refreshDownloads()
            else -> Unit
        }
    }

    fun push(next: CloudRoute) {
        // The old full-screen input overlay route is now the browser itself.
        val target = if (next == CloudRoute.InputOverlay) CloudRoute.BrowserView else next
        nav.push(target)
        route = nav.current
        demoNotice = null
        if (target == CloudRoute.Monitor) refreshMetrics()
    }

    fun goBack() {
        demoNotice = null
        if (nav.pop()) {
            route = nav.current
        } else {
            onBack()
        }
    }

    /** Idempotent Chrome provisioning; stages show in the overlay. */
    fun ensurePrepared(): Job = prepareJob ?: scope.launch(Dispatchers.IO) {
        provision = ProvisionState(stage = "Connecting to the server…")
        val result = api.prepareBrowser { stage -> provision = ProvisionState(stage = stage) }
        provision = result.fold(
            onSuccess = { null },
            onFailure = { ProvisionState(stage = provision?.stage ?: "", failed = it.message) },
        )
    }.also { prepareJob = it }

    /** Opens (or reuses) the active browser session and enters the viewport. */
    fun openBrowser() {
        if (connectionState != ConnectionState.Connected || server == null) {
            val saved = server
            if (saved == null || saved.authMethod != AuthMethod.Password) {
                push(CloudRoute.Connect)
                return
            }
            // One-tap relaunch: decrypt the saved SSH password, reconnect,
            // and only fall back to the connect screen if that fails.
            scope.launch {
                val stored = withContext(Dispatchers.IO) {
                    runCatching { credentialStore.load(saved.id) }.getOrNull()
                }
                if (stored.isNullOrBlank()) {
                    push(CloudRoute.Connect)
                    return@launch
                }
                stashPassword(saved, stored)
                val connected = withContext(Dispatchers.IO) { api.connect(saved) }
                if (connected.isSuccess) {
                    // completeConnect runs a blocking latency probe.
                    withContext(Dispatchers.IO) { completeConnect(saved) }
                    openBrowser()
                } else {
                    push(CloudRoute.Connect)
                }
            }
            return
        }
        val serverId = server.id
        browserError = null
        reconnecting = false
        appliedViewportKey = ""
        browserBusy = "Starting your browser on the VPS…"
        scope.launch {
            prepareJob?.join()
            if (provision?.failed != null) {
                browserBusy = null
                demoNotice = "Browser setup failed — open the browser to retry"
                return@launch
            }
            val existing = withContext(Dispatchers.IO) {
                // Reuse any live (even paused) session; entering the browser
                // route resumes its stream. Only Ended rows are skipped.
                api.listSessions().getOrDefault(emptyList())
                    .firstOrNull { it.state != SessionState.Ended }
            }
            val session = existing ?: run {
                // Mobile-first: the remote tab emulates THIS phone's content
                // area, so sites serve mobile pages and frames fill screen.
                val geo = launchViewport(browserMode)
                val launched = withContext(Dispatchers.IO) {
                    api.launchSession(
                        serverId = serverId,
                        browser = BrowserKind.Chrome,
                        profile = "Default",
                        resolution = streamConfig.resolution,
                        quality = streamConfig.quality,
                        frameRate = streamConfig.frameRate,
                        timeoutSecs = 60,
                        mode = browserMode,
                        cssWidth = geo.cssWidth,
                        cssHeight = geo.cssHeight,
                        deviceScaleFactor = geo.deviceScaleFactor,
                    )
                }
                launched.getOrElse {
                    browserBusy = null
                    demoNotice = it.message
                    browserError = it.message
                    return@launch
                }
            }
            activeSessionId = session.id
            page = PageInfo(loading = true)
            sessions = withContext(Dispatchers.IO) { api.listSessions().getOrDefault(sessions) }
            if (initialNavDone.add(session.id)) {
                val first = withContext(Dispatchers.IO) { api.navigate(session.id, HOME_URL) }
                first.onSuccess { page = it }
            }
            browserBusy = null
            push(CloudRoute.BrowserView)
        }
    }

    fun onSessionAction(id: String, action: SessionAction) {
        when (action) {
            SessionAction.Open -> {
                activeSessionId = id
                push(CloudRoute.ActiveSession)
            }
            SessionAction.Pause -> io {
                api.pauseSession(id)
                refreshSessions()
            }
            SessionAction.Resume -> io {
                api.resumeSession(id)
                refreshSessions()
            }
            SessionAction.Close -> io {
                if (id == activeSessionId) viewportBridge.clear(id)
                api.closeSession(id)
                refreshSessions()
            }
        }
    }

    fun openFileDialog(dialog: FileDialog, prefill: String = "") {
        dialogInput = prefill
        dialogError = null
        fileDialog = dialog
    }

    /** Copies a password into the RAM-only credential store (never logged). */
    fun stashPassword(draft: SavedServer, password: String) {
        if (draft.authMethod != AuthMethod.Password || password.isEmpty()) return
        val chars = password.toCharArray()
        try {
            EphemeralCredentials.setPassword(draft.id, chars)
        } finally {
            chars.fill('\u0000')
        }
    }

    /** Shared tail of a successful SSH connect: latency, persist, prepare. */
    fun completeConnect(draft: SavedServer): Result<Unit> {
        val latency = api.testConnection(draft).getOrNull()
        latencyMs = latency
        serverStore.upsert(draft.copy(lastLatencyMs = latency))
        connectionState = ConnectionState.Connected
        refreshSessions()
        refreshMetrics()
        ensurePrepared()
        return Result.success(Unit)
    }

    fun handleConnectFailure(draft: SavedServer, failure: Result<Unit>): Result<Unit> {
        val message = failure.exceptionOrNull()?.message.orEmpty()
        val match = Regex("SHA256:([A-Za-z0-9+/=]+)").findAll(message).lastOrNull()
        return if (match != null && message.contains("Unknown host key")) {
            pendingHostKey = PendingHostKey(
                server = draft,
                host = draft.host,
                port = draft.port,
                fingerprint = match.groupValues[1],
            )
            failure
        } else {
            failure
        }
    }

    // Real input surface: fire-and-forget onto Dispatchers.IO; failures are
    // shown as a short subtitle, never as a crash or a silent swallow.
    val browserInput = remember(api) {
        object : BrowserInput {
            private fun sessionId(): String? = activeSessionIdState.value
            private fun handleFailure(raw: String?) {
                if (isDroppedLink(raw)) {
                    // The CDP target (or SSH) died under us: heal quietly;
                    // relaunchBrowser() owns the reconnecting flag/guard.
                    demoNotice = null
                    relaunchBrowser()
                } else {
                    demoNotice = friendlyCloudError(raw)
                }
            }
            private fun run(call: suspend VpsApi.(String) -> Result<*>) {
                val id = sessionId() ?: return
                scope.launch(Dispatchers.IO) {
                    api.call(id).onFailure { handleFailure(it.message) }
                }
            }

            override fun touchStart(points: List<TouchPointFraction>) {
                val id = sessionId() ?: return
                scope.launch(Dispatchers.IO) {
                    api.touchStart(id, points).onFailure { handleFailure(it.message) }
                }
            }

            override fun touchMove(points: List<TouchPointFraction>) {
                val id = sessionId() ?: return
                // Hot path: fire-and-forget without extra coroutine churn.
                scope.launch(Dispatchers.IO) {
                    api.touchMove(id, points).onFailure { handleFailure(it.message) }
                }
            }

            override fun touchEnd(points: List<TouchPointFraction>) {
                val id = sessionId() ?: return
                scope.launch(Dispatchers.IO) {
                    api.touchEnd(id, points).onFailure { handleFailure(it.message) }
                }
            }

            override fun click(fx: Float, fy: Float, button: RemoteMouseButton, clickCount: Int) {
                run { this.click(it, fx, fy, button, clickCount) }
            }

            override fun press(fx: Float, fy: Float, button: RemoteMouseButton) {
                run { this.pointerPress(it, fx, fy, button) }
            }

            override fun move(fx: Float, fy: Float) {
                run { this.pointerMove(it, fx, fy) }
            }

            override fun release(fx: Float, fy: Float, button: RemoteMouseButton) {
                run { this.pointerRelease(it, fx, fy, button) }
            }

            override fun relativeMove(dfx: Float, dfy: Float) {
                run { this.pointerMoveRelative(it, dfx, dfy) }
            }

            override fun wheel(fx: Float, fy: Float, deltaXPx: Double, deltaYPx: Double, ctrlKey: Boolean) {
                val id = sessionId() ?: return
                scope.launch(Dispatchers.IO) {
                    api.wheel(id, fx, fy, deltaXPx, deltaYPx, ctrlKey)
                        .onFailure { handleFailure(it.message) }
                }
            }

            override fun zoom(steps: Int, fx: Float, fy: Float) {
                val id = sessionId() ?: return
                page = page.copy(
                    zoomPct = CloudBrowserEngine.clampZoom(CdpInput.stepZoom(page.zoomPct, steps)),
                )
                scope.launch(Dispatchers.IO) {
                    api.zoom(id, steps, fx, fy)
                        .onSuccess { page = page.copy(zoomPct = it) }
                        .onFailure { handleFailure(it.message) }
                }
            }

            override fun resetZoom() {
                val id = sessionId() ?: return
                page = page.copy(zoomPct = 100)
                scope.launch(Dispatchers.IO) {
                    api.resetZoom(id).onSuccess { page = page.copy(zoomPct = it) }
                }
            }

            override fun typeText(text: String) {
                run { this.typeText(it, text) }
            }

            override fun pressKey(label: String) {
                run { this.pressKey(it, label) }
            }
        }
    }

    val browserActions = remember(api) {
        BrowserViewActions(
            onNavigate = { raw ->
                val id = activeSessionId ?: return@BrowserViewActions
                page = page.copy(loading = true)
                scope.launch(Dispatchers.IO) {
                    api.navigate(id, raw)
                        .onSuccess { page = it }
                        .onFailure {
                            page = page.copy(loading = false)
                            browserError = it.message
                        }
                }
            },
            onReload = {
                val id = activeSessionId ?: return@BrowserViewActions
                page = page.copy(loading = true)
                scope.launch(Dispatchers.IO) { api.reload(id).onFailure { browserError = it.message } }
            },
            onStop = {
                val id = activeSessionId ?: return@BrowserViewActions
                page = page.copy(loading = false)
                scope.launch(Dispatchers.IO) { api.stopLoading(id) }
            },
            onBack = {
                val id = activeSessionId ?: return@BrowserViewActions
                page = page.copy(loading = true)
                scope.launch(Dispatchers.IO) { api.goBack(id).onFailure { demoNotice = it.message } }
            },
            onForward = {
                val id = activeSessionId ?: return@BrowserViewActions
                page = page.copy(loading = true)
                scope.launch(Dispatchers.IO) { api.goForward(id).onFailure { demoNotice = it.message } }
            },
            onHome = {
                val id = activeSessionId ?: return@BrowserViewActions
                page = page.copy(loading = true)
                scope.launch(Dispatchers.IO) {
                    api.navigate(id, HOME_URL)
                        .onSuccess { page = it }
                        .onFailure { browserError = it.message }
                }
            },
            onZoomSteps = { steps -> browserInput.zoom(steps) },
            onResetZoom = { browserInput.resetZoom() },
            onScreenshot = {
                val id = activeSessionId ?: return@BrowserViewActions
                scope.launch(Dispatchers.IO) {
                    api.saveScreenshot(id).fold(
                        onSuccess = { demoNotice = "Screenshot saved to Pictures/Orbit" },
                        onFailure = { demoNotice = it.message },
                    )
                }
            },
            onQualityChange = { quality ->
                val id = activeSessionId
                streamConfig = streamConfig.copy(quality = quality)
                if (id != null) {
                    scope.launch(Dispatchers.IO) {
                        api.applyStream(id, quality, streamConfig.frameRate)
                            .onFailure { demoNotice = friendlyCloudError(it.message) }
                    }
                }
            },
            onModeChange = { mode ->
                val id = activeSessionId ?: return@BrowserViewActions
                if (mode == browserMode) return@BrowserViewActions
                val previous = browserMode
                browserMode = mode
                page = page.copy(zoomPct = 100)
                scope.launch(Dispatchers.IO) {
                    api.setMode(id, mode)
                        .onFailure {
                            // Roll back the UI switch; the session was never
                            // changed server-side.
                            browserMode = previous
                            browserError = friendlyCloudError(it.message)
                        }
                }
            },
            onViewportMeasured = { cssW, cssH, density ->
                val id = activeSessionId ?: return@BrowserViewActions
                val key = "$browserMode:$cssW:$cssH:$density"
                if (key == appliedViewportKey) return@BrowserViewActions
                appliedViewportKey = key
                scope.launch(Dispatchers.IO) {
                    api.applyViewport(id, ViewportGeometry(cssW, cssH, density.toDouble()))
                        .onFailure {
                            // Fitting is best-effort: launch defaults still work.
                        }
                }
            },
            onRetry = {
                browserError = null
                val id = activeSessionId
                if (id == null) {
                    openBrowser()
                } else {
                    // If the CDP link itself is gone, rebuild the whole target.
                    if (reconnecting) {
                        relaunchBrowser()
                    } else {
                        scope.launch(Dispatchers.IO) {
                            api.applyStream(id, streamConfig.quality, streamConfig.frameRate)
                            api.pageInfo(id)
                                .onSuccess { page = it }
                                .onFailure { relaunchBrowser() }
                        }
                    }
                }
            },
            onExit = { goBack() },
            input = browserInput,
        )
    }

    val showTabs = settings.introSeen && route == CloudRoute.Home

    ToolShell(
        tool = tool,
        onBack = { goBack() },
        modifier = modifier,
        subtitle = demoNotice?.let { friendlyCloudError(it) } ?: server?.name ?: "Not connected",
        actions = {
            OrbitIconButton(
                icon = OrbitIcons.Refresh,
                contentDescription = "Refresh",
                onClick = {
                    when (route) {
                        CloudRoute.Monitor -> refreshMetrics()
                        CloudRoute.Files -> refreshFiles()
                        CloudRoute.Downloads -> refreshDownloads()
                        CloudRoute.BrowserView -> browserActions.onRetry()
                        else -> refreshSessions()
                    }
                },
            )
        },
        menuContent = { dismiss ->
            OrbitMenuItem(
                text = "Connection setup",
                onClick = { dismiss(); go(CloudRoute.Connect) },
                icon = OrbitIcons.Link,
            )
            OrbitMenuItem(
                text = "Downloads",
                onClick = { dismiss(); go(CloudRoute.Downloads) },
                icon = OrbitIcons.Download,
            )
            OrbitMenuItem(
                text = "Disconnect",
                onClick = {
                    dismiss()
                    scope.launch {
                        withContext(Dispatchers.IO) { api.disconnect() }
                        connectionState = ConnectionState.Disconnected
                        prepareJob = null
                        provision = null
                        go(CloudRoute.Home)
                    }
                },
                icon = OrbitIcons.Close,
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(CloudColors.Bg),
        ) {
            if (settings.introSeen && route == CloudRoute.BrowserView) {
                // Full-bleed browser: no scrolling chrome, no bottom tabs.
                BrowserViewScreen(
                    state = BrowserViewState(
                        frame = activeFrame,
                        page = page,
                        zoomPct = CloudBrowserEngine.clampZoom(page.zoomPct),
                        interaction = BrowserInteraction.Direct,
                        browserMode = browserMode,
                        quality = streamConfig.quality,
                        busy = browserBusy,
                        error = browserError?.let { friendlyCloudError(it) },
                        reconnecting = reconnecting,
                    ),
                    actions = browserActions,
                )
            } else {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = CloudColors.BodyPaddingH, vertical = CloudSpacing.PadMd),
                ) {
                    OrbitContentContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadMd)) {
                            if (!settings.introSeen) {
                                IntroScreen(
                                    onStart = {
                                        settingsStore.save(settings.copy(introSeen = true))
                                        go(CloudRoute.Home)
                                    },
                                )
                            } else {
                                if (isDemo || connectionState != ConnectionState.Connected) {
                                    CloudBadge(
                                        text = if (isDemo) "○ Demo — Not connected" else "○ Not connected",
                                        green = false,
                                    )
                                }
                                when (route) {
                                    CloudRoute.Home -> HomeScreen(
                                        server = server,
                                        connectionState = connectionState,
                                        latencyMs = latencyMs,
                                        isDemo = isDemo,
                                        activeSessionCount = sessions.count { it.state == SessionState.Active },
                                        onLaunchBrowser = { openBrowser() },
                                        onManageServer = { push(CloudRoute.Connect) },
                                        onOpenSessions = { go(CloudRoute.Sessions) },
                                        onOpenMonitor = { push(CloudRoute.Monitor) },
                                        onOpenFiles = { go(CloudRoute.Files) },
                                    )
                                    CloudRoute.Connect, CloudRoute.BrowserView -> ConnectScreen(
                                        initial = server,
                                        isDemo = isDemo,
                                        onTestConnection = { draft, password, _ ->
                                            stashPassword(draft, password)
                                            api.testConnection(draft)
                                        },
                                        onConnect = { draft, password, savePassword ->
                                            val withId = if (draft.id.isBlank()) {
                                                draft.copy(id = UUID.randomUUID().toString())
                                            } else {
                                                draft
                                            }
                                            stashPassword(withId, password)
                                            val connected = api.connect(withId)
                                            connected.onSuccess {
                                                // Persist (or explicitly forget) the password;
                                                // this callback already runs on Dispatchers.IO.
                                                runCatching {
                                                    if (savePassword) {
                                                        credentialStore.save(withId.id, password)
                                                    } else {
                                                        credentialStore.delete(withId.id)
                                                    }
                                                }
                                                completeConnect(withId)
                                            }
                                            handleConnectFailure(withId, connected)
                                        },
                                        onLoadSavedPassword = { draft ->
                                            runCatching { credentialStore.load(draft.id) }.getOrNull()
                                        },
                                        onConnected = { go(CloudRoute.Home) },
                                    )
                                    CloudRoute.ActiveSession -> {
                                        val session = sessions.firstOrNull { it.id == activeSessionId }
                                        if (session == null) {
                                            EmptyState(
                                                title = "Session ended",
                                                subtitle = "Pick another session to keep browsing.",
                                                actionLabel = "Back to sessions",
                                                onAction = { go(CloudRoute.Sessions) },
                                            )
                                        } else {
                                            ActiveSessionScreen(
                                                session = session,
                                                serverName = server?.name ?: "Your server",
                                                isDemo = isDemo,
                                                onPollDetails = { api.pollSessionDetails(it) },
                                                onCloseSession = {
                                                    api.closeSession(it).also { refreshSessions() }
                                                },
                                                onRestartSession = {
                                                    api.restartSession(it).also { refreshSessions() }
                                                },
                                                onOpenFullscreen = {
                                                    activeSessionId = session.id
                                                    push(CloudRoute.BrowserView)
                                                },
                                                onSessionEnded = { go(CloudRoute.Sessions) },
                                            )
                                        }
                                    }
                                    CloudRoute.Sessions -> SessionsScreen(
                                        sessions = sessions,
                                        serverNameOf = { id ->
                                            servers.firstOrNull { it.id == id }?.name
                                                ?: server?.name
                                                ?: "Your server"
                                        },
                                        durationTextOf = { session ->
                                            CloudBrowserEngine.sessionAgeText(
                                                session.startedAtEpochMs,
                                                System.currentTimeMillis(),
                                            )
                                        },
                                        onAction = { id, action -> onSessionAction(id, action) },
                                        onNew = { push(CloudRoute.NewSession) },
                                    )
                                    CloudRoute.Monitor -> {
                                        if (server == null) {
                                            EmptyState(
                                                title = "No server connected",
                                                subtitle = "Connect a VPS to see live health metrics.",
                                                actionLabel = "Connection setup",
                                                onAction = { push(CloudRoute.Connect) },
                                            )
                                        } else {
                                            MonitorScreen(
                                                metrics = metrics,
                                                isDemo = isDemo,
                                                onRefresh = { refreshMetrics() },
                                            )
                                        }
                                    }
                                    CloudRoute.Files -> FilesScreen(
                                        path = currentPath,
                                        files = files,
                                        onNavigate = {
                                            currentPath = it
                                            refreshFiles()
                                        },
                                        onUpload = {
                                            openFileDialog(
                                                FileDialog.Notice(
                                                    title = "Upload unavailable",
                                                    message = "This backend has no upload endpoint, so files " +
                                                        "can only move within the VPS for now.",
                                                ),
                                            )
                                        },
                                        onDownload = { path ->
                                            val queued = downloads.firstOrNull { it.sourcePath == path }
                                            if (queued != null && queued.state != DownloadState.Completed) {
                                                val id = queued.id
                                                scope.launch {
                                                    withContext(Dispatchers.IO) { api.downloadToPhone(id) }
                                                    refreshDownloads()
                                                    go(CloudRoute.Downloads)
                                                }
                                            } else {
                                                openFileDialog(
                                                    FileDialog.Notice(
                                                        title = "Download unavailable",
                                                        message = "Direct file download is not supported by this " +
                                                            "backend yet. Queued transfers live in Downloads.",
                                                    ),
                                                )
                                            }
                                        },
                                        onRename = { path ->
                                            openFileDialog(FileDialog.Rename(path), path.substringAfterLast('/'))
                                        },
                                        onMove = { openFileDialog(FileDialog.Move(it), currentPath) },
                                        onDelete = { path ->
                                            scope.launch {
                                                withContext(Dispatchers.IO) { api.deleteFile(path) }
                                                refreshFiles()
                                            }
                                        },
                                    )
                                    CloudRoute.Settings -> SettingsScreen(
                                        settings = settings,
                                        cacheText = CloudBrowserEngine.formatBytes(settings.screenshotCacheBytes),
                                        isDemo = isDemo,
                                        onUpdate = { settingsStore.save(it) },
                                        onClearCache = {
                                            settingsStore.save(settings.copy(screenshotCacheBytes = 0L))
                                        },
                                    )
                                    CloudRoute.NewSession -> NewSessionScreen(
                                        servers = servers,
                                        defaults = settings,
                                        onLaunch = { serverId, browser, profile, resolution, quality, frameRate, timeoutSecs ->
                                            streamConfig = streamConfig.copy(
                                                quality = quality,
                                                frameRate = frameRate,
                                                resolution = resolution,
                                            )
                                            scope.launch {
                                                browserBusy = "Starting your browser on the VPS…"
                                                prepareJob?.join()
                                                val geo = launchViewport(browserMode)
                                                val result = withContext(Dispatchers.IO) {
                                                    api.launchSession(
                                                        serverId = serverId,
                                                        browser = browser,
                                                        profile = profile,
                                                        resolution = resolution,
                                                        quality = quality,
                                                        frameRate = frameRate,
                                                        timeoutSecs = timeoutSecs,
                                                        mode = browserMode,
                                                        cssWidth = geo.cssWidth,
                                                        cssHeight = geo.cssHeight,
                                                        deviceScaleFactor = geo.deviceScaleFactor,
                                                    )
                                                }
                                                result.onSuccess { launched ->
                                                    activeSessionId = launched.id
                                                    page = PageInfo(loading = true)
                                                    refreshSessions()
                                                    val first = withContext(Dispatchers.IO) {
                                                        api.navigate(launched.id, HOME_URL)
                                                    }
                                                    first.onSuccess { page = it }
                                                    browserBusy = null
                                                    push(CloudRoute.BrowserView)
                                                }.onFailure {
                                                    browserBusy = null
                                                    demoNotice = friendlyCloudError(it.message)
                                                }
                                            }
                                        },
                                        onCancel = { goBack() },
                                    )
                                    CloudRoute.InputOverlay -> Unit
                                    CloudRoute.Downloads -> DownloadsScreen(
                                        items = downloads,
                                        filter = DownloadFilter.entries.getOrElse(downloadFilterIndex) {
                                            DownloadFilter.All
                                        },
                                        onFilter = {
                                            downloadFilterIndex = DownloadFilter.entries
                                                .indexOf(it)
                                                .coerceIn(DownloadFilter.entries.indices)
                                        },
                                        onDownloadToPhone = { id ->
                                            scope.launch {
                                                withContext(Dispatchers.IO) { api.downloadToPhone(id) }
                                                refreshDownloads()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                if (showTabs) {
                    CloudBottomNav(
                        active = route,
                        onSelect = { go(it) },
                    )
                }
            }
        }
    }

    // ── TOFU host-key approval ──
    pendingHostKey?.let { pending ->
        OrbitModal(
            visible = true,
            onDismiss = { pendingHostKey = null },
            title = "New server fingerprint",
            description = "First connection to ${pending.host}:${pending.port}.\n\n" +
                "Fingerprint: SHA256:${pending.fingerprint}\n\n" +
                "Only trust it if you recognise the server. A changed key on a " +
                "later connection will be refused automatically.",
            footer = {
                OrbitButton(
                    text = "Cancel",
                    onClick = { pendingHostKey = null },
                    variant = OrbitButtonVariant.Secondary,
                    size = OrbitButtonSize.Small,
                )
                OrbitButton(
                    text = "Trust & connect",
                    onClick = {
                        val draft = pending.server
                        pendingHostKey = null
                        scope.launch(Dispatchers.IO) {
                            api.trustHostKey(pending.host, pending.port, pending.fingerprint)
                            val retry = api.connect(draft)
                            retry.onSuccess { completeConnect(draft) }
                                .onFailure { demoNotice = it.message }
                        }
                    },
                    size = OrbitButtonSize.Small,
                )
            },
                        ) {}
    }

    // ── Launch-in-progress indicator (shown over the home/session screens) ──
    if (browserBusy != null && route != CloudRoute.BrowserView) {
        OrbitModal(
            visible = true,
            onDismiss = {},
            title = "Starting browser",
            description = browserBusy,
            footer = {},
        ) {}
    }

    // ── Chrome provisioning overlay ──
    provision?.let { state ->
        OrbitModal(
            visible = true,
            onDismiss = { if (state.failed != null) provision = null },
            title = if (state.failed == null) "Preparing the browser" else "Browser setup failed",
            description = if (state.failed == null) {
                "${state.stage}\n\nChrome is installed once on the VPS; later starts take seconds."
            } else {
                "${state.stage}\n\n${state.failed}"
            },
            footer = {
                if (state.failed != null) {
                    OrbitButton(
                        text = "Cancel",
                        onClick = { provision = null },
                        variant = OrbitButtonVariant.Secondary,
                        size = OrbitButtonSize.Small,
                    )
                    OrbitButton(
                        text = "Retry",
                        onClick = {
                            prepareJob = null
                            ensurePrepared()
                        },
                        size = OrbitButtonSize.Small,
                    )
                }
            },
        ) {}
    }

    FileDialogHost(
        dialog = fileDialog,
        input = dialogInput,
        error = dialogError,
        onInputChange = { dialogInput = it },
        onConfirm = {
            when (val current = fileDialog) {
                is FileDialog.Rename -> {
                    val name = dialogInput.trim()
                    if (name.isEmpty()) {
                        dialogError = "Enter a file name"
                    } else {
                        val target = current.path
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { api.renameFile(target, name) }
                            result.fold(
                                onSuccess = { fileDialog = null; refreshFiles() },
                                onFailure = { dialogError = it.message },
                            )
                        }
                    }
                }
                is FileDialog.Move -> {
                    val destination = dialogInput.trim()
                    if (destination.isEmpty()) {
                        dialogError = "Enter a destination folder"
                    } else {
                        val target = current.path
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { api.moveFile(target, destination) }
                            result.fold(
                                onSuccess = { fileDialog = null; refreshFiles() },
                                onFailure = { dialogError = it.message },
                            )
                        }
                    }
                }
                is FileDialog.Notice, null -> {
                    fileDialog = null
                }
            }
        },
        onDismiss = { fileDialog = null },
        onViewDownloads = { fileDialog = null; go(CloudRoute.Downloads) },
    )
}

/** Mockup bottom nav: top line border, emoji 16sp + 10sp label per item. */
@Composable
private fun CloudBottomNav(
    active: CloudRoute,
    onSelect: (CloudRoute) -> Unit,
) {
    val items = listOf(
        Triple("🏠", "Home", CloudRoute.Home),
        Triple("🗂", "Sessions", CloudRoute.Sessions),
        Triple("📁", "Files", CloudRoute.Files),
        Triple("⚙", "Settings", CloudRoute.Settings),
    )
    Column(Modifier.fillMaxWidth().background(CloudColors.Bg)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(CloudSpacing.BorderWidth)
                .background(CloudColors.Line),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = CloudSpacing.PadSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (emoji, label, destination) ->
                val selected = active == destination
                val color = if (selected) CloudColors.Blue else CloudColors.Faint
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            onClickLabel = label,
                            role = Role.Button,
                            onClick = { onSelect(destination) },
                        )
                        .padding(vertical = CloudSpacing.PadXxs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadXxs),
                ) {
                    BasicText(
                        text = emoji,
                        style = TextStyle(
                            fontSize = CloudColors.NavEmojiSize,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    BasicText(
                        text = label,
                        style = TextStyle(
                            color = color,
                            fontSize = CloudColors.NavLabelSize,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        }
    }
}

/** Rename / move prompts and honest notices for unsupported file operations. */
@Composable
private fun FileDialogHost(
    dialog: FileDialog?,
    input: String,
    error: String?,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onViewDownloads: () -> Unit,
) {
    when (dialog) {
        is FileDialog.Rename -> OrbitModal(
            visible = true,
            onDismiss = onDismiss,
            title = "Rename file",
            description = dialog.path,
            footer = {
                OrbitButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    variant = OrbitButtonVariant.Secondary,
                    size = OrbitButtonSize.Small,
                )
                OrbitButton(
                    text = "Rename",
                    onClick = onConfirm,
                    size = OrbitButtonSize.Small,
                )
            },
        ) {
            OrbitTextField(
                value = input,
                onValueChange = onInputChange,
                label = "New name",
                errorText = error,
            )
        }
        is FileDialog.Move -> OrbitModal(
            visible = true,
            onDismiss = onDismiss,
            title = "Move file",
            description = dialog.path,
            footer = {
                OrbitButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    variant = OrbitButtonVariant.Secondary,
                    size = OrbitButtonSize.Small,
                )
                OrbitButton(
                    text = "Move",
                    onClick = onConfirm,
                    size = OrbitButtonSize.Small,
                )
            },
        ) {
            OrbitTextField(
                value = input,
                onValueChange = onInputChange,
                label = "Destination folder",
                errorText = error,
            )
        }
        is FileDialog.Notice -> OrbitModal(
            visible = true,
            onDismiss = onDismiss,
            title = dialog.title,
            description = dialog.message,
            icon = OrbitIcons.Info,
            footer = {
                OrbitButton(
                    text = "Close",
                    onClick = onDismiss,
                    variant = OrbitButtonVariant.Secondary,
                    size = OrbitButtonSize.Small,
                )
                OrbitButton(
                    text = "View downloads",
                    onClick = onViewDownloads,
                    size = OrbitButtonSize.Small,
                )
            },
        ) {
        }
        null -> Unit
    }
}
