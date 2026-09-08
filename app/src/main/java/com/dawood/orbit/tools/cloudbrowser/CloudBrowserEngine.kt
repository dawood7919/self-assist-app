package com.dawood.orbit.tools.cloudbrowser

/** Pure presentation data for the remote browser dashboard. */
object CloudBrowserEngine {
    data class ServerSnapshot(
        val name: String = "My VPS Server",
        val location: String = "Germany",
        val ip: String = "185.xxx.xxx.xxx",
        val latency: String = "42 ms",
        val cpu: Int = 18,
        val ramUsedGb: Double = 2.4,
        val ramTotalGb: Double = 8.0,
        val bandwidth: String = "1 Gbps",
    )

    data class Session(val name: String, val browser: String, val state: String, val duration: String)

    fun ramProgress(snapshot: ServerSnapshot): Float =
        (snapshot.ramUsedGb / snapshot.ramTotalGb).toFloat().coerceIn(0f, 1f)

    fun connectionLabel(snapshot: ServerSnapshot): String =
        "${snapshot.location} • ${snapshot.latency}"
}
