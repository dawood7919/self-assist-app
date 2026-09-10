package com.dawood.orbit.tools.cloudbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudPollingTest {

    private val metrics = VpsMetrics(cpuPct = 18f, ramUsedGb = 2.4, ramTotalGb = 8.0, isLive = false)

    @Test
    fun successResetsFailuresAndDelay() {
        val state = CloudPolling.foldMetrics(prev = null, next = Result.success(metrics), failures = 3)

        assertEquals(metrics, state.metrics)
        assertEquals(0, state.failures)
        assertEquals(1_000L, state.delayMs)
    }

    @Test
    fun failureKeepsPreviousMetricsAndBacksOff() {
        val first = CloudPolling.foldMetrics(null, Result.failure(RuntimeException("down")), 0)
        assertNull(first.metrics)
        assertEquals(1, first.failures)
        assertEquals(2_000L, first.delayMs)

        val second = CloudPolling.foldMetrics(metrics, Result.failure(RuntimeException("down")), first.failures)
        assertEquals(metrics, second.metrics)
        assertEquals(2, second.failures)
        assertEquals(4_000L, second.delayMs)
    }

    @Test
    fun delayCapsAtEightSeconds() {
        val state = CloudPolling.foldMetrics(metrics, Result.failure(RuntimeException("down")), 10)

        assertEquals(11, state.failures)
        assertEquals(8_000L, state.delayMs)
    }

    @Test
    fun negativeFailuresAreTreatedAsZero() {
        val failed = CloudPolling.foldMetrics(null, Result.failure(RuntimeException("down")), -5)

        assertEquals(1, failed.failures)
        assertNull(failed.metrics)
        assertEquals(2_000L, failed.delayMs)
        assertEquals(PollState(), CloudPolling.initial())
    }
}
