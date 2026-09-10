package com.dawood.orbit.tools.cloudbrowser

/**
 * Parser for the single-shot VPS health probe used by the REAL backend.
 *
 * Pure Kotlin — zero android.* / androidx.* imports — so it is covered by JVM
 * unit tests without a device. [COMMAND] is executed once per metrics poll
 * over SSH; [parse] turns its stdout into [ParsedMetrics].
 *
 * The probe prints five labelled sections; every parser below tolerates a
 * missing or malformed section by falling back to zeros. [parse] never
 * throws: any unexpected input (including an empty string) yields a zeroed
 * [ParsedMetrics].
 *
 * CPU percentage comes from /proc/stat idle/total deltas between two polls,
 * so the first poll (no previous sample) always reports 0f. Network rates
 * come from /proc/net/dev byte-counter deltas over the wall-clock interval,
 * so the first poll always reports 0.0 Mbps. Callers keep the previous
 * samples between polls; [extractCpuSample] and [extractNetSample] expose
 * the current raw counters for that purpose.
 */
object SshMetricsParser {

    /**
     * Single bash probe printing `__CPU__` (first line of /proc/stat),
     * `__MEM__` (MemTotal/MemAvailable), `__UP__` (/proc/uptime),
     * `__DF__` (byte counts for /), and `__NET__` (first physical-looking
     * interface lines of /proc/net/dev). One exec call per poll.
     */
    const val COMMAND =
        "echo __CPU__; head -n 1 /proc/stat; " +
            "echo __MEM__; grep -E '^(MemTotal|MemAvailable):' /proc/meminfo; " +
            "echo __UP__; cat /proc/uptime; " +
            "echo __DF__; df -B1 / | tail -n 1; " +
            "echo __NET__; grep -E 'eth0|ens|enp|wlan' /proc/net/dev | head -n 5; " +
            "echo __END__"

    /** Raw CPU counters captured at [atMs] wall-clock millis. */
    data class CpuSample(
        val total: Long = 0L,
        val idle: Long = 0L,
        val atMs: Long = 0L,
    )

    /** Raw interface byte counters captured at [atMs] wall-clock millis. */
    data class NetSample(
        val rxBytes: Long = 0L,
        val txBytes: Long = 0L,
        val atMs: Long = 0L,
    )

    /** Point-in-time health numbers; zeros when the probe gave nothing usable. */
    data class ParsedMetrics(
        val cpuPct: Float = 0f,
        val ramUsedGb: Double = 0.0,
        val ramTotalGb: Double = 0.0,
        val uptimeSecs: Long = 0L,
        val diskUsedGb: Double = 0.0,
        val diskTotalGb: Double = 0.0,
        val netDownMbps: Double = 0.0,
        val netUpMbps: Double = 0.0,
    )

    /**
     * Parses probe [text] into metrics. [prevCpu]/[prevNet] are the samples
     * returned by [extractCpuSample]/[extractNetSample] for the previous
     * poll (null on the first poll, which reports cpu 0f and net 0.0).
     * [nowMs] is the current wall-clock time in millis. Never throws.
     */
    fun parse(
        text: String,
        prevCpu: CpuSample?,
        prevNet: NetSample?,
        nowMs: Long,
    ): ParsedMetrics {
        return try {
            parseInternal(text, prevCpu, prevNet, nowMs)
        } catch (_: Exception) {
            ParsedMetrics()
        }
    }

    /**
     * Extracts the current raw CPU counters from probe [text], or null when
     * the `__CPU__` section is missing or malformed. Never throws.
     */
    fun extractCpuSample(text: String, nowMs: Long): CpuSample? {
        return try {
            val (total, idle) = parseCpuLine(section(text, "__CPU__")) ?: return null
            CpuSample(total = total, idle = idle, atMs = nowMs)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts the current raw network counters from probe [text], or null
     * when the `__NET__` section has no usable interface line. Never throws.
     */
    fun extractNetSample(text: String, nowMs: Long): NetSample? {
        return try {
            val (rx, tx) = parseNetLine(section(text, "__NET__")) ?: return null
            NetSample(rxBytes = rx, txBytes = tx, atMs = nowMs)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseInternal(
        text: String,
        prevCpu: CpuSample?,
        prevNet: NetSample?,
        nowMs: Long,
    ): ParsedMetrics {
        val cpuSection = section(text, "__CPU__")
        val memSection = section(text, "__MEM__")
        val upSection = section(text, "__UP__")
        val dfSection = section(text, "__DF__")
        val netSection = section(text, "__NET__")

        val currentCpu = parseCpuLine(cpuSection)
        val cpuPct = if (prevCpu == null || currentCpu == null) {
            0f
        } else {
            val totalDelta = currentCpu.first - prevCpu.total
            val idleDelta = currentCpu.second - prevCpu.idle
            if (totalDelta <= 0L) {
                0f
            } else {
                ((1.0 - idleDelta.toDouble() / totalDelta.toDouble()) * 100.0)
                    .coerceIn(0.0, 100.0).toFloat()
            }
        }

        val (ramUsedGb, ramTotalGb) = parseMeminfo(memSection)
        val uptimeSecs = parseUptime(upSection)
        val (diskUsedGb, diskTotalGb) = parseDf(dfSection)

        val currentNet = parseNetLine(netSection)
        val (netDownMbps, netUpMbps) = if (prevNet == null || currentNet == null) {
            0.0 to 0.0
        } else {
            val intervalSecs = (nowMs - prevNet.atMs).toDouble() / 1000.0
            if (intervalSecs <= 0.0) {
                0.0 to 0.0
            } else {
                val down = ((currentNet.first - prevNet.rxBytes).coerceAtLeast(0L).toDouble() * 8.0 / 1_000_000.0 / intervalSecs)
                    .coerceAtLeast(0.0)
                val up = ((currentNet.second - prevNet.txBytes).coerceAtLeast(0L).toDouble() * 8.0 / 1_000_000.0 / intervalSecs)
                    .coerceAtLeast(0.0)
                down to up
            }
        }

        return ParsedMetrics(
            cpuPct = cpuPct,
            ramUsedGb = ramUsedGb,
            ramTotalGb = ramTotalGb,
            uptimeSecs = uptimeSecs,
            diskUsedGb = diskUsedGb,
            diskTotalGb = diskTotalGb,
            netDownMbps = netDownMbps,
            netUpMbps = netUpMbps,
        )
    }

    /** Returns the slice of [text] after [marker] up to the next section marker. */
    private fun section(text: String, marker: String): String {
        val start = text.indexOf(marker)
        if (start < 0) return ""
        val from = start + marker.length
        var end = text.length
        for (candidate in SECTION_MARKERS) {
            if (candidate == marker) continue
            val index = text.indexOf(candidate, from)
            if (index >= 0 && index < end) end = index
        }
        return text.substring(from, end)
    }

    /**
     * Parses the `cpu ...` aggregate line into (total, idle) jiffy counters,
     * where idle includes iowait. Returns null when absent or malformed.
     */
    private fun parseCpuLine(cpuSection: String): Pair<Long, Long>? {
        val line = cpuSection.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("cpu ") }
            ?: return null
        val numbers = line.split(WHITESPACE).drop(1).mapNotNull { it.toLongOrNull() }
        if (numbers.size < 4) return null
        val total = numbers.sum()
        val idle = numbers[3] + (numbers.getOrNull(4) ?: 0L)
        if (total <= 0L) return null
        return total to idle
    }

    /** Parses MemTotal/MemAvailable (kB) into (used GiB, total GiB). */
    private fun parseMeminfo(memSection: String): Pair<Double, Double> {
        val totalKb = memSection.lineSequence()
            .firstOrNull { it.trimStart().startsWith("MemTotal:") }
            ?.let { MEM_VALUE.find(it)?.groupValues?.getOrNull(1)?.toLongOrNull() }
            ?: return 0.0 to 0.0
        if (totalKb <= 0L) return 0.0 to 0.0
        val availableKb = memSection.lineSequence()
            .firstOrNull { it.trimStart().startsWith("MemAvailable:") }
            ?.let { MEM_VALUE.find(it)?.groupValues?.getOrNull(1)?.toLongOrNull() }
            ?: totalKb
        val usedKb = (totalKb - availableKb).coerceAtLeast(0L)
        return usedKb.toDouble() / KB_PER_GB to totalKb.toDouble() / KB_PER_GB
    }

    /** Parses the first /proc/uptime float into whole seconds. */
    private fun parseUptime(upSection: String): Long {
        val first = upSection.trim().split(WHITESPACE).firstOrNull() ?: return 0L
        return first.toDoubleOrNull()?.toLong()?.coerceAtLeast(0L) ?: 0L
    }

    /**
     * Parses the `df -B1 /` data line into (used GiB, total GiB).
     * Expects `<fs> <totalBytes> <usedBytes> ...`; anything else is zeros.
     */
    private fun parseDf(dfSection: String): Pair<Double, Double> {
        val line = dfSection.lineSequence()
            .map { it.trim() }
            .lastOrNull { it.isNotEmpty() }
            ?: return 0.0 to 0.0
        val tokens = line.split(WHITESPACE)
        if (tokens.size < 3) return 0.0 to 0.0
        val totalBytes = tokens[1].toLongOrNull() ?: return 0.0 to 0.0
        val usedBytes = tokens[2].toLongOrNull() ?: return 0.0 to 0.0
        if (totalBytes <= 0L) return 0.0 to 0.0
        return usedBytes.coerceAtLeast(0L).toDouble() / BYTES_PER_GB to
            totalBytes.toDouble() / BYTES_PER_GB
    }

    /**
     * Parses the first usable physical-interface line of /proc/net/dev into
     * (rxBytes, txBytes). Matches eth0, ens*, enp* and wlan* interfaces;
     * returns null when no line parses.
     */
    private fun parseNetLine(netSection: String): Pair<Long, Long>? {
        for (raw in netSection.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || !line.contains(':')) continue
            val name = line.substringBefore(':').trim()
            val physical = name == "eth0" ||
                name.startsWith("ens") ||
                name.startsWith("enp") ||
                name.startsWith("wlan")
            if (!physical) continue
            val fields = line.substringAfter(':').trim().split(WHITESPACE)
            if (fields.size < 9) continue
            val rx = fields[0].toLongOrNull() ?: continue
            val tx = fields[8].toLongOrNull() ?: continue
            if (rx < 0L || tx < 0L) continue
            return rx to tx
        }
        return null
    }

    companion object {
        private val SECTION_MARKERS = listOf("__CPU__", "__MEM__", "__UP__", "__DF__", "__NET__", "__END__")
        private val WHITESPACE = Regex("\\s+")
        private val MEM_VALUE = Regex(":\\s*(\\d+)")
        private const val KB_PER_GB = 1024.0 * 1024.0
        private const val BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0
    }
}
