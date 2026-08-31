package com.dawood.orbit.tools.videodownloader.resolve

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * One HTTP client for the whole tool.
 *
 * Read timeout is deliberately generous: a slow mobile connection stalling for
 * half a minute mid-file is normal and must not be treated as a failure, since
 * every failure costs the user a resume round trip.
 */
object HttpClients {

    /**
     * Plenty of media hosts reject the default OkHttp agent outright, so the
     * client identifies as a normal browser.
     */
    const val USER_AGENT: String =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

    val shared: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
}
