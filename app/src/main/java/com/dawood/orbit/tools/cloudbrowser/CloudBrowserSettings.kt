package com.dawood.orbit.tools.cloudbrowser

import android.content.Context

/**
 * Connection settings for the remote Chromium session on the VPS.
 * Defaults point at the personal Orbit VPS reverse proxy (HTTPS).
 */
class CloudBrowserSettings private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        // One-time migration: older builds used plain HTTP on 8088 which
        // Selkies rejects with "requires a secure connection".
        if (!prefs.getBoolean(KEY_MIGRATED_HTTPS, false)) {
            val port = prefs.getInt(KEY_PORT, DEFAULT_PORT)
            val https = prefs.getBoolean(KEY_HTTPS, false)
            if (!https && (port == 8088 || !prefs.contains(KEY_PORT))) {
                prefs.edit()
                    .putBoolean(KEY_HTTPS, true)
                    .putInt(KEY_PORT, DEFAULT_PORT)
                    .putBoolean(KEY_MIGRATED_HTTPS, true)
                    .apply()
            } else {
                prefs.edit().putBoolean(KEY_MIGRATED_HTTPS, true).apply()
            }
        }
    }

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
        get() = prefs.getBoolean(KEY_HTTPS, true)
        set(value) = prefs.edit().putBoolean(KEY_HTTPS, value).apply()

    /** Base URL of the remote stream + control API (always ends with /). */
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
        private const val KEY_MIGRATED_HTTPS = "migrated_https_v1"

        const val DEFAULT_HOST = "43.134.10.177"
        /** HTTPS reverse proxy (Selkies requires a secure context). */
        const val DEFAULT_PORT = 8443
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
