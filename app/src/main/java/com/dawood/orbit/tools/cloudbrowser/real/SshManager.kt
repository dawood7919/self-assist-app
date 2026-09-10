package com.dawood.orbit.tools.cloudbrowser.real

import android.content.Context
import com.dawood.orbit.tools.cloudbrowser.AuthMethod
import com.dawood.orbit.tools.cloudbrowser.SavedServer
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import com.jcraft.jsch.UserInfo
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

/**
 * Single-owner SSH transport for the Cloud Browser REAL backend.
 *
 * Blocking contract: EVERY method below blocks on network I/O and MUST be
 * called from a background thread (callers already run on Dispatchers.IO).
 * No method posts to Main, touches the UI, or launches coroutines — callers
 * own threading. All [Session] access is guarded by [lock]; the local port
 * forward handle lives in [tunnel] so open/close are race-free.
 *
 * Security rules:
 * - Trust-on-first-use host-key verification. An unknown key NEVER
 *   auto-trusts: connect fails with a message carrying "SHA256:<base64>" plus
 *   host:port so the UI can ask the user to approve it. A changed key fails
 *   closed with expected-vs-got fingerprints.
 * - Key files are used by path at the auth moment only; key bytes are never
 *   cached here. Passwords and key passphrases come from
 *   [EphemeralCredentials] and the consumed copy is zeroed in a finally
 *   block. (JSch takes passwords as [String], so one transient immutable
 *   copy exists until GC; the caller-owned [CharArray] is always zeroed.)
 * - Crypto comes from mwiede JSch alone — pure-Java SSH2 with zero
 *   dependencies. No BouncyCastle/SpongyCastle provider is installed,
 *   removed, or referenced anywhere: Android ships a stripped crypto copy
 *   that must never be disturbed.
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
    private var session: Session? = null
    private var jsch: JSch? = null
    private val tunnel = AtomicReference<ActiveTunnel?>(null)

    /**
     * Blocking: opens the control connection and authenticates.
     * Must be called on Dispatchers.IO. Replaces any stale connection.
     */
    fun connect(server: SavedServer): Result<Unit> {
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
            val j = JSch()
            j.hostKeyRepository = TofuRepository(server.host, server.port)
            val s: Session = try {
                j.getSession(server.username, server.host, server.port)
            } catch (e: Exception) {
                clearIdentitiesQuietly(j)
                return Result.failure(mapConnectFailure(server, e))
            }
            val prepared = prepareAuth(j, s, server)
            if (prepared.isFailure) {
                disconnectQuietly(s)
                clearIdentitiesQuietly(j)
                return prepared
            }
            s.setTimeout(CONNECT_TIMEOUT_MS)
            try {
                s.connect(CONNECT_TIMEOUT_MS)
            } catch (e: Exception) {
                disconnectQuietly(s)
                clearIdentitiesQuietly(j)
                return Result.failure(mapConnectFailure(server, e))
            }
            session = s
            jsch = j
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

    /** Thread-safe snapshot: true only while the control connection is up. */
    fun isConnected(): Boolean {
        lock.lock()
        try {
            val s = session
            if (s == null) {
                return false
            }
            return try {
                // connect() only stores the session after authentication
                // succeeds, and dropLocked() nulls it on teardown, so a live
                // session here is connected AND authenticated.
                s.isConnected
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
        val s: Session?
        lock.lock()
        try {
            s = session
        } finally {
            lock.unlock()
        }
        if (s == null || !isSessionLive(s)) {
            return Result.failure(Exception("Not connected — call connect() first."))
        }
        var channel: ChannelExec? = null
        try {
            channel = try {
                s.openChannel("exec") as ChannelExec
            } catch (e: Exception) {
                return Result.failure(mapExecFailure(e))
            }
            channel.setCommand(cmd.toByteArray(Charsets.UTF_8))
            channel.inputStream = null
            val stdout = channel.inputStream
            val stderr = channel.errStream
            try {
                channel.connect(timeoutMs.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt())
            } catch (e: Exception) {
                return Result.failure(mapExecFailure(e))
            }
            val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(1L)
            val outBytes = ByteArrayOutputStream()
            val errBytes = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (true) {
                drainAvailable(stdout, outBytes, buf)
                drainAvailable(stderr, errBytes, buf)
                if (channel.isClosed) {
                    drainAvailable(stdout, outBytes, buf)
                    drainAvailable(stderr, errBytes, buf)
                    break
                }
                if (System.currentTimeMillis() > deadline) {
                    return Result.failure(
                        mapExecFailure(Exception("exec timed out after ${timeoutMs}ms: $cmd")),
                    )
                }
                try {
                    Thread.sleep(50)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return Result.failure(mapExecFailure(ie))
                }
            }
            val exit = try {
                channel.exitStatus
            } catch (_: Exception) {
                -1
            }
            if (exit != -1 && exit != 0) {
                val detail = String(errBytes.toByteArray(), Charsets.UTF_8).trim().take(300)
                return Result.failure(
                    Exception("Remote command failed (exit $exit): $detail"),
                )
            }
            return Result.success(String(outBytes.toByteArray(), Charsets.UTF_8))
        } catch (e: Exception) {
            return Result.failure(mapExecFailure(e))
        } finally {
            try {
                channel?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Blocking: opens an SFTP channel on the current connection.
     * Must be called on Dispatchers.IO. The CALLER owns and must close the
     * returned [SshSftp] (it holds a channel on this connection).
     */
    fun sftp(): Result<SshSftp> {
        val s: Session?
        lock.lock()
        try {
            s = session
        } finally {
            lock.unlock()
        }
        if (s == null || !isSessionLive(s)) {
            return Result.failure(Exception("Not connected — call connect() first."))
        }
        return try {
            val channel = s.openChannel("sftp") as ChannelSftp
            try {
                channel.connect(CONNECT_TIMEOUT_MS)
            } catch (e: Exception) {
                try {
                    channel.disconnect()
                } catch (_: Exception) {
                }
                return Result.failure(mapExecFailure(e))
            }
            Result.success(SshSftp(channel))
        } catch (e: Exception) {
            Result.failure(mapExecFailure(e))
        }
    }

    /**
     * Blocking (fast: bind only; forwarding is served by the JSch session).
     * Must be called on Dispatchers.IO. Forwards ephemeral 127.0.0.1:<local>
     * to 127.0.0.1:[remotePort] on the VPS. Reuses the existing forward when
     * it already targets [remotePort]. Returns the local port number.
     */
    fun openTunnel(remotePort: Int = 9222): Result<Int> {
        lock.lock()
        try {
            val s = session
            if (s == null || !isSessionLive(s)) {
                return Result.failure(Exception("Not connected — call connect() first."))
            }
            val existing = tunnel.get()
            if (existing != null &&
                existing.remotePort == remotePort &&
                forwardAlive(s, existing.localPort)
            ) {
                return Result.success(existing.localPort)
            }
            closeTunnelLocked()
            return try {
                val localPort = s.setPortForwardingL(0, "127.0.0.1", remotePort)
                tunnel.set(ActiveTunnel(localPort = localPort, remotePort = remotePort))
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
        val s = session
        session = null
        val j = jsch
        jsch = null
        if (s != null) {
            disconnectQuietly(s)
        }
        if (j != null) {
            clearIdentitiesQuietly(j)
        }
    }

    private fun closeTunnelLocked() {
        val t = tunnel.getAndSet(null)
        if (t != null) {
            try {
                session?.delPortForwardingL(t.localPort)
            } catch (_: Exception) {
            }
        }
    }

    private fun forwardAlive(s: Session, localPort: Int): Boolean {
        return try {
            val forwards = s.portForwardingL ?: return false
            forwards.any { it.contains(":$localPort:") }
        } catch (_: Exception) {
            false
        }
    }

    private fun isSessionLive(s: Session): Boolean {
        return try {
            s.isConnected
        } catch (_: Exception) {
            false
        }
    }

    private fun prepareAuth(jsch: JSch, s: Session, server: SavedServer): Result<Unit> {
        return if (server.authMethod == AuthMethod.SshKey) {
            prepareKeyAuth(jsch, server)
        } else {
            preparePasswordAuth(s, server)
        }
    }

    private fun prepareKeyAuth(jsch: JSch, server: SavedServer): Result<Unit> {
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
        var passphrase: CharArray? = null
        try {
            passphrase = EphemeralCredentials.consumePassword(server.id)
            try {
                if (passphrase == null || passphrase.isEmpty()) {
                    jsch.addIdentity(keyPath)
                } else {
                    jsch.addIdentity(keyPath, passphrase.concatToString())
                }
            } catch (e: Exception) {
                return Result.failure(Exception(KEY_REJECTED_PREFIX + " Detail: ${firstMessage(e)}"))
            }
            return Result.success(Unit)
        } finally {
            try {
                passphrase?.fill('\u0000')
            } catch (_: Exception) {
            }
        }
    }

    private fun preparePasswordAuth(s: Session, server: SavedServer): Result<Unit> {
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
            s.setPassword(password.concatToString())
            return Result.success(Unit)
        } finally {
            try {
                password?.fill('\u0000')
            } catch (_: Exception) {
            }
        }
    }

    private inner class TofuRepository(
        private val host: String,
        private val port: Int,
    ) : HostKeyRepository {

        override fun check(host: String?, key: ByteArray?): Int {
            if (key == null) {
                throw JSchException(
                    "Unknown host key (SHA256:unavailable) — approve it to connect. " +
                        "(${this.host}:${this.port})",
                )
            }
            val fingerprint = try {
                sha256Base64(key)
            } catch (_: Exception) {
                throw JSchException(
                    "Unknown host key (SHA256:unavailable) — approve it to connect. " +
                        "(${this.host}:${this.port})",
                )
            }
            val known = try {
                hostKeyStore.knownFingerprint(this.host, this.port)
            } catch (_: Exception) {
                null
            }
            if (known == null) {
                throw JSchException(
                    "Unknown host key (SHA256:$fingerprint) — approve it to connect. " +
                        "(${this.host}:${this.port} SHA256:$fingerprint)",
                )
            }
            if (known.equals(fingerprint, ignoreCase = true)) {
                return HostKeyRepository.OK
            }
            // Legacy tolerance: fingerprints trusted by older builds were
            // stored as lowercase hex of the same key bytes. The same key in
            // either encoding is still the same key, so it matches.
            if (known.equals(sha256FingerprintHex(key), ignoreCase = true)) {
                return HostKeyRepository.OK
            }
            throw JSchException(
                "Host key mismatch for ${this.host}:${this.port} — expected SHA256:$known " +
                    "but got SHA256:$fingerprint. Failing closed.",
            )
        }

        override fun add(hostkey: HostKey?, ui: UserInfo?) {
            // Trust is explicit via HostKeyStore.trust after user approval;
            // this repository never auto-adds keys.
        }

        override fun remove(host: String?, type: String?) {
        }

        override fun remove(host: String?, type: String?, key: ByteArray?) {
        }

        override fun getKnownHostsRepositoryID(): String = "orbit-tofu"

        override fun getHostKey(): Array<HostKey> = emptyArray()

        override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
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
        if (isAuthFailure(e)) {
            return Exception(authPrefix(server) + " Detail: ${firstMessage(e)}")
        }
        if (isUnreachable(e)) {
            return Exception(
                "Cannot reach ${server.host}:${server.port} — check host spelling, " +
                    "port, and that the VPS firewall allows SSH (TCP/22). " +
                    "Detail: ${firstMessage(e)}",
            )
        }
        if (isInteropNullCrash(e)) {
            return Exception(
                "Internal handshake error — please update the app to the latest build. " +
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

    private fun isInteropNullCrash(e: Throwable): Boolean {
        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 12) {
            val message = current.message ?: ""
            if (message.contains("Parameter specified as non-null is null")) {
                return true
            }
            current = current.cause
            depth += 1
        }
        return false
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

    private fun isAuthFailure(e: Throwable): Boolean {
        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 12) {
            val lower = (current.message ?: "").lowercase()
            if (lower.contains("auth fail") ||
                lower.contains("auth cancel") ||
                lower.contains("userauth")
            ) {
                return true
            }
            current = current.cause
            depth += 1
        }
        return false
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
                lower.contains("unknownhost") ||
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

    private fun disconnectQuietly(s: Session) {
        try {
            s.disconnect()
        } catch (_: Exception) {
        }
    }

    private fun clearIdentitiesQuietly(j: JSch) {
        try {
            j.removeAllIdentity()
        } catch (_: Exception) {
        }
    }

    /**
     * Lock-free drain helper for exec streams (no [lock] needed): copies
     * every currently available byte into [sink]. Never throws.
     */
    private fun drainAvailable(
        stream: java.io.InputStream,
        sink: ByteArrayOutputStream,
        buf: ByteArray,
    ): Boolean {
        var any = false
        try {
            while (stream.available() > 0) {
                val read = stream.read(buf)
                if (read <= 0) {
                    break
                }
                sink.write(buf, 0, read)
                any = true
            }
        } catch (_: Exception) {
        }
        return any
    }

    private data class ActiveTunnel(
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
    }
}

/** One SFTP directory entry: [mtimeSecs] is POSIX seconds, UTC. */
data class SftpEntry(
    val name: String = "",
    val isDir: Boolean = false,
    val sizeBytes: Long = 0L,
    val mtimeSecs: Long = 0L,
)

/**
 * Caller-owned SFTP handle over the [SshManager] control connection.
 *
 * Blocking contract: EVERY method blocks on network I/O and MUST be called
 * from a background thread. The owner MUST call [close] when done — it holds
 * a channel on the control connection. Missing remote paths surface as
 * messages containing "No such file: <path>" so callers can map them to
 * their own "No such directory/file" texts; all other remote failures
 * surface as "SFTP <op> failed for <path>: <detail>".
 */
class SshSftp internal constructor(private val channel: ChannelSftp) {

    /** Blocking: lists [path]. Must be called on Dispatchers.IO. */
    fun ls(path: String): List<SftpEntry> {
        val vector = try {
            channel.ls(path)
        } catch (e: SftpException) {
            throw mapSftpFailure("ls", path, e)
        }
        return vector.map { entry ->
            val attrs = entry.attrs
            SftpEntry(
                name = entry.filename,
                isDir = attrs.isDir,
                sizeBytes = attrs.size,
                mtimeSecs = attrs.mTime.toLong(),
            )
        }
    }

    /**
     * Blocking: stats [path], or null when it does not exist.
     * Must be called on Dispatchers.IO.
     */
    fun stat(path: String): SftpEntry? {
        val attrs = try {
            channel.stat(path)
        } catch (e: SftpException) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return null
            }
            throw mapSftpFailure("stat", path, e)
        }
        val base = path.trimEnd('/').substringAfterLast('/').ifEmpty { path }
        return SftpEntry(
            name = base,
            isDir = attrs.isDir,
            sizeBytes = attrs.size,
            mtimeSecs = attrs.mTime.toLong(),
        )
    }

    /** Blocking: renames/moves [src] to [dst]. Must be called on Dispatchers.IO. */
    fun rename(src: String, dst: String) {
        try {
            channel.rename(src, dst)
        } catch (e: SftpException) {
            throw mapSftpFailure("rename", "$src -> $dst", e)
        }
    }

    /** Blocking: deletes the file at [path]. Must be called on Dispatchers.IO. */
    fun rm(path: String) {
        try {
            channel.rm(path)
        } catch (e: SftpException) {
            throw mapSftpFailure("rm", path, e)
        }
    }

    /** Blocking: deletes the empty directory at [path]. Must be called on Dispatchers.IO. */
    fun rmdir(path: String) {
        try {
            channel.rmdir(path)
        } catch (e: SftpException) {
            throw mapSftpFailure("rmdir", path, e)
        }
    }

    /** Blocking: downloads [remote] to [localFile]. Must be called on Dispatchers.IO. */
    fun get(remote: String, localFile: File) {
        try {
            channel.get(remote, localFile.absolutePath)
        } catch (e: SftpException) {
            throw mapSftpFailure("get", remote, e)
        }
    }

    /** Blocking: uploads [localFile] to [remote]. Must be called on Dispatchers.IO. */
    fun put(localFile: File, remote: String) {
        try {
            channel.put(localFile.absolutePath, remote)
        } catch (e: SftpException) {
            throw mapSftpFailure("put", remote, e)
        }
    }

    /** Drops the channel. Never throws. */
    fun close() {
        try {
            channel.disconnect()
        } catch (_: Exception) {
        }
    }

    private fun mapSftpFailure(op: String, path: String, e: SftpException): Exception {
        if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
            return Exception("No such file: $path")
        }
        var current: Throwable? = e
        while (current != null) {
            val message = current.message
            if (!message.isNullOrBlank()) {
                return Exception("SFTP $op failed for $path: ${message.take(250)}")
            }
            current = current.cause
        }
        return Exception("SFTP $op failed for $path: ${e.javaClass.simpleName}")
    }
}

/**
 * PURE function: SHA-256 of [keyBytes] as standard Base64 (with padding).
 *
 * Uses java.security.MessageDigest plus java.util.Base64 only — no Android
 * import — so it runs on plain JVM unit tests as well as on device. Same
 * construction pattern as [sha256FingerprintHex], but Base64-encoded to
 * match the OpenSSH `SHA256:<base64>` fingerprint presentation the TOFU
 * verifier compares against [HostKeyStore].
 */
fun sha256Base64(keyBytes: ByteArray): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hashed = digest.digest(keyBytes)
    return java.util.Base64.getEncoder().encodeToString(hashed)
}
