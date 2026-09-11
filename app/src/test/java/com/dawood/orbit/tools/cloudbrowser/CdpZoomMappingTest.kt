package com.dawood.orbit.tools.cloudbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CdpZoomMappingTest {

    @Test
    fun zoomStepsAreMultiplicativeLikeChrome() {
        assertEquals(110, CdpInput.stepZoom(100, +1))
        assertEquals(90, CdpInput.stepZoom(100, -1))
        assertEquals(120, CdpInput.stepZoom(100, +2)) // 100 * 1.1^2 = 121 -> 120
        assertEquals(100, CdpInput.stepZoom(110, -1))
    }

    @Test
    fun zoomClampsToChromeRange() {
        assertEquals(25, CdpInput.stepZoom(25, -5))
        assertEquals(500, CdpInput.stepZoom(480, +5))
        assertEquals(25, CdpInput.clampBrowserZoom(1))
        assertEquals(500, CdpInput.clampBrowserZoom(900))
    }

    @Test
    fun pinchRatioMapsToZoomNotches() {
        // One notch in/out (ratio 1.1 and its inverse).
        assertEquals(110, CdpInput.pinchZoom(100, 1.1f))
        assertEquals(90, CdpInput.pinchZoom(100, 1f / 1.1f))
        // ~two notches out at ratio ~0.826
        val twoOut = CdpInput.pinchZoom(100, 1f / (1.1f * 1.1f))
        assertTrue(twoOut <= 85)
        assertTrue(twoOut >= 80)
    }

    @Test
    fun pinchInsideDeadbandStaysPut() {
        assertEquals(100, CdpInput.pinchZoom(100, 1.03f))
    }

    @Test
    fun wheelDeltaScalesPhonePixelsToRemoteCssPixels() {
        assertEquals(250.0, CdpInput.wheelDeltaPixels(100f), 0.001)
        assertEquals(-250.0, CdpInput.wheelDeltaPixels(-100f), 0.001)
    }

    @Test
    fun trackpadDeltaIsFractionOfAxis() {
        // A full swipe crosses ~90% of the remote desktop.
        assertEquals(0.9f, CdpInput.trackpadFractionDelta(1000f, 1000f), 0.0001f)
        assertEquals(-0.09f, CdpInput.trackpadFractionDelta(-100f, 1000f), 0.0001f)
        assertEquals(0f, CdpInput.trackpadFractionDelta(50f, 0f), 0f)
    }

    @Test
    fun pointFractionsMapToRemotePixelCorners() {
        val (x0, y0) = CdpInput.pointFromFractions(0f, 0f, 1920, 1080)
        assertEquals(0.0, x0, 0.0)
        assertEquals(0.0, y0, 0.0)
        val (x1, y1) = CdpInput.pointFromFractions(1f, 1f, 1920, 1080)
        assertEquals(1920.0, x1, 0.0)
        assertEquals(1080.0, y1, 0.0)
        // Out-of-range fractions clamp into the viewport.
        val (x2, _) = CdpInput.pointFromFractions(2f, 0f, 1920, 1080)
        assertEquals(1920.0, x2, 0.0)
    }

    @Test
    fun contentFractionsRemoveLetterboxMargins() {
        // Box exactly matches the coded frame ratio.
        CdpInput.contentFractions(100f, 50f, 200f, 100f, 200f, 100f)!!.also { (fx, fy) ->
            assertEquals(0.5f, fx, 0.001f)
            assertEquals(0.5f, fy, 0.001f)
        }
        // Wide box letterboxing a 1:1 frame: 100px side margins each side.
        CdpInput.contentFractions(150f, 100f, 400f, 200f, 200f, 200f)!!.also { (fx, fy) ->
            assertEquals(0.25f, fx, 0.001f)
            assertEquals(0.5f, fy, 0.001f)
        }
        // Touch inside the side margin maps to nothing.
        assertNull(CdpInput.contentFractions(10f, 100f, 400f, 200f, 200f, 200f))
    }

    @Test
    fun invalidDimensionsNeverThrow() {
        assertNull(CdpInput.contentFractions(10f, 10f, 0f, 0f, 100f, 100f))
    }
}
