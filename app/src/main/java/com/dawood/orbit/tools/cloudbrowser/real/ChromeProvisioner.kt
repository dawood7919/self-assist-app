package com.dawood.orbit.tools.cloudbrowser.real

/**
 * Installs and starts headless Chrome ON THE VPS over the open SSH control
 * connection.
 *
 * Blocking contract: every method performs SSH exec I/O and MUST be called
 * from a background thread (Dispatchers.IO). Nothing throws: failures come
 * back as [Result.failure] with a human-readable stage message.
 *
 * The flow is the same one validated by `.github/e2e/provision.sh` against
 * a pristine Ubuntu container in CI:
 *   1. already listening on loopback 9222? done.
 *   2. a usable Chrome/Chromium binary on PATH? use it.
 *   3. otherwise install google-chrome-stable from Google's signed apt repo
 *      (passwordless sudo when the cloud image allows it, else the ephemeral
 *      SSH password is piped to sudo), with distro chromium as a fallback.
 *   4. launch headless on 127.0.0.1:9222 with the profile this app owns,
 *      wait for the DevTools endpoint to answer.
 *
 * Chrome binds loopback only; the phone reaches it through the SSH tunnel.
 */
class ChromeProvisioner(private val ssh: SshManager) {

    /** Idempotent: ensures the node can serve DevTools on loopback 9222. */
    fun ensure(onStage: (String) -> Unit = {}): Result<Unit> {
        return try {
            if (isDevToolsUp()) {
                onStage("Chrome is already running on the server")
                return Result.success(Unit)
            }

            var binary = detectBinary().getOrNull().orEmpty()
            if (binary.isBlank()) {
                onStage("Installing Chrome on the server (first time, a few minutes)")
                installChrome().onFailure { return Result.failure(it) }
                binary = detectBinary().getOrNull().orEmpty()
            }
            if (binary.isBlank()) {
                return Result.failure(
                    Exception(
                        "Chrome installed but could not be started. Check the server has " +
                            "a working apt source and ~150 MB free, then reconnect.",
                    ),
                )
            }
            onStage("Starting headless Chrome")
            startChrome(binary).onFailure { return Result.failure(it) }

            val deadline = System.currentTimeMillis() + START_WAIT_MS
            while (System.currentTimeMillis() < deadline) {
                if (isDevToolsUp()) {
                    onStage("Chrome is ready")
                    return Result.success(Unit)
                }
                Thread.sleep(1_000)
            }
            Result.failure(
                Exception(
                    "Chrome started but DevTools did not answer on port 9222 within " +
                        "${START_WAIT_MS / 1000}s. It may still be starting on a slow server — retry.",
                ),
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Result.failure(Exception("Interrupted while preparing Chrome: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Chrome provisioning failed: ${e.message}"))
        }
    }

    // ------------------------------------------------------------------

    private fun isDevToolsUp(): Boolean =
        ssh.exec(VERSION_PROBE, 6_000).getOrNull()?.contains("Browser", ignoreCase = true) == true

    /**
     * Prints the first Chrome/Chromium binary that actually runs. Snap
     * stubs are skipped by path, and every probe is bounded by `timeout 15`,
     * so a snap shim that stalls trying to reach snapd can neither win nor
     * hold the SSH channel silent until it dies (the root cause of the
     * "Not connected" failure during first-time provisioning).
     *
     * Shell variables are written as `${'$'}name` because the script lives
     * in a Kotlin raw string, where a bare `$` would be a template.
     */
    private fun detectBinary(): Result<String> {
        val script = """
            for c in google-chrome-stable google-chrome chromium chromium-browser; do
                p=${'$'}(command -v "${'$'}c" 2>/dev/null) || continue
                case "${'$'}p" in /snap/*) continue ;; esac
                if command -v timeout >/dev/null 2>&1; then
                    timeout 15 "${'$'}p" --version >/dev/null 2>&1 || continue
                else
                    "${'$'}p" --version >/dev/null 2>&1 || continue
                fi
                echo "${'$'}p"
                exit 0
            done
            exit 1
        """.trimIndent()
        val result = ssh.exec(script, 15_000)
        val path = result.getOrNull()?.trim().orEmpty()
        return if (path.isNotBlank()) Result.success(path) else Result.failure(
            Exception("No Chrome binary found on the server yet."),
        )
    }

    private fun installChrome(): Result<Unit> {
        // Bounded network options mirror .github/e2e/provision.sh: a stalled
        // mirror must fail the stage (and fall through to distro chromium)
        // instead of hanging the SSH channel until the user sees a timeout.
        val aptOpts = "-o Acquire::Retries=3 " +
            "-o Acquire::http::Timeout=20 -o Acquire::https::Timeout=20"
        // Best-effort: a stale/unreachable initial mirror is not fatal —
        // the Google repo and the distro-chromium fallback each re-run the
        // update path and bring their own diagnostics.
        ssh.execRoot("asroot apt-get $aptOpts update -y", INSTALL_TIMEOUT_MS)
        val prereqs = ssh.execRoot(
            "asroot apt-get $aptOpts install -y --no-install-recommends " +
                "wget gnupg ca-certificates apt-transport-https curl",
            INSTALL_TIMEOUT_MS,
        )
        if (prereqs.isFailure) {
            return Result.failure(
                Exception("Could not install prerequisites: ${prereqs.exceptionOrNull()?.message}"),
            )
        }
        val repo = """
            set -e
            OPTS="-o Acquire::Retries=3 -o Acquire::http::Timeout=20 -o Acquire::https::Timeout=20"
            TMPKEY=${'$'}(mktemp)
            wget -q -T 20 -t 2 -O "${'$'}TMPKEY" https://dl.google.com/linux/linux_signing_key.pub || exit 11
            install -d -m 755 /usr/share/keyrings
            if gpg --dearmor < "${'$'}TMPKEY" > /usr/share/keyrings/google-chrome.gpg 2>/dev/null; then :; else
                cp "${'$'}TMPKEY" /usr/share/keyrings/google-chrome.pub
            fi
            rm -f "${'$'}TMPKEY"
            printf '%s\n' 'deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome.gpg] https://dl.google.com/linux/chrome/deb/ stable main' \
                > /etc/apt/sources.list.d/google-chrome.list
            apt-get ${'$'}OPTS update -y
            apt-get ${'$'}OPTS install -y google-chrome-stable
        """.trimIndent()
        val installed = ssh.execRoot("asroot bash -c " + shellSingleQuote(repo), INSTALL_TIMEOUT_MS)
        if (installed.isSuccess && detectBinary().isSuccess) return Result.success(Unit)

        // Fallback: distro chromium (works on Debian images and Ubuntu <24.04
        // without snap restrictions).
        val fallback = ssh.execRoot(
            "asroot apt-get $aptOpts install -y chromium-browser " +
                "|| asroot apt-get $aptOpts install -y chromium",
            INSTALL_TIMEOUT_MS,
        )
        return if (fallback.isSuccess && detectBinary().isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(
                Exception(
                    "Chrome installation failed. Google repo: " +
                        "${installed.exceptionOrNull()?.message} " +
                        "Fallback: ${fallback.exceptionOrNull()?.message}",
                ),
            )
        }
    }

    /**
     * [binary] is the discovered path (intentionally interpolated as a
     * Kotlin template); every shell-only variable is escaped so it reaches
     * bash literally.
     */
    private fun startChrome(binary: String): Result<Unit> {
        val script = """
            mkdir -p "${'$'}HOME/.config/orbit-chrome"
            pkill -f 'config/orbit-chrome' 2>/dev/null || true
            sleep 1
            nohup "$binary" \
                --headless=new \
                --remote-debugging-port=9222 \
                --remote-debugging-address=127.0.0.1 \
                --remote-allow-origins='*' \
                --no-sandbox \
                --disable-gpu \
                --disable-dev-shm-usage \
                --user-data-dir="${'$'}HOME/.config/orbit-chrome" \
                --window-size=1280,720 \
                --no-first-run \
                --no-default-browser-check \
                --disable-background-networking \
                --disable-sync \
                about:blank >/tmp/orbit-chrome.log 2>&1 &
            echo started
        """.trimIndent()
        return ssh.exec(script, 10_000).map { }
    }

    private fun shellSingleQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private companion object {
        const val START_WAIT_MS = 40_000L
        const val INSTALL_TIMEOUT_MS = 300_000L

        val VERSION_PROBE = """
            (curl -fsS --max-time 3 http://127.0.0.1:9222/json/version 2>/dev/null \
              || wget -qO- --timeout=3 http://127.0.0.1:9222/json/version 2>/dev/null) \
              | head -c 200
        """.trimIndent()
    }
}
