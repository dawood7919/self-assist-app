package com.dawood.orbit.tools.cloudbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Demo-honesty and error-message locks for [FakeVpsApi].
 *
 * FakeVpsApiTest already drives the happy lifecycle; this file pins the exact
 * Demo strings (so demo numbers are never presented as live), the blank-host
 * rejections, the unknown-id messages, session ordering, and the
 * pause-after-close edge.
 *
 * JUnit4 only.
 */
class FakeVpsApiDemoHonestyTest {

    private val server = SavedServer(id = "srv-1", name = "Demo", host = "185.xxx.xxx.xxx", username = "root")

    @Test
    fun metricsAreNeverLiveAndUseFixedDemoLatency() {
        val api = FakeVpsApi()
        api.connect(server)

        val first = api.pollMetrics(FakeVpsApi.DEMO_SERVER_ID).getOrThrow()
        val second = api.pollMetrics(FakeVpsApi.DEMO_SERVER_ID).getOrThrow()

        assertFalse(first.isLive)
        assertFalse(second.isLive)
        assertEquals(FakeVpsApi.DEMO_LATENCY_MS, first.latencyMs)
        assertEquals(42L, first.latencyMs)
        assertEquals(42L, second.latencyMs)
        assertEquals(8.0, first.ramTotalGb, 0.0)
        assertEquals(1_000.0, first.bandwidthMbps, 0.0)
        assertEquals(42.0, first.diskUsedGb, 0.0)
        assertEquals(100.0, first.diskTotalGb, 0.0)
    }

    @Test
    fun metricsUptimeWalksFromTwelveDays() {
        val api = FakeVpsApi()
        api.connect(server)

        val base = 12L * 86_400L
        val first = api.pollMetrics(FakeVpsApi.DEMO_SERVER_ID).getOrThrow()
        val second = api.pollMetrics(FakeVpsApi.DEMO_SERVER_ID).getOrThrow()

        assertEquals(base + 1, first.uptimeSecs)
        assertEquals(base + 2, second.uptimeSecs)
    }

    @Test
    fun sessionDetailsAreDemoHonest() {
        val api = FakeVpsApi()

        val first = api.pollSessionDetails("demo-session-01").getOrThrow()
        assertEquals("demo-session-01", first.sessionId)
        assertEquals("Chrome", first.browser)
        assertEquals("Ubuntu 24.04", first.os)
        assertEquals(5_075L, first.durationSecs)
        assertEquals("1920 × 1080", first.resolution)
        assertEquals("Demo relay", first.connection)
        assertTrue(first.connection.startsWith("Demo"))
        assertEquals(42L, first.latencyMs)
        assertEquals(24.0, first.bandwidthMbps, 0.0)

        val second = api.pollSessionDetails("demo-session-02").getOrThrow()
        assertEquals("demo-session-02", second.sessionId)
        assertEquals("Chromium", second.browser)
        assertEquals("Demo relay", second.connection)
    }

    @Test
    fun blankHostFailsWithHelpfulMessage() {
        val api = FakeVpsApi()
        val blank = SavedServer(id = "s", name = "V", host = "  ", username = "u")

        val testFailure = api.testConnection(blank)
        assertTrue(testFailure.isFailure)
        assertEquals("Enter a host name or IP address", testFailure.exceptionOrNull()?.message)

        val connectFailure = api.connect(blank)
        assertTrue(connectFailure.isFailure)
        assertEquals("Enter a host name or IP address", connectFailure.exceptionOrNull()?.message)

        val emptyHost = SavedServer(id = "s", name = "V", host = "", username = "u")
        assertTrue(api.testConnection(emptyHost).isFailure)
        assertTrue(api.connect(emptyHost).isFailure)
    }

    @Test
    fun unknownIdsFailWithClearMessages() {
        val api = FakeVpsApi()

        assertEquals("Unknown server id: nope", api.pollMetrics("nope").exceptionOrNull()?.message)
        assertEquals("Unknown session id: nope", api.pollSessionDetails("nope").exceptionOrNull()?.message)
        assertEquals("Unknown session id: nope", api.pauseSession("nope").exceptionOrNull()?.message)
        assertEquals("Unknown session id: nope", api.resumeSession("nope").exceptionOrNull()?.message)
        assertEquals("Unknown session id: nope", api.closeSession("nope").exceptionOrNull()?.message)
        assertEquals("Unknown session id: nope", api.restartSession("nope").exceptionOrNull()?.message)
        assertEquals("No such directory: /no/such/dir", api.listFiles("/no/such/dir").exceptionOrNull()?.message)
        assertEquals("Unknown download id: nope", api.downloadToPhone("nope").exceptionOrNull()?.message)
    }

    @Test
    fun fileValidationMessages() {
        val api = FakeVpsApi()
        val dir = FakeVpsApi.DEMO_DOWNLOADS_DIR

        assertEquals("Enter a file name", api.renameFile("$dir/movie.mp4", "").exceptionOrNull()?.message)
        assertEquals("Enter a file name", api.renameFile("$dir/movie.mp4", "   ").exceptionOrNull()?.message)
        assertEquals("Name must not contain '/'", api.renameFile("$dir/movie.mp4", "a/b").exceptionOrNull()?.message)
        assertEquals("No such file: /nope.txt", api.deleteFile("/nope.txt").exceptionOrNull()?.message)
        assertEquals("No such file: /nope.txt", api.moveFile("/nope.txt", dir).exceptionOrNull()?.message)
        assertEquals("No such directory: /nope-dir", api.moveFile("$dir/movie.mp4", "/nope-dir").exceptionOrNull()?.message)
    }

    @Test
    fun listSessionsIsNewestFirst() {
        val api = FakeVpsApi()
        val sessions = api.listSessions().getOrThrow()

        assertEquals(2, sessions.size)
        // demo-session-02 has the newer lastSeenEpochMs, so it sorts first.
        assertEquals("demo-session-02", sessions[0].id)
        assertEquals("demo-session-01", sessions[1].id)
    }

    @Test
    fun launchUsesSequentialDemoIdsAndDefaultsBlankProfile() {
        val api = FakeVpsApi()

        val first = api.launchSession("srv-1", BrowserKind.Chromium, "", Resolution.P1080, Quality.Balanced, 30, 0).getOrThrow()
        assertEquals("demo-session-03", first.id)
        assertEquals("Default", first.profile)
        assertEquals(SessionState.Active, first.state)

        val second = api.launchSession("srv-1", BrowserKind.Firefox, "Work", Resolution.P720, Quality.High, 60, 3600).getOrThrow()
        assertEquals("demo-session-04", second.id)
        assertEquals("Work", second.profile)
    }

    @Test
    fun endedSessionCannotBePausedOrResumedButCanRestart() {
        val api = FakeVpsApi()
        val launched = api.launchSession("srv-1", BrowserKind.Chromium, "Default", Resolution.P1080, Quality.Balanced, 30, 0).getOrThrow()
        assertTrue(api.closeSession(launched.id).isSuccess)

        val pauseFailure = api.pauseSession(launched.id)
        assertTrue(pauseFailure.isFailure)
        assertTrue(pauseFailure.exceptionOrNull() is IllegalStateException)

        val resumeFailure = api.resumeSession(launched.id)
        assertTrue(resumeFailure.isFailure)
        assertTrue(resumeFailure.exceptionOrNull() is IllegalStateException)

        assertTrue(api.restartSession(launched.id).isSuccess)
        assertEquals(SessionState.Active, api.listSessions().getOrThrow().first { it.id == launched.id }.state)
    }

    @Test
    fun cannedFileTreeShape() {
        val api = FakeVpsApi()

        assertEquals(listOf("home"), api.listFiles("/").getOrThrow().map { it.name })
        assertEquals(listOf("user"), api.listFiles("/home").getOrThrow().map { it.name })
        assertEquals(2, api.listFiles("/home/user").getOrThrow().size)
        assertEquals(4, api.listFiles(FakeVpsApi.DEMO_DOWNLOADS_DIR).getOrThrow().size)
        // Trailing slash normalises to the same directory.
        assertEquals(4, api.listFiles(FakeVpsApi.DEMO_DOWNLOADS_DIR + "/").getOrThrow().size)
        assertEquals("/home/user/downloads", FakeVpsApi.DEMO_DOWNLOADS_DIR)
    }
}
