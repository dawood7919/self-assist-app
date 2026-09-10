package com.dawood.orbit.tools.cloudbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameThrottleTest {

    @Test
    fun dropsFramesOverFpsBudget() {
        val throttle = FrameThrottle(maxFps = 5, minIntervalMs = 200)

        assertTrue(throttle.shouldAccept(0L, 1_000))
        assertFalse(throttle.shouldAccept(50L, 1_000))
        assertFalse(throttle.shouldAccept(199L, 1_000))
    }

    @Test
    fun acceptsFrameAfterIntervalPasses() {
        val throttle = FrameThrottle(maxFps = 5, minIntervalMs = 200)

        assertTrue(throttle.shouldAccept(0L, 1_000))
        assertTrue(throttle.shouldAccept(200L, 1_000))
        assertTrue(throttle.shouldAccept(400L, 1_000))
    }

    @Test
    fun rejectsOversizeFrames() {
        val throttle = FrameThrottle(maxBytes = 150_000)

        assertFalse(throttle.shouldAccept(10_000L, 150_001))
        assertTrue(throttle.shouldAccept(10_000L, 150_000))
    }

    @Test
    fun scalesWhileKeepingAspect() {
        val throttle = FrameThrottle()

        assertEquals(Pair(1280, 720), throttle.scaledSize(1920, 1080, 1280))
    }

    @Test
    fun latestWinsWithNoQueue() {
        val throttle = FrameThrottle(maxFps = 5, minIntervalMs = 200)

        assertTrue(throttle.shouldAccept(0L, 1_000))
        // Dropped frames leave no state behind: the window stays anchored at
        // the last ACCEPTED frame, so the newest frame always wins.
        assertFalse(throttle.shouldAccept(100L, 2_000))
        assertTrue(throttle.shouldAccept(200L, 3_000))
    }
}
