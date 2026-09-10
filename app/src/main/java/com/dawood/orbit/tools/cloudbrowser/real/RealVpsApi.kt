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
import com.dawood.orbit.tools.cloudbrowser.BrowserSession
import com.dawood.orbit.tools.cloudbrowser.CdpEvent
import com.dawood.orbit.tools.cloudbrowser.CdpInput
import com.dawood.orbit.tools.cloudbrowser.CdpMessages
import com.dawood.orbit.tools.cloudbrowser.DownloadItem
import com.dawood.orbit.tools.cloudbrowser.DownloadState
import com.dawood.orbit.tools.cloudbrowser.DownloadStore
import com.dawood.orbit.tools.cloudbrowser.FileType
import com.dawood.orbit.tools.cloudbrowser.FrameThrottle
import com.dawood.orbit.tools.cloudbrowser.Quality
import com.dawood.orbit.tools.cloudbrowser.RemoteFile
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
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.sftp.SFTPClient
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
    private val onFrame: (sessionId: String, bytes: ByteArray) -> Unit = { _, _ -> },
) : VpsApi {

    private val appContext: Context = appCtx.applicationContext
    private val lock = Any()
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
            synchronized(lock) { currentServerId = server.id }
            Result.success(Unit)
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
     * Launches a sized Chromium target and starts its screencast within a
     * 30 s budget: ensureRunning → openTunnel → createTarget(about:blank,
     * w×h) → connect target WS → Page.enable → navigate about:blank →
     * startScreencast (cadence/quality from the presets) → Active row.
     * Firefox has no CDP endpoint and fails honestly.
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
                        val isDir = info.attributes.isDirectory
                        RemoteFile(
                            path = joinPath(dir, info.name),
                            name = info.name,
                            isDir = isDir,
                            type = fileTypeFor(info.name, isDir),
                            sizeBytes = info.attributes.size.coerceAtLeast(0L),
                            modifiedEpochMs = info.attributes.mtime.toLong() * 1_000L,
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
                if (destInfo == null || !destInfo.attributes.isDirectory) {
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
                if (!info.attributes.isDirectory) {
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
                if (info.attributes.isDirectory) {
                    return@withSftp Result.failure(Exception("Cannot download a directory: $sourcePath"))
                }
                val name = clean.substringAfterLast('/')
                val item = DownloadItem(
                    id = UUID.randomUUID().toString(),
                    fileName = name,
                    type = fileTypeFor(name, false),
                    sizeBytes = info.attributes.size.coerceAtLeast(0L),
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
                if (info == null || info.attributes.isDirectory) {
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
                val total = maxOf(info.attributes.size, localBytes).coerceAtLeast(0L)
                mirror.upsert(item.copy(sizeBytes = total, downloadedBytes = total, state = DownloadState.Completed))
                Result.success(Unit)
            }
        }

    // ------------------------------------------------------------------
    // Remote input (extra beyond VpsApi; the tool calls these directly)
    // ------------------------------------------------------------------

    /**
     * Sends one overlay key label to the session: KEY_TABLE labels go out
     * as keyDown/keyUp, any other single character goes out as insertText.
     * Anything else fails honestly. Blocking; call on Dispatchers.IO.
     */
    fun sendKey(sessionId: String, keyLabel: String): Result<Unit> =
        safeCall {
            rejectOnMain<Unit>()?.let { return@safeCall it }
            val session = liveOf(sessionId)
                ?: return@safeCall Result.failure(unknownSession(sessionId))
            // Overlay aliases for the standalone KEY_TABLE names.
            val canonical = when (keyLabel) {
                "Esc" -> "Escape"
                "⌫" -> "Backspace"
                else -> keyLabel
            }
            val windowsCode = CdpInput.KEY_TABLE[canonical]
            if (windowsCode != null) {
                val downId = CdpMessages.nextId()
                val down = session.cdp.sendAndAwait(
                    CdpMessages.keyDown(downId, windowsCode, canonical, canonical),
                    downId,
                    IO_TIMEOUT_MS,
                )
                if (down.isFailure) {
                    return@safeCall Result.failure(down.exceptionOrNull() ?: Exception("Could not send the key."))
                }
                val upId = CdpMessages.nextId()
                val up = session.cdp.sendAndAwait(
                    CdpMessages.keyUp(upId, windowsCode, canonical, canonical),
                    upId,
                    IO_TIMEOUT_MS,
                )
                if (up.isFailure) {
                    return@safeCall Result.failure(up.exceptionOrNull() ?: Exception("Could not send the key."))
                }
                touchLive(sessionId)
                return@safeCall Result.success(Unit)
            }
            if (keyLabel.length == 1) {
                val insertId = CdpMessages.nextId()
                val inserted = session.cdp.sendAndAwait(CdpMessages.insertText(insertId, keyLabel), insertId, IO_TIMEOUT_MS)
                if (inserted.isFailure) {
                    return@safeCall Result.failure(
                        inserted.exceptionOrNull() ?: Exception("Could not send the key."),
                    )
                }
                touchLive(sessionId)
                return@safeCall Result.success(Unit)
            }
            Result.failure(Exception("Unsupported key: $keyLabel"))
        }

    /**
     * Moves the remote pointer by a touchpad drag. The per-session cursor
     * is tracked in 0..1 fractions (a full-width drag crosses the screen)
     * and mapped through pointFromFractions. Blocking; call on
     * Dispatchers.IO.
     */
    fun sendPointerMove(sessionId: String, dxPixels: Float, dyPixels: Float): Result<Unit> =
        safeCall {
            rejectOnMain<Unit>()?.let { return@safeCall it }
            val session = liveOf(sessionId)
                ?: return@safeCall Result.failure(unknownSession(sessionId))
            val point: Pair<Double, Double>
            synchronized(lock) {
                session.cursorX = (session.cursorX + dxPixels / DRAG_FULL_WIDTH_PX).coerceIn(0f, 1f)
                session.cursorY = (session.cursorY + dyPixels / DRAG_FULL_WIDTH_PX).coerceIn(0f, 1f)
                point = CdpInput.pointFromFractions(session.cursorX, session.cursorY, session.width, session.height)
            }
            val moveId = CdpMessages.nextId()
            val moved = session.cdp.sendAndAwait(
                CdpMessages.mouse(moveId, "mouseMoved", point.first, point.second),
                moveId,
                IO_TIMEOUT_MS,
            )
            if (moved.isFailure) {
                return@safeCall Result.failure(moved.exceptionOrNull() ?: Exception("Could not move the pointer."))
            }
            touchLive(sessionId)
            Result.success(Unit)
        }

    /**
     * Handles overlay toolbar actions beyond Refresh/Zoom (which the tool
     * handles locally): Back navigates the remote page back, Screenshot
     * captures the page into Pictures. Anything else fails honestly.
     * Blocking; call on Dispatchers.IO.
     */
    fun sendToolbarAction(sessionId: String, action: String): Result<Unit> =
        safeCall {
            rejectOnMain<Unit>()?.let { return@safeCall it }
            val session = liveOf(sessionId)
                ?: return@safeCall Result.failure(unknownSession(sessionId))
            when (action) {
                "Back" -> {
                    val backId = CdpMessages.nextId()
                    val back = session.cdp.sendAndAwait(CdpMessages.evaluate(backId, "history.back()"), backId, IO_TIMEOUT_MS)
                    if (back.isFailure) {
                        return@safeCall Result.failure(back.exceptionOrNull() ?: Exception("Could not go back."))
                    }
                    touchLive(sessionId)
                    Result.success(Unit)
                }
                "Screenshot" -> {
                    val shotId = CdpMessages.nextId()
                    val shot = session.cdp.sendAndAwait(CdpMessages.captureScreenshot(shotId), shotId, IO_TIMEOUT_MS)
                    if (shot.isFailure) {
                        return@safeCall Result.failure(
                            shot.exceptionOrNull() ?: Exception("Could not capture the page."),
                        )
                    }
                    val data = shot.getOrNull()?.optString("data", "").orEmpty()
                    if (data.isNullOrEmpty()) {
                        return@safeCall Result.failure(Exception("The page returned an empty screenshot."))
                    }
                    val bytes = try {
                        Base64.decode(data, Base64.DEFAULT)
                    } catch (e: Exception) {
                        return@safeCall Result.failure(Exception("The screenshot bytes were corrupt."))
                    }
                    val saved = saveImageToPictures(bytes, "orbit-$sessionId.jpg")
                    if (saved.isFailure) {
                        return@safeCall Result.failure(
                            saved.exceptionOrNull() ?: Exception("Could not save the screenshot."),
                        )
                    }
                    touchLive(sessionId)
                    Result.success(Unit)
                }
                else -> Result.failure(Exception("$action is not available on this backend"))
            }
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
        val ensured = chromium.ensureRunning()
        if (ensured.isFailure) return Result.failure(ensured.exceptionOrNull() ?: Exception("Chromium is not running."))
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
        cdp.setEventListener { event -> onScreencastEvent(sessionId, cdp, event) }
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
     * Frame path, on the socket reader thread: ack first so the browser
     * keeps streaming, then decode, throttle, and deliver. Never throws and
     * never blocks on network.
     */
    private fun onScreencastEvent(sessionId: String, cdp: CdpClient, event: CdpEvent) {
        val frame = event as? CdpEvent.ScreencastFrame ?: return
        ackAsync(cdp, frame.sessionId)
        val bytes = try {
            Base64.decode(frame.dataB64, Base64.DEFAULT)
        } catch (_: Exception) {
            return
        }
        if (bytes.isEmpty()) return
        if (!thumbs.shouldAccept(System.currentTimeMillis(), bytes.size)) return
        try {
            onFrame(sessionId, bytes)
        } catch (_: Exception) {
        }
    }

    /**
     * Fire-and-forget screencast ack on a daemon thread. The standalone
     * CdpClient only offers blocking sendAndAwait, which must never run on
     * the socket reader thread (the reply arrives on that same thread), so
     * the ack goes out from a short-lived thread instead. Best effort.
     */
    private fun ackAsync(cdp: CdpClient, screencastSessionId: Int) {
        try {
            Thread {
                try {
                    val ackId = CdpMessages.nextId()
                    cdp.sendAndAwait(CdpMessages.screencastAck(ackId, screencastSessionId), ackId, IO_TIMEOUT_MS)
                } catch (_: Exception) {
                }
            }.apply { isDaemon = true; start() }
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

    private fun <T> withSftp(block: (SFTPClient) -> Result<T>): Result<T> {
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

    private fun statOrNull(sftp: SFTPClient, path: String): RemoteResourceInfo? =
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
        private const val LAUNCH_TIMEOUT_MS = 30_000L
        private const val IO_TIMEOUT_MS = 10_000L
        private const val REMOTE_DEBUG_PORT = 9222
        private const val ECHO_TOKEN = "orbit-ok"
        private const val ECHO_PROBE = "echo orbit-ok"
        private const val CONNECTION_LABEL = "SSH tunnel"
        private const val DRAG_FULL_WIDTH_PX = 1200f
    }
}
