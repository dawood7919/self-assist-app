package com.dawood.orbit.tools.cloudbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the shared option lists and label helpers at the bottom of
 * [CloudBrowserEngine] (FrameRates / TimeoutsSecs / resolutionLabel /
 * qualityLabel / frameRateLabel / timeoutLabel / sessionAgeText).
 *
 * Pure JUnit4 — no Android / Compose — matching app/build.gradle.kts
 * (test deps are junit + json only).
 */
class CloudBrowserEngineLabelsTest {

    @Test
    fun frameRatesListIsExpected() {
        assertEquals(listOf(15, 30, 60), CloudBrowserEngine.FrameRates)
    }

    @Test
    fun timeoutsListIsExpected() {
        assertEquals(listOf(1800, 3600, 14400, 0), CloudBrowserEngine.TimeoutsSecs)
    }

    @Test
    fun resolutionLabelsMatchUiStrings() {
        assertEquals("720p", CloudBrowserEngine.resolutionLabel(Resolution.P720))
        assertEquals("1080p", CloudBrowserEngine.resolutionLabel(Resolution.P1080))
        assertEquals("1440p", CloudBrowserEngine.resolutionLabel(Resolution.P1440))
        assertEquals("Auto", CloudBrowserEngine.resolutionLabel(Resolution.Auto))
    }

    @Test
    fun qualityLabelsMatchEnumNames() {
        assertEquals("Low", CloudBrowserEngine.qualityLabel(Quality.Low))
        assertEquals("Balanced", CloudBrowserEngine.qualityLabel(Quality.Balanced))
        assertEquals("High", CloudBrowserEngine.qualityLabel(Quality.High))
        assertEquals("Ultra", CloudBrowserEngine.qualityLabel(Quality.Ultra))
        Quality.entries.forEach { quality ->
            assertEquals(quality.name, CloudBrowserEngine.qualityLabel(quality))
        }
    }

    @Test
    fun frameRateLabelsAppendFps() {
        assertEquals("15 FPS", CloudBrowserEngine.frameRateLabel(15))
        assertEquals("30 FPS", CloudBrowserEngine.frameRateLabel(30))
        assertEquals("60 FPS", CloudBrowserEngine.frameRateLabel(60))
        // Generic formatting for any rate, not just the offered presets.
        assertEquals("0 FPS", CloudBrowserEngine.frameRateLabel(0))
    }

    @Test
    fun timeoutLabelsForKnownPresets() {
        assertEquals("Never", CloudBrowserEngine.timeoutLabel(0))
        assertEquals("30m", CloudBrowserEngine.timeoutLabel(1800))
        assertEquals("1h", CloudBrowserEngine.timeoutLabel(3600))
        assertEquals("4h", CloudBrowserEngine.timeoutLabel(14400))
    }

    @Test
    fun timeoutLabelsFallBackToFormatDuration() {
        // Fallback is formatDuration(secs): spot-check against the same engine.
        assertEquals(
            CloudBrowserEngine.formatDuration(60L),
            CloudBrowserEngine.timeoutLabel(60),
        )
        assertEquals("1m", CloudBrowserEngine.timeoutLabel(60))
        assertEquals("1m 30s", CloudBrowserEngine.timeoutLabel(90))
        assertEquals("45s", CloudBrowserEngine.timeoutLabel(45))
        assertEquals("2h", CloudBrowserEngine.timeoutLabel(7200))
    }

    @Test
    fun sessionAgeTextDelegatesToFormatDuration() {
        assertEquals("0s", CloudBrowserEngine.sessionAgeText(0L, 0L))
        assertEquals("45s", CloudBrowserEngine.sessionAgeText(0L, 45_000L))
        assertEquals("1m 1s", CloudBrowserEngine.sessionAgeText(0L, 61_000L))
        assertEquals("1h", CloudBrowserEngine.sessionAgeText(0L, 3_600_000L))
        assertEquals(
            "3h 12m",
            CloudBrowserEngine.sessionAgeText(0L, (3 * 3600 + 12 * 60) * 1000L),
        )
    }

    @Test
    fun sessionAgeTextClampsNegativeAndTruncatesSubSecond() {
        // Clock skew (now < started) reads as zero, never a negative duration.
        assertEquals("0s", CloudBrowserEngine.sessionAgeText(10_000L, 0L))
        assertEquals("0s", CloudBrowserEngine.sessionAgeText(5_000L, 5_000L - 1L))
        // Millis are truncated by integer division before formatting.
        assertEquals("0s", CloudBrowserEngine.sessionAgeText(0L, 999L))
        assertEquals("1s", CloudBrowserEngine.sessionAgeText(0L, 1_500L))
        assertEquals("1s", CloudBrowserEngine.sessionAgeText(1_000L, 2_000L))
    }

    @Test
    fun labelHelpersAreExhaustiveOverEnums() {
        // Every enum value has a non-blank label; guards against a new enum
        // entry silently falling through to a generic branch.
        Resolution.entries.forEach { resolution ->
            assertTrue(CloudBrowserEngine.resolutionLabel(resolution).isNotBlank())
        }
        Quality.entries.forEach { quality ->
            assertTrue(CloudBrowserEngine.qualityLabel(quality).isNotBlank())
        }
    }
}
