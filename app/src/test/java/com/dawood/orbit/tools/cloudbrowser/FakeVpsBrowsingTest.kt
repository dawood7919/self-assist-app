package com.dawood.orbit.tools.cloudbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The in-memory fake must honour the new browsing/input contract so the UI
 * can run fully in previews/demos with honest behaviour.
 */
class FakeVpsBrowsingTest {

    private val fake = FakeVpsApi()
    private val server = SavedServer(
        id = FakeVpsApi.DEMO_SERVER_ID,
        name = "Demo",
        host = "demo.example",
        port = 22,
        username = "ubuntu",
        protocol = Protocol.Ssh,
        authMethod = AuthMethod.Password,
    )
    private val sessionId = "demo-session-01"

    @Test
    fun navigateNormalisesAddressAndUpdatesPage() {
        fake.connect(server)
        val page = fake.navigate(sessionId, "example.com").getOrThrow()
        assertEquals("https://example.com", page.url)
        assertEquals("example.com", page.title)
        assertEquals(page.url, fake.pageInfo(sessionId).getOrThrow().url)
    }

    @Test
    fun unknownSessionFailsEveryBrowsingVerb() {
        assertTrue(fake.navigate("nope", "example.com").isFailure)
        assertTrue(fake.click("nope", 0.5f, 0.5f, RemoteMouseButton.Left).isFailure)
        assertTrue(fake.wheel("nope", 0.5f, 0.5f, 0.0, 100.0).isFailure)
        assertTrue(fake.zoom("nope", +1).isFailure)
        assertTrue(fake.pageInfo("nope").isFailure)
    }

    @Test
    fun zoomWalksAndResets() {
        fake.connect(server)
        assertEquals(110, fake.zoom(sessionId, +1).getOrThrow())
        assertEquals(90, fake.zoom(sessionId, -2).getOrThrow())
        assertEquals(100, fake.resetZoom(sessionId).getOrThrow())
    }

    @Test
    fun inputVerbsSucceedOnKnownSession() {
        fake.connect(server)
        assertTrue(fake.pointerMove(sessionId, 0.5f, 0.5f).isSuccess)
        assertTrue(fake.pointerMoveRelative(sessionId, 0.01f, -0.02f).isSuccess)
        assertTrue(fake.pointerPress(sessionId, 0.2f, 0.3f, RemoteMouseButton.Left).isSuccess)
        assertTrue(fake.pointerRelease(sessionId, 0.2f, 0.3f, RemoteMouseButton.Left).isSuccess)
        assertTrue(fake.click(sessionId, 0.2f, 0.3f, RemoteMouseButton.Right, clickCount = 1).isSuccess)
        assertTrue(fake.wheel(sessionId, 0.5f, 0.5f, 0.0, 250.0).isSuccess)
        assertTrue(fake.typeText(sessionId, "hello").isSuccess)
        assertTrue(fake.pressKey(sessionId, "Enter").isSuccess)
        assertTrue(fake.reload(sessionId).isSuccess)
        assertTrue(fake.goBack(sessionId).isSuccess)
        assertTrue(fake.goForward(sessionId).isSuccess)
        assertTrue(fake.stopLoading(sessionId).isSuccess)
        assertTrue(fake.applyStream(sessionId, Quality.High, 30).isSuccess)
        assertTrue(fake.saveScreenshot(sessionId).isSuccess)
    }

    @Test
    fun prepareBrowserReportsStageAndSucceeds() {
        fake.connect(server)
        var stage: String? = null
        val result = fake.prepareBrowser { stage = it }
        assertTrue(result.isSuccess)
        assertNotEquals(null, stage)
    }
}
