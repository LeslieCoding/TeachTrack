#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   TEACHTRACK_DB_URL=postgresql://teachtrack@127.0.0.1:55432/teachtrack \
#   PG_BIN=/d/SoftWare/pgsql-16/bin ./backup.sh
#
# Writes a custom-format dump plus SHA-256 to ./backups.
# Never stores dumps in the same place as the database server.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PG_BIN="${PG_BIN:-/d/SoftWare/pgsql-16/bin}"
DB_URL="${TEACHTRACK_DB_URL:-postgresql://teachtrack@127.0.0.1:55432/teachtrack}"
OUT_DIR="${OUT_DIR:-$ROOT/backups/postgres}"

mkdir -p "$OUT_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
FILE="$OUT_DIR/teachtrack-$STAMP.dump"

"$PG_BIN/pg_dump.exe" --format=custom --no-owner --file="$FILE" "$DB_URL"

if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$FILE" | tee "$FILE.sha256"
else
  certutil -hashfile "$FILE" SHA256 | tee "$FILE.sha256"
fi

echo "backup written: $FILE"
