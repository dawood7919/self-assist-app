package com.dawood.orbit.tools.cloudbrowser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitModal
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine.SessionAction
import com.dawood.orbit.tools.cloudbrowser.screens.ActiveSessionScreen
import com.dawood.orbit.tools.cloudbrowser.screens.BrowserViewScreen
import com.dawood.orbit.tools.cloudbrowser.screens.ConnectScreen
import com.dawood.orbit.tools.cloudbrowser.screens.DownloadsScreen
import com.dawood.orbit.tools.cloudbrowser.screens.FilesScreen
import com.dawood.orbit.tools.cloudbrowser.screens.HomeScreen
import com.dawood.orbit.tools.cloudbrowser.screens.InputOverlayScreen
import com.dawood.orbit.tools.cloudbrowser.screens.MonitorScreen
import com.dawood.orbit.tools.cloudbrowser.screens.NewSessionScreen
import com.dawood.orbit.tools.cloudbrowser.screens.SessionsScreen
import com.dawood.orbit.tools.cloudbrowser.screens.SettingsScreen
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolShell
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tool-owned file dialog: rename / move prompts or an honest notice. */
private sealed interface FileDialog {
    data class Rename(val path: String) : FileDialog
    data class Move(val path: String) : FileDialog
    data class Notice(val title: String, val message: String) : FileDialog
}

/**
 * Cloud Browser entry: owns all tool state and the blocking [VpsApi] calls.
 *
 * Demo backend: [FakeVpsApi] answers instantly with canned data — replace it
 * with a real [VpsApi] implementation to go live. While the fake is in use
 * [isDemo] stays true and every screen shows the honest demo banner.
 * Screens receive values plus callbacks and never call the API themselves.
 */
@Composable
fun CloudBrowserTool(tool: Tool, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val window = LocalOrbitWindow.current
    // Replace FakeVpsApi() with a real VpsApi implementation to go live.
    val api = remember { FakeVpsApi() }
    val isDemo = true

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

    val tabs: (@Composable RowScope.() -> Unit)? =
        if (window.isCompact) {
            {
                CloudTab("Home", route == CloudRoute.Home, { go(CloudRoute.Home) }, Modifier.weight(1f))
                CloudTab("Sessions", route == CloudRoute.Sessions, { go(CloudRoute.Sessions) }, Modifier.weight(1f))
                CloudTab("Files", route == CloudRoute.Files, { go(CloudRoute.Files) }, Modifier.weight(1f))
                CloudTab("Settings", route == CloudRoute.Settings, { go(CloudRoute.Settings) }, Modifier.weight(1f))
            }
        } else {
            null
        }

    ToolShell(
        tool = tool,
        onBack = { goBack() },
        modifier = modifier,
        subtitle = demoNotice ?: server?.let { it.name + " • Demo" } ?: "Demo • Not connected",
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
        bottomBar = tabs,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(OrbitTheme.spacing.lg),
        ) {
            OrbitContentContainer {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                    if (isDemo) {
                        OrbitBadge(
                            text = "Demo — not connected",
                            tone = OrbitTone.Warning,
                            icon = OrbitIcons.Warning,
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
                            onConnect = { draft ->
                                val withId = if (draft.id.isBlank()) {
                                    draft.copy(id = UUID.randomUUID().toString())
                                } else {
                                    draft
                                }
                                // Called on Dispatchers.IO by ConnectScreen; keep blocking here.
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
                            onMove = { openFileDialog(FileDialog.Move(path), currentPath) },
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
                                demoNotice = "Sent $key — demo backend, not delivered"
                            },
                            onTouchDrag = { _, _ ->
                                if (demoNotice?.startsWith("Touchpad") != true) {
                                    demoNotice = "Touchpad input — demo backend, not delivered"
                                }
                            },
                            onToolbar = { action ->
                                when (action) {
                                    "Refresh" -> refreshCurrent()
                                    "Zoom" -> push(CloudRoute.BrowserView)
                                    else -> demoNotice = "$action is not available in this demo"
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

@Composable
private fun CloudTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OrbitButton(
        text = label,
        onClick = onClick,
        modifier = modifier,
        variant = if (selected) OrbitButtonVariant.Tertiary else OrbitButtonVariant.Ghost,
        size = OrbitButtonSize.Small,
    )
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
