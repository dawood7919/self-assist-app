package com.dawood.orbit.tools.cloudbrowser

/**
 * Idempotent Ubuntu 22.04 / 24.04 setup script for the Cloud Browser VPS.
 *
 * Pure Kotlin: no android.* imports. The script installs Chrome stable
 * (chromium-browser fallback), prepares the orbit directories, installs the
 * per-user systemd unit, enables it, and verifies the local DevTools
 * endpoint. Chrome listens on loopback only and is reached via SSH tunnel.
 */
object VpsSetupScript {
    const val TEXT = """#!/bin/bash
set -euo pipefail
# Orbit Cloud Browser node setup - idempotent, Ubuntu 22.04 / 24.04.
# Installs Chrome stable (chromium-browser fallback), prepares orbit
# directories and the per-user systemd unit, then verifies the local
# DevTools endpoint. Chrome binds loopback only; reach it via SSH tunnel.

if ! command -v google-chrome >/dev/null 2>&1; then
  sudo apt-get update
  sudo apt-get install -y wget gnupg
  sudo mkdir -p /usr/share/keyrings
  wget -q -O /tmp/google-chrome-key.pub https://dl.google.com/linux/linux_signing_key.pub
  sudo install -m 644 /tmp/google-chrome-key.pub /usr/share/keyrings/google-chrome.gpg
  echo 'deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome.gpg] https://dl.google.com/linux/chrome/deb/ stable main' | sudo tee /etc/apt/sources.list.d/google-chrome.list
  sudo apt-get update
  sudo apt-get install -y google-chrome-stable
fi
if ! command -v google-chrome >/dev/null 2>&1; then
  sudo apt-get install -y chromium-browser
  sudo ln -sf /usr/bin/chromium-browser /usr/bin/google-chrome
fi

mkdir -p ~/.config/orbit-chrome ~/orbit/files ~/orbit/downloads ~/.config/systemd/user
chmod 700 ~/.config/orbit-chrome
chmod 700 ~/orbit/files
chmod 700 ~/orbit/downloads

cat > ~/.config/systemd/user/orbit-chrome.service <<'UNIT'
[Unit]
Description=Orbit headless Chrome for Cloud Browser
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
ExecStart=/usr/bin/google-chrome --headless=new --remote-debugging-port=9222 --remote-debugging-address=127.0.0.1 --no-sandbox --disable-gpu --disable-dev-shm-usage --user-data-dir=~/.config/orbit-chrome --window-size=1280,720 --no-first-run --no-default-browser-check about:blank
Restart=on-failure
RestartSec=5

[Install]
WantedBy=default.target
UNIT

loginctl enable-linger
systemctl --user daemon-reload
systemctl --user enable --now orbit-chrome.service
sleep 3
curl -fsS http://127.0.0.1:9222/json/version
echo 'WARNING: never expose port 9222 - loopback + SSH tunnel only.'
"""
}
