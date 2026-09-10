package com.dawood.orbit.tools.cloudbrowser.real

/**
 * Memory-only vault for per-server SSH passwords.
 *
 * Secrets live in a private in-memory map and are never logged, never
 * persisted to disk, and never leave the process except as an argument to the
 * SSH authentication call. Callers on Android invoke from a background thread.
 *
 * Ownership rules:
 * - [setPassword] copies the caller's array, so the caller may zero its own
 *   copy immediately after calling.
 * - [consumePassword] removes the entry, zeroes the stored array, and returns
 *   a copy for the caller to use. The caller must zero the returned copy
 *   (fill with '\u0000') once authentication has been attempted.
 * - [clear] and [clearAll] overwrite stored arrays with '\u0000' before
 *   dropping them.
 */
object EphemeralCredentials {

    private val lock = Any()
    private val passwords = mutableMapOf<String, CharArray>()

    /**
     * Stores a private copy of [password] for [serverId], zeroing any
     * previously stored entry for the same server first.
     */
    fun setPassword(serverId: String, password: CharArray) {
        val copy = password.copyOf()
        synchronized(lock) {
            passwords.remove(serverId)?.fill('\u0000')
            passwords[serverId] = copy
        }
    }

    /**
     * Removes the entry for [serverId], zeroes the stored array, and returns
     * a usable copy, or null when nothing was stored. The caller owns the
     * returned array and must zero it after use.
     */
    fun consumePassword(serverId: String): CharArray? {
        synchronized(lock) {
            val stored = passwords.remove(serverId) ?: return null
            val copy = stored.copyOf()
            stored.fill('\u0000')
            return copy
        }
    }

    /** Zeroes and drops the entry for [serverId], if present. */
    fun clear(serverId: String) {
        synchronized(lock) {
            passwords.remove(serverId)?.fill('\u0000')
        }
    }

    /** Zeroes and drops every stored entry. */
    fun clearAll() {
        synchronized(lock) {
            for (entry in passwords.values) {
                entry.fill('\u0000')
            }
            passwords.clear()
        }
    }
}
