package com.dawood.orbit.tools.cloudbrowser.real

import android.content.Context
import com.dawood.orbit.tools.cloudbrowser.AuthMethod
import com.dawood.orbit.tools.cloudbrowser.SavedServer
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.ServerSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.PublicKey
import java.security.Security
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import org.spongycastle.jce.provider.BouncyCastleProvider

/**
 * Single-owner SSH transport for the Cloud Browser REAL backend.
 *
 * Blocking contract: EVERY method below blocks on network I/O and MUST be
 * called from a background thread (callers already run on Dispatchers.IO).
 * No method posts to Main, touches the UI, or launches coroutines — callers
 * own threading. All [SSHClient] access is guarded by [lock]; the local port
 * forward handle lives in [tunnel] so open/close are race-free.
 *
 * Security rules:
 * - Trust-on-first-use host-key verification. An unknown key NEVER
 *   auto-trusts: connect fails with a message carrying "SHA256:<hex>" plus
 *   host:port so the UI can ask the user to approve it. A changed key fails
 *   closed with expected-vs-got fingerprints.
 * - Key files are used by path at the auth moment only; key bytes are never
 *   cached here. Passwords come from [EphemeralCredentials] and the consumed
 *   copy is zeroed in a finally block.
 * - Crypto provider is SpongyCastle ("SC") only. The stock "BC" provider is
 *   removed at init and never installed: Android ships a stripped BC copy
 *   that breaks SSHJ key parsing.
 *
 * Error-message contract (user-facing prefixes, matched verbatim by UI):
 * - L0 unreachable: "Cannot reach host:port — check host spelling, port, and
 *   that the VPS firewall allows SSH (TCP/22)."
 * - L1 key: "SSH key rejected — check key path and that the public key is in
 *   ~/.ssh/authorized_keys."
 * - L1 password: "Password rejected — check username/password."
 * - L1 unknown-host: "Unknown host key (SHA256:…) — approve it to connect."
 * - L1 timeout: "SSH timed out after 10 s — VPS overloaded or wrong port."
 */
class SshManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val hostKeyStore = HostKeyStore(appContext)

    private val lock = ReentrantLock()
    private var client: SSHClient? = null
    private val tunnel = AtomicReference<ActiveTunnel?>(null)

    /**
     * Blocking: opens the control connection and authenticates.
     * Must be called on Dispatchers.IO. Replaces any stale connection.
     */
    fun connect(server: SavedServer): Result<Unit> {
        ensureProvider()
        if (server.host.isBlank()) {
            return Result.failure(
                Exception(
                    "Cannot reach ${server.host}:${server.port} — check host spelling, " +
                        "port, and that the VPS firewall allows SSH (TCP/22). " +
                        "Detail: empty hostname.",
                ),
            )
        }
        if (server.username.isBlank()) {
            return Result.failure(
                Exception(authPrefix(server) + " Detail: empty username."),
            )
        }
        lock.lock()
        try {
            dropLocked()
            val c = SSHClient()
            c.addHostKeyVerifier(TofuVerifier(server.host, server.port))
            c.setConnectTimeout(CONNECT_TIMEOUT_MS)
            c.setTimeout(CONNECT_TIMEOUT_MS)
            try {
                c.connect(server.host, server.port)
            } catch (e: Exception) {
                closeQuietly(c)
                return Result.failure(mapConnectFailure(server, e))
            }
            val authResult = authenticate(c, server)
            if (authResult.isFailure) {
                closeQuietly(c)
                return authResult
            }
            client = c
            return Result.success(Unit)
        } finally {
            lock.unlock()
        }
    }

    /**
     * Blocking: drops the tunnel and the control connection.
     * Must be called on Dispatchers.IO. Succeeds when already disconnected.
     */
    fun disconnect(): Result<Unit> {
        lock.lock()
        try {
            dropLocked()
            return Result.success(Unit)
        } finally {
            lock.unlock()
        }
    }

    /** Thread-safe snapshot: true only when connected AND authenticated. */
    fun isConnected(): Boolean {
        lock.lock()
        try {
            val c = client
            if (c == null) {
                return false
            }
            return try {
                c.isConnected && c.isAuthenticated
            } catch (_: Exception) {
                false
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * Blocking: runs [cmd] and returns stdout.
     * Must be called on Dispatchers.IO. Fails when not connected, on timeout
     * ([timeoutMs] default 10_000), or on non-zero remote exit status.
     */
    fun exec(cmd: String, timeoutMs: Long = 10_000): Result<String> {
        val c: SSHClient?
        lock.lock()
        try {
            c = client
        } finally {
            lock.unlock()
        }
        if (c == null) {
            return Result.failure(Exception("Not connected — call connect() first."))
        }
        try {
            if (!c.isConnected) {
                return Result.failure(Exception("Not connected — call connect() first."))
            }
        } catch (e: Exception) {
            return Result.failure(mapExecFailure(e))
        }
        return try {
            val session = c.startSession()
            try {
                val remoteCmd = session.exec(cmd)
                try {
                    remoteCmd.join(timeoutMs, TimeUnit.MILLISECONDS)
                } catch (e: Exception) {
                    return Result.failure(mapExecFailure(e))
                }
                val stdout: String
                val stderr: String
                try {
                    stdout = String(IOUtils.readFully(remoteCmd.inputStream), Charsets.UTF_8)
                } catch (e: Exception) {
                    return Result.failure(mapExecFailure(e))
                }
                try {
                    stderr = String(IOUtils.readFully(remoteCmd.errorStream), Charsets.UTF_8)
                } catch (_: Exception) {
                    stderr = ""
                }
                val exit = try {
                    remoteCmd.exitStatus
                } catch (_: Exception) {
                    null
                }
                if (exit != null && exit != 0) {
                    val detail = stderr.trim().take(300)
                    return Result.failure(
                        Exception("Remote command failed (exit $exit): $detail"),
                    )
                }
                Result.success(stdout)
            } finally {
                try {
                    session.close()
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            Result.failure(mapExecFailure(e))
        }
    }

    /**
     * Blocking: opens an SFTP client on the current connection.
     * Must be called on Dispatchers.IO. The CALLER owns and must close the
     * returned [SFTPClient] (it holds a channel on this connection).
     */
    fun sftp(): Result<SFTPClient> {
        val c: SSHClient?
        lock.lock()
        try {
            c = client
        } finally {
            lock.unlock()
        }
        if (c == null) {
            return Result.failure(Exception("Not connected — call connect() first."))
        }
        return try {
            Result.success(c.newSFTPClient())
        } catch (e: Exception) {
            Result.failure(mapExecFailure(e))
        }
    }

    /**
     * Blocking (fast: bind only; the listener runs on a daemon thread).
     * Must be called on Dispatchers.IO. Forwards ephemeral 127.0.0.1:<local>
     * to 127.0.0.1:[remotePort] on the VPS. Reuses the existing forward when
     * it already targets [remotePort]. Returns the local port number.
     */
    fun openTunnel(remotePort: Int = 9222): Result<Int> {
        lock.lock()
        try {
            val c = client
            if (c == null) {
                return Result.failure(Exception("Not connected — call connect() first."))
            }
            try {
                if (!c.isConnected) {
                    return Result.failure(Exception("Not connected — call connect() first."))
                }
            } catch (e: Exception) {
                return Result.failure(mapExecFailure(e))
            }
            val existing = tunnel.get()
            if (existing != null &&
                existing.remotePort == remotePort &&
                !existing.serverSocket.isClosed &&
                existing.forwarder.isRunning
            ) {
                return Result.success(existing.localPort)
            }
            closeTunnelLocked()
            return try {
                val ss = ServerSocket()
                ss.setReuseAddress(true)
                ss.bind(InetSocketAddress("127.0.0.1", 0))
                val localPort = ss.localPort
                val params = LocalPortForwarder.Parameters(
                    "127.0.0.1",
                    localPort,
                    "127.0.0.1",
                    remotePort,
                )
                val forwarder = c.newLocalPortForwarder(params, ss)
                val listener = Thread({
                    try {
                        forwarder.listen()
                    } catch (_: IOException) {
                    } catch (_: Exception) {
                    }
                }, "orbit-ssh-tunnel")
                listener.isDaemon = true
                listener.start()
                tunnel.set(ActiveTunnel(forwarder, ss, localPort, remotePort))
                Result.success(localPort)
            } catch (e: Exception) {
                Result.failure(mapExecFailure(e))
            }
        } finally {
            lock.unlock()
        }
    }

    /** Thread-safe: stops the local forward, if any. Never throws. */
    fun closeTunnel() {
        lock.lock()
        try {
            closeTunnelLocked()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Blocking: connects, authenticates, then disconnects, keeping no state.
     * Must be called on Dispatchers.IO. Used as a credential check without
     * leaving a session behind.
     */
    fun quickAuth(server: SavedServer): Result<Unit> {
        val result = connect(server)
        try {
            return result
        } finally {
            try {
                disconnect()
            } catch (_: Exception) {
            }
        }
    }

    // ---- internals (all assume the caller holds [lock] unless noted) ----

    private fun dropLocked() {
        closeTunnelLocked()
        val c = client
        client = null
        if (c != null) {
            closeQuietly(c)
        }
    }

    private fun closeTunnelLocked() {
        val t = tunnel.getAndSet(null)
        if (t != null) {
            try {
                t.forwarder.close()
            } catch (_: Exception) {
            }
            try {
                t.serverSocket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun authenticate(c: SSHClient, server: SavedServer): Result<Unit> {
        return if (server.authMethod == AuthMethod.SshKey) {
            authenticateWithKey(c, server)
        } else {
            authenticateWithPassword(c, server)
        }
    }

    private fun authenticateWithKey(c: SSHClient, server: SavedServer): Result<Unit> {
        val keyPath = server.keyPath
        if (keyPath.isNullOrBlank()) {
            return Result.failure(
                Exception(
                    KEY_REJECTED_PREFIX +
                        " Detail: no key file configured for this server.",
                ),
            )
        }
        if (!File(keyPath).exists()) {
            return Result.failure(
                Exception(KEY_REJECTED_PREFIX + " Detail: key file not found: $keyPath"),
            )
        }
        return try {
            c.authPublickey(server.username, keyPath)
            Result.success(Unit)
        } catch (e: UserAuthException) {
            Result.failure(Exception(KEY_REJECTED_PREFIX + " Detail: ${e.message}"))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception(TIMEOUT_PREFIX + " Detail: ${e.message}"))
        } catch (e: TimeoutException) {
            Result.failure(Exception(TIMEOUT_PREFIX + " Detail: ${e.message}"))
        } catch (e: TransportException) {
            if (isTimeoutMessage(e.message)) {
                Result.failure(Exception(TIMEOUT_PREFIX + " Detail: ${e.message}"))
            } else {
                Result.failure(Exception(KEY_REJECTED_PREFIX + " Detail: ${e.message}"))
            }
        } catch (e: Exception) {
            if (isTimeoutMessage(e.message)) {
                Result.failure(Exception(TIMEOUT_PREFIX + " Detail: ${e.message}"))
            } else {
                Result.failure(Exception(KEY_REJECTED_PREFIX + " Detail: ${e.message}"))
            }
        }
    }

    private fun authenticateWithPassword(c: SSHClient, server: SavedServer): Result<Unit> {
        var password: CharArray? = null
        try {
            password = EphemeralCredentials.consumePassword(server.id)
            if (password == null) {
                return Result.failure(
                    Exception(
                        PASSWORD_REJECTED_PREFIX +
                            " Detail: no password stored for this session.",
                    ),
                )
            }
            return try {
                c.authPassword(server.username, password)
                Result.success(Unit)
            } catch (e: UserAuthException) {
                Result.failure(Exception(PASSWORD_REJECTED_PREFIX + " Detail: ${e.message}"))
            } catch (e: SocketTimeoutException) {
                Result.failure(Exception(TIMEOUT_PREFIX + " Detail: ${e.message}"))
            } catch (e: TimeoutException) {
                Result.failure(Exception(TIMEOUT_PREFIX + " Detail: ${e.message}"))
            } catch (e: TransportException) {
                if (isTimeoutMessage(e.message)) {
                    Result.failure(Exception(TIMEOUT_PREFIX + " Detail: ${e.message}"))
                } else {
                    Result.failure(Exception(PASSWORD_REJECTED_PREFIX + " Detail: ${e.message}"))
                }
            } catch (e: Exception) {
                if (isTimeoutMessage(e.message)) {
                    Result.failure(Exception(TIMEOUT_PREFIX + " Detail: ${e.message}"))
                } else {
                    Result.failure(Exception(PASSWORD_REJECTED_PREFIX + " Detail: ${e.message}"))
                }
            }
        } finally {
            try {
                password?.fill('\u0000')
            } catch (_: Exception) {
            }
        }
    }

    private inner class TofuVerifier(
        private val host: String,
        private val port: Int,
    ) : HostKeyVerifier {

        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
            val fingerprint = try {
                sha256FingerprintHex(key.encoded)
            } catch (_: Exception) {
                throw UnknownHostKeyException(
                    "Unknown host key (SHA256:unavailable) — approve it to connect. " +
                        "($host:$port)",
                )
            }
            val known = try {
                hostKeyStore.knownFingerprint(host, port)
            } catch (_: Exception) {
                null
            }
            if (known == null) {
                throw UnknownHostKeyException(
                    "Unknown host key (SHA256:$fingerprint) — approve it to connect. " +
                        "($host:$port SHA256:$fingerprint)",
                )
            }
            if (!known.equals(fingerprint, ignoreCase = true)) {
                throw HostKeyMismatchException(
                    "Host key mismatch for $host:$port — expected SHA256:$known " +
                        "but got SHA256:$fingerprint. Failing closed.",
                )
            }
            return true
        }

        override fun findExistingAlgorithms(hostname: String, port: Int): List<String> {
            return emptyList()
        }
    }

    private fun authPrefix(server: SavedServer): String {
        return if (server.authMethod == AuthMethod.SshKey) {
            KEY_REJECTED_PREFIX
        } else {
            PASSWORD_REJECTED_PREFIX
        }
    }

    private fun mapConnectFailure(server: SavedServer, e: Exception): Exception {
        val hostKeyFailure = findHostKeyFailure(e)
        if (hostKeyFailure != null) {
            return hostKeyFailure
        }
        if (isTimeout(e)) {
            return Exception(TIMEOUT_PREFIX + " Detail: ${firstMessage(e)}")
        }
        if (isUnreachable(e)) {
            return Exception(
                "Cannot reach ${server.host}:${server.port} — check host spelling, " +
                    "port, and that the VPS firewall allows SSH (TCP/22). " +
                    "Detail: ${firstMessage(e)}",
            )
        }
        return Exception(
            "Cannot reach ${server.host}:${server.port} — check host spelling, " +
                "port, and that the VPS firewall allows SSH (TCP/22). " +
                "Detail: ${firstMessage(e)}",
        )
    }

    private fun mapExecFailure(e: Exception): Exception {
        val hostKeyFailure = findHostKeyFailure(e)
        if (hostKeyFailure != null) {
            return hostKeyFailure
        }
        if (isTimeout(e)) {
            return Exception(TIMEOUT_PREFIX + " Detail: ${firstMessage(e)}")
        }
        return Exception(firstMessage(e))
    }

    private fun findHostKeyFailure(e: Throwable): Exception? {
        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 12) {
            if (current is UnknownHostKeyException || current is HostKeyMismatchException) {
                return current as Exception
            }
            val message = current.message ?: ""
            if (message.contains("Unknown host key (SHA256:") ||
                message.contains("Host key mismatch for")
            ) {
                return Exception(message)
            }
            current = current.cause
            depth += 1
        }
        return null
    }

    private fun isTimeout(e: Throwable): Boolean {
        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 12) {
            if (current is SocketTimeoutException || current is TimeoutException) {
                return true
            }
            if (isTimeoutMessage(current.message)) {
                return true
            }
            current = current.cause
            depth += 1
        }
        return false
    }

    private fun isTimeoutMessage(message: String?): Boolean {
        if (message == null) {
            return false
        }
        val lower = message.lowercase()
        return lower.contains("timed out") || lower.contains("timeout") ||
            lower.contains("connect timed out")
    }

    private fun isUnreachable(e: Throwable): Boolean {
        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 12) {
            if (current is UnknownHostException ||
                current is ConnectException ||
                current is NoRouteToHostException
            ) {
                return true
            }
            if (current is SocketException) {
                return true
            }
            val lower = (current.message ?: "").lowercase()
            if (lower.contains("refused") ||
                lower.contains("unreachable") ||
                lower.contains("no route") ||
                lower.contains("unknown host") ||
                lower.contains("failed to connect")
            ) {
                return true
            }
            current = current.cause
            depth += 1
        }
        return false
    }

    private fun firstMessage(e: Throwable): String {
        var current: Throwable? = e
        while (current != null) {
            val message = current.message
            if (!message.isNullOrBlank()) {
                return message.take(300)
            }
            current = current.cause
        }
        return e.javaClass.simpleName
    }

    private fun closeQuietly(c: SSHClient) {
        try {
            c.disconnect()
        } catch (_: Exception) {
        }
        try {
            c.close()
        } catch (_: Exception) {
        }
    }

    private data class ActiveTunnel(
        val forwarder: LocalPortForwarder,
        val serverSocket: ServerSocket,
        val localPort: Int,
        val remotePort: Int,
    )

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000

        private const val KEY_REJECTED_PREFIX =
            "SSH key rejected — check key path and that the public key is in ~/.ssh/authorized_keys."
        private const val PASSWORD_REJECTED_PREFIX =
            "Password rejected — check username/password."
        private const val TIMEOUT_PREFIX =
            "SSH timed out after 10 s — VPS overloaded or wrong port."

        private val providerInstalled = AtomicBoolean(false)

        /**
         * Installs SpongyCastle ("SC") at position 1 once, removing stock
         * "BC". Background-safe: safe to call from any Dispatchers.IO thread;
         * the AtomicBoolean guarantees single installation.
         */
        private fun ensureProvider() {
            if (providerInstalled.compareAndSet(false, true)) {
                try {
                    Security.removeProvider("BC")
                } catch (_: Exception) {
                }
                try {
                    if (Security.getProvider("SC") == null) {
                        Security.insertProviderAt(BouncyCastleProvider(), 1)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }
}

/** Thrown by the TOFU verifier for a host with no trusted fingerprint. Never auto-trusted. */
private class UnknownHostKeyException(message: String) : RuntimeException(message)

/** Thrown by the TOFU verifier when the key changed. Fails closed. */
private class HostKeyMismatchException(message: String) : RuntimeException(message)
