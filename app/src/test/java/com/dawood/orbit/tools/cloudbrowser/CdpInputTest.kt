package com.dawood.orbit.tools.cloudbrowser

import org.junit.Assert.assertEquals
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
    fun remoteSizesMatchPresets() {
        assertEquals(Pair(1280, 720), CdpInput.remoteSize(Resolution.P720))
        assertEquals(Pair(1920, 1080), CdpInput.remoteSize(Resolution.P1080))
        assertEquals(Pair(2560, 1440), CdpInput.remoteSize(Resolution.P1440))
        assertEquals(Pair(1280, 720), CdpInput.remoteSize(Resolution.Auto))
    }

    @Test
    fun qualityTriplesMatchSpec() {
        // Triple(maxWidth, jpegQuality, everyNthFrame)
        assertEquals(Triple(960, 40, 2), CdpInput.screencastParams(Quality.Low))
        assertEquals(Triple(1280, 60, 1), CdpInput.screencastParams(Quality.Balanced))
        assertEquals(Triple(1920, 72, 1), CdpInput.screencastParams(Quality.High))
        assertEquals(Triple(1920, 82, 1), CdpInput.screencastParams(Quality.Ultra))
    }
}
