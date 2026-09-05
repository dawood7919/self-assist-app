package com.dawood.orbit.tools.cloudbrowser

import android.content.Context

/**
 * Connection settings for the remote Chromium session on the VPS.
 * Defaults point at the personal Orbit VPS reverse proxy.
 */
class CloudBrowserSettings private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var host: String
        get() = prefs.getString(KEY_HOST, DEFAULT_HOST).orEmpty().ifBlank { DEFAULT_HOST }
        set(value) = prefs.edit().putString(KEY_HOST, value.trim()).apply()

    var port: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_PORT, value.coerceIn(1, 65535)).apply()

    var username: String
        get() = prefs.getString(KEY_USER, DEFAULT_USER).orEmpty().ifBlank { DEFAULT_USER }
        set(value) = prefs.edit().putString(KEY_USER, value.trim()).apply()

    var password: String
        get() = prefs.getString(KEY_PASS, DEFAULT_PASS).orEmpty()
        set(value) = prefs.edit().putString(KEY_PASS, value).apply()

    var useHttps: Boolean
        get() = prefs.getBoolean(KEY_HTTPS, false)
        set(value) = prefs.edit().putBoolean(KEY_HTTPS, value).apply()

    fun baseUrl(): String {
        val scheme = if (useHttps) "https" else "http"
        return "$scheme://${host.trim()}:$port/"
    }

    companion object {
        private const val PREFS = "cloud_browser"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_USER = "user"
        private const val KEY_PASS = "pass"
        private const val KEY_HTTPS = "https"

        const val DEFAULT_HOST = "43.134.10.177"
        const val DEFAULT_PORT = 8088
        const val DEFAULT_USER = "orbit"
        const val DEFAULT_PASS = "OrbitCloud911"

        @Volatile
        private var instance: CloudBrowserSettings? = null

        fun get(context: Context): CloudBrowserSettings =
            instance ?: synchronized(this) {
                instance ?: CloudBrowserSettings(context).also { instance = it }
            }
    }
}
