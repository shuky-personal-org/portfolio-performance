#!/usr/bin/env bash
set -Eeuo pipefail

# Unified entrypoint for Portfolio Performance
# Supports two modes: server (API) or ui (Desktop GUI with VNC)

RUN_MODE="${RUN_MODE:-server}"

echo "=================================================="
echo "Portfolio Performance Docker Container"
echo "Mode: ${RUN_MODE}"
echo "=================================================="

# Ensure workspace directory exists
WORKSPACE_DIR="${WORKSPACE_DIR:-/app/workspace}"
PORTFOLIO_DIR="${PORTFOLIO_DIR:-/opt/pp/portfolios}"
FLEX_REPORTS_DIR="${FLEX_REPORTS_DIR:-/app/out/flex}"

echo "Setting up directories..."
echo "  Workspace: ${WORKSPACE_DIR}"
echo "  Portfolios: ${PORTFOLIO_DIR}"
echo "  Flex reports: ${FLEX_REPORTS_DIR}"

mkdir -p "${WORKSPACE_DIR}" "${PORTFOLIO_DIR}" "${FLEX_REPORTS_DIR}"

echo "Starting as root..."
echo ""

case "${RUN_MODE}" in
  server)
    echo "Starting in SERVER mode (REST API)"
    exec /opt/bin/start_pp_server.sh
    ;;
  
  ui)
    echo "Starting in UI mode (Desktop GUI with VNC)"
    
    # Start Xvfb + Fluxbox + VNC in background
    /opt/bin/start_x_vnc.sh &
    XVNC_PID=$!
    
    # Give the X server a moment to come up
    sleep 3
    
    echo "X server ready, starting Portfolio Performance UI..."
    exec /opt/bin/start_pp_ui.sh
    
    # If UI exits, clean up VNC
    wait "${XVNC_PID}"
    ;;
  
  *)
    echo "ERROR: Invalid RUN_MODE '${RUN_MODE}'"
    echo "Valid modes: server, ui"
    exit 1
    ;;
esac

