package com.dawood.orbit.tools.cloudbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CdpInputTest {

    @Test
    fun keyTableHoldsExpectedWindowsCodes() {
        assertEquals(13, CdpInput.KEY_TABLE["Enter"])
        assertEquals(8, CdpInput.KEY_TABLE["Backspace"])
        assertEquals(9, CdpInput.KEY_TABLE["Tab"])
        assertEquals(27, CdpInput.KEY_TABLE["Escape"])
        assertEquals(37, CdpInput.KEY_TABLE["ArrowLeft"])
        assertEquals(38, CdpInput.KEY_TABLE["ArrowUp"])
        assertEquals(39, CdpInput.KEY_TABLE["ArrowRight"])
        assertEquals(40, CdpInput.KEY_TABLE["ArrowDown"])
        assertEquals(46, CdpInput.KEY_TABLE["Delete"])
        assertEquals(112, CdpInput.KEY_TABLE["F1"])
        assertEquals(113, CdpInput.KEY_TABLE["F2"])
        assertEquals(121, CdpInput.KEY_TABLE["F10"])
        assertEquals(123, CdpInput.KEY_TABLE["F12"])
    }

    @Test
    fun fractionsMapToRemotePixels() {
        assertEquals(Pair(960.0, 270.0), CdpInput.pointFromFractions(0.5f, 0.25f, 1920, 1080))
    }

    @Test
    fun hostWindowStaysDesktopSized() {
        // The OS window behind the emulated viewport is always desktop-wide;
        // the mobile viewport comes from device-metrics emulation.
        assertEquals(1280, CdpInput.remoteSize(Resolution.P720).first)
        assertEquals(1366, CdpInput.remoteSize(Resolution.Auto).first)
    }

    @Test
    fun mobileGeometryFillsPhoneContentBox() {
        // 1080x2200 physical panel at density 2.75 -> ~393x800 CSS at dsf 2.75.
        val geo = CdpInput.emulatedViewport(1080, 2200, 2.75f, BrowserMode.Mobile)
        assertEquals(2.75, geo.deviceScaleFactor, 0.001)
        assertEquals(393, geo.cssWidth)
        assertEquals(800, geo.cssHeight)
        // A coded frame must stay wider than it is tall only in desktop.
        val mobileFrame = CdpInput.frameSize(geo, Quality.Balanced)
        assertEquals(1081, mobileFrame.first)
    }

    @Test
    fun desktopGeometryIsRealDesktopViewport() {
        val geo = CdpInput.emulatedViewport(1080, 2200, 2.75f, BrowserMode.Desktop)
        assertEquals(1.0, geo.deviceScaleFactor, 0.001)
        assertEquals(CdpInput.DESKTOP_CSS_WIDTH, geo.cssWidth)
        // Height follows the phone aspect so the frame still fills the box.
        assertEquals(2607, geo.cssHeight)
    }

    @Test
    fun frameCapsBandwidth() {
        val geo = CdpInput.emulatedViewport(1440, 3000, 3.5f, BrowserMode.Mobile)
        val (w, _) = CdpInput.frameSize(geo, Quality.Low)
        assert(w <= 960)
    }

    @Test
    fun remoteCssPointHandlesPinchScaleAndScroll() {
        // Centre tap, unzoomed -> centre CSS point.
        val p0 = CdpInput.remoteCssPoint(0.5f, 0.5f, 390, 800)
        assertEquals(195.0, p0.first, 0.1)
        assertEquals(400.0, p0.second, 0.1)
        // At pinch scale 2, scrolled down 300: the visible window is half as
        // wide in CSS and starts at scroll offset.
        val p1 = CdpInput.remoteCssPoint(0f, 0f, 390, 800, scale = 2.0, scrollY = 300.0)
        assertEquals(0.0, p1.first, 0.1)
        assertEquals(300.0, p1.second, 0.1)
        val p2 = CdpInput.remoteCssPoint(1f, 1f, 390, 800, scale = 2.0, scrollY = 300.0)
        assertEquals(195.0, p2.first, 0.1)
        assertEquals(700.0, p2.second, 0.1)
    }

    @Test
    fun userAgentsMatchModes() {
        assert(CdpInput.MOBILE_USER_AGENT.contains("Android"))
        assert(CdpInput.MOBILE_USER_AGENT.contains("Mobile"))
        assert(!CdpInput.DESKTOP_USER_AGENT.contains("Mobile"))
    }

    @Test
    fun qualityTriplesMatchSpec() {
        // Triple(jpegQuality, everyNthFrame, capWidth)
        assertEquals(Triple(46, 2, 960), CdpInput.screencastTuning(Quality.Low))
        assertEquals(Triple(88, 1, 1920), CdpInput.screencastTuning(Quality.Ultra))
    }

    @Test
    fun jpegDimensionsReadSofMarker() {
        // Minimal JPEG container: SOI, a SOF0 (H=2, W=3, three components), EOI.
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xC0.toByte(), 0x00, 0x11, 0x08,
            0x00, 0x02,
            0x00, 0x03,
            0x03, 0x01, 0x11, 0x02, 0x11, 0x00, 0x03, 0x11, 0x01,
            0xFF.toByte(), 0xD9.toByte(),
        )
        assertEquals(Pair(3, 2), CdpInput.jpegDimensions(jpeg))
        assertEquals(null, CdpInput.jpegDimensions(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun screenshotCadenceIsOrderedAndBelowPushRate() {
        // Request/response frames: higher quality -> shorter interval, and
        // the fastest tier stays under 25 fps (>40 ms).
        val low = CdpInput.screenshotIntervalMs(Quality.Low)
        val balanced = CdpInput.screenshotIntervalMs(Quality.Balanced)
        val high = CdpInput.screenshotIntervalMs(Quality.High)
        val ultra = CdpInput.screenshotIntervalMs(Quality.Ultra)
        assertTrue(low > balanced)
        assertTrue(balanced > high)
        assertTrue(high > ultra)
        assertTrue(ultra >= 40L)
    }
}
