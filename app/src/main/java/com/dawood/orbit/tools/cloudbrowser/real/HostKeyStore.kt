package com.dawood.orbit.tools.cloudbrowser.real

import android.content.Context

/**
 * Trust-on-first-use store for SSH host-key fingerprints.
 *
 * Fingerprints are PUBLIC data (derived from the server's public host key),
 * so they are kept in plain MODE_PRIVATE SharedPreferences with no encryption
 * dependency. The only Android type used here is [Context], solely to open
 * the preferences file.
 *
 * Keys are stored per "host:port" as lowercase hex SHA-256 of the host key's
 * encoded bytes. See [sha256FingerprintHex], which is the same function the
 * SSH verifier uses, keeping storage and verification in agreement.
 */
class HostKeyStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    /** Returns the trusted lowercase hex fingerprint for [host]:[port], or null. */
    fun knownFingerprint(host: String, port: Int): String? {
        return prefs.getString(keyFor(host, port), null)
    }

    /** Trusts [sha256] (stored lowercase) for [host]:[port]. */
    fun trust(host: String, port: Int, sha256: String) {
        prefs.edit().putString(keyFor(host, port), sha256.lowercase()).apply()
    }

    private fun keyFor(host: String, port: Int): String {
        return host.trim().lowercase() + ":" + port
    }

    private companion object {
        const val PREFS_NAME = "orbit_host_keys"
    }
}

/**
 * PURE function: SHA-256 of [keyBytes] as 64 lowercase hex chars.
 *
 * Uses java.security.MessageDigest only — no Android import — so it runs on
 * plain JVM unit tests as well as on device.
 */
fun sha256FingerprintHex(keyBytes: ByteArray): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hashed = digest.digest(keyBytes)
    val out = StringBuilder(hashed.size * 2)
    for (b in hashed) {
        val v = b.toInt() and 0xFF
        if (v < 0x10) {
            out.append('0')
        }
        out.append(Integer.toHexString(v))
    }
    return out.toString()
}
