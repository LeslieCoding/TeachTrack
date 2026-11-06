#!/usr/bin/env bash
set -euo pipefail

# Start a temporary Cloudflare Quick Tunnel to the local backend.
# WARNING: the URL is public (no Cloudflare Access). The backend still requires
# a Bearer token, but /actuator/health is public. Use for testing only.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLOUDFLARED="${CLOUDFLARED:-/d/SoftWare/cloudflared.exe}"
PORT="${PORT:-8080}"
LOG="${TUNNEL_LOG:-$ROOT/backend/cloudflared-quick.log}"

if [[ ! -x "$CLOUDFLARED" ]]; then
  echo "cloudflared not found: $CLOUDFLARED" >&2
  exit 1
fi

: > "$LOG"
nohup "$CLOUDFLARED" tunnel --url "http://127.0.0.1:$PORT" --no-autoupdate > "$LOG" 2>&1 &
PID=$!

URL=""
for _ in $(seq 1 30); do
  URL="$(grep -oE 'https://[a-zA-Z0-9.-]+trycloudflare\.com' "$LOG" | head -1 || true)"
  [[ -n "$URL" ]] && break
  sleep 1
done

if [[ -z "$URL" ]]; then
  echo "tunnel did not start; see $LOG" >&2
  exit 1
fi

echo "temporary HTTPS URL: $URL"
echo "backend: http://127.0.0.1:$PORT"
echo "pid: $PID"
echo "stop: kill $PID"
echo "note: URL changes on every restart; do not use for permanent production"
