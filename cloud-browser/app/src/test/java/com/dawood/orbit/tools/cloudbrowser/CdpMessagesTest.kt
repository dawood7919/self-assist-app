package com.dawood.orbit.tools.cloudbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CdpMessagesTest {

    @Test
    fun navigateEnvelopeHasIdMethodAndUrl() {
        val json = CdpMessages.navigate(1, "https://example.com")

        assertTrue(json.contains("\"id\":1"))
        assertTrue(json.contains("\"method\":\"Page.navigate\""))
        assertTrue(json.contains("\"url\":\"https://example.com\""))
    }

    @Test
    fun startScreencastCarriesSizeQualityAndEveryNth() {
        val json = CdpMessages.startScreencast(2, 1280, 720, 60, 2)

        assertTrue(json.contains("\"method\":\"Page.startScreencast\""))
        assertTrue(json.contains("\"maxWidth\":1280"))
        assertTrue(json.contains("\"maxHeight\":720"))
        assertTrue(json.contains("\"quality\":60"))
        assertTrue(json.contains("\"everyNthFrame\":2"))
    }

    @Test
    fun captureViewportCarriesClipScaleAndSurfaceFlags() {
        val json = CdpMessages.captureViewport(7, 78, 393, 800, 1.5)

        assertTrue(json.contains("\"method\":\"Page.captureScreenshot\""))
        assertTrue(json.contains("\"format\":\"jpeg\""))
        assertTrue(json.contains("\"quality\":78"))
        assertTrue(json.contains("\"captureBeyondViewport\":false"))
        assertTrue(json.contains("\"fromSurface\":true"))
        assertTrue(json.contains("\"optimizeForSpeed\":true"))
        assertTrue(json.contains("\"width\":393"))
        assertTrue(json.contains("\"height\":800"))
        assertTrue(json.contains("\"scale\":1.5"))
    }

    @Test
    fun mousePressedAndReleasedCarryButtonAndClickCount() {
        val pressed = CdpMessages.mouse(3, "mousePressed", 100.0, 200.0, button = "left", clickCount = 1)

        assertTrue(pressed.contains("\"method\":\"Input.dispatchMouseEvent\""))
        assertTrue(pressed.contains("\"type\":\"mousePressed\""))
        assertTrue(pressed.contains("\"button\":\"left\""))
        assertTrue(pressed.contains("\"clickCount\":1"))

        val released = CdpMessages.mouse(4, "mouseReleased", 100.0, 200.0, button = "left", clickCount = 1)

        assertTrue(released.contains("\"type\":\"mouseReleased\""))
        assertTrue(released.contains("\"button\":\"left\""))
    }

    @Test
    fun keyDownEnterCarriesWindowsCode13() {
        val json = CdpMessages.keyDown(5, 13, "Enter", "Enter")

        assertTrue(json.contains("\"method\":\"Input.dispatchKeyEvent\""))
        assertTrue(json.contains("\"type\":\"keyDown\""))
        assertTrue(json.contains("\"windowsVirtualKeyCode\":13"))
        assertTrue(json.contains("\"key\":\"Enter\""))
    }

    @Test
    fun parseScreencastFrameEventKeepsSessionIdAndData() {
        val json = "{\"method\":\"Page.screencastFrame\"," +
            "\"params\":{\"sessionId\":7,\"data\":\"aGk=\",\"metadata\":{\"deviceWidth\":1280}}}"

        val event = CdpMessages.parseEvent(json)

        assertTrue(event is CdpEvent.ScreencastFrame)
        val frame = event as CdpEvent.ScreencastFrame
        assertEquals(7, frame.sessionId)
        assertEquals("aGk=", frame.dataB64)
    }

    @Test
    fun parseResponseRoutesResultById() {
        val json = "{\"id\":9,\"result\":{\"frameId\":\"F1\"}}"

        val response = CdpMessages.parseResponse(json)

        assertEquals(9, response.id)
        assertEquals("F1", response.result?.optString("frameId"))
        assertNull(response.error)
    }

    @Test
    fun parseResponsePreservesError() {
        val json = "{\"id\":10,\"error\":{\"code\":-32000,\"message\":\"No target\"}}"

        val response = CdpMessages.parseResponse(json)

        assertEquals(10, response.id)
        assertNotNull(response.error)
        assertTrue((response.error?.optString("message") ?: "").contains("No target"))
    }

    @Test
    fun createAndCloseTargetRoundTrip() {
        val created = CdpMessages.createTarget(11, "https://example.com", 1280, 720)

        assertTrue(created.contains("\"method\":\"Target.createTarget\""))
        assertTrue(created.contains("\"url\":\"https://example.com\""))

        val closed = CdpMessages.closeTarget(12, "TARGET123")

        assertTrue(closed.contains("\"method\":\"Target.closeTarget\""))
        assertTrue(closed.contains("\"targetId\":\"TARGET123\""))
    }
}
