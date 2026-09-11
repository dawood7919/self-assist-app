#!/bin/bash
# Orbit Cloud Browser node provisioning — IDENTICAL in spirit to the app's
# ChromeProvisioner: idempotent, installs Chrome when missing, starts it on
# loopback:9222. Arg $1 = sudo password (may be empty for passwordless sudo).
set -uo pipefail   # no -e: every stage is handled explicitly with diagnostics
PASS="${1:-}"
log() { echo "[provision] $*"; }

# --- sudo helper -----------------------------------------------------------
if sudo -n true 2>/dev/null; then
  SUDO="sudo -n"
  log "passwordless sudo available"
else
  SUDO="sudo -S -p ''"
  export SUDO_ASKPASS=""
fi
s() { if [ "$SUDO" = "sudo -S -p ''" ]; then printf '%s\n' "$PASS" | $SUDO "$@"; else $SUDO "$@"; fi; }

# --- already running? ------------------------------------------------------
if curl -fsS --max-time 3 http://127.0.0.1:9222/json/version 2>/dev/null | grep -q Browser; then
  log "DevTools already listening on 9222"
  BIN=$(pgrep -af 'remote-debugging-port=9222' | head -1 || true)
  log "process: $BIN"
  exit 0
fi

# --- find or install Chrome ------------------------------------------------
find_chrome() {
  command -v google-chrome-stable 2>/dev/null || command -v google-chrome 2>/dev/null \
    || command -v chromium 2>/dev/null || command -v chromium-browser 2>/dev/null || true
}
BIN="$(find_chrome)"
if [ -z "$BIN" ]; then
  log "no Chrome found — installing Google Chrome stable"
  export DEBIAN_FRONTEND=noninteractive
  s apt-get update -y
  s apt-get install -y wget gnupg ca-certificates apt-transport-https curl
  TMPKEY="$(mktemp)"
  wget -q -O "$TMPKEY" https://dl.google.com/linux/linux_signing_key.pub
  # Run the whole keyring/repo block under ONE root shell: piping the sudo
  # password via stdin makes per-command pipelines (gpg | sudo tee) lose the
  # key data, since sudo consumes stdin for the password.
  s bash -c '
    set -e
    install -d -m 755 /usr/share/keyrings
    if gpg --dearmor < "'"$TMPKEY"'" > /usr/share/keyrings/google-chrome.gpg 2>/dev/null; then
      :
    else
      cp "'"$TMPKEY"'" /usr/share/keyrings/google-chrome.pub
    fi
    printf "%s\n" "deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome.gpg] https://dl.google.com/linux/chrome/deb/ stable main" \
      > /etc/apt/sources.list.d/google-chrome.list
  ' || log "WARN: Google repo setup failed"
  rm -f "$TMPKEY"
  s apt-get update -y || log "WARN: repo update failed"
  s apt-get install -y google-chrome-stable || {
    log "google-chrome-stable failed, trying distro chromium"
    s apt-get install -y chromium-browser || s apt-get install -y chromium
  }
  BIN="$(find_chrome)"
fi
log "browser binary: $BIN ($($BIN --version 2>/dev/null || echo unknown))"
[ -z "$BIN" ] && { log "FATAL: Chrome could not be installed"; exit 1; }

# --- start (kill nothing: if a profile lock exists, it is our own) --------
mkdir -p "$HOME/.config/orbit-chrome"
pkill -f 'orbit-chrome' 2>/dev/null || true
sleep 1
nohup "$BIN" \
  --headless=new \
  --remote-debugging-port=9222 \
  --remote-debugging-address=127.0.0.1 \
  --remote-allow-origins='*' \
  --no-sandbox --disable-gpu --disable-dev-shm-usage \
  --user-data-dir="$HOME/.config/orbit-chrome" \
  --window-size=1280,720 \
  --no-first-run --no-default-browser-check \
  --disable-background-networking --disable-sync \
  about:blank >/tmp/orbit-chrome.log 2>&1 &
log "launch issued, waiting for DevTools..."
for i in $(seq 1 40); do
  if curl -fsS --max-time 2 http://127.0.0.1:9222/json/version 2>/dev/null | grep -q Browser; then
    log "DevTools is up after ${i}s"
    curl -fsS http://127.0.0.1:9222/json/version
    exit 0
  fi
  sleep 1
done
log "FATAL: DevTools never came up; chrome log tail:"
tail -40 /tmp/orbit-chrome.log || true
exit 1
