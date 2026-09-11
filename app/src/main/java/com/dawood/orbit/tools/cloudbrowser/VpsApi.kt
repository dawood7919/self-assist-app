package com.dawood.orbit.tools.cloudbrowser

/**
 * Contract for talking to the VPS that hosts the remote browsers.
 *
 * Pure Kotlin with plain blocking functions returning [Result]. The fakes are
 * deliberately NOT suspend functions: the unit-test source set has no
 * kotlinx-coroutines-test dependency (see app/build.gradle.kts) and the spec
 * forbids new Gradle dependencies, so blocking calls keep every behaviour
 * verifiable with JUnit4 alone. Callers on Android dispatch to a background
 * thread (Dispatchers.IO); the hot input path additionally uses fire-and-
 * forget socket enqueues so drags and typing never wait on a tunnel round
 * trip.
 *
 * Failures use [Result.failure] with a human-readable message (unknown ids,
 * unreachable hosts, ended sessions). Implementations never throw.
 */
interface VpsApi {

    /** Measures round-trip latency without opening a session. Success holds millis. */
    fun testConnection(server: SavedServer): Result<Long>

    /** Opens (or reuses) the control connection to [server]. */
    fun connect(server: SavedServer): Result<Unit>

    /** Drops the control connection. Succeeds even when already disconnected. */
    fun disconnect(): Result<Unit>

    /**
     * Ensures headless Chrome is installed and serving DevTools on loopback
     * 9222 on the node. Installs Chrome on first run (can take minutes); the
     * optional [onStage] callback receives human progress lines. Idempotent.
     */
    fun prepareBrowser(onStage: (String) -> Unit = {}): Result<Unit>

    /** Currently known remote browser sessions, newest first. */
    fun listSessions(): Result<List<BrowserSession>>

    /** Launches a new remote browser window on [serverId]. */
    fun launchSession(
        serverId: String,
        browser: BrowserKind,
        profile: String,
        resolution: Resolution,
        quality: Quality,
        frameRate: Int,
        timeoutSecs: Int,
    ): Result<BrowserSession>

    /** Pauses rendering of session [id] without closing it. */
    fun pauseSession(id: String): Result<Unit>

    /** Resumes a paused or idle session [id]. */
    fun resumeSession(id: String): Result<Unit>

    /** Ends session [id]; the window is gone but the record may remain as Ended. */
    fun closeSession(id: String): Result<Unit>

    /** Restarts session [id] back into the Active state. */
    fun restartSession(id: String): Result<Unit>

    // ── Browsing: navigation, history, page state ──────────────────────

    /**
     * Normalises [rawInput] (URL or search terms) and loads it in [id].
     * Success holds the resulting [PageInfo]; network failures
     * (DNS, refused) come back as a human-readable failure.
     */
    fun navigate(id: String, rawInput: String): Result<PageInfo>

    /** Reloads the current page. */
    fun reload(id: String): Result<Unit>

    /** Stops an in-flight page load. */
    fun stopLoading(id: String): Result<Unit>

    /** Goes back one entry in the in-tab history. */
    fun goBack(id: String): Result<Unit>

    /** Goes forward one entry in the in-tab history. */
    fun goForward(id: String): Result<Unit>

    /** Current URL, title, zoom and back/forward availability. */
    fun pageInfo(id: String): Result<PageInfo>

    // ── Remote pointer (coordinates as 0..1 fractions of the viewport) ──

    /** Moves the pointer to an absolute point (direct-touch mode). */
    fun pointerMove(id: String, fx: Float, fy: Float): Result<Unit>

    /** Moves the pointer relative to its current spot (trackpad mode). */
    fun pointerMoveRelative(id: String, dfx: Float, dfy: Float): Result<Unit>

    /** Presses [button] at an absolute point (starts a possible drag). */
    fun pointerPress(id: String, fx: Float, fy: Float, button: RemoteMouseButton): Result<Unit>

    /** Releases [button] at an absolute point (ends a possible drag). */
    fun pointerRelease(id: String, fx: Float, fy: Float, button: RemoteMouseButton): Result<Unit>

    /**
     * Full click: move, press, release in order. [clickCount] 1 = click,
     * 2 = double click, 3 = triple.
     */
    fun click(id: String, fx: Float, fy: Float, button: RemoteMouseButton, clickCount: Int = 1): Result<Unit>

    /** Wheel scroll (deltas in remote CSS pixels; positive Y scrolls down). */
    fun wheel(
        id: String,
        fx: Float,
        fy: Float,
        deltaXPx: Double,
        deltaYPx: Double,
        ctrlKey: Boolean = false,
    ): Result<Unit>

    // ── Remote keyboard and zoom ───────────────────────────────────────

    /** Inserts text as if typed in the currently focused field. */
    fun typeText(id: String, text: String): Result<Unit>

    /** Presses and releases one special key by name (Enter, Backspace, arrows…). */
    fun pressKey(id: String, label: String): Result<Unit>

    /**
     * Browser zoom by [steps] notches (one notch ≈ 10%, positive zooms in),
     * centred on [fx],[fy]. Success holds the new zoom percentage.
     */
    fun zoom(id: String, steps: Int, fx: Float = 0.5f, fy: Float = 0.5f): Result<Int>

    /** Resets browser zoom to 100%. */
    fun resetZoom(id: String): Result<Int>

    /** Restarts the frame stream with new quality/frame-rate settings. */
    fun applyStream(id: String, quality: Quality, frameRate: Int): Result<Unit>

    /** Saves a full-resolution JPEG of the current page into the phone's gallery. */
    fun saveScreenshot(id: String): Result<Unit>

    // ── Health, files, downloads ───────────────────────────────────────

    /** Latest health snapshot for [serverId]. */
    fun pollMetrics(serverId: String): Result<VpsMetrics>

    /** Latest detail row for session [sessionId]. */
    fun pollSessionDetails(sessionId: String): Result<SessionDetails>

    /** Directory listing for remote [path]. */
    fun listFiles(path: String): Result<List<RemoteFile>>

    /** Renames the remote file at [path] to [newName] (same directory). */
    fun renameFile(path: String, newName: String): Result<Unit>

    /** Moves the remote file at [srcPath] into directory [dstDir]. */
    fun moveFile(srcPath: String, dstDir: String): Result<Unit>

    /** Deletes the remote file at [path]. */
    fun deleteFile(path: String): Result<Unit>

    /** Current download queue, newest first. */
    fun listDownloads(): Result<List<DownloadItem>>

    /**
     * Copies download [downloadId] to the phone.
     *
     * Succeeds honestly: implementations only return success once the bytes
     * are queued locally, never to pretend a transfer happened.
     */
    fun downloadToPhone(downloadId: String): Result<Unit>
}

/** Mouse buttons the remote pointer can press. */
enum class RemoteMouseButton(val cdpName: String, val bit: Int) {
    Left("left", 1),
    Right("right", 2),
    Middle("middle", 4),
}

/** Live page events pushed from the remote target (frame navigation/load). */
sealed class BrowserPageEvent {
    /** Main frame started ([loading]=true) or finished (false) loading. */
    data class Loading(val loading: Boolean) : BrowserPageEvent()

    /** The main frame navigated to [url]. */
    data class Navigated(val url: String) : BrowserPageEvent()
}
