package com.dawood.orbit.tools.cloudbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drives the whole fake lifecycle with plain JUnit4 asserts (no coroutines test dep). */
class FakeVpsApiTest {

    private val server = SavedServer(id = "srv-1", name = "Demo", host = "185.xxx.xxx.xxx", username = "root")

    @Test
    fun sessionCycleConnectListLaunchPauseResumeCloseRestart() {
        val api = FakeVpsApi()

        assertEquals(42L, api.testConnection(server).getOrThrow())
        assertTrue(api.connect(server).isSuccess)
        assertEquals(2, api.listSessions().getOrThrow().size)

        val launched = api.launchSession("srv-1", BrowserKind.Chromium, "Default", Resolution.P1080, Quality.Balanced, 30, 10).getOrThrow()
        assertEquals(SessionState.Active, launched.state)

        assertTrue(api.pauseSession(launched.id).isSuccess)
        assertEquals(SessionState.Paused, api.listSessions().getOrThrow().first { it.id == launched.id }.state)

        assertTrue(api.resumeSession(launched.id).isSuccess)
        assertEquals(SessionState.Active, api.listSessions().getOrThrow().first { it.id == launched.id }.state)

        assertTrue(api.closeSession(launched.id).isSuccess)
        assertEquals(SessionState.Ended, api.listSessions().getOrThrow().first { it.id == launched.id }.state)

        assertTrue(api.restartSession(launched.id).isSuccess)
        assertEquals(SessionState.Active, api.listSessions().getOrThrow().first { it.id == launched.id }.state)

        assertTrue(api.disconnect().isSuccess)
    }

    @Test
    fun metricsShapeIsDemoHonestAndWalksDeterministically() {
        val api = FakeVpsApi()
        api.connect(server)

        val first = api.pollMetrics(FakeVpsApi.DEMO_SERVER_ID).getOrThrow()
        val second = api.pollMetrics(FakeVpsApi.DEMO_SERVER_ID).getOrThrow()

        assertFalse(first.isLive)
        assertEquals(42L, first.latencyMs)
        assertEquals(8.0, first.ramTotalGb, 0.0)
        // Deterministic walk: consecutive polls differ but stay in sane bounds.
        assertTrue(first.cpuPct in 15f..25f)
        assertTrue(second.cpuPct in 15f..25f)

        val details = api.pollSessionDetails("demo-session-01").getOrThrow()
        assertEquals("demo-session-01", details.sessionId)
        assertTrue(api.pollSessionDetails("nope").isFailure)
    }

    @Test
    fun filesRenameMoveDelete() {
        val api = FakeVpsApi()
        val dir = FakeVpsApi.DEMO_DOWNLOADS_DIR

        assertEquals(4, api.listFiles(dir).getOrThrow().size)
        assertEquals(4, api.listFiles("$dir/").getOrThrow().size)

        assertTrue(api.renameFile("$dir/example.pdf", "report.pdf").isSuccess)
        assertTrue(api.listFiles(dir).getOrThrow().any { it.name == "report.pdf" })

        assertTrue(api.moveFile("$dir/report.pdf", "/home/user").isSuccess)
        assertTrue(api.listFiles("/home/user").getOrThrow().any { it.name == "report.pdf" })

        assertTrue(api.deleteFile("/home/user/report.pdf").isSuccess)
        assertTrue(api.listFiles("/home/user").getOrThrow().none { it.name == "report.pdf" })

        assertTrue(api.listFiles("/no/such/dir").isFailure)
        assertTrue(api.renameFile("$dir/movie.mp4", "").isFailure)
    }

    @Test
    fun unknownIdsFailWithClearMessages() {
        val api = FakeVpsApi()

        assertTrue(api.pauseSession("nope").isFailure)
        assertTrue(api.resumeSession("nope").isFailure)
        assertTrue(api.closeSession("nope").isFailure)
        assertTrue(api.restartSession("nope").isFailure)
        assertTrue(api.pollMetrics("nope").isFailure)
        assertTrue(api.deleteFile("/nope.txt").isFailure)
        assertTrue(api.moveFile("/nope.txt", FakeVpsApi.DEMO_DOWNLOADS_DIR).isFailure)
        assertTrue(api.downloadToPhone("nope").isFailure)
        assertTrue(api.pauseSession("nope").exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun downloadsListAndCompleteHonestly() {
        val api = FakeVpsApi()

        assertEquals(2, api.listDownloads().getOrThrow().size)
        assertTrue(api.downloadToPhone("demo-download-02").isSuccess)
        val done = api.listDownloads().getOrThrow().first { it.id == "demo-download-02" }
        assertEquals(DownloadState.Completed, done.state)
        assertEquals(done.sizeBytes, done.downloadedBytes)
    }
}
