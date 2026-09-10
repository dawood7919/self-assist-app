package com.dawood.orbit.tools.cloudbrowser

/**
 * Deterministic in-memory [VpsApi] for demos, screenshots and JVM unit tests.
 *
 * Pure Kotlin: no network code, no secrets, no real hosts. Every canned value
 * is marked with a DEMO comment, all metrics report isLive=false, latency is
 * always the fixed 42 ms, and unknown ids fail with a clear message instead
 * of inventing data.
 */
class FakeVpsApi : VpsApi {

    companion object {
        /** Fixed demo latency; keeps tests and screenshots stable. */
        const val DEMO_LATENCY_MS = 42L
        const val DEMO_SERVER_ID = "demo-server"
        const val DEMO_DOWNLOADS_DIR = "/home/user/downloads"
    }

    private var connectedServerId: String? = null
    private var metricsTick = 0
    private var nextSessionNumber = 3

    // DEMO: two canned sessions shown before the user launches anything real.
    private val sessions = mutableListOf(
        BrowserSession(
            id = "demo-session-01",
            serverId = DEMO_SERVER_ID,
            browser = BrowserKind.Chrome,
            profile = "Default",
            resolution = Resolution.P1080,
            quality = Quality.Balanced,
            frameRate = 30,
            state = SessionState.Active,
            startedAtEpochMs = 1_700_000_000_000L,
            lastSeenEpochMs = 1_700_000_000_000L,
        ),
        // DEMO: an idle second session so the sessions screen is never empty.
        BrowserSession(
            id = "demo-session-02",
            serverId = DEMO_SERVER_ID,
            browser = BrowserKind.Chromium,
            profile = "Work",
            resolution = Resolution.P720,
            quality = Quality.Balanced,
            frameRate = 30,
            state = SessionState.Idle,
            startedAtEpochMs = 1_700_000_100_000L,
            lastSeenEpochMs = 1_700_000_100_000L,
        ),
    )

    // DEMO: canned directory listings; the host below is masked, not a real IP.
    private val files = mutableMapOf(
        "/" to mutableListOf(
            RemoteFile(path = "/home", name = "home", isDir = true, type = FileType.Folder),
        ),
        "/home" to mutableListOf(
            RemoteFile(path = "/home/user", name = "user", isDir = true, type = FileType.Folder),
        ),
        "/home/user" to mutableListOf(
            RemoteFile(path = DEMO_DOWNLOADS_DIR, name = "downloads", isDir = true, type = FileType.Folder),
            RemoteFile(
                path = "/home/user/notes.txt",
                name = "notes.txt",
                isDir = false,
                type = FileType.Doc,
                sizeBytes = 4_096L,
            ),
        ),
        // DEMO: one of each download filter kind so every chip matches something.
        DEMO_DOWNLOADS_DIR to mutableListOf(
            RemoteFile(
                path = "$DEMO_DOWNLOADS_DIR/example.pdf",
                name = "example.pdf",
                isDir = false,
                type = FileType.Doc,
                sizeBytes = 2_400_000L,
            ),
            RemoteFile(
                path = "$DEMO_DOWNLOADS_DIR/movie.mp4",
                name = "movie.mp4",
                isDir = false,
                type = FileType.Video,
                sizeBytes = 800_000_000L,
            ),
            RemoteFile(
                path = "$DEMO_DOWNLOADS_DIR/photo.jpg",
                name = "photo.jpg",
                isDir = false,
                type = FileType.Image,
                sizeBytes = 3_100_000L,
            ),
            RemoteFile(
                path = "$DEMO_DOWNLOADS_DIR/archive.zip",
                name = "archive.zip",
                isDir = false,
                type = FileType.Archive,
                sizeBytes = 120_000_000L,
            ),
        ),
    )

    // DEMO: a finished and an in-progress download for the queue screen.
    private val downloads = mutableListOf(
        DownloadItem(
            id = "demo-download-01",
            fileName = "example.pdf",
            type = FileType.Doc,
            sizeBytes = 2_400_000L,
            downloadedBytes = 2_400_000L,
            state = DownloadState.Completed,
            sourcePath = "$DEMO_DOWNLOADS_DIR/example.pdf",
        ),
        DownloadItem(
            id = "demo-download-02",
            fileName = "movie.mp4",
            type = FileType.Video,
            sizeBytes = 800_000_000L,
            downloadedBytes = 320_000_000L,
            state = DownloadState.Downloading,
            sourcePath = "$DEMO_DOWNLOADS_DIR/movie.mp4",
        ),
    )

    // ------------------------------------------------------------------
    // Connection
    // ------------------------------------------------------------------

    override fun testConnection(server: SavedServer): Result<Long> {
        if (server.host.isBlank()) return Result.failure(IllegalArgumentException("Enter a host name or IP address"))
        // DEMO: no packets leave the phone; every host answers in 42 ms.
        return Result.success(DEMO_LATENCY_MS)
    }

    override fun connect(server: SavedServer): Result<Unit> {
        if (server.host.isBlank()) return Result.failure(IllegalArgumentException("Enter a host name or IP address"))
        // DEMO: remember the id so pollMetrics can reject ids we never saw.
        connectedServerId = server.id.ifBlank { DEMO_SERVER_ID }
        return Result.success(Unit)
    }

    override fun disconnect(): Result<Unit> {
        connectedServerId = null
        return Result.success(Unit)
    }

    // ------------------------------------------------------------------
    // Sessions
    // ------------------------------------------------------------------

    override fun listSessions(): Result<List<BrowserSession>> =
        Result.success(sessions.sortedByDescending { it.lastSeenEpochMs })

    override fun launchSession(
        serverId: String,
        browser: BrowserKind,
        profile: String,
        resolution: Resolution,
        quality: Quality,
        frameRate: Int,
        timeoutSecs: Int,
    ): Result<BrowserSession> {
        val session = BrowserSession(
            // DEMO: sequential ids keep launches deterministic across test runs.
            id = "demo-session-%02d".format(nextSessionNumber++),
            serverId = serverId,
            browser = browser,
            profile = profile.ifBlank { "Default" },
            resolution = resolution,
            quality = quality,
            frameRate = frameRate,
            state = SessionState.Active,
            startedAtEpochMs = System.currentTimeMillis(),
            lastSeenEpochMs = System.currentTimeMillis(),
        )
        sessions.add(session)
        return Result.success(session)
    }

    override fun pauseSession(id: String): Result<Unit> {
        val session = sessions.firstOrNull { it.id == id }
            ?: return Result.failure(IllegalArgumentException("Unknown session id: $id"))
        if (session.state == SessionState.Ended) {
            return Result.failure(IllegalStateException("Session has ended and cannot be paused"))
        }
        replace(session.copy(state = SessionState.Paused, lastSeenEpochMs = System.currentTimeMillis()))
        return Result.success(Unit)
    }

    override fun resumeSession(id: String): Result<Unit> {
        val session = sessions.firstOrNull { it.id == id }
            ?: return Result.failure(IllegalArgumentException("Unknown session id: $id"))
        if (session.state == SessionState.Ended) {
            return Result.failure(IllegalStateException("Session has ended and cannot be resumed"))
        }
        replace(session.copy(state = SessionState.Active, lastSeenEpochMs = System.currentTimeMillis()))
        return Result.success(Unit)
    }

    override fun closeSession(id: String): Result<Unit> {
        val session = sessions.firstOrNull { it.id == id }
            ?: return Result.failure(IllegalArgumentException("Unknown session id: $id"))
        // DEMO: keep the row as Ended so restart-after-close stays possible.
        replace(session.copy(state = SessionState.Ended, lastSeenEpochMs = System.currentTimeMillis()))
        return Result.success(Unit)
    }

    override fun restartSession(id: String): Result<Unit> {
        val session = sessions.firstOrNull { it.id == id }
            ?: return Result.failure(IllegalArgumentException("Unknown session id: $id"))
        replace(session.copy(state = SessionState.Active, lastSeenEpochMs = System.currentTimeMillis()))
        return Result.success(Unit)
    }

    private fun replace(session: BrowserSession) {
        val index = sessions.indexOfFirst { it.id == session.id }
        if (index >= 0) sessions[index] = session
    }

    // ------------------------------------------------------------------
    // Metrics
    // ------------------------------------------------------------------

    override fun pollMetrics(serverId: String): Result<VpsMetrics> {
        if (serverId != DEMO_SERVER_ID && serverId != connectedServerId) {
            return Result.failure(IllegalArgumentException("Unknown server id: $serverId"))
        }
        metricsTick++
        // DEMO: values walk deterministically so tests and screenshots are stable.
        return Result.success(
            VpsMetrics(
                cpuPct = (18 + metricsTick % 5).toFloat(),
                ramUsedGb = 2.4 + (metricsTick % 3) * 0.1,
                ramTotalGb = 8.0,
                netDownMbps = 245.0 + (metricsTick % 4),
                netUpMbps = 87.0 + (metricsTick % 3),
                diskUsedGb = 42.0,
                diskTotalGb = 100.0,
                uptimeSecs = 12L * 86_400L + metricsTick,
                latencyMs = DEMO_LATENCY_MS,
                bandwidthMbps = 1_000.0,
                // DEMO: never live — the UI must not badge this as real telemetry.
                isLive = false,
            ),
        )
    }

    override fun pollSessionDetails(sessionId: String): Result<SessionDetails> {
        val session = sessions.firstOrNull { it.id == sessionId }
            ?: return Result.failure(IllegalArgumentException("Unknown session id: $sessionId"))
        // DEMO: plausible fixed details for the canned sessions.
        return Result.success(
            SessionDetails(
                sessionId = session.id,
                browser = session.browser.name,
                os = "Ubuntu 24.04",
                durationSecs = 5_075L,
                resolution = "1920 × 1080",
                connection = "Demo relay",
                latencyMs = DEMO_LATENCY_MS,
                bandwidthMbps = 24.0,
            ),
        )
    }

    // ------------------------------------------------------------------
    // Files
    // ------------------------------------------------------------------

    override fun listFiles(path: String): Result<List<RemoteFile>> {
        val dir = normaliseDir(path)
        return files[dir]?.let { Result.success(it.toList()) }
            ?: Result.failure(IllegalArgumentException("No such directory: $path"))
    }

    override fun renameFile(path: String, newName: String): Result<Unit> {
        if (newName.isBlank()) return Result.failure(IllegalArgumentException("Enter a file name"))
        if (newName.contains("/")) return Result.failure(IllegalArgumentException("Name must not contain '/'"))
        val entry = findFile(path)
            ?: return Result.failure(IllegalArgumentException("No such file: $path"))
        val dir = dirOf(path)
        val siblings = files.getOrPut(dir) { mutableListOf() }
        if (siblings.any { it.name == newName }) {
            return Result.failure(IllegalStateException("A file named \"$newName\" already exists"))
        }
        siblings.removeAll { it.path == entry.path }
        siblings.add(entry.copy(name = newName, path = "$dir/$newName"))
        return Result.success(Unit)
    }

    override fun moveFile(srcPath: String, dstDir: String): Result<Unit> {
        val entry = findFile(srcPath)
            ?: return Result.failure(IllegalArgumentException("No such file: $srcPath"))
        val target = normaliseDir(dstDir)
        if (!files.containsKey(target)) {
            return Result.failure(IllegalArgumentException("No such directory: $dstDir"))
        }
        files.getOrPut(dirOf(srcPath)) { mutableListOf() }.removeAll { it.path == entry.path }
        files.getOrPut(target) { mutableListOf() }.add(entry.copy(path = "$target/${entry.name}"))
        return Result.success(Unit)
    }

    override fun deleteFile(path: String): Result<Unit> {
        val entry = findFile(path)
            ?: return Result.failure(IllegalArgumentException("No such file: $path"))
        files.getOrPut(dirOf(path)) { mutableListOf() }.removeAll { it.path == entry.path }
        return Result.success(Unit)
    }

    private fun findFile(path: String): RemoteFile? =
        files.values.flatten().firstOrNull { it.path == path }

    private fun dirOf(path: String): String {
        val trimmed = path.trimEnd('/')
        val slash = trimmed.lastIndexOf('/')
        return if (slash <= 0) "/" else trimmed.substring(0, slash)
    }

    private fun normaliseDir(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty() || trimmed == "/") return "/"
        return trimmed.trimEnd('/')
    }

    // ------------------------------------------------------------------
    // Downloads
    // ------------------------------------------------------------------

    override fun listDownloads(): Result<List<DownloadItem>> =
        Result.success(downloads.toList())

    override fun downloadToPhone(downloadId: String): Result<Unit> {
        val index = downloads.indexOfFirst { it.id == downloadId }
        if (index < 0) return Result.failure(IllegalArgumentException("Unknown download id: $downloadId"))
        // DEMO: honestly complete the canned row instead of pretending a transfer.
        val item = downloads[index]
        downloads[index] = item.copy(downloadedBytes = item.sizeBytes, state = DownloadState.Completed)
        return Result.success(Unit)
    }
}
