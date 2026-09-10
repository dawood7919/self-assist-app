package com.dawood.orbit.tools.cloudbrowser

/**
 * Contract for talking to the VPS that hosts the remote browsers.
 *
 * Pure Kotlin with plain blocking functions returning [Result]. The fakes are
 * deliberately NOT suspend functions: the unit-test source set has no
 * kotlinx-coroutines-test dependency (see app/build.gradle.kts) and the spec
 * forbids new Gradle dependencies, so blocking calls keep every behaviour
 * verifiable with JUnit4 alone. Callers on Android dispatch to a background
 * thread; the UI drives polling on top of [CloudPolling].
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
