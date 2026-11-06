#!/usr/bin/env bash
set -euo pipefail

# Encrypted PostgreSQL backup to Baidu Netdisk through bypy.
# This uploads a consistent pg_dump, NOT the live PostgreSQL data directory.
#
# Requirements: bypy authorized (bypy info), openssl, pg_dump.
# Key: backend/.cloud-backup-key (created by cloud-backup.sh if missing).

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PG_BIN="${PG_BIN:-/d/SoftWare/pgsql-16/bin}"
SOURCE_DB_URL="${SOURCE_DB_URL:-postgresql://teachtrack@127.0.0.1:55432/teachtrack}"
KEY_FILE="${CLOUD_BACKUP_KEY_FILE:-$ROOT/backend/.cloud-backup-key}"
REMOTE_ROOT="${BAIDU_BACKUP_ROOT:-teachtrack/backups}"
STAMP="$(date +%Y%m%d-%H%M%S)"

if ! command -v bypy >/dev/null 2>&1; then
  echo "bypy is required (pip install bypy && bypy info)" >&2
  exit 1
fi
if [[ ! -f "$KEY_FILE" ]]; then
  echo "backup key not found: $KEY_FILE" >&2
  echo "run backend/ops/cloud-backup.sh once to create it" >&2
  exit 1
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "1/4 dump local database"
"$PG_BIN/pg_dump.exe" --format=custom --no-owner --file="$WORK/teachtrack.dump" "$SOURCE_DB_URL"

echo "2/4 encrypt"
openssl enc -aes-256-cbc -pbkdf2 -salt \
  -in "$(cygpath -m "$WORK/teachtrack.dump")" \
  -out "$(cygpath -m "$WORK/teachtrack.dump.enc")" \
  -pass file:"$(cygpath -m "$KEY_FILE")"

SHA="$(python - "$WORK/teachtrack.dump.enc" <<'PY'
import hashlib,sys
print(hashlib.sha256(open(sys.argv[1],'rb').read()).hexdigest())
PY
)"
printf '%s\n' "$SHA" > "$WORK/teachtrack.dump.enc.sha256"

echo "3/4 manifest"
cat > "$WORK/manifest.json" <<EOF
{
  "createdAt": "$(date -Iseconds)",
  "database": "teachtrack",
  "encryption": "AES-256-CBC/PBKDF2",
  "dumpFile": "teachtrack.dump.enc",
  "dumpSha256": "$SHA",
  "note": "pg_dump backup; not a live PostgreSQL data directory",
  "counts": {
    "students": $(MSYS_NO_PATHCONV=1 "$PG_BIN/psql.exe" -d "$SOURCE_DB_URL" -Atc "select count(*) from students"),
    "courseSessions": $(MSYS_NO_PATHCONV=1 "$PG_BIN/psql.exe" -d "$SOURCE_DB_URL" -Atc "select count(*) from course_sessions"),
    "questions": $(MSYS_NO_PATHCONV=1 "$PG_BIN/psql.exe" -d "$SOURCE_DB_URL" -Atc "select count(*) from questions")
  }
}
EOF

echo "4/4 upload to Baidu Netdisk"
export MSYS_NO_PATHCONV=1
REMOTE_DIR="$REMOTE_ROOT/$STAMP"
bypy mkdir "$REMOTE_ROOT" >/dev/null 2>&1 || true
bypy mkdir "$REMOTE_DIR" >/dev/null 2>&1 || true

upload() {
  local file="$1" name="$2"
  bypy upload "$(cygpath -w "$file")" "$REMOTE_DIR/$name" overwrite >/dev/null
}

upload "$WORK/teachtrack.dump.enc" "teachtrack.dump.enc"
upload "$WORK/teachtrack.dump.enc.sha256" "teachtrack.dump.enc.sha256"
upload "$WORK/manifest.json" "manifest.json"

echo "baidu backup completed: $REMOTE_DIR"
bypy list "/$REMOTE_DIR" 2>&1 | tail -5
