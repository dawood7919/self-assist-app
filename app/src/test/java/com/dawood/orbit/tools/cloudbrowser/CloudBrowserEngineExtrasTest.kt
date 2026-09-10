package com.dawood.orbit.tools.cloudbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudBrowserEngineExtrasTest {

    // ------------------------------------------------------------------
    // sessionActions: exhaustive per-state contract
    // ------------------------------------------------------------------

    @Test
    fun sessionActionsAreExhaustivePerState() {
        assertEquals(
            listOf(
                CloudBrowserEngine.SessionAction.Open,
                CloudBrowserEngine.SessionAction.Pause,
                CloudBrowserEngine.SessionAction.Close,
            ),
            CloudBrowserEngine.sessionActions(SessionState.Active),
        )
        assertEquals(
            listOf(CloudBrowserEngine.SessionAction.Open, CloudBrowserEngine.SessionAction.Close),
            CloudBrowserEngine.sessionActions(SessionState.Idle),
        )
        assertEquals(
            listOf(CloudBrowserEngine.SessionAction.Resume, CloudBrowserEngine.SessionAction.Close),
            CloudBrowserEngine.sessionActions(SessionState.Paused),
        )
        assertEquals(
            listOf(CloudBrowserEngine.SessionAction.Close),
            CloudBrowserEngine.sessionActions(SessionState.Ended),
        )
    }

    // ------------------------------------------------------------------
    // nextPollDelayMs: cap beyond the already-covered 0..3,10
    // ------------------------------------------------------------------

    @Test
    fun pollDelayCapsBeyondThreeFailures() {
        assertEquals(8_000L, CloudBrowserEngine.nextPollDelayMs(4))
        assertEquals(8_000L, CloudBrowserEngine.nextPollDelayMs(5))
        assertEquals(8_000L, CloudBrowserEngine.nextPollDelayMs(100))
    }

    // ------------------------------------------------------------------
    // breadcrumb edge cases
    // ------------------------------------------------------------------

    @Test
    fun breadcrumbHandlesRepeatsSpacesAndSingleSegment() {
        assertEquals(listOf("home", "user"), CloudBrowserEngine.breadcrumbSegments("//home//user//"))
        assertEquals(listOf("home", "user"), CloudBrowserEngine.breadcrumbSegments(" /home / user / "))
        assertEquals(listOf("home"), CloudBrowserEngine.breadcrumbSegments("home"))
        assertEquals(listOf("home"), CloudBrowserEngine.breadcrumbSegments("/home"))
        assertTrue(CloudBrowserEngine.breadcrumbSegments("   ").isEmpty())
        assertTrue(CloudBrowserEngine.breadcrumbSegments("///").isEmpty())
    }

    // ------------------------------------------------------------------
    // filter edge cases
    // ------------------------------------------------------------------

    @Test
    fun filterDownloadsEdgeCases() {
        assertTrue(CloudBrowserEngine.filterDownloads(emptyList(), DownloadFilter.All).isEmpty())
        assertTrue(CloudBrowserEngine.filterDownloads(emptyList(), DownloadFilter.Docs).isEmpty())

        val items = listOf(
            DownloadItem(id = "1", type = FileType.Other),
            DownloadItem(id = "2", type = FileType.Folder),
            DownloadItem(id = "3", type = FileType.Doc),
        )
        assertEquals(3, CloudBrowserEngine.filterDownloads(items, DownloadFilter.All).size)
        assertEquals(listOf("3"), CloudBrowserEngine.filterDownloads(items, DownloadFilter.Docs).map { it.id })
        assertTrue(CloudBrowserEngine.filterDownloads(items, DownloadFilter.Videos).isEmpty())
        assertTrue(CloudBrowserEngine.filterDownloads(items, DownloadFilter.Images).isEmpty())
        assertTrue(CloudBrowserEngine.filterDownloads(items, DownloadFilter.Archives).isEmpty())
    }

    @Test
    fun filterFilesEdgeCases() {
        assertTrue(CloudBrowserEngine.filterFiles(emptyList()).isEmpty())
        assertTrue(CloudBrowserEngine.filterFiles(emptyList(), "pdf").isEmpty())

        val files = listOf(
            RemoteFile(name = "movie.mp4", type = FileType.Video),
            RemoteFile(name = "docs", isDir = true, type = FileType.Folder),
        )
        assertTrue(CloudBrowserEngine.filterFiles(files, "zzz-no-match").isEmpty())
        // Blank and whitespace-only queries match everything.
        assertEquals(2, CloudBrowserEngine.filterFiles(files, "   ").size)
        // Query is trimmed before matching.
        assertEquals(listOf("movie.mp4"), CloudBrowserEngine.filterFiles(files, "  mov  ").map { it.name })
        // Dirs sort first even when their names compare last alphabetically.
        val mixed = listOf(
            RemoteFile(name = "a-file.txt", type = FileType.Doc),
            RemoteFile(name = "zzz-dir", isDir = true, type = FileType.Folder),
        )
        assertEquals(
            listOf("zzz-dir", "a-file.txt"),
            CloudBrowserEngine.filterFiles(mixed).map { it.name },
        )
        // Sorting within each group is case-insensitive.
        val cases = listOf(
            RemoteFile(name = "Banana.txt", type = FileType.Doc),
            RemoteFile(name = "apple.txt", type = FileType.Doc),
        )
        assertEquals(
            listOf("apple.txt", "Banana.txt"),
            CloudBrowserEngine.filterFiles(cases).map { it.name },
        )
    }

    // ------------------------------------------------------------------
    // validation + parsing edges
    // ------------------------------------------------------------------

    @Test
    fun whitespaceDisplayNameIsFlagged() {
        val errors = CloudBrowserEngine.validateServer("   ", "h", 22, "u", AuthMethod.Password, null)

        assertTrue(errors.containsKey("name"))
        assertEquals(1, errors.size)
    }

    @Test
    fun parsePortEdgeCases() {
        assertEquals(7, CloudBrowserEngine.parsePort("007"))
        assertEquals(22, CloudBrowserEngine.parsePort("0022"))
        assertNull(CloudBrowserEngine.parsePort("-1"))
        assertNull(CloudBrowserEngine.parsePort("22.0"))
        assertNull(CloudBrowserEngine.parsePort("  "))
        assertNull(CloudBrowserEngine.parsePort("+"))
    }

    // ------------------------------------------------------------------
    // labels: latency is only honoured for Connected
    // ------------------------------------------------------------------

    @Test
    fun nonConnectedLabelsIgnoreLatency() {
        listOf(null, 42L).forEach { latency ->
            assertEquals("Testing…", CloudBrowserEngine.connectionLabel(ConnectionState.Testing, latency, false))
            assertEquals("Connecting…", CloudBrowserEngine.connectionLabel(ConnectionState.Connecting, latency, false))
            assertEquals("Connection failed", CloudBrowserEngine.connectionLabel(ConnectionState.Error, latency, false))
            assertEquals("Not connected", CloudBrowserEngine.connectionLabel(ConnectionState.Disconnected, latency, false))
            assertEquals("Demo • Testing…", CloudBrowserEngine.connectionLabel(ConnectionState.Testing, latency, true))
            assertEquals("Demo • Connecting…", CloudBrowserEngine.connectionLabel(ConnectionState.Connecting, latency, true))
            assertEquals("Demo • Connection failed", CloudBrowserEngine.connectionLabel(ConnectionState.Error, latency, true))
            assertEquals("Demo • Not connected", CloudBrowserEngine.connectionLabel(ConnectionState.Disconnected, latency, true))
        }
        assertEquals("Demo • Connected", CloudBrowserEngine.connectionLabel(ConnectionState.Connected, null, true))
        assertEquals("Demo • 0 ms", CloudBrowserEngine.connectionLabel(ConnectionState.Connected, 0L, true))
        assertEquals("0 ms", CloudBrowserEngine.connectionLabel(ConnectionState.Connected, 0L, false))
    }

    // ------------------------------------------------------------------
    // formatting branches not covered by the main engine test
    // ------------------------------------------------------------------

    @Test
    fun ramProgressInfiniteIsZero() {
        assertEquals(0f, CloudBrowserEngine.ramProgress(Double.POSITIVE_INFINITY, 8.0), 0f)
        assertEquals(0f, CloudBrowserEngine.ramProgress(2.4, Double.POSITIVE_INFINITY), 0f)
        assertEquals(0f, CloudBrowserEngine.ramProgress(Double.NEGATIVE_INFINITY, 8.0), 0f)
        assertEquals(0f, CloudBrowserEngine.ramProgress(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY), 0f)
    }

    @Test
    fun formatBytesCoversKbMbGbTb() {
        assertEquals("1 KB", CloudBrowserEngine.formatBytes(1024L))
        assertEquals("1.5 KB", CloudBrowserEngine.formatBytes(1536L))
        assertEquals("150 KB", CloudBrowserEngine.formatBytes(150L * 1024L))
        assertEquals("1 MB", CloudBrowserEngine.formatBytes(1024L * 1024L))
        assertEquals("1.5 MB", CloudBrowserEngine.formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("1 GB", CloudBrowserEngine.formatBytes(1024L * 1024L * 1024L))
        assertEquals("2 TB", CloudBrowserEngine.formatBytes(2L * 1024L * 1024L * 1024L * 1024L))
    }

    @Test
    fun formatDurationCoversMinutesAndSecondsCombo() {
        assertEquals("0s", CloudBrowserEngine.formatDuration(0L))
        assertEquals("0s", CloudBrowserEngine.formatDuration(-5L))
        assertEquals("1m", CloudBrowserEngine.formatDuration(60L))
        assertEquals("1m 1s", CloudBrowserEngine.formatDuration(61L))
        assertEquals("12m 30s", CloudBrowserEngine.formatDuration(12 * 60 + 30))
        assertEquals("1h", CloudBrowserEngine.formatDuration(3600L + 30L))
        assertEquals("1h 1m", CloudBrowserEngine.formatDuration(3600L + 60L + 1L))
    }

    @Test
    fun formatUptimeDelegatesBelowOneDay() {
        assertEquals("0s", CloudBrowserEngine.formatUptime(0L))
        assertEquals("0s", CloudBrowserEngine.formatUptime(-10L))
        assertEquals("2 days", CloudBrowserEngine.formatUptime(2 * 86_400))
        assertEquals("23h 59m", CloudBrowserEngine.formatUptime(86_399L))
    }

    @Test
    fun clampZoomBoundaryValues() {
        assertEquals(75, CloudBrowserEngine.clampZoom(74))
        assertEquals(75, CloudBrowserEngine.clampZoom(76 - 1))
        assertEquals(150, CloudBrowserEngine.clampZoom(151))
        assertEquals(149, CloudBrowserEngine.clampZoom(149))
    }
}
