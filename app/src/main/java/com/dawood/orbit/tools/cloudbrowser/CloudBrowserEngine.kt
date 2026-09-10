package com.dawood.orbit.tools.cloudbrowser

/**
 * Pure presentation and validation logic for the Cloud Browser tool.
 *
 * Pure Kotlin — no Android / Compose imports — so it is covered by JVM unit
 * tests in CI. The composable collects input and shows results; it never does
 * arithmetic, parsing or filtering itself.
 *
 * Demo honesty: every label produced with isDemo=true is prefixed with
 * "Demo" and never claims the connection is Secure, Live or Encrypted.
 */
object CloudBrowserEngine {

    /** Actions the UI may offer for a session in a given state. */
    enum class SessionAction { Open, Pause, Resume, Close }

    // ------------------------------------------------------------------
    // Legacy snapshot API (kept for CloudBrowserTool compatibility).
    // New code should use SavedServer / VpsMetrics / connectionLabel below.
    // ------------------------------------------------------------------

    /** Pure presentation data for the remote browser dashboard. */
    data class ServerSnapshot(
        val name: String = "My VPS Server",
        val location: String = "Germany",
        val ip: String = "185.xxx.xxx.xxx",
        val latency: String = "42 ms",
        val cpu: Int = 18,
        val ramUsedGb: Double = 2.4,
        val ramTotalGb: Double = 8.0,
        val bandwidth: String = "1 Gbps",
    )

    /** Legacy overload: delegates to the NaN-safe [ramProgress] below. */
    fun ramProgress(snapshot: ServerSnapshot): Float =
        ramProgress(snapshot.ramUsedGb, snapshot.ramTotalGb)

    /** Legacy overload: keeps the dashboard subtitle working. */
    fun connectionLabel(snapshot: ServerSnapshot): String =
        "${snapshot.location} • ${snapshot.latency}"

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    /**
     * Validates the connect form. Returns field name to error message; empty
     * means the form is valid. Keys: name, host, port, username, keyPath.
     */
    fun validateServer(
        name: String,
        host: String,
        port: Int,
        username: String,
        auth: AuthMethod,
        keyPath: String?,
    ): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (name.isBlank()) errors["name"] = "Enter a name for this server"
        if (host.isBlank()) errors["host"] = "Enter a host name or IP address"
        if (port !in 1..65_535) errors["port"] = "Port must be 1–65535"
        if (username.isBlank()) errors["username"] = "Enter a username"
        if (auth == AuthMethod.SshKey && keyPath.isNullOrBlank()) {
            errors["keyPath"] = "Choose a private key file for key authentication"
        }
        return errors
    }

    /** Parses a raw port field: trims, requires digits, requires 1..65535. */
    fun parsePort(raw: String): Int? {
        val port = raw.trim().toIntOrNull() ?: return null
        return if (port in 1..65_535) port else null
    }

    // ------------------------------------------------------------------
    // Labels (demo-honest)
    // ------------------------------------------------------------------

    /**
     * Status line for the connection badge.
     *
     * When [isDemo] is true the result always starts with "Demo" and never
     * contains "Secure", "Live" or "Encrypted": demo numbers must not be
     * presented as a real protected connection.
     */
    fun connectionLabel(state: ConnectionState, latencyMs: Long?, isDemo: Boolean): String {
        if (isDemo) {
            return when (state) {
                ConnectionState.Disconnected -> "Demo • Not connected"
                ConnectionState.Testing -> "Demo • Testing…"
                ConnectionState.Connecting -> "Demo • Connecting…"
                ConnectionState.Connected ->
                    if (latencyMs != null) "Demo • $latencyMs ms" else "Demo • Connected"
                ConnectionState.Error -> "Demo • Connection failed"
            }
        }
        return when (state) {
            ConnectionState.Disconnected -> "Not connected"
            ConnectionState.Testing -> "Testing…"
            ConnectionState.Connecting -> "Connecting…"
            ConnectionState.Connected ->
                if (latencyMs != null) "$latencyMs ms" else "Connected"
            ConnectionState.Error -> "Connection failed"
        }
    }

    // ------------------------------------------------------------------
    // Formatting
    // ------------------------------------------------------------------

    /**
     * Fraction of RAM in use, clamped to 0..1. Returns 0f when [total] is
     * zero/negative or either side is NaN/infinite (NaN guard: NaN would
     * otherwise survive coerceIn and break progress bars).
     */
    fun ramProgress(used: Double, total: Double): Float {
        if (total <= 0.0 || used.isNaN() || total.isNaN()) return 0f
        if (used.isInfinite() || total.isInfinite()) return 0f
        return (used / total).toFloat().coerceIn(0f, 1f)
    }

    /** Human byte count: 512 B, 1.5 KB, 2.4 GB. Negative input reads as 0 B. */
    fun formatBytes(bytes: Long): String {
        val value = bytes.coerceAtLeast(0L).toDouble()
        if (value < 1024.0) return "${value.toLong()} B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var scaled = value / 1024.0
        var unit = 0
        while (scaled >= 1024.0 && unit < units.lastIndex) {
            scaled /= 1024.0
            unit++
        }
        val text = if (scaled >= 100.0) scaled.toLong().toString() else trimTrailingZero(scaled)
        return "$text ${units[unit]}"
    }

    private fun trimTrailingZero(value: Double): String =
        String.format(java.util.Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')

    /** Short duration: 45s, 12m, 3h 12m, 3h. */
    fun formatDuration(seconds: Long): String {
        val total = seconds.coerceAtLeast(0L)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 && secs > 0 -> "${minutes}m ${secs}s"
            minutes > 0 -> "${minutes}m"
            else -> "${secs}s"
        }
    }

    /** Uptime with a day bucket: 12 days, 1 day, otherwise [formatDuration]. */
    fun formatUptime(seconds: Long): String {
        val total = seconds.coerceAtLeast(0L)
        val days = total / 86_400
        return when {
            days > 1 -> "$days days"
            days == 1L -> "1 day"
            else -> formatDuration(total)
        }
    }

    /** Clamps browser zoom to the 75..150 % range the streamer supports. */
    fun clampZoom(zoomPct: Int): Int = zoomPct.coerceIn(75, 150)

    // ------------------------------------------------------------------
    // Lists
    // ------------------------------------------------------------------

    /** Filters the download queue by the selected chip. */
    fun filterDownloads(items: List<DownloadItem>, filter: DownloadFilter): List<DownloadItem> =
        when (filter) {
            DownloadFilter.All -> items
            DownloadFilter.Docs -> items.filter { it.type == FileType.Doc }
            DownloadFilter.Videos -> items.filter { it.type == FileType.Video }
            DownloadFilter.Images -> items.filter { it.type == FileType.Image }
            DownloadFilter.Archives -> items.filter { it.type == FileType.Archive }
        }

    /**
     * Filters remote files by [query] (case-insensitive name match, blank
     * matches everything) and sorts directories first, then alphabetically.
     */
    fun filterFiles(files: List<RemoteFile>, query: String = ""): List<RemoteFile> {
        val q = query.trim().lowercase()
        return files
            .filter { q.isEmpty() || it.name.lowercase().contains(q) }
            .sortedWith(compareByDescending<RemoteFile> { it.isDir }.thenBy { it.name.lowercase() })
    }

    /**
     * Splits a remote path into breadcrumb names. Root ("/" or blank) has no
     * segments; trailing slashes are ignored: "/a/b/" -> ["a", "b"].
     */
    fun breadcrumbSegments(path: String): List<String> =
        path.split("/").map { it.trim() }.filter { it.isNotEmpty() }

    /** Which actions the UI offers for a session in [state]. */
    fun sessionActions(state: SessionState): List<SessionAction> =
        when (state) {
            SessionState.Active -> listOf(SessionAction.Open, SessionAction.Pause, SessionAction.Close)
            SessionState.Idle -> listOf(SessionAction.Open, SessionAction.Close)
            SessionState.Paused -> listOf(SessionAction.Resume, SessionAction.Close)
            SessionState.Ended -> listOf(SessionAction.Close)
        }

    /**
     * Poll backoff: 1s with no failures, doubling per failure, capped at 8s.
     * Negative failure counts are treated as zero.
     */
    fun nextPollDelayMs(failures: Int): Long {
        if (failures <= 0) return 1_000L
        var delay = 1_000L
        repeat(minOf(failures, 3)) { delay *= 2 }
        return delay.coerceAtMost(8_000L)
    }

    // ------------------------------------------------------------------
    // Shared option lists and labels (pure, additive — no behavior change
    // to any function above; UI screens share these instead of triplicating
    // literals).
    // ------------------------------------------------------------------

    /** Stream frame rates offered by New Session, Settings and controls sheet. */
    val FrameRates: List<Int> = listOf(15, 30, 60)

    /**
     * Session startup timeouts in seconds: 30m, 1h, 4h, Never (0 = Never).
     */
    val TimeoutsSecs: List<Int> = listOf(1800, 3600, 14400, 0)

    /** Display label for a resolution preset (matches existing UI strings). */
    fun resolutionLabel(resolution: Resolution): String =
        when (resolution) {
            Resolution.P720 -> "720p"
            Resolution.P1080 -> "1080p"
            Resolution.P1440 -> "1440p"
            Resolution.Auto -> "Auto"
        }

    /** Display label for a quality preset (matches existing UI strings). */
    fun qualityLabel(quality: Quality): String = quality.name

    /** Display label for a frame rate (matches existing "$rate FPS" strings). */
    fun frameRateLabel(frameRate: Int): String = "$frameRate FPS"

    /** Display label for a startup timeout (0 = Never). */
    fun timeoutLabel(timeoutSecs: Int): String =
        when (timeoutSecs) {
            0 -> "Never"
            1800 -> "30m"
            3600 -> "1h"
            14400 -> "4h"
            else -> formatDuration(timeoutSecs.toLong())
        }

    /**
     * Age of a session in display form: clamps negative spans to zero and
     * delegates to [formatDuration].
     */
    fun sessionAgeText(startedAtEpochMs: Long, nowEpochMs: Long): String {
        val secs = ((nowEpochMs - startedAtEpochMs) / 1000).coerceAtLeast(0L)
        return formatDuration(secs)
    }
}
