#!/usr/bin/env bash
set -Eeuo pipefail

# Generic X + VNC startup script for Portfolio Performance

DISPLAY="${DISPLAY:-:0}"
SCREEN_GEOM="${SCREEN_GEOM:-1920x1080x24}"
RFB_PORT="${RFB_PORT:-5900}"

# Clean up stale X11 lock files from a previous run. They survive a container
# restart (the filesystem is kept), and Xvfb refuses to start while they exist:
# "Server is already active for display 0".
DISPLAY_NUM="${DISPLAY#:}"
rm -f "/tmp/.X${DISPLAY_NUM}-lock"
rm -f "/tmp/.X11-unix/X${DISPLAY_NUM}"

echo "Starting Xvfb on ${DISPLAY} with geometry ${SCREEN_GEOM}"

# Start Xvfb (headless X server)
Xvfb "${DISPLAY}" -screen 0 "${SCREEN_GEOM}" -ac +extension GLX +render -noreset &
XVFB_PID=$!

# Ensure we clean up on exit
cleanup() {
  echo "Cleaning up X server..."
  kill -TERM "${XVFB_PID}" 2>/dev/null || true
}
trap cleanup EXIT

# Give Xvfb a moment to start
sleep 1

echo "Starting Fluxbox window manager..."
# Start a very light window manager
fluxbox >/tmp/fluxbox.log 2>&1 &

# Prepare VNC auth if password provided
VNC_ARGS=()
if [[ -n "${VNC_PASSWORD:-}" ]]; then
  echo "Setting up VNC with password authentication..."
  # store passwd for x11vnc
  x11vnc -storepasswd "${VNC_PASSWORD}" /root/.vncpass >/dev/null 2>&1
  VNC_ARGS=(-rfbauth /root/.vncpass)
else
  echo "Starting VNC without password (use VNC_PASSWORD env var to set one)"
  VNC_ARGS=(-nopw)
fi

echo "Starting VNC server on port ${RFB_PORT}..."
# Start VNC server bound to the X display
x11vnc \
  -display "${DISPLAY}" \
  -forever \
  -shared \
  -rfbport "${RFB_PORT}" \
  "${VNC_ARGS[@]}" \
  -o /tmp/x11vnc.log \
  >/tmp/x11vnc.stdout 2>&1 &

echo "VNC server ready on port ${RFB_PORT}"

# Keep process in foreground while Xvfb lives
wait "${XVFB_PID}"

