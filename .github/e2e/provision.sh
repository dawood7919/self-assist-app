#!/bin/bash
# Orbit Cloud Browser node provisioning — IDENTICAL in spirit to the app's
# ChromeProvisioner: idempotent, installs Chrome when missing, starts it on
# loopback:9222. Arg $1 = sudo password (may be empty for passwordless sudo).
set -uo pipefail   # no -e: every stage is handled explicitly with diagnostics
PASS="${1:-}"
log() { echo "[provision] $*"; }

# --- sudo helper -----------------------------------------------------------
if ! command -v sudo >/dev/null 2>&1; then
  if [ "$(id -u)" -eq 0 ]; then
    # Root image without sudo: emulate sudo as a plain exec.
    sudo() { "$@"; }
    log "sudo absent; running as root"
  else
    log "FATAL: sudo is not installed and we are not root; cannot provision"
    exit 1
  fi
fi
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
# A timeout-bounded wrapper so a stalled mirror/key fetch fails the stage
# (and triggers the chromium fallback) instead of hanging the whole job.
WGET="wget -q -T 20 -t 2"
APT_OPTS="-o Acquire::Retries=3 -o Acquire::http::Timeout=20 -o Acquire::https::Timeout=20"
find_chrome() {
  for c in google-chrome-stable google-chrome chromium chromium-browser; do
    p="$(command -v "$c" 2>/dev/null)" || continue
    case "$p" in /snap/*) continue ;; esac
    if command -v timeout >/dev/null 2>&1; then
      timeout 15 "$p" --version >/dev/null 2>&1 || continue
    else
      "$p" --version >/dev/null 2>&1 || continue
    fi
    echo "$p"; return 0
  done
  return 0
}
BIN="$(find_chrome)"
if [ -z "$BIN" ]; then
  log "no Chrome found — installing Google Chrome stable"
  export DEBIAN_FRONTEND=noninteractive
  log "stage: apt-get update"
  s apt-get $APT_OPTS update -y || log "WARN: initial apt update failed"
  log "stage: install prerequisites"
  if ! s apt-get $APT_OPTS install -y --no-install-recommends wget gnupg ca-certificates apt-transport-https curl; then
    log "WARN: prerequisites failed — attempting distro chromium directly"
    if s apt-get $APT_OPTS install -y chromium-browser || s apt-get $APT_OPTS install -y chromium; then
      BIN="$(find_chrome)"
    fi
    [ -n "$BIN" ] && log "fallback chromium: $BIN"
    if [ -z "$BIN" ]; then log "FATAL: prerequisites failed and no chromium available"; exit 1; fi
  fi
  if [ -z "$BIN" ]; then
  TMPKEY="$(mktemp)"
  log "stage: fetch Google signing key"
  $WGET -O "$TMPKEY" https://dl.google.com/linux/linux_signing_key.pub \
    || log "WARN: signing key download failed"
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
  log "stage: apt-get update (Google repo)"
  s apt-get $APT_OPTS update -y || log "WARN: repo update failed"
  log "stage: install google-chrome-stable"
  s apt-get $APT_OPTS install -y google-chrome-stable || {
    log "google-chrome-stable failed, trying distro chromium"
    s apt-get $APT_OPTS install -y chromium-browser || s apt-get $APT_OPTS install -y chromium
  }
  BIN="$(find_chrome)"
  fi
fi
log "browser binary: $BIN ($($BIN --version 2>/dev/null || echo unknown))"

# Xvfb lets Chrome run headful (screencast honors deviceScaleFactor there;
# headless only emits CSS-sized, soft mobile frames). Bounded + optional so
# a slow mirror cannot break the whole provisioning (headless fallback).
if command -v Xvfb >/dev/null 2>&1; then
  log "Xvfb already installed"
else
  log "stage: install xvfb (best effort, 150s cap)"
  INSTALL_RC=1
  if command -v timeout >/dev/null 2>&1 && [ "$SUDO" = "sudo -n" ]; then
    timeout 150 sudo -n apt-get $APT_OPTS install -y --no-install-recommends xvfb
    INSTALL_RC=$?
  elif command -v timeout >/dev/null 2>&1; then
    printf '%s\n' "$PASS" | timeout 150 sudo -S -p '' apt-get $APT_OPTS install -y --no-install-recommends xvfb
    INSTALL_RC=$?
  else
    s apt-get $APT_OPTS install -y --no-install-recommends xvfb
    INSTALL_RC=$?
  fi
  [ "$INSTALL_RC" -eq 0 ] || log "WARN: xvfb install failed/timed out - headless fallback"
fi

# --- start (kill nothing: if a profile lock exists, it is our own) --------
mkdir -p "$HOME/.config/orbit-chrome"
pkill -f 'orbit-chrome' 2>/dev/null || true
sleep 1
# Headful Chrome under Xvfb is preferred: Page.startScreencast honors
# deviceScaleFactor there, so mobile frames stream at physical-pixel
# sharpness. Headless Chrome delivers CSS-sized (blurry-upscaled) frames.
if command -v Xvfb >/dev/null 2>&1; then
  log "starting headful Chrome under Xvfb :99"
  pkill -f 'Xvfb :99' 2>/dev/null || true
  Xvfb :99 -screen 0 1400x2640x24 -nolisten tcp >/tmp/xvfb.log 2>&1 &
  for i in $(seq 1 20); do [ -e /tmp/.X11-unix/X99 ] && break; sleep 1; done
  HEAD_ARGS="--headless=new --window-size=1280,720"
  if [ -e /tmp/.X11-unix/X99 ]; then
    export DISPLAY=:99
    HEAD_ARGS="--window-size=1280,2607"
    log "Xvfb display ready - running headful"
  else
    log "Xvfb did not come up - falling back to headless"
  fi
  nohup "$BIN" \
    $HEAD_ARGS \
    --remote-debugging-port=9222 \
    --remote-debugging-address=127.0.0.1 \
    --remote-allow-origins='*' \
    --no-sandbox --disable-gpu --disable-dev-shm-usage \
    --user-data-dir="$HOME/.config/orbit-chrome" \
    --no-first-run --no-default-browser-check \
    --disable-background-networking --disable-sync \
    about:blank >/tmp/orbit-chrome.log 2>&1 &
else
  log "Xvfb unavailable - falling back to --headless=new (mobile frames less sharp)"
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
fi
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
