package com.dawood.orbit.tools.cloudbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudBrowserEngineTest {
    @Test
    fun ramProgressUsesServerCapacity() {
        val snapshot = CloudBrowserEngine.ServerSnapshot(ramUsedGb = 2.4, ramTotalGb = 8.0)

        assertEquals(0.3f, CloudBrowserEngine.ramProgress(snapshot), 0.0001f)
    }

    @Test
    fun ramProgressIsClampedForUnexpectedServerValues() {
        val snapshot = CloudBrowserEngine.ServerSnapshot(ramUsedGb = 10.0, ramTotalGb = 8.0)

        assertTrue(CloudBrowserEngine.ramProgress(snapshot) <= 1f)
    }

    @Test
    fun connectionLabelCombinesLocationAndLatency() {
        val snapshot = CloudBrowserEngine.ServerSnapshot(location = "Germany", latency = "42 ms")

        assertEquals("Germany • 42 ms", CloudBrowserEngine.connectionLabel(snapshot))
    }

    @Test
    fun validPasswordServerHasNoErrors() {
        val errors = CloudBrowserEngine.validateServer("VPS", "example.com", 22, "root", AuthMethod.Password, null)

        assertTrue(errors.isEmpty())
    }

    @Test
    fun blankFieldsAndBadPortAreFlagged() {
        val errors = CloudBrowserEngine.validateServer("", "  ", 0, "", AuthMethod.Password, null)

        assertTrue(errors.containsKey("name"))
        assertTrue(errors.containsKey("host"))
        assertTrue(errors.containsKey("username"))
        assertTrue(errors.containsKey("port"))
        assertTrue(CloudBrowserEngine.validateServer("V", "h", 65_536, "u", AuthMethod.Password, null).containsKey("port"))
        assertTrue(CloudBrowserEngine.validateServer("V", "h", 65_535, "u", AuthMethod.Password, null).isEmpty())
    }

    @Test
    fun keyAuthRequiresKeyPath() {
        assertTrue(CloudBrowserEngine.validateServer("V", "h", 22, "u", AuthMethod.SshKey, null).containsKey("keyPath"))
        assertTrue(CloudBrowserEngine.validateServer("V", "h", 22, "u", AuthMethod.SshKey, " ").containsKey("keyPath"))
        assertTrue(CloudBrowserEngine.validateServer("V", "h", 22, "u", AuthMethod.SshKey, "/keys/id").isEmpty())
    }

    @Test
    fun parsePortAcceptsOnly1To65535() {
        assertEquals(22, CloudBrowserEngine.parsePort("22"))
        assertEquals(22, CloudBrowserEngine.parsePort("  22  "))
        assertNull(CloudBrowserEngine.parsePort(""))
        assertNull(CloudBrowserEngine.parsePort("ssh"))
        assertNull(CloudBrowserEngine.parsePort("0"))
        assertNull(CloudBrowserEngine.parsePort("65536"))
        assertEquals(1, CloudBrowserEngine.parsePort("1"))
        assertEquals(65_535, CloudBrowserEngine.parsePort("65535"))
    }

    @Test
    fun demoLabelsAreDemoHonest() {
        assertEquals("Demo • Not connected", CloudBrowserEngine.connectionLabel(ConnectionState.Disconnected, null, true))
        assertEquals("Demo • 42 ms", CloudBrowserEngine.connectionLabel(ConnectionState.Connected, 42L, true))
        ConnectionState.entries.forEach { state ->
            listOf(null, 42L).forEach { latency ->
                val label = CloudBrowserEngine.connectionLabel(state, latency, true)
                assertTrue(label.startsWith("Demo"))
                assertFalse(label.contains("Secure"))
                assertFalse(label.contains("Live"))
                assertFalse(label.contains("Encrypted"))
            }
        }
    }

    @Test
    fun realLabelsStayHonest() {
        assertEquals("Not connected", CloudBrowserEngine.connectionLabel(ConnectionState.Disconnected, null, false))
        assertEquals("42 ms", CloudBrowserEngine.connectionLabel(ConnectionState.Connected, 42L, false))
        assertEquals("Connected", CloudBrowserEngine.connectionLabel(ConnectionState.Connected, null, false))
    }

    @Test
    fun ramProgressIsNanSafeAndClamped() {
        assertEquals(0f, CloudBrowserEngine.ramProgress(2.4, 0.0), 0f)
        assertEquals(0f, CloudBrowserEngine.ramProgress(2.4, -8.0), 0f)
        assertEquals(0f, CloudBrowserEngine.ramProgress(Double.NaN, 8.0), 0f)
        assertEquals(0f, CloudBrowserEngine.ramProgress(2.4, Double.NaN), 0f)
        assertEquals(0f, CloudBrowserEngine.ramProgress(-1.0, 8.0), 0f)
        assertEquals(1f, CloudBrowserEngine.ramProgress(10.0, 8.0), 0f)
        assertEquals(0.3f, CloudBrowserEngine.ramProgress(2.4, 8.0), 0.0001f)
    }

    @Test
    fun formatsBytesDurationsAndUptime() {
        assertEquals("0 B", CloudBrowserEngine.formatBytes(0L))
        assertEquals("512 B", CloudBrowserEngine.formatBytes(512L))
        assertEquals("0 B", CloudBrowserEngine.formatBytes(-10L))
        assertEquals("2.4 GB", CloudBrowserEngine.formatBytes(2_576_980_377L))
        assertEquals("3h 12m", CloudBrowserEngine.formatDuration(3 * 3600 + 12 * 60))
        assertEquals("3h", CloudBrowserEngine.formatDuration(3 * 3600))
        assertEquals("12m", CloudBrowserEngine.formatDuration(12 * 60))
        assertEquals("45s", CloudBrowserEngine.formatDuration(45))
        assertEquals("12 days", CloudBrowserEngine.formatUptime(12 * 86_400))
        assertEquals("1 day", CloudBrowserEngine.formatUptime(86_400))
        assertEquals("3h 12m", CloudBrowserEngine.formatUptime(3 * 3600 + 12 * 60))
    }

    @Test
    fun clampZoomStaysInRange() {
        assertEquals(75, CloudBrowserEngine.clampZoom(10))
        assertEquals(75, CloudBrowserEngine.clampZoom(75))
        assertEquals(100, CloudBrowserEngine.clampZoom(100))
        assertEquals(150, CloudBrowserEngine.clampZoom(150))
        assertEquals(150, CloudBrowserEngine.clampZoom(400))
    }

    @Test
    fun filterDownloadsSelectsType() {
        val items = listOf(
            DownloadItem(id = "1", type = FileType.Doc),
            DownloadItem(id = "2", type = FileType.Video),
            DownloadItem(id = "3", type = FileType.Image),
            DownloadItem(id = "4", type = FileType.Archive),
        )

        assertEquals(4, CloudBrowserEngine.filterDownloads(items, DownloadFilter.All).size)
        assertEquals(listOf("1"), CloudBrowserEngine.filterDownloads(items, DownloadFilter.Docs).map { it.id })
        assertEquals(listOf("2"), CloudBrowserEngine.filterDownloads(items, DownloadFilter.Videos).map { it.id })
        assertEquals(listOf("3"), CloudBrowserEngine.filterDownloads(items, DownloadFilter.Images).map { it.id })
        assertEquals(listOf("4"), CloudBrowserEngine.filterDownloads(items, DownloadFilter.Archives).map { it.id })
    }

    @Test
    fun filterFilesPutsDirsFirstThenAlphaAndMatchesQuery() {
        val files = listOf(
            RemoteFile(name = "movie.mp4", type = FileType.Video),
            RemoteFile(name = "docs", isDir = true, type = FileType.Folder),
            RemoteFile(name = "archive.zip", type = FileType.Archive),
            RemoteFile(name = "Downloads", isDir = true, type = FileType.Folder),
        )

        assertEquals(
            listOf("docs", "Downloads", "archive.zip", "movie.mp4"),
            CloudBrowserEngine.filterFiles(files).map { it.name },
        )
        assertEquals(listOf("movie.mp4"), CloudBrowserEngine.filterFiles(files, "MOV").map { it.name })
    }

    @Test
    fun breadcrumbSegmentsHandleRootAndTrailingSlash() {
        assertTrue(CloudBrowserEngine.breadcrumbSegments("/").isEmpty())
        assertTrue(CloudBrowserEngine.breadcrumbSegments("").isEmpty())
        assertEquals(listOf("home", "user", "downloads"), CloudBrowserEngine.breadcrumbSegments("/home/user/downloads/"))
        assertEquals(listOf("home", "user"), CloudBrowserEngine.breadcrumbSegments("home/user"))
    }

    @Test
    fun sessionActionsMatchState() {
        assertEquals(
            listOf(CloudBrowserEngine.SessionAction.Open, CloudBrowserEngine.SessionAction.Pause, CloudBrowserEngine.SessionAction.Close),
            CloudBrowserEngine.sessionActions(SessionState.Active),
        )
        assertTrue(CloudBrowserEngine.sessionActions(SessionState.Paused).contains(CloudBrowserEngine.SessionAction.Resume))
        assertFalse(CloudBrowserEngine.sessionActions(SessionState.Ended).contains(CloudBrowserEngine.SessionAction.Open))
    }

    @Test
    fun pollDelayBacksOffAndCaps() {
        assertEquals(1_000L, CloudBrowserEngine.nextPollDelayMs(0))
        assertEquals(1_000L, CloudBrowserEngine.nextPollDelayMs(-3))
        assertEquals(2_000L, CloudBrowserEngine.nextPollDelayMs(1))
        assertEquals(4_000L, CloudBrowserEngine.nextPollDelayMs(2))
        assertEquals(8_000L, CloudBrowserEngine.nextPollDelayMs(3))
        assertEquals(8_000L, CloudBrowserEngine.nextPollDelayMs(10))
    }
}
