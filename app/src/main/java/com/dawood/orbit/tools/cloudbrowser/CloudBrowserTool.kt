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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
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
import com.dawood.orbit.tools.cloudbrowser.screens.BrowserViewScreen
import com.dawood.orbit.tools.cloudbrowser.screens.ConnectScreen
import com.dawood.orbit.tools.cloudbrowser.screens.DownloadsScreen
import com.dawood.orbit.tools.cloudbrowser.screens.FilesScreen
import com.dawood.orbit.tools.cloudbrowser.screens.HomeScreen
import com.dawood.orbit.tools.cloudbrowser.screens.InputOverlayScreen
import com.dawood.orbit.tools.cloudbrowser.screens.IntroScreen
import com.dawood.orbit.tools.cloudbrowser.screens.MonitorScreen
import com.dawood.orbit.tools.cloudbrowser.screens.NewSessionScreen
import com.dawood.orbit.tools.cloudbrowser.screens.SessionsScreen
import com.dawood.orbit.tools.cloudbrowser.screens.SettingsScreen
import com.dawood.orbit.tools.cloudbrowser.real.CdpClient
import com.dawood.orbit.tools.cloudbrowser.real.ChromiumManager
import com.dawood.orbit.tools.cloudbrowser.real.EphemeralCredentials
import com.dawood.orbit.tools.cloudbrowser.real.RealVpsApi
import com.dawood.orbit.tools.cloudbrowser.real.SshManager
import com.dawood.orbit.tools.cloudbrowser.real.ViewportBridge
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolShell
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** Tool-owned file dialog: rename / move prompts or an honest notice. */
private sealed interface FileDialog {
    data class Rename(val path: String) : FileDialog
    data class Move(val path: String) : FileDialog
    data class Notice(val title: String, val message: String) : FileDialog
}

/**
 * Cloud Browser entry: owns all tool state and the blocking [VpsApi] calls.
 *
 * Exact visual copy of the HTML mockup for the tool chrome (feature-local
 * override ordered by the user): dark-only mockup tokens, emoji tab bar that
 * is visible only on the Home route, first-run intro until `introSeen` is
 * persisted. The ToolShell top bar stays as required chrome.
 *
 * Live backend: [RealVpsApi] talks to the VPS over SSH and drives headless
 * Chromium through an SSH tunnel, posting screencast frames into the
 * viewport bridge. [isDemo] stays false while the real backend is in use;
 * the banner shows while the control connection is down.
 * Screens receive values plus callbacks and never call the API themselves.
 */
@Composable
fun CloudBrowserTool(tool: Tool, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    // Live backend: SSH transport plus headless Chromium reached over an SSH
    // tunnel, with screencast frames posted into the viewport bridge.
    // Every blocking VpsApi call below runs on Dispatchers.IO.
    val ssh = remember(appContext) { SshManager(appContext) }
    val okHttp = remember { OkHttpClient() }
    val viewportBridge = remember { ViewportBridge() }
    val api = remember(appContext) {
        RealVpsApi(
            appCtx = appContext,
            ssh = ssh,
            chromium = ChromiumManager(ssh) { CdpClient(okHttp) },
            okHttp = okHttp,
            onFrame = { sessionId, bytes -> viewportBridge.post(sessionId, bytes) },
        )
    }
    // No fake backend in use: screens render their live (non-demo) copy. The
    // banner below still shows while the control connection is down.
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
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    var downloadFilterIndex by rememberSaveable { mutableIntStateOf(0) }
    var fileDialog by remember { mutableStateOf<FileDialog?>(null) }
    var dialogInput by rememberSaveable { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var demoNotice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Live viewport: latest screencast bytes per session from the bridge,
    // decoded to an ImageBitmap off the Main thread for BrowserViewScreen.
    val framesBySession by viewportBridge.frames.collectAsStateWithLifecycle()
    val latestFrameBytes = activeSessionId?.let { framesBySession[it] }
    var liveFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(latestFrameBytes, activeSessionId) {
        val id = activeSessionId
        liveFrame = if (id == null || latestFrameBytes == null) {
            null
        } else {
            withContext(Dispatchers.Default) { viewportBridge.decodeLatest(id) }
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

    fun refreshCurrent() {
        when (route) {
            CloudRoute.Monitor -> refreshMetrics()
            CloudRoute.Files -> refreshFiles()
            CloudRoute.Downloads -> refreshDownloads()
            else -> refreshSessions()
        }
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
        nav.push(next)
        route = nav.current
        demoNotice = null
        if (next == CloudRoute.Monitor) refreshMetrics()
    }

    fun goBack() {
        demoNotice = null
        if (nav.pop()) {
            route = nav.current
        } else {
            onBack()
        }
    }

    fun onSessionAction(id: String, action: SessionAction) {
        when (action) {
            SessionAction.Open -> {
                activeSessionId = id
                push(CloudRoute.ActiveSession)
            }
            SessionAction.Pause -> {
                scope.launch {
                    withContext(Dispatchers.IO) { api.pauseSession(id) }
                    refreshSessions()
                }
            }
            SessionAction.Resume -> {
                scope.launch {
                    withContext(Dispatchers.IO) { api.resumeSession(id) }
                    refreshSessions()
                }
            }
            SessionAction.Close -> {
                scope.launch {
                    withContext(Dispatchers.IO) { api.closeSession(id) }
                    refreshSessions()
                }
            }
        }
    }

    fun openFileDialog(dialog: FileDialog, prefill: String = "") {
        dialogInput = prefill
        dialogError = null
        fileDialog = dialog
    }

    // Mockup bottom nav: visible only on the Home route (never on stack
    // routes, never over the intro). Rendered inside the content so it gets
    // the exact mockup styling (bg + top line border).
    val showTabs = settings.introSeen && route == CloudRoute.Home

    ToolShell(
        tool = tool,
        onBack = { goBack() },
        modifier = modifier,
        subtitle = demoNotice ?: server?.name ?: "Not connected",
        actions = {
            OrbitIconButton(
                icon = OrbitIcons.Refresh,
                contentDescription = "Refresh",
                onClick = { refreshCurrent() },
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
                                    onLaunchBrowser = {
                                        if (connectionState == ConnectionState.Connected && server != null) {
                                            push(CloudRoute.BrowserView)
                                        } else {
                                            push(CloudRoute.Connect)
                                        }
                                    },
                                    onManageServer = { push(CloudRoute.Connect) },
                                    onOpenSessions = { go(CloudRoute.Sessions) },
                                    onOpenMonitor = { push(CloudRoute.Monitor) },
                                    onOpenFiles = { go(CloudRoute.Files) },
                                )
                                CloudRoute.Connect -> ConnectScreen(
                                    initial = server,
                                    isDemo = isDemo,
                                    onTestConnection = { api.testConnection(it) },
                                    onConnect = { draft, password ->
                                        val withId = if (draft.id.isBlank()) {
                                            draft.copy(id = UUID.randomUUID().toString())
                                        } else {
                                            draft
                                        }
                                        // RAM-only handoff: copy the password into
                                        // EphemeralCredentials (never logged, never persisted)
                                        // before connect, then zero this copy. SshManager
                                        // consumes and zeroes the stored copy during auth.
                                        val passwordChars = password.toCharArray()
                                        try {
                                            if (withId.authMethod == AuthMethod.Password) {
                                                EphemeralCredentials.setPassword(withId.id, passwordChars)
                                            }
                                        } finally {
                                            passwordChars.fill('\u0000')
                                        }
                                        // Runs on Dispatchers.IO via ConnectScreen; blocking calls are safe here.
                                        api.connect(withId).onSuccess {
                                            val latency = api.testConnection(withId).getOrNull()
                                            latencyMs = latency
                                            serverStore.upsert(withId.copy(lastLatencyMs = latency))
                                            connectionState = ConnectionState.Connected
                                            refreshSessions()
                                            refreshMetrics()
                                        }
                                    },
                                    onConnected = { go(CloudRoute.Home) },
                                )
                                CloudRoute.BrowserView -> BrowserViewScreen(
                                    serverName = server?.name,
                                    connectionState = connectionState,
                                    latencyMs = latencyMs,
                                    isDemo = isDemo,
                                    config = streamConfig,
                                    onConfigChange = { streamConfig = it },
                                    onOpenInputOverlay = { push(CloudRoute.InputOverlay) },
                                    frame = liveFrame,
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
                                            serverName = server?.name ?: "Demo server",
                                            isDemo = isDemo,
                                            onPollDetails = { api.pollSessionDetails(it) },
                                            onCloseSession = {
                                                api.closeSession(it).also { refreshSessions() }
                                            },
                                            onRestartSession = {
                                                api.restartSession(it).also { refreshSessions() }
                                            },
                                            onOpenFullscreen = { push(CloudRoute.BrowserView) },
                                            onSessionEnded = { go(CloudRoute.Sessions) },
                                        )
                                    }
                                }
                                CloudRoute.Sessions -> SessionsScreen(
                                    sessions = sessions,
                                    serverNameOf = { id ->
                                        servers.firstOrNull { it.id == id }?.name
                                            ?: server?.name
                                            ?: "Demo server"
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
                                    onClearCache = { settingsStore.save(settings.copy(screenshotCacheBytes = 0L)) },
                                )
                                CloudRoute.NewSession -> NewSessionScreen(
                                    servers = servers,
                                    defaults = settings,
                                    onLaunch = { serverId, browser, profile, resolution, quality, frameRate, timeoutSecs ->
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                api.launchSession(
                                                    serverId = serverId,
                                                    browser = browser,
                                                    profile = profile,
                                                    resolution = resolution,
                                                    quality = quality,
                                                    frameRate = frameRate,
                                                    timeoutSecs = timeoutSecs,
                                                )
                                            }
                                            result.onSuccess {
                                                activeSessionId = it.id
                                                refreshSessions()
                                                push(CloudRoute.ActiveSession)
                                            }
                                        }
                                    },
                                    onCancel = { goBack() },
                                )
                                CloudRoute.InputOverlay -> InputOverlayScreen(
                                    mode = streamConfig.mode,
                                    onMode = { streamConfig = streamConfig.copy(mode = it) },
                                    onKey = { key ->
                                        val id = activeSessionId
                                            ?: sessions.firstOrNull { it.state == SessionState.Active }?.id
                                        if (id == null) {
                                            demoNotice = "No active session — launch one first"
                                        } else {
                                            scope.launch {
                                                val result = withContext(Dispatchers.IO) { api.sendKey(id, key) }
                                                result.onFailure { demoNotice = it.message }
                                            }
                                        }
                                    },
                                    onTouchDrag = { dx, dy ->
                                        val id = activeSessionId
                                            ?: sessions.firstOrNull { it.state == SessionState.Active }?.id
                                        if (id != null) {
                                            scope.launch {
                                                val result = withContext(Dispatchers.IO) {
                                                    api.sendPointerMove(id, dx, dy)
                                                }
                                                result.onFailure { demoNotice = it.message }
                                            }
                                        }
                                    },
                                    onToolbar = { action ->
                                        when (action) {
                                            "Refresh" -> refreshCurrent()
                                            "Zoom" -> push(CloudRoute.BrowserView)
                                            else -> {
                                                val id = activeSessionId
                                                    ?: sessions.firstOrNull { it.state == SessionState.Active }?.id
                                                if (id == null) {
                                                    demoNotice = "No active session — launch one first"
                                                } else {
                                                    scope.launch {
                                                        val result = withContext(Dispatchers.IO) {
                                                            api.sendToolbarAction(id, action)
                                                        }
                                                        result.onFailure { demoNotice = it.message }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                )
                                CloudRoute.Downloads -> DownloadsScreen(
                                    items = downloads,
                                    filter = DownloadFilter.entries.getOrElse(downloadFilterIndex) { DownloadFilter.All },
                                    onFilter = {
                                        downloadFilterIndex = DownloadFilter.entries.indexOf(it).coerceIn(DownloadFilter.entries.indices)
                                    },
                                    onDownloadToPhone = {
                                        val id = it
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
        Triple("🗂", "Files", CloudRoute.Files),
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
