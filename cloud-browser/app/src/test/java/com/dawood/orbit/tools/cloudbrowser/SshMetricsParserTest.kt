package com.dawood.orbit.tools.cloudbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure-parsing checks for the SSH metrics probe (JUnit4 only, no device). */
class SshMetricsParserTest {

    private fun probe(
        cpu: String,
        mem: String,
        up: String,
        df: String,
        net: String,
    ): String =
        "__CPU__\n$cpu\n" +
            "__MEM__\n$mem\n" +
            "__UP__\n$up\n" +
            "__DF__\n$df\n" +
            "__NET__\n$net\n" +
            "__END__\n"

    @Test
    fun cpuDeltaAcrossTwoPollsIsAboutFortyTwoPercent() {
        val first = probe(
            cpu = "cpu  100 0 100 800 0 0 0 0 0 0",
            mem = "",
            up = "",
            df = "",
            net = "",
        )
        val second = probe(
            cpu = "cpu  142 0 100 858 0 0 0 0 0 0",
            mem = "",
            up = "",
            df = "",
            net = "",
        )
        val prev = SshMetricsParser.extractCpuSample(first, 0L)!!
        val parsed = SshMetricsParser.parse(second, prev, null, 1_000L)
        assertEquals(42.0f, parsed.cpuPct, 1.0f)
    }

    @Test
    fun firstPollReportsZeroCpu() {
        val text = probe(
            cpu = "cpu  142 0 100 858 0 0 0 0 0 0",
            mem = "",
            up = "",
            df = "",
            net = "",
        )
        val parsed = SshMetricsParser.parse(text, null, null, 1_000L)
        assertEquals(0f, parsed.cpuPct, 0f)
    }

    @Test
    fun meminfoKilobytesBecomeGib() {
        val text = probe(
            cpu = "",
            mem = "MemTotal:        8044544 kB\nMemAvailable:    5123456 kB\n",
            up = "",
            df = "",
            net = "",
        )
        val parsed = SshMetricsParser.parse(text, null, null, 0L)
        assertEquals(7.67, parsed.ramTotalGb, 0.01)
        assertEquals(2.79, parsed.ramUsedGb, 0.01)
    }

    @Test
    fun uptimeFloatBecomesWholeSeconds() {
        val text = probe(cpu = "", mem = "", up = "12345.67 54321.00\n", df = "", net = "")
        val parsed = SshMetricsParser.parse(text, null, null, 0L)
        assertEquals(12_345L, parsed.uptimeSecs)
    }

    @Test
    fun dfByteLineBecomesGib() {
        val text = probe(
            cpu = "",
            mem = "",
            up = "",
            df = "/dev/vda1 107374182400 42949672960 64424509440  40% /\n",
            net = "",
        )
        val parsed = SshMetricsParser.parse(text, null, null, 0L)
        assertEquals(100.0, parsed.diskTotalGb, 0.001)
        assertEquals(40.0, parsed.diskUsedGb, 0.001)
    }

    @Test
    fun networkRatesComeFromCounterDeltas() {
        val text = probe(
            cpu = "",
            mem = "",
            up = "",
            df = "",
            net = "  eth0: 2000000 0 0 0 0 0 0 0 1000000 0 0 0 0 0 0 0\n",
        )
        val prev = SshMetricsParser.NetSample(rxBytes = 1_000_000L, txBytes = 500_000L, atMs = 0L)
        val parsed = SshMetricsParser.parse(text, null, prev, 8_000L)
        assertEquals(1.0, parsed.netDownMbps, 0.001)
        assertEquals(0.5, parsed.netUpMbps, 0.001)
    }

    @Test
    fun firstPollReportsZeroNetworkRates() {
        val text = probe(
            cpu = "",
            mem = "",
            up = "",
            df = "",
            net = "  eth0: 2000000 0 0 0 0 0 0 0 1000000 0 0 0 0 0 0 0\n",
        )
        val parsed = SshMetricsParser.parse(text, null, null, 8_000L)
        assertEquals(0.0, parsed.netDownMbps, 0.0)
        assertEquals(0.0, parsed.netUpMbps, 0.0)
    }

    @Test
    fun emptyInputYieldsZerosWithoutThrowing() {
        val parsed = SshMetricsParser.parse("", null, null, 0L)
        assertEquals(SshMetricsParser.ParsedMetrics(), parsed)
        assertNull(SshMetricsParser.extractCpuSample("", 0L))
        assertNull(SshMetricsParser.extractNetSample("", 0L))
    }

    @Test
    fun nonNumericMemTotalYieldsZeroRam() {
        val text = probe(
            cpu = "",
            mem = "MemTotal: abc kB\nMemAvailable: 5123456 kB\n",
            up = "",
            df = "",
            net = "",
        )
        val parsed = SshMetricsParser.parse(text, null, null, 0L)
        assertEquals(0.0, parsed.ramTotalGb, 0.0)
        assertEquals(0.0, parsed.ramUsedGb, 0.0)
    }
}
