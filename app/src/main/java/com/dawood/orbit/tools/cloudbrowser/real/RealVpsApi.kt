package com.dawood.orbit.tools.cloudbrowser.real

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import com.dawood.orbit.tools.cloudbrowser.BrowserKind
import com.dawood.orbit.tools.cloudbrowser.BrowserPageEvent
import com.dawood.orbit.tools.cloudbrowser.BrowserSession
import com.dawood.orbit.tools.cloudbrowser.CdpEvent
import com.dawood.orbit.tools.cloudbrowser.CdpInput
import com.dawood.orbit.tools.cloudbrowser.CdpMessages
import com.dawood.orbit.tools.cloudbrowser.DownloadItem
import com.dawood.orbit.tools.cloudbrowser.DownloadState
import com.dawood.orbit.tools.cloudbrowser.DownloadStore
import com.dawood.orbit.tools.cloudbrowser.FileType
import com.dawood.orbit.tools.cloudbrowser.FrameThrottle
import com.dawood.orbit.tools.cloudbrowser.HistorySnapshot
import com.dawood.orbit.tools.cloudbrowser.PageInfo
import com.dawood.orbit.tools.cloudbrowser.Quality
import com.dawood.orbit.tools.cloudbrowser.RemoteFile
import com.dawood.orbit.tools.cloudbrowser.RemoteMouseButton
import com.dawood.orbit.tools.cloudbrowser.Resolution
import com.dawood.orbit.tools.cloudbrowser.SavedServer
import com.dawood.orbit.tools.cloudbrowser.SessionDetails
import com.dawood.orbit.tools.cloudbrowser.SessionState
import com.dawood.orbit.tools.cloudbrowser.SshMetricsParser
import com.dawood.orbit.tools.cloudbrowser.VpsApi
import com.dawood.orbit.tools.cloudbrowser.VpsMetrics
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import okhttp3.OkHttpClient
import org.json.JSONObject

// ---------------------------------------------------------------------------
// RealVpsApi: live VpsApi over SSH + Chromium CDP.
// ---------------------------------------------------------------------------

/**
 * Live [VpsApi] implementation: SSH transport ([SshManager]) plus a headless
 * Chromium on the VPS driven over a local SSH tunnel via CDP.
 *
 * Blocking contract: EVERY method blocks on network I/O and MUST be called
 * from a background thread (the tool dispatches to Dispatchers.IO). Methods
 * fail fast with "Internal: network on Main" when invoked on the main
 * thread, and never throw — every failure is a [Result.failure] carrying a
 * human-readable message.
 *
 * Budgets: testConnection 15 s (L0 TCP dial, L1 SSH handshake/host key, L2
 * auth, L3 command-channel echo), connect 20 s, launch 30 s (Chromium
 * ensure, tunnel, target, screencast). Password auth consumes the one-shot
 * password the tool stored in [EphemeralCredentials] beforehand; the consume
 * itself happens inside [SshManager] at the auth moment.
 *
 * Sessions: live targets are tracked in memory; [closeSession] and
 * [disconnect] leave honest Ended tombstones so [listSessions] keeps
 * history newest-first. Pause stops the screencast only — the remote page
 * keeps running, and resume restarts the stream.
 *
 * Frames: each live target owns a [CdpClient] whose listener queues an ack
 * for every Page.screencastFrame on a daemon thread (the standalone client
 * only offers blocking sends, which must never run on the socket reader
 * thread), decodes the JPEG payload, gates it through [thumbs], then invokes
 * [onFrame] (which must return quickly — it runs on the socket reader thread).
 */
class RealVpsApi(
    appCtx: Context,
    private val ssh: SshManager,
    private val chromium: ChromiumManager,
    private val okHttp: OkHttpClient,
    private val thumbs: FrameThrottle = FrameThrottle(),
    private val onFrame: (sessionId: String, frame: LiveFrame) -> Unit = { _, _ -> },
    private val onPageEvent: (sessionId: String, event: BrowserPageEvent) -> Unit = { _, _ -> },
) : VpsApi {

    private val appContext: Context = appCtx.applicationContext
    private val provisioner = ChromeProvisioner(ssh)
    private val lock = Any()
    @Volatile
    private var browserPrepared: Boolean = false
    private val live = LinkedHashMap<String, LiveSession>()
    private val tombstones = LinkedHashMap<String, BrowserSession>()
    private val lastDetails = mutableMapOf<String, SessionDetails>()
    private var currentServerId: String? = null
    private var prevCpu: SshMetricsParser.CpuSample? = null
    private var prevNet: SshMetricsParser.NetSample? = null

    /** In-memory live target: CDP handle plus the record shown to the UI. */
    private data class LiveSession(
        val sessionId: String,
        val targetId: String,
        val wsUrl: String,
        val localPort: Int,
        val cdp: CdpClient,
        var state: SessionState,
        val serverId: String,
        val browser: BrowserKind,
        val profile: String,
        val resolution: Resolution,
        val quality: Quality,
        val frameRate: Int,
        val width: Int,
        val height: Int,
        val startedAtEpochMs: Long,
        var lastSeenEpochMs: Long,
        var screencasting: Boolean,
        var cursorX: Float,
        var cursorY: Float,
        var zoomPct: Int = 100,
        var buttonsMask: Int = 0,
    ) {
        fun toRecord(): BrowserSession =
            BrowserSession(
                id = sessionId,
                serverId = serverId,
                browser = browser,
                profile = profile,
                resolution = resolution,
                quality = quality,
                frameRate = frameRate,
                state = state,
                startedAtEpochMs = startedAtEpochMs,
                lastSeenEpochMs = lastSeenEpochMs,
            )
    }

    private data class OpenedTarget(
        val targetId: String,
        val wsUrl: String,
        val localPort: Int,
        val cdp: CdpClient,
        val width: Int,
        val height: Int,
    )

    // ------------------------------------------------------------------
    // Connection
    // ------------------------------------------------------------------

    /**
     * L0 TCP dial (exact SshManager L0 text) when no control connection is
     * up, then L1 handshake/host key plus L2 auth via [SshManager.connect]
     * (exact L1/L2 texts propagate), then an L3 command-channel echo.
     * Returns wall-clock millis. Leaves the control connection up.
     */
    override fun testConnection(server: SavedServer): Result<Long> =
        safeCall {
            rejectOnMain<Long>()?.let { return@safeCall it }
            if (server.host.isBlank()) {
                return@safeCall Result.failure(
                    Exception(
                        "Cannot reach ${server.host}:${server.port} — check host spelling, " +
                            "port, and that the VPS firewall allows SSH (TCP/22). " +
                            "Detail: empty hostname.",
                    ),
                )
            }
            val start = System.currentTimeMillis()
            val deadline = start + TEST_TIMEOUT_MS
            if (!ssh.isConnected()) {
                val dialBudget = (deadline - System.currentTimeMillis()).coerceIn(1_000L, 5_000L)
                val dialError = dialTcp(server.host, server.port, dialBudget)
                if (dialError != null) {
                    return@safeCall Result.failure(
                        Exception(
                            "Cannot reach ${server.host}:${server.port} — check host spelling, " +
                                "port, and that the VPS firewall allows SSH (TCP/22). " +
                                "Detail: $dialError",
                        ),
                    )
                }
                val connected = ssh.connect(server)
                if (connected.isFailure) {
                    return@safeCall Result.failure(
                        connected.exceptionOrNull() ?: Exception("Could not connect to the server."),
                    )
                }
            }
            val echo = ssh.exec(ECHO_PROBE, remainingMs(deadline))
            if (echo.isFailure) {
                return@safeCall Result.failure(
                    echo.exceptionOrNull() ?: Exception("The server connected but ran no commands."),
                )
            }
            if (echo.getOrNull()?.trim() != ECHO_TOKEN) {
                return@safeCall Result.failure(Exception("The server gave an unexpected reply to the echo check."))
            }
            Result.success(System.currentTimeMillis() - start)
        }

    /**
     * Opens (or replaces) the control connection within a 20 s budget. For
     * password servers the one-shot password must already sit in
     * [EphemeralCredentials] — the tool stores it just before this call and
     * [SshManager] consumes and zeroes it during auth.
     */
    override fun connect(server: SavedServer): Result<Unit> =
        safeCall {
            rejectOnMain<Unit>()?.let { return@safeCall it }
            val connected = ssh.connect(server)
            if (connected.isFailure) {
                return@safeCall Result.failure(
                    connected.exceptionOrNull() ?: Exception("Could not connect to the server."),
                )
            }
            synchronized(lock) {
                currentServerId = server.id
                browserPrepared = false
            }
            Result.success(Unit)
        }

    /**
     * Persists a user-approved TOFU host key. Exposed outside [VpsApi] because
     * trusting a host is an SSH-transport concern, not a browsing verb.
     */
    fun trustHostKey(host: String, port: Int, sha256Base64: String) {
        ssh.trustHostKey(host, port, sha256Base64)
    }

    /**
     * Ensures headless Chrome is installed and listening on the node. Runs
     * the (idempotent) provisioner once per connection and caches success,
     * while disconnect/[connect] resets the flag.
     */
    override fun prepareBrowser(onStage: (String) -> Unit): Result<Unit> =
        safeCall {
            rejectOnMain<Unit>()?.let { return@safeCall it }
            val already = synchronized(lock) { browserPrepared }
            if (already) return@safeCall Result.success(Unit)
            val result = provisioner.ensure(onStage)
            if (result.isSuccess) {
                synchronized(lock) { browserPrepared = true }
            }
            result
        }

    /**
     * Drops screencasts, closes target sockets, then the tunnel and SSH, in
     * that order; zeroes every stored secret; marks live sessions Ended.
     * Succeeds when already disconnected. Never throws.
     */
    override fun disconnect(): Result<Unit> =
        safeCall {
            rejectOnMain<Unit>()?.let { return@safeCall it }
            val closing: List<LiveSession>
            synchronized(lock) {
                closing = live.values.toList()
                for (session in closing) {
                    session.state = SessionState.Ended
                    session.lastSeenEpochMs = System.currentTimeMillis()
                    tombstones[session.sessionId] = session.toRecord()
                }
                live.clear()
                prevCpu = null
                prevNet = null
                browserPrepared = false
            }
            for (session in closing) {
                try {
                    stopScreencastBestEffort(session.cdp)
                } catch (_: Exception) {
                }
                try {
                    session.cdp.close()
                } catch (_: Exception) {
                }
            }
            try {
                ssh.closeTunnel()
            } catch (_: Exception) {
            }
            try {
                ssh.disconnect()
            } catch (_: Exception) {
            }
            try {
                EphemeralCredentials.clearAll()
            } catch (_: Exception) {
            }
            Result.success(Unit)
        }

    // ------------------------------------------------------------------
    // Sessions
    // ------------------------------------------------------------------

    override fun listSessions(): Result<List<BrowserSession>> =
        safeCall {
            rejectOnMain<List<BrowserSession>>()?.let { return@safeCall it }
            val rows: List<BrowserSession>
            synchronized(lock) {
                rows = live.values.map { it.toRecord() } + tombstones.values.toList()
            }
            Result.success(rows.sortedByDescending { it.lastSeenEpochMs })
        }

    /**
     * Ensures Chrome is provisioned, then launches a sized target and starts
     * its screencast within the timeout budget: prepareBrowser → openTunnel
     * → createTarget(about:blank, w×h) → connect target WS → resize window →
     * Page.enable → navigate about:blank → startScreencast (cadence/quality
     * from the presets) → Active row. Firefox has no CDP endpoint and fails
     * honestly.
     */
    override fun launchSession(
        serverId: String,
        browser: BrowserKind,
        profile: String,
        resolution: Resolution,
        quality: Quality,
        frameRate: Int,
        timeoutSecs: Int,
    ): Result<BrowserSession> =
        safeCall {
            rejectOnMain<BrowserSession>()?.let { return@safeCall it }
            if (browser == BrowserKind.Firefox) {
                return@safeCall Result.failure(
                    Exception("Firefox is not supported by this backend yet — choose Chromium."),
                )
            }
            if (!ssh.isConnected()) {
                return@safeCall Result.failure(Exception("Not connected — call connect() first."))
            }
            val provisioned = prepareBrowser { }
            if (provisioned.isFailure) {
                return@safeCall Result.failure(
                    provisioned.exceptionOrNull() ?: Exception("Chrome could not be prepared on the server."),
                )
            }
            val sessionId = UUID.randomUUID().toString()
            val deadline = System.currentTimeMillis() +
                maxOf(LAUNCH_TIMEOUT_MS, timeoutSecs.coerceAtLeast(1) * 1_000L)
            val opened = openTarget(sessionId, resolution, quality, frameRate, deadline)
                .getOrElse { return@safeCall Result.failure(it) }
            val now = System.currentTimeMillis()
            val session = LiveSession(
                sessionId = sessionId,
                targetId = opened.targetId,
                wsUrl = opened.wsUrl,
                localPort = opened.localPort,
                cdp = opened.cdp,
                state = SessionState.Active,
                serverId = serverId,
                browser = browser,
                profile = profile.ifBlank { "Default" },
                resolution = resolution,
                quality = quality,
                frameRate = frameRate,
                width = opened.width,
                height = opened.height,
                startedAtEpochMs = now,
                lastSeenEpochMs = now,
                screencasting = true,
                cursorX = 0.5f,
                cursorY = 0.5f,
            )
            synchronized(lock) {
                live[sessionId] = session
                tombstones.remove(sessionId)
                currentServerId = serverId
            }
            Result.success(session.toRecord())
        }

    /**
     * Stops the screencast and marks Paused. Honest: the remote page keeps
     * running; only the stream stops.
     */
    override fun pauseSession(id: String): Result<Unit> =
        safeCall {
            rejectOnMain<Unit>()?.let { return@safeCall it }
            val session = liveOf(id)
                ?: return@safeCall if (tombstoneOf(id) != null) {
                    Result.failure(Exception("Session has ended and cannot be paused"))
                } else {
                    Result.failure(Exception("Unknown session id: $id"))
                }
            if (session.state != SessionState.Paused || session.screencasting) {
                val stopId = CdpMessages.nextId()
                val stopped = session.cdp.sendAndAwait(CdpMessages.stopScreencast(stopId), stopId, IO_TIMEOUT_MS)
                if (stopped.isFailure && session.state != SessionState.Paused) {
                    return@safeCall Result.failure(
                        stopped.exceptionOrNull() ?: Exception("Could not pause the session."),
                    )
                }
            }
            synchronized(lock) {
                session.state = SessionState.Paused
                session.screencasting = false
                session.lastSeenEpochMs = System.currentTimeMillis()
            }
            Result.success(Unit)
        }

    /** Restarts the screencast and marks Active. */
    override fun resumeSession(id: String): Result<Unit> =
        safeCall {
            rejectOnMain<Unit>()?.let { return@safeCall it }
            val session = liveOf(id)
                ?: return@safeCall if (tombstoneOf(id) != null) {
                    Result.failure(Exception("Session has ended and cannot be resumed"))
                } else {
                    Result.failure(Exception("Unknown session id: $id"))
                }
            if (!session.screencasting) {
                val started = startScreencast(session, IO_TIMEOUT_MS)
                if (started.isFailure) {
                    return@safeCall Result.failure(
                        started.exceptionOrNull() ?: Exception("Could not resume the session."),
                    )
                }
            }
            synchronized(lock) {
                session.state = SessionState.Active
                session.screencasting = true
                session.lastSeenEpochMs = System.currentTimeMillis()
            }
            Result.success(Unit)
        }

    /**
     * Stops the stream, closes the remote target, closes the socket, and
     * keeps an Ended tombstone. Idempotent for already-ended ids.
     */
    override fun closeSession(id: String): Result<Unit> =
        safeCall {
            rejectOnMain<Unit>()?.let { return@safeCall it }
            val session = liveOf(id)
            if (session == null) {
                return@safeCall if (tombstoneOf(id) != null) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Unknown session id: $id"))
                }
            }
            try {
                stopScreencastBestEffort(session.cdp)
            } catch (_: Exception) {
            }
            try {
                chromium.closeTarget(session.localPort, session.targetId)
            } catch (_: Exception) {
            }
            try {
                session.cdp.close()
            } catch (_: Exception) {
            }
            synchronized(lock) {
                live.remove(id)
                session.state = SessionState.Ended
                session.lastSeenEpochMs = System.currentTimeMillis()
                tombstones[id] = session.toRecord()
            }
            Result.success(Unit)
        }

    /**
     * Live target: navigates back to about:blank, ensures the stream runs,
     * marks Active. Ended tombstone: recreates the target with the stored
     * preset, keeping the same session id.
     */
    override fun restartSession(id: String): Result<Unit> =
        safeCall {
            rejectOnMain<Unit>()?.let { return@safeCall it }
            val session = liveOf(id)
            if (session != null) {
                val navId = CdpMessages.nextId()
                val navigated = session.cdp.sendAndAwait(CdpMessages.navigate(navId, "about:blank"), navId, IO_TIMEOUT_MS)
                if (navigated.isFailure) {
                    return@safeCall Result.failure(
                        navigated.exceptionOrNull() ?: Exception("Could not restart the session."),
                    )
                }
                if (!session.screencasting) {
                    val started = startScreencast(session, IO_TIMEOUT_MS)
                    if (started.isFailure) {
                        return@safeCall Result.failure(
                            started.exceptionOrNull() ?: Exception("Could not restart the session."),
                        )
                    }
                }
                synchronized(lock) {
                    session.state = SessionState.Active
                    session.screencasting = true
                    session.lastSeenEpochMs = System.currentTimeMillis()
                }
                return@safeCall Result.success(Unit)
            }
            val tombstone = tombstoneOf(id)
                ?: return@safeCall Result.failure(Exception("Unknown session id: $id"))
            if (!ssh.isConnected()) {
                return@safeCall Result.failure(Exception("Not connected — call connect() first."))
            }
            val deadline = System.currentTimeMillis() + LAUNCH_TIMEOUT_MS
            val opened = openTarget(id, tombstone.resolution, tombstone.quality, tombstone.frameRate, deadline)
                .getOrElse { return@safeCall Result.failure(it) }
            val now = System.currentTimeMillis()
            val revived = LiveSession(
                sessionId = id,
                targetId = opened.targetId,
                wsUrl = opened.wsUrl,
                localPort = opened.localPort,
                cdp = opened.cdp,
                state = SessionState.Active,
                serverId = tombstone.serverId,
                browser = tombstone.browser,
                profile = tombstone.profile,
                resolution = tombstone.resolution,
                quality = tombstone.quality,
                frameRate = tombstone.frameRate,
                width = opened.width,
                height = opened.height,
                startedAtEpochMs = now,
                lastSeenEpochMs = now,
                screencasting = true,
                cursorX = 0.5f,
                cursorY = 0.5f,
            )
            synchronized(lock) {
                live[id] = revived
                tombstones.remove(id)
            }
            Result.success(Unit)
        }

    // ------------------------------------------------------------------
    // Metrics
    // ------------------------------------------------------------------

    /**
     * One SSH exec of [SshMetricsParser.COMMAND] parsed into live metrics.
     * latencyMs is the measured wall time; bandwidthMbps is 0.0 (the probe
     * reports directional rates instead); isLive is always true here.
     */
    override fun pollMetrics(serverId: String): Result<VpsMetrics> =
        safeCall {
            rejectOnMain<VpsMetrics>()?.let { return@safeCall it }
            val start = System.currentTimeMillis()
            val output = ssh.exec(SshMetricsParser.COMMAND, IO_TIMEOUT_MS)
                .getOrElse { return@safeCall Result.failure(it) }
            val nowMs = System.currentTimeMillis()
            val cpu = SshMetricsParser.extractCpuSample(output, nowMs)
            val net = SshMetricsParser.extractNetSample(output, nowMs)
            val parsed: SshMetricsParser.ParsedMetrics
            synchronized(lock) {
                parsed = SshMetricsParser.parse(output, prevCpu, prevNet, nowMs)
                if (cpu != null) prevCpu = cpu
                if (net != null) prevNet = net
            }
            Result.success(
                VpsMetrics(
                    cpuPct = parsed.cpuPct,
                    ramUsedGb = parsed.ramUsedGb,
                    ramTotalGb = parsed.ramTotalGb,
                    netDownMbps = parsed.netDownMbps,
                    netUpMbps = parsed.netUpMbps,
                    diskUsedGb = parsed.diskUsedGb,
                    diskTotalGb = parsed.diskTotalGb,
                    uptimeSecs = parsed.uptimeSecs,
                    latencyMs = nowMs - start,
                    bandwidthMbps = 0.0,
                    isLive = true,
                ),
            )
        }

    /**
     * Runtime.evaluate for the live page title plus URL, with duration math
     * off the stored start time. OS comes from uname; latencyMs is the
     * evaluate wall time. Ended sessions report their last known details
     * with a frozen duration.
     */
    override fun pollSessionDetails(sessionId: String): Result<SessionDetails> =
        safeCall {
            rejectOnMain<SessionDetails>()?.let { return@safeCall it }
            val session = liveOf(sessionId)
            if (session == null) {
                val cached = synchronized(lock) { lastDetails[sessionId] }
                if (cached != null) return@safeCall Result.success(cached)
                val tombstone = tombstoneOf(sessionId)
                if (tombstone != null) {
                    return@safeCall Result.success(
                        SessionDetails(
                            sessionId = sessionId,
                            browser = tombstone.browser.name,
                            os = describeOs(),
                            durationSecs = ((tombstone.lastSeenEpochMs - tombstone.startedAtEpochMs) / 1_000L).coerceAtLeast(0L),
                            resolution = resolutionLabel(tombstone.resolution),
                            connection = CONNECTION_LABEL,
                            latencyMs = 0L,
                            bandwidthMbps = 0.0,
                        ),
                    )
                }
                return@safeCall Result.failure(Exception("Unknown session id: $sessionId"))
            }
            val start = System.currentTimeMillis()
            val evalId = CdpMessages.nextId()
            val evaluated = session.cdp.sendAndAwait(
                CdpMessages.evaluate(evalId, "JSON.stringify({title: document.title, url: location.href})"),
                evalId,
                IO_TIMEOUT_MS,
            )
            if (evaluated.isFailure) {
                return@safeCall Result.failure(
                    evaluated.exceptionOrNull() ?: Exception("Could not read the session details."),
                )
            }
            val latency = System.currentTimeMillis() - start
            touchLive(sessionId)
            val details = SessionDetails(
                sessionId = sessionId,
                browser = session.browser.name,
                os = describeOs(),
                durationSecs = ((System.currentTimeMillis() - session.startedAtEpochMs) / 1_000L).coerceAtLeast(0L),
                resolution = "${session.width} × ${session.height}",
                connection = CONNECTION_LABEL,
                latencyMs = latency,
                bandwidthMbps = 0.0,
            )
            synchronized(lock) { lastDetails[sessionId] = details.copy() }
            Result.success(details)
        }

    // ------------------------------------------------------------------
    // Files (SFTP)
    // ------------------------------------------------------------------

    override fun listFiles(path: String): Result<List<RemoteFile>> =
        safeCall {
            rejectOnMain<List<RemoteFile>>()?.let { return@safeCall it }
            val dir = canonicalDir(path)
            withSftp { sftp ->
                val entries = try {
                    sftp.ls(dir)
                } catch (e: Exception) {
                    return@withSftp Result.failure(missingAs(dir, e, "No such directory: $path"))
                }
                val rows = entries
                    .filter { it.name != "." && it.name != ".." }
                    .map { info ->
                        val isDir = info.isDir
                        RemoteFile(
                            path = joinPath(dir, info.name),
                            name = info.name,
                            isDir = isDir,
                            type = fileTypeFor(info.name, isDir),
                            sizeBytes = info.sizeBytes.coerceAtLeast(0L),
                            modifiedEpochMs = info.mtimeSecs * 1_000L,
                        )
                    }
                    .sortedWith(compareByDescending<RemoteFile> { it.isDir }.thenBy { it.name.lowercase() })
                Result.success(rows)
            }
        }

    override fun renameFile(path: String, newName: String): Result<Unit> =
        safeCall {
            rejectOnMain<Unit>()?.let { return@safeCall it }
            if (newName.isBlank()) return@safeCall Result.failure(Exception("Enter a file name"))
            if (newName.contains("/")) return@safeCall Result.failure(Exception("Name must not contain '/'"))
            withSftp { sftp ->
                if (statOrNull(sftp, path) == null) {
                    return@withSftp Result.failure(Exception("No such file: $path"))
                }
                val dest = joinPath(parentDir(path), newName.trim())
                if (statOrNull(sftp, dest) != null) {
                    return@withSftp Result.failure(Exception("A file named \"$newName\" already exists"))
                }
                try {
                    sftp.rename(path, dest)
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(Exception(firstMessage(e)))
                }
            }
        }

    override fun moveFile(srcPath: String, dstDir: String): Result<Unit> =
        safeCall {
            rejectOnMain<Unit>()?.let { return@safeCall it }
            val destDir = canonicalDir(dstDir)
            withSftp { sftp ->
                val entry = statOrNull(sftp, srcPath)
                    ?: return@withSftp Result.failure(Exception("No such file: $srcPath"))
                val destInfo = statOrNull(sftp, destDir)
                if (destInfo == null || !destInfo.isDir) {
                    return@withSftp Result.failure(Exception("No such directory: $dstDir"))
                }
                try {
                    sftp.rename(srcPath, joinPath(destDir, entry.name))
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(Exception(firstMessage(e)))
                }
            }
        }

    /**
     * Files go out with rm, empty directories with rmdir. Non-empty
     * directories are refused honestly — recursive delete is unsupported.
     */
    override fun deleteFile(path: String): Result<Unit> =
        safeCall {
            rejectOnMain<Unit>()?.let { return@safeCall it }
            withSftp { sftp ->
                val info = statOrNull(sftp, path)
                    ?: return@withSftp Result.failure(Exception("No such file: $path"))
                if (!info.isDir) {
                    return@withSftp try {
                        sftp.rm(path)
                        Result.success(Unit)
                    } catch (e: Exception) {
                        Result.failure(Exception(firstMessage(e)))
                    }
                }
                val children = try {
                    sftp.ls(path).filter { it.name != "." && it.name != ".." }
                } catch (e: Exception) {
                    return@withSftp Result.failure(Exception(firstMessage(e)))
                }
                if (children.isNotEmpty()) {
                    return@withSftp Result.failure(
                        Exception("Directory is not empty — recursive delete is not supported: $path"),
                    )
                }
                try {
                    sftp.rmdir(path)
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(Exception(firstMessage(e)))
                }
            }
        }

    // ------------------------------------------------------------------
    // Downloads (SFTP get → phone storage → DownloadStore mirror)
    // ------------------------------------------------------------------

    /** Newest-first mirror of the download queue. */
    override fun listDownloads(): Result<List<DownloadItem>> =
        safeCall {
            rejectOnMain<List<DownloadItem>>()?.let { return@safeCall it }
            Result.success(downloadMirror().items.value.reversed())
        }

    /**
     * Queues a remote file for download and mirrors the row as Downloading.
     * Extra beyond [VpsApi]: the tool calls this before [downloadToPhone]
     * once a queue affordance exists; directories are refused honestly.
     */
    fun queueDownload(sourcePath: String): Result<DownloadItem> =
        safeCall {
            rejectOnMain<DownloadItem>()?.let { return@safeCall it }
            val clean = sourcePath.trim()
            if (clean.isBlank() || clean.endsWith("/")) {
                return@safeCall Result.failure(Exception("No such file: $sourcePath"))
            }
            withSftp { sftp ->
                val info = statOrNull(sftp, clean)
                    ?: return@withSftp Result.failure(Exception("No such file: $sourcePath"))
                if (info.isDir) {
                    return@withSftp Result.failure(Exception("Cannot download a directory: $sourcePath"))
                }
                val name = clean.substringAfterLast('/')
                val item = DownloadItem(
                    id = UUID.randomUUID().toString(),
                    fileName = name,
                    type = fileTypeFor(name, false),
                    sizeBytes = info.sizeBytes.coerceAtLeast(0L),
                    downloadedBytes = 0L,
                    state = DownloadState.Downloading,
                    sourcePath = clean,
                )
                downloadMirror().upsert(item)
                Result.success(item)
            }
        }

    /**
     * Fetches the queued remote file over SFTP into cacheDir/orbit, copies
     * it into Downloads (MediaStore on API 29+, else the public Downloads
     * folder), then mirrors the row as Completed with full bytes. The
     * mirror row flips to Failed when the transfer or the copy fails.
     */
    override fun downloadToPhone(downloadId: String): Result<Unit> =
        safeCall {
            rejectOnMain<Unit>()?.let { return@safeCall it }
            val mirror = downloadMirror()
            val item = mirror.get(downloadId)
                ?: return@safeCall Result.failure(Exception("Unknown download id: $downloadId"))
            withSftp { sftp ->
                val info = statOrNull(sftp, item.sourcePath)
                if (info == null || info.isDir) {
                    mirror.upsert(item.copy(state = DownloadState.Failed))
                    return@withSftp Result.failure(Exception("No such file: ${item.sourcePath}"))
                }
                val cacheDir = File(appContext.cacheDir, "orbit")
                if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                    mirror.upsert(item.copy(state = DownloadState.Failed))
                    return@withSftp Result.failure(Exception("Could not create the local cache directory."))
                }
                val tmp = File(cacheDir, "$downloadId.part")
                try {
                    sftp.get(item.sourcePath, tmp)
                } catch (e: Exception) {
                    mirror.upsert(item.copy(state = DownloadState.Failed))
                    try {
                        tmp.delete()
                    } catch (_: Exception) {
                    }
                    return@withSftp Result.failure(Exception(firstMessage(e)))
                }
                val saved = saveToDownloads(tmp, item.fileName)
                val localBytes = try {
                    tmp.length()
                } catch (_: Exception) {
                    0L
                }
                try {
                    tmp.delete()
                } catch (_: Exception) {
                }
                if (saved.isFailure) {
                    mirror.upsert(item.copy(state = DownloadState.Failed))
                    return@withSftp Result.failure(
                        saved.exceptionOrNull() ?: Exception("Could not save the download on the phone."),
                    )
                }
                val total = maxOf(info.sizeBytes, localBytes).coerceAtLeast(0L)
                mirror.upsert(item.copy(sizeBytes = total, downloadedBytes = total, state = DownloadState.Completed))
                Result.success(Unit)
            }
        }

    // ------------------------------------------------------------------
    // Browsing: navigation, history, page state
    // ------------------------------------------------------------------

    override fun navigate(sessionId: String, rawInput: String): Result<PageInfo> =
        safeCall {
            rejectOnMain<PageInfo>()?.let { return@safeCall it }
            val session = liveOf(sessionId)
                ?: return@safeCall Result.failure(unknownSession(sessionId))
            val url = CdpInput.normalizeAddress(rawInput)
                ?: return@safeCall Result.failure(Exception("Enter a web address or search terms"))
            val navId = CdpMessages.nextId()
            val navigated = session.cdp.sendAndAwait(
                CdpMessages.navigate(navId, url),
                navId,
                NAV_TIMEOUT_MS,
            )
            if (navigated.isFailure) {
                return@safeCall Result.failure(
                    navigated.exceptionOrNull() ?: Exception("Could not open $url"),
                )
            }
            // Chrome puts DNS/TLS/connection errors in errorText rather than
            // failing the command; surface them as a readable failure.
            val errorText = navigated.getOrNull()?.optString("errorText", "").orEmpty()
            if (errorText.isNotBlank() && errorText != "net::OK") {
                return@safeCall Result.failure(Exception(humanizeNetError(errorText, url)))
            }
            synchronized(lock) {
                session.cursorX = 0.5f
                session.cursorY = 0.5f
            }
            Thread.sleep(400)
            pageInfo(sessionId)
        }

    override fun reload(sessionId: String): Result<Unit> =
        sendAwait(sessionId) { id -> CdpMessages.reload(id) }

    override fun stopLoading(sessionId: String): Result<Unit> =
        sendAwait(sessionId) { id -> CdpMessages.stopLoading(id) }

    override fun goBack(sessionId: String): Result<Unit> =
        historyMove(sessionId, "history.back()")

    override fun goForward(sessionId: String): Result<Unit> =
        historyMove(sessionId, "history.forward()")

    private fun historyMove(sessionId: String, script: String): Result<Unit> =
        safeCall {
            rejectOnMain<Unit>()?.let { return@safeCall it }
            val session = liveOf(sessionId)
                ?: return@safeCall Result.failure(unknownSession(sessionId))
            val id = CdpMessages.nextId()
            val result = session.cdp.sendAndAwait(CdpMessages.evaluateValue(id, script), id, IO_TIMEOUT_MS)
            if (result.isFailure) {
                return@safeCall Result.failure(result.exceptionOrNull() ?: Exception("History move failed"))
            }
            touchLive(sessionId)
            Result.success(Unit)
        }

    override fun pageInfo(sessionId: String): Result<PageInfo> =
        safeCall {
            rejectOnMain<PageInfo>()?.let { return@safeCall it }
            val session = liveOf(sessionId)
                ?: return@safeCall Result.failure(unknownSession(sessionId))
            val evalId = CdpMessages.nextId()
            val evaluated = session.cdp.sendAndAwait(
                CdpMessages.evaluateValue(
                    evalId,
                    "JSON.stringify({url:location.href,title:document.title," +
                        "zoom:(window.visualViewport?window.visualViewport.scale:1)})",
                ),
                evalId,
                IO_TIMEOUT_MS,
            )
            if (evaluated.isFailure) {
                return@safeCall Result.failure(
                    evaluated.exceptionOrNull() ?: Exception("Could not read the page."),
                )
            }
            val parsed = CdpMessages.parsePageInfo(evaluated.getOrNull())
                ?: return@safeCall Result.failure(Exception("The page returned no state."))
            val historyId = CdpMessages.nextId()
            val historyResult = session.cdp.sendAndAwait(
                CdpMessages.getNavigationHistory(historyId),
                historyId,
                IO_TIMEOUT_MS,
            )
            val history: HistorySnapshot = if (historyResult.isSuccess) {
                CdpMessages.parseHistory(historyResult.getOrNull())
            } else {
                HistorySnapshot()
            }
            var zoomPct = parsed.zoomPct
            synchronized(lock) {
                if (zoomPct in CdpInput.ZOOM_MIN_PCT..CdpInput.ZOOM_MAX_PCT) {
                    session.zoomPct = zoomPct
                } else {
                    zoomPct = session.zoomPct
                }
            }
            touchLive(sessionId)
            Result.success(
                parsed.copy(
                    canGoBack = history.canGoBack,
                    canGoForward = history.canGoForward,
                    zoomPct = zoomPct,
                ),
            )
        }

    // ------------------------------------------------------------------
    // Remote pointer
    // ------------------------------------------------------------------

    override fun pointerMove(sessionId: String, fx: Float, fy: Float): Result<Unit> =
        safeCall {
            val session = liveOf(sessionId)
                ?: return@safeCall Result.failure(unknownSession(sessionId))
            val point = setCursor(session, fx, fy)
            session.cdp.enqueue(
                CdpMessages.mouse(
                    CdpMessages.nextId(),
                    "mouseMoved",
                    point.first,
                    point.second,
                    buttons = session.buttonsMask.takeIf { it != 0 },
                ),
            )
            Result.success(Unit)
        }

    override fun pointerMoveRelative(sessionId: String, dfx: Float, dfy: Float): Result<Unit> =
        safeCall {
            val session = liveOf(sessionId)
                ?: return@safeCall Result.failure(unknownSession(sessionId))
            val next = synchronized(lock) {
                session.cursorX = (session.cursorX + dfx).coerceIn(0f, 1f)
                session.cursorY = (session.cursorY + dfy).coerceIn(0f, 1f)
                session.cursorX to session.cursorY
            }
            pointerMove(sessionId, next.first, next.second)
        }

    override fun pointerPress(
        sessionId: String,
        fx: Float,
        fy: Float,
        button: RemoteMouseButton,
    ): Result<Unit> =
        safeCall {
            val session = liveOf(sessionId)
                ?: return@safeCall Result.failure(unknownSession(sessionId))
            val point = setCursor(session, fx, fy)
            synchronized(lock) { session.buttonsMask = session.buttonsMask or button.bit }
            session.cdp.enqueue(
                CdpMessages.mouse(
                    CdpMessages.nextId(),
                    "mousePressed",
                    point.first,
                    point.second,
                    button = button.cdpName,
                    buttons = session.buttonsMask,
                    clickCount = 1,
                ),
            )
            Result.success(Unit)
        }

    override fun pointerRelease(
        sessionId: String,
        fx: Float,
        fy: Float,
        button: RemoteMouseButton,
    ): Result<Unit> =
        safeCall {
            val session = liveOf(sessionId)
                ?: return@safeCall Result.failure(unknownSession(sessionId))
            val point = setCursor(session, fx, fy)
            synchronized(lock) { session.buttonsMask = session.buttonsMask and button.bit.inv() }
            session.cdp.enqueue(
                CdpMessages.mouse(
                    CdpMessages.nextId(),
                    "mouseReleased",
                    point.first,
                    point.second,
                    button = button.cdpName,
                    buttons = session.buttonsMask,
                    clickCount = 1,
                ),
            )
            Result.success(Unit)
        }

    override fun click(
        sessionId: String,
        fx: Float,
        fy: Float,
        button: RemoteMouseButton,
        clickCount: Int,
    ): Result<Unit> =
        safeCall {
            val session = liveOf(sessionId)
                ?: return@safeCall Result.failure(unknownSession(sessionId))
            val point = setCursor(session, fx, fy)
            val cdp = session.cdp
            // Ordered burst: move, press, release. For right/middle click the
            // clickCount stays 1; double/triple left clicks carry the count.
            val count = clickCount.coerceIn(1, 3)
            cdp.enqueue(
                CdpMessages.mouse(
                    CdpMessages.nextId(),
                    "mouseMoved",
                    point.first,
                    point.second,
                ),
            )
            cdp.enqueue(
                CdpMessages.mouse(
                    CdpMessages.nextId(),
                    "mousePressed",
                    point.first,
                    point.second,
                    button = button.cdpName,
                    buttons = button.bit,
                    clickCount = count,
                ),
            )
            cdp.enqueue(
                CdpMessages.mouse(
                    CdpMessages.nextId(),
                    "mouseReleased",
                    point.first,
                    point.second,
                    button = button.cdpName,
                    buttons = 0,
                    clickCount = count,
                ),
            )
            touchLive(sessionId)
            Result.success(Unit)
        }

    override fun wheel(
        sessionId: String,
        fx: Float,
        fy: Float,
        deltaXPx: Double,
        deltaYPx: Double,
        ctrlKey: Boolean,
    ): Result<Unit> =
        safeCall {
            val session = liveOf(sessionId)
                ?: return@safeCall Result.failure(unknownSession(sessionId))
            val point = setCursor(session, fx, fy)
            val cdp = session.cdp
            cdp.enqueue(CdpMessages.mouse(CdpMessages.nextId(), "mouseMoved", point.first, point.second))
            cdp.enqueue(
                CdpMessages.mouse(
                    CdpMessages.nextId(),
                    "mouseWheel",
                    point.first,
                    point.second,
                    deltaX = deltaXPx,
                    deltaY = deltaYPx,
                    modifiers = if (ctrlKey) CdpMessages.MOD_CTRL else null,
                    deltaMode = 0,
                ),
            )
            touchLive(sessionId)
            Result.success(Unit)
        }

    // ------------------------------------------------------------------
    // Keyboard and zoom
    // ------------------------------------------------------------------

    override fun typeText(sessionId: String, text: String): Result<Unit> =
        safeCall {
            if (text.isEmpty()) return@safeCall Result.success(Unit)
            val session = liveOf(sessionId)
                ?: return@safeCall Result.failure(unknownSession(sessionId))
            // Fire-and-forget like a real keyboard: many long pages would
            // otherwise pay a tunnel round trip per character.
            session.cdp.enqueue(CdpMessages.insertText(CdpMessages.nextId(), text))
            touchLive(sessionId)
            Result.success(Unit)
        }

    override fun pressKey(sessionId: String, label: String): Result<Unit> =
        safeCall {
            rejectOnMain<Unit>()?.let { return@safeCall it }
            val session = liveOf(sessionId)
                ?: return@safeCall Result.failure(unknownSession(sessionId))
            val canonical = when (label) {
                "Esc" -> "Escape"
                "⌫" -> "Backspace"
                else -> label
            }
            val code = CdpInput.KEY_TABLE[canonical]
                ?: return@safeCall Result.failure(Exception("Unsupported key: $label"))
            val (key, domCode) = CdpInput.domIdentity(canonical)
            // Press+release ordered, awaited once so failures (dead target)
            // are honest instead of disappearing into the queue.
            val down = CdpMessages.nextId()
            session.cdp.enqueue(CdpMessages.keyDown(down, code, key, domCode))
            val up = CdpMessages.nextId()
            val released = session.cdp.sendAndAwait(
                CdpMessages.keyUp(up, code, key, domCode),
                up,
                IO_TIMEOUT_MS,
            )
            if (released.isFailure) {
                return@safeCall Result.failure(
                    released.exceptionOrNull() ?: Exception("Could not send the key."),
                )
            }
            touchLive(sessionId)
            Result.success(Unit)
        }

    override fun zoom(sessionId: String, steps: Int, fx: Float, fy: Float): Result<Int> =
        safeCall {
            if (steps == 0) {
                return@safeCall Result.success(liveOf(sessionId)?.zoomPct ?: 100)
            }
            val session = liveOf(sessionId)
                ?: return@safeCall Result.failure(unknownSession(sessionId))
            val newZoom = synchronized(lock) {
                val next = CdpInput.stepZoom(session.zoomPct, steps)
                session.zoomPct = next
                next
            }
            // Chrome's zoom hotkeys: Ctrl+= / Ctrl+- (one notch each). This
            // is the sequence the real-Chrome e2e probe validates; the exact
            // percentage is later corrected from visualViewport.scale.
            val cdp = session.cdp
            val count = if (steps > 0) steps else -steps
            val zoomIn = steps > 0
            val keyCode = if (zoomIn) 187 else 189
            val key = if (zoomIn) "=" else "-"
            val code = if (zoomIn) "Equal" else "Minus"
            cdp.enqueue(
                CdpMessages.keyDown(
                    CdpMessages.nextId(), 17, "Control", "ControlLeft",
                    modifiers = CdpMessages.MOD_CTRL,
                ),
            )
            repeat(count) {
                cdp.enqueue(
                    CdpMessages.keyDown(
                        CdpMessages.nextId(), keyCode, key, code,
                        modifiers = CdpMessages.MOD_CTRL,
                    ),
                )
                cdp.enqueue(
                    CdpMessages.keyUp(
                        CdpMessages.nextId(), keyCode, key, code,
                        modifiers = CdpMessages.MOD_CTRL,
                    ),
                )
            }
            val upId = CdpMessages.nextId()
            val released = cdp.sendAndAwait(
                CdpMessages.keyUp(upId, 17, "Control", "ControlLeft"),
                upId,
                IO_TIMEOUT_MS,
            )
            if (released.isFailure) {
                return@safeCall Result.failure(
                    released.exceptionOrNull() ?: Exception("Could not change zoom"),
                )
            }
            touchLive(sessionId)
            Result.success(newZoom)
        }

    override fun resetZoom(sessionId: String): Result<Int> =
        safeCall {
            rejectOnMain<Int>()?.let { return@safeCall it }
            val session = liveOf(sessionId)
                ?: return@safeCall Result.failure(unknownSession(sessionId))
            // Ctrl+0 returns zoom to 100% in Chrome.
            session.cdp.enqueue(
                CdpMessages.keyDown(
                    CdpMessages.nextId(), 17, "Control", "ControlLeft",
                    modifiers = CdpMessages.MOD_CTRL,
                ),
            )
            val up0 = CdpMessages.nextId()
            // Digit0 shortcut is dispatched as a raw (non-text) key.
            session.cdp.enqueue(
                CdpMessages.keyDown(
                    up0, 48, "0", "Digit0",
                    modifiers = CdpMessages.MOD_CTRL,
                    eventType = "rawKeyDown",
                ),
            )
            session.cdp.enqueue(
                CdpMessages.keyUp(
                    CdpMessages.nextId(), 48, "0", "Digit0",
                    modifiers = CdpMessages.MOD_CTRL,
                ),
            )
            val upCtrl = CdpMessages.nextId()
            val done = session.cdp.sendAndAwait(
                CdpMessages.keyUp(upCtrl, 17, "Control", "ControlLeft"),
                upCtrl,
                IO_TIMEOUT_MS,
            )
            if (done.isFailure) {
                return@safeCall Result.failure(done.exceptionOrNull() ?: Exception("Could not reset zoom"))
            }
            synchronized(lock) { session.zoomPct = 100 }
            Result.success(100)
        }

    override fun applyStream(sessionId: String, quality: Quality, frameRate: Int): Result<Unit> =
        safeCall {
            rejectOnMain<Unit>()?.let { return@safeCall it }
            val session = liveOf(sessionId)
                ?: return@safeCall Result.failure(unknownSession(sessionId))
            try {
                stopScreencastBestEffort(session.cdp)
            } catch (_: Exception) {
            }
            val restarted = startScreencastOn(
                session.cdp,
                quality,
                frameRate,
                session.width,
                session.height,
                IO_TIMEOUT_MS,
            )
            if (restarted.isFailure) {
                return@safeCall Result.failure(restarted.exceptionOrNull() ?: Exception("Could not retune the stream"))
            }
            synchronized(lock) {
                session.quality = quality
                session.frameRate = frameRate
                session.screencasting = true
                session.state = SessionState.Active
            }
            Result.success(Unit)
        }

    override fun saveScreenshot(sessionId: String): Result<Unit> =
        safeCall {
            rejectOnMain<Unit>()?.let { return@safeCall it }
            val session = liveOf(sessionId)
                ?: return@safeCall Result.failure(unknownSession(sessionId))
            val shotId = CdpMessages.nextId()
            val shot = session.cdp.sendAndAwait(CdpMessages.captureScreenshot(shotId, 80), shotId, IO_TIMEOUT_MS)
            if (shot.isFailure) {
                return@safeCall Result.failure(shot.exceptionOrNull() ?: Exception("Could not capture the page."))
            }
            val data = shot.getOrNull()?.optString("data", "").orEmpty()
            if (data.isEmpty()) {
                return@safeCall Result.failure(Exception("The page returned an empty screenshot."))
            }
            val bytes = try {
                Base64.decode(data, Base64.DEFAULT)
            } catch (e: Exception) {
                return@safeCall Result.failure(Exception("The screenshot bytes were corrupt."))
            }
            val saved = saveImageToPictures(bytes, "orbit-$sessionId-${System.currentTimeMillis()}.jpg")
            if (saved.isFailure) {
                return@safeCall Result.failure(
                    saved.exceptionOrNull() ?: Exception("Could not save the screenshot."),
                )
            }
            touchLive(sessionId)
            Result.success(Unit)
        }

    // ------------------------------------------------------------------
    // Input internals
    // ------------------------------------------------------------------

    /** Stores absolute fractions on the session and returns remote pixels. */
    private fun setCursor(session: LiveSession, fx: Float, fy: Float): Pair<Double, Double> {
        val clampedX = fx.coerceIn(0f, 1f)
        val clampedY = fy.coerceIn(0f, 1f)
        synchronized(lock) {
            session.cursorX = clampedX
            session.cursorY = clampedY
        }
        return CdpInput.pointFromFractions(clampedX, clampedY, session.width, session.height)
    }

    private inline fun sendAwait(
        sessionId: String,
        crossinline build: (Int) -> String,
    ): Result<Unit> = safeCall {
        rejectOnMain<Unit>()?.let { return@safeCall it }
        val session = liveOf(sessionId)
            ?: return@safeCall Result.failure(unknownSession(sessionId))
        val id = CdpMessages.nextId()
        val result = session.cdp.sendAndAwait(build(id), id, IO_TIMEOUT_MS)
        if (result.isFailure) {
            return@safeCall Result.failure(result.exceptionOrNull() ?: Exception("Command failed"))
        }
        touchLive(sessionId)
        Result.success(Unit)
    }

    private fun humanizeNetError(errorText: String, url: String): String = when {
        errorText.contains("NAME_NOT_RESOLVED", ignoreCase = true) ->
            "The server name for $url could not be found"
        errorText.contains("INTERNET_DISCONNECTED", ignoreCase = true) ||
            errorText.contains("NETWORK_CHANGED", ignoreCase = true) ->
            "The VPS has no internet connection right now"
        errorText.contains("CONNECTION_REFUSED", ignoreCase = true) ->
            "$url refused the connection"
        errorText.contains("TIMED_OUT", ignoreCase = true) ->
            "$url took too long to respond"
        errorText.contains("CERT_", ignoreCase = true) ->
            "The security certificate for $url is not trusted"
        else -> "Could not open $url ($errorText)"
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun openTarget(
        sessionId: String,
        resolution: Resolution,
        quality: Quality,
        frameRate: Int,
        deadline: Long,
    ): Result<OpenedTarget> {
        val ready = provisioner.ensure()
        if (ready.isFailure) {
            return Result.failure(ready.exceptionOrNull() ?: Exception("Chrome is not ready on the server."))
        }
        val localPort = ssh.openTunnel(REMOTE_DEBUG_PORT)
            .getOrElse { return Result.failure(it) }
        val (width, height) = CdpInput.remoteSize(resolution)
        val target = chromium.createTarget(localPort, "about:blank", width, height)
            .getOrElse {
                return Result.failure(it)
            }
        val cdp = CdpClient(okHttp)
        val connected = cdp.connectBlocking(target.wsUrl, remainingMs(deadline))
        if (connected.isFailure) {
            try {
                chromium.closeTarget(localPort, target.id)
            } catch (_: Exception) {
            }
            cdp.close()
            return Result.failure(connected.exceptionOrNull() ?: Exception("Could not reach the new target."))
        }
        cdp.setEventListener { event -> onTargetEvent(sessionId, cdp, event) }
        // Size the remote window to the requested preset before the stream
        // starts, so frames are coded at the real viewport, not scaled.
        chromium.resizeWindow(localPort, target.id, width, height)
        val prepared = try {
            prepareTarget(cdp, deadline)
        } catch (e: Exception) {
            Result.failure(Exception(firstMessage(e)))
        }
        if (prepared.isFailure) {
            try {
                chromium.closeTarget(localPort, target.id)
            } catch (_: Exception) {
            }
            cdp.close()
            return Result.failure(prepared.exceptionOrNull() ?: Exception("Could not prepare the new target."))
        }
        val tuned = startScreencastOn(cdp, quality, frameRate, width, height, remainingMs(deadline))
        if (tuned.isFailure) {
            try {
                chromium.closeTarget(localPort, target.id)
            } catch (_: Exception) {
            }
            cdp.close()
            return Result.failure(tuned.exceptionOrNull() ?: Exception("Could not start the stream."))
        }
        return Result.success(
            OpenedTarget(
                targetId = target.id,
                wsUrl = target.wsUrl,
                localPort = localPort,
                cdp = cdp,
                width = width,
                height = height,
            ),
        )
    }

    private fun prepareTarget(cdp: CdpClient, deadline: Long): Result<Unit> {
        val enableId = CdpMessages.nextId()
        val enabled = cdp.sendAndAwait(pageEnableJson(enableId), enableId, remainingMs(deadline))
        if (enabled.isFailure) return Result.failure(enabled.exceptionOrNull() ?: Exception("Page.enable failed."))
        val navId = CdpMessages.nextId()
        val navigated = cdp.sendAndAwait(CdpMessages.navigate(navId, "about:blank"), navId, remainingMs(deadline))
        if (navigated.isFailure) {
            return Result.failure(navigated.exceptionOrNull() ?: Exception("Initial navigation failed."))
        }
        return Result.success(Unit)
    }

    private fun startScreencast(session: LiveSession, timeoutMs: Long): Result<Unit> =
        startScreencastOn(session.cdp, session.quality, session.frameRate, session.width, session.height, timeoutMs)

    private fun startScreencastOn(
        cdp: CdpClient,
        quality: Quality,
        frameRate: Int,
        width: Int,
        height: Int,
        timeoutMs: Long,
    ): Result<Unit> {
        // Standalone tuning is Triple(maxWidth, quality, everyNthFrame); the
        // requested frame rate is folded into the quality preset there.
        val (maxWidth, jpegQuality, everyNth) = CdpInput.screencastParams(quality)
        val startId = CdpMessages.nextId()
        val started = cdp.sendAndAwait(
            CdpMessages.startScreencast(
                startId,
                minOf(maxWidth, width),
                height,
                jpegQuality,
                everyNth,
            ),
            startId,
            timeoutMs,
        )
        if (started.isFailure) {
            return Result.failure(started.exceptionOrNull() ?: Exception("Page.startScreencast failed."))
        }
        return Result.success(Unit)
    }

    /**
     * All unsolicited CDP events, on the socket reader thread.
     *
     * Frames: ack immediately via the ordered, non-blocking [CdpClient.enqueue]
     * (waiting for an ack reply on this same reader thread would deadlock),
     * then decode and hand the frame to the bridge. Navigation/load events
     * become [BrowserPageEvent]s so the UI can follow the real address bar.
     * Never throws and never blocks on the network.
     */
    private fun onTargetEvent(sessionId: String, cdp: CdpClient, event: CdpEvent) {
        when (event) {
            is CdpEvent.ScreencastFrame -> {
                // Ack first so the browser keeps the stream going.
                try {
                    cdp.enqueue(CdpMessages.screencastAck(CdpMessages.nextId(), event.sessionId))
                } catch (_: Exception) {
                }
                val bytes = try {
                    Base64.decode(event.dataB64, Base64.DEFAULT)
                } catch (_: Exception) {
                    return
                }
                if (bytes.isEmpty()) return
                if (!thumbs.shouldAccept(System.currentTimeMillis(), bytes.size)) return
                try {
                    onFrame(
                        sessionId,
                        LiveFrame(
                            bytes = bytes,
                            deviceWidth = event.deviceWidth,
                            deviceHeight = event.deviceHeight,
                            pageScaleFactor = event.pageScaleFactor,
                            targetId = synchronized(live) { live[sessionId]?.targetId }.orEmpty(),
                        ),
                    )
                } catch (_: Exception) {
                }
            }
            is CdpEvent.FrameNavigated -> {
                if (event.isMainFrame && event.url.isNotBlank()) {
                    safeEmitPageEvent(sessionId, BrowserPageEvent.Navigated(event.url))
                }
            }
            is CdpEvent.LoadStateChanged -> {
                safeEmitPageEvent(sessionId, BrowserPageEvent.Loading(event.loading))
            }
            is CdpEvent.Ignored -> Unit
        }
    }

    private fun safeEmitPageEvent(sessionId: String, event: BrowserPageEvent) {
        try {
            onPageEvent(sessionId, event)
        } catch (_: Exception) {
        }
    }

    /** Best-effort Page.stopScreencast for teardown paths. Never throws. */
    private fun stopScreencastBestEffort(cdp: CdpClient) {
        try {
            val stopId = CdpMessages.nextId()
            cdp.sendAndAwait(CdpMessages.stopScreencast(stopId), stopId, IO_TIMEOUT_MS)
        } catch (_: Exception) {
        }
    }

    /** Page.enable envelope: CdpMessages ships no builder for it. */
    private fun pageEnableJson(id: Int): String =
        JSONObject()
            .put("id", id)
            .put("method", "Page.enable")
            .put("params", JSONObject())
            .toString()

    private fun <T> withSftp(block: (SshSftp) -> Result<T>): Result<T> {
        val sftp = ssh.sftp().getOrElse { return Result.failure(it) }
        try {
            return block(sftp)
        } catch (e: Exception) {
            return Result.failure(Exception(firstMessage(e)))
        } finally {
            try {
                sftp.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun downloadMirror(): DownloadStore = DownloadStore.get(appContext)

    private fun liveOf(id: String): LiveSession? = synchronized(lock) { live[id] }

    private fun tombstoneOf(id: String): BrowserSession? = synchronized(lock) { tombstones[id] }

    private fun touchLive(id: String) {
        synchronized(lock) {
            live[id]?.lastSeenEpochMs = System.currentTimeMillis()
        }
    }

    private fun unknownSession(id: String): Exception =
        if (tombstoneOf(id) != null) {
            Exception("Session has ended: $id")
        } else {
            Exception("Unknown session id: $id")
        }

    private fun dialTcp(host: String, port: Int, timeoutMs: Long): String? {
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs.coerceAtLeast(1L).toInt())
            null
        } catch (e: Exception) {
            firstMessage(e)
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun remainingMs(deadline: Long): Long =
        (deadline - System.currentTimeMillis()).coerceAtLeast(1_000L)

    private fun <T> rejectOnMain(): Result<T>? {
        return try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                Result.failure(Exception("Internal: network on Main"))
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private inline fun <T> safeCall(block: () -> Result<T>): Result<T> {
        return try {
            block()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Result.failure(Exception("Interrupted: ${firstMessage(e)}"))
        } catch (e: Exception) {
            Result.failure(Exception(firstMessage(e)))
        } catch (t: Throwable) {
            Result.failure(Exception(t.message?.take(300) ?: t.javaClass.simpleName))
        }
    }

    private fun firstMessage(t: Throwable): String {
        var current: Throwable? = t
        while (current != null) {
            val message = current.message
            if (!message.isNullOrBlank()) return message.take(300)
            current = current.cause
        }
        return t.javaClass.simpleName
    }

    private fun missingAs(path: String, e: Throwable?, missingText: String): Exception {
        val detail = e?.message?.trim().orEmpty()
        return if (detail.contains("no such", ignoreCase = true) ||
            detail.contains("not found", ignoreCase = true) ||
            detail.contains("ENOENT", ignoreCase = true)
        ) {
            Exception(missingText)
        } else if (detail.isNotEmpty()) {
            Exception(detail.take(300))
        } else {
            Exception(missingText)
        }
    }

    private fun statOrNull(sftp: SshSftp, path: String): SftpEntry? =
        runCatching { sftp.stat(path) }.getOrNull()

    private fun canonicalDir(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty() || trimmed == "/") return "/"
        return trimmed.trimEnd('/')
    }

    private fun parentDir(path: String): String {
        val trimmed = path.trimEnd('/')
        val slash = trimmed.lastIndexOf('/')
        if (slash <= 0) return "/"
        return trimmed.substring(0, slash)
    }

    private fun joinPath(dir: String, name: String): String {
        val base = dir.trimEnd('/')
        return if (base.isEmpty()) "/$name" else "$base/$name"
    }

    private fun fileTypeFor(name: String, isDir: Boolean): FileType {
        if (isDir) return FileType.Folder
        return when (name.substringAfterLast('.', "").lowercase()) {
            "pdf", "txt", "md", "markdown", "doc", "docx", "odt", "rtf" -> FileType.Doc
            "mp4", "mkv", "webm", "mov", "avi", "m4v" -> FileType.Video
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic" -> FileType.Image
            "zip", "tar", "gz", "tgz", "bz2", "xz", "rar", "7z" -> FileType.Archive
            else -> FileType.Other
        }
    }

    private fun resolutionLabel(resolution: Resolution): String =
        when (resolution) {
            Resolution.P720 -> "1280 × 720"
            Resolution.P1080 -> "1920 × 1080"
            Resolution.P1440 -> "2560 × 1440"
            Resolution.Auto -> "Auto"
        }

    private fun describeOs(): String {
        return try {
            ssh.exec("uname -sr", 5_000L).getOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: "Linux"
        } catch (_: Exception) {
            "Linux"
        }
    }

    private fun mimeFor(fileName: String): String =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }

    @SuppressLint("NewApi")
    private fun saveToDownloads(tmp: File, fileName: String): Result<Unit> {
        val name = fileName.ifBlank { "orbit-download" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mimeFor(name))
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Orbit")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = appContext.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: return Result.failure(Exception("The phone refused the download (MediaStore insert failed)."))
                try {
                    val out = appContext.contentResolver.openOutputStream(uri)
                        ?: throw IOException("Could not open the download destination.")
                    out.use { sink -> tmp.inputStream().use { source -> source.copyTo(sink) } }
                } catch (e: Exception) {
                    try {
                        appContext.contentResolver.delete(uri, null, null)
                    } catch (_: Exception) {
                    }
                    throw e
                }
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                appContext.contentResolver.update(uri, done, null, null)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(Exception(firstMessage(e)))
            }
        }
        return saveToPublicDir(tmp, name, Environment.DIRECTORY_DOWNLOADS)
    }

    @SuppressLint("NewApi")
    @Suppress("DEPRECATION")
    private fun saveImageToPictures(bytes: ByteArray, fileName: String): Result<Unit> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Orbit")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = appContext.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: return Result.failure(Exception("The phone refused the screenshot (MediaStore insert failed)."))
                try {
                    val out = appContext.contentResolver.openOutputStream(uri)
                        ?: throw IOException("Could not open the screenshot destination.")
                    out.use { it.write(bytes) }
                } catch (e: Exception) {
                    try {
                        appContext.contentResolver.delete(uri, null, null)
                    } catch (_: Exception) {
                    }
                    throw e
                }
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                appContext.contentResolver.update(uri, done, null, null)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(Exception(firstMessage(e)))
            }
        }
        return try {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Orbit",
            )
            if (!dir.exists() && !dir.mkdirs()) {
                return Result.failure(Exception("Could not create the Pictures/Orbit folder."))
            }
            File(dir, uniqueName(dir, fileName)).writeBytes(bytes)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(firstMessage(e)))
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToPublicDir(tmp: File, fileName: String, type: String): Result<Unit> {
        return try {
            val dir = File(Environment.getExternalStoragePublicDirectory(type), "Orbit")
            if (!dir.exists() && !dir.mkdirs()) {
                return Result.failure(Exception("Could not create the Downloads/Orbit folder."))
            }
            tmp.copyTo(File(dir, uniqueName(dir, fileName)), overwrite = true)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(firstMessage(e)))
        }
    }

    private fun uniqueName(dir: File, fileName: String): String {
        if (!File(dir, fileName).exists()) return fileName
        val dot = fileName.lastIndexOf('.')
        val stem = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var index = 2
        while (File(dir, "$stem ($index)$ext").exists()) index += 1
        return "$stem ($index)$ext"
    }

    companion object {
        private const val TEST_TIMEOUT_MS = 15_000L
        private const val LAUNCH_TIMEOUT_MS = 60_000L
        private const val NAV_TIMEOUT_MS = 20_000L
        private const val IO_TIMEOUT_MS = 10_000L
        private const val REMOTE_DEBUG_PORT = 9222
        private const val ECHO_TOKEN = "orbit-ok"
        private const val ECHO_PROBE = "echo orbit-ok"
        private const val CONNECTION_LABEL = "SSH tunnel"
    }
}
