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
 * - [peekPassword] returns a USABLE copy without dropping the entry, because
 *   the same secret is needed first for SSH authentication and again if the
 *   node needs first-time provisioning (sudo installs Chrome on the VPS). The
 *   secret still never touches disk or logs; [clear]/[clearAll] on
 *   disconnect zeroes the stored array.
 * - [consumePassword] removes the entry, zeroes the stored array, and returns
 *   a copy for a caller that takes ownership.
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
    /**
     * Returns a usable copy WITHOUT dropping the entry, or null when nothing
     * is stored. Needed because one secret serves two consumers during a
     * first-time connection: SSH auth and password-protected sudo while the
     * VPS is provisioned. The caller must zero the returned copy after use;
     * the stored copy is wiped on [clear]/[clearAll] (called at disconnect).
     */
    fun peekPassword(serverId: String): CharArray? {
        synchronized(lock) {
            return passwords[serverId]?.copyOf()
        }
    }

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
