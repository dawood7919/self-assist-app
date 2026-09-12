package com.dawood.orbit.tools.cloudbrowser

import java.util.UUID

/**
 * Cloud Browser data contracts (Workstream A: backend + contracts).
 *
 * Pure Kotlin — zero android.* / androidx.* / compose imports — so every model
 * and codec round trip is covered by JVM unit tests without a device.
 *
 * Every data class declares a stable default for every field. Codecs rely on
 * this: when stored JSON is missing a field (older app version) or carries an
 * unknown enum value, decoding falls back to the default instead of throwing.
 */

/** Lifecycle of the phone-to-VPS connection. */
enum class ConnectionState {
    Disconnected,
    Testing,
    Connecting,
    Connected,
    Error,
}

/**
 * How the user proves who they are to the VPS.
 *
 * Only the key *path* is ever persisted ([SavedServer.keyPath]). Key file
 * contents are never read into memory here and never written to storage.
 */
enum class AuthMethod {
    SshKey,
    Password,
}

/** Transport used to reach the VPS. */
enum class Protocol {
    Ssh,
    WebSocket,
    SecureTunnel,
}

/** Lifecycle of one remote browser window. */
enum class SessionState {
    Active,
    Idle,
    Paused,
    Ended,
}

/** Remote browser flavours the VPS can launch. */
enum class BrowserKind {
    Chromium,
    Firefox,
    Chrome,
}

/** Remote stream quality preset. */
enum class Quality {
    Low,
    Balanced,
    High,
    Ultra,
}

/** Remote stream resolution preset. */
enum class Resolution {
    P720,
    P1080,
    P1440,
    Auto,
}

/** How phone input maps onto the remote browser. */
enum class InputMode {
    Touch,
    Mouse,
    Trackpad,
    Keyboard,
    Fullscreen,
}

/**
 * Which kind of device the remote tab emulates. Mobile is the default and
 * makes websites serve their phone layout; Desktop is a real desktop
 * viewport (different UA, viewport, mouse semantics), not a zoomed page.
 */
enum class BrowserMode {
    Mobile,
    Desktop,
}

/**
 * The emulated browser viewport in CSS pixels plus its device scale factor
 * (physical px per CSS px). Chosen so the coded screencast frame maps 1:1 to
 * the phone content area.
 */
data class ViewportGeometry(
    val cssWidth: Int,
    val cssHeight: Int,
    val deviceScaleFactor: Double,
)

/** Download queue filter chips. */
enum class DownloadFilter {
    All,
    Docs,
    Videos,
    Images,
    Archives,
}

/** Kind of a remote file or download, used for icons and filtering. */
enum class FileType {
    Folder,
    Doc,
    Video,
    Image,
    Archive,
    Other,
}

/** Progress state of one download. */
enum class DownloadState {
    Downloading,
    Completed,
    Failed,
}

/**
 * A VPS the user saved on the connect screen.
 *
 * [keyPath] is a filesystem path to a private key, never the key itself.
 * [lastLatencyMs] is the last measured round trip, null when never tested.
 */
data class SavedServer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    val authMethod: AuthMethod = AuthMethod.SshKey,
    val keyPath: String? = null,
    val protocol: Protocol = Protocol.Ssh,
    val lastLatencyMs: Long? = null,
)

/** One remote browser window running on a [SavedServer]. */
data class BrowserSession(
    val id: String = UUID.randomUUID().toString(),
    val serverId: String = "",
    val browser: BrowserKind = BrowserKind.Chromium,
    val profile: String = "Default",
    val resolution: Resolution = Resolution.P1080,
    val quality: Quality = Quality.Balanced,
    val frameRate: Int = 30,
    val state: SessionState = SessionState.Active,
    val mode: BrowserMode = BrowserMode.Mobile,
    val startedAtEpochMs: Long = 0L,
    val lastSeenEpochMs: Long = 0L,
)

/**
 * Point-in-time VPS health snapshot.
 *
 * [isLive] is false for demo/fake data. The UI must only show "Live" badges
 * when this is true, so demo numbers are never presented as real telemetry.
 */
data class VpsMetrics(
    val cpuPct: Float = 0f,
    val ramUsedGb: Double = 0.0,
    val ramTotalGb: Double = 0.0,
    val netDownMbps: Double = 0.0,
    val netUpMbps: Double = 0.0,
    val diskUsedGb: Double = 0.0,
    val diskTotalGb: Double = 0.0,
    val uptimeSecs: Long = 0L,
    val latencyMs: Long = 0L,
    val bandwidthMbps: Double = 0.0,
    val isLive: Boolean = false,
)

/** Detail row for one [BrowserSession], shown on the session screen. */
data class SessionDetails(
    val sessionId: String = "",
    val browser: String = "",
    val os: String = "",
    val durationSecs: Long = 0L,
    val resolution: String = "",
    val connection: String = "",
    val latencyMs: Long = 0L,
    val bandwidthMbps: Double = 0.0,
)

/** One entry in a remote directory listing. */
data class RemoteFile(
    val path: String = "",
    val name: String = "",
    val isDir: Boolean = false,
    val type: FileType = FileType.Other,
    val sizeBytes: Long = 0L,
    val modifiedEpochMs: Long = 0L,
)

/** One row in the download queue. */
data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String = "",
    val type: FileType = FileType.Other,
    val sizeBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val state: DownloadState = DownloadState.Downloading,
    val sourcePath: String = "",
)

/** Persisted user preferences for the Cloud Browser tool (0..1 row in storage). */
data class CloudSettings(
    val defaultBrowser: BrowserKind = BrowserKind.Chromium,
    val defaultQuality: Quality = Quality.High,
    val defaultResolution: Resolution = Resolution.P1080,
    val defaultFrameRate: Int = 30,
    val hwAccel: Boolean = true,
    val autoReconnect: Boolean = true,
    val keepRunning: Boolean = false,
    val dataSaver: Boolean = false,
    val screenshotCacheBytes: Long = 32L * 1024L * 1024L,
    val introSeen: Boolean = false,
)

/** Live tuning of one remote stream (browser controls sheet). */
data class StreamConfig(
    val mode: InputMode = InputMode.Touch,
    val zoomPct: Int = 100,
    // High by default: mobile text pages looked soft at Balanced bitrate.
    val quality: Quality = Quality.High,
    val frameRate: Int = 30,
    val resolution: Resolution = Resolution.P1080,
)

/**
 * Live state of the page shown in one remote tab: drives the address bar,
 * the back/forward affordances and the zoom label. [zoomPct] is the browser
 * page zoom (100 = normal).
 */
data class PageInfo(
    val url: String = "",
    val title: String = "",
    val loading: Boolean = false,
    val zoomPct: Int = 100,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val inputFocused: Boolean = false,
) {
    /** True for https pages, false for http/about:blank/data URLs. */
    val isSecure: Boolean
        get() = url.startsWith("https://", ignoreCase = true)
}

/** One entry of the in-tab navigation history. */
data class HistoryEntry(
    val id: Int = -1,
    val url: String = "",
    val title: String = "",
)

/** Parsed `Page.getNavigationHistory` result: current index plus entries. */
data class HistorySnapshot(
    val currentIndex: Int = -1,
    val entries: List<HistoryEntry> = emptyList(),
) {
    val canGoBack: Boolean get() = currentIndex > 0
    val canGoForward: Boolean get() = currentIndex in 0 until entries.lastIndex
    val currentUrl: String get() = entries.getOrNull(currentIndex)?.url ?: ""
    val currentTitle: String get() = entries.getOrNull(currentIndex)?.title ?: ""
}

/**
 * How finger gestures on the phone viewport map onto the remote browser.
 *  - [Direct]: the finger IS the cursor. Tap = click at that point, long
 *    press = right click, drag = press-and-drag, two-finger drag = wheel
 *    scroll, pinch = browser zoom.
 *  - [Trackpad]: a laptop-style pad. One finger moves a floating cursor,
 *    tap clicks under the cursor, two-finger drag scrolls, pinch zooms.
 */
enum class BrowserInteraction { Direct, Trackpad }

/** One remote pointer interaction translated from a touch gesture. */
data class RemotePoint(
    val fx: Float,
    val fy: Float,
) {
    fun toPixels(width: Int, height: Int): Pair<Double, Double> =
        CdpInput.pointFromFractions(fx, fy, width, height)
}

/**
 * One finger as fractions (0..1) of the displayed remote frame. The API
 * layer converts fractions to the emulated CSS coordinates Chrome expects.
 */
data class TouchPointFraction(
    val id: Int,
    val fx: Float,
    val fy: Float,
)
