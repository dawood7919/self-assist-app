package com.dawood.orbit.tools.cloudbrowser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloudBrowserEngineTest {
    @Test
    fun ramProgressUsesServerCapacity() {
        val snapshot = CloudBrowserEngine.ServerSnapshot(ramUsedGb = 2.4, ramTotalGb = 8.0)

        assertEquals(0.3f, CloudBrowserEngine.ramProgress(snapshot))
    }

    @Test
    fun ramProgressIsClampedForUnexpectedServerValues() {
        val snapshot = CloudBrowserEngine.ServerSnapshot(ramUsedGb = 10.0, ramTotalGb = 8.0)

        assertTrue(CloudBrowserEngine.ramProgress(snapshot) <= 1f)
    }

    @Test
    fun connectionLabelCombinesLocationAndLatency() {
        val snapshot = CloudBrowserEngine.ServerSnapshot(location = "Germany", latency = "42 ms")

        assertEquals("Germany • 42 ms", CloudBrowserEngine.connectionLabel(snapshot))
    }
}
