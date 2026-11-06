#!/usr/bin/env bash
set -euo pipefail

# Restore the newest encrypted pg_dump from Baidu Netdisk into a target database.
#
#   TARGET_DB_URL='postgresql://teachtrack@127.0.0.1:55432/teachtrack_restore_test' \
#   CONFIRM=YES \
#   ./baidu-restore.sh
#
# CONFIRM=YES is required because pg_restore --clean drops objects in the target.
# The target should be a NEW/EMPTY database, never the live one unless you intend it.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PG_BIN="${PG_BIN:-/d/SoftWare/pgsql-16/bin}"
TARGET_DB_URL="${TARGET_DB_URL:-postgresql://teachtrack@127.0.0.1:55432/teachtrack_restore_test}"
KEY_FILE="${CLOUD_BACKUP_KEY_FILE:-$ROOT/backend/.cloud-backup-key}"
REMOTE_ROOT="${BAIDU_BACKUP_ROOT:-teachtrack/backups}"

if [[ "${CONFIRM:-}" != "YES" ]]; then
  echo "Refusing to restore without CONFIRM=YES (target objects will be dropped)." >&2
  echo "Target: $TARGET_DB_URL" >&2
  exit 2
fi
if [[ ! -f "$KEY_FILE" ]]; then
  echo "backup key not found: $KEY_FILE" >&2
  exit 1
fi

export MSYS_NO_PATHCONV=1
STAMP="$(bypy list "/$REMOTE_ROOT" 2>/dev/null | awk '$1=="D"{print $2}' | sort | tail -1)"
if [[ -z "$STAMP" ]]; then
  echo "no backup directory found under /apps/bypy/$REMOTE_ROOT" >&2
  exit 1
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "restoring Baidu backup: $STAMP"
bypy download "/$REMOTE_ROOT/$STAMP/teachtrack.dump.enc" "$(cygpath -w "$WORK/teachtrack.dump.enc")" >/dev/null
bypy download "/$REMOTE_ROOT/$STAMP/teachtrack.dump.enc.sha256" "$(cygpath -w "$WORK/teachtrack.dump.enc.sha256")" >/dev/null
bypy download "/$REMOTE_ROOT/$STAMP/manifest.json" "$(cygpath -w "$WORK/manifest.json")" >/dev/null

echo "verify sha256"
EXPECTED="$(tr -d ' \r\n' < "$WORK/teachtrack.dump.enc.sha256")"
ACTUAL="$(python - "$WORK/teachtrack.dump.enc" <<'PY'
import hashlib,sys
print(hashlib.sha256(open(sys.argv[1],'rb').read()).hexdigest())
PY
)"
if [[ "$EXPECTED" != "$ACTUAL" ]]; then
  echo "sha256 mismatch" >&2
  exit 1
fi

echo "decrypt"
openssl enc -d -aes-256-cbc -pbkdf2 \
  -in "$(cygpath -m "$WORK/teachtrack.dump.enc")" \
  -out "$(cygpath -m "$WORK/teachtrack.dump")" \
  -pass file:"$(cygpath -m "$KEY_FILE")"

echo "restore into target"
"$PG_BIN/pg_restore.exe" --clean --if-exists --no-owner --no-privileges \
  -d "$TARGET_DB_URL" "$(cygpath -w "$WORK/teachtrack.dump")"

echo "verify counts"
"$PG_BIN/psql.exe" -d "$TARGET_DB_URL" -Atc "
select 'students=' || count(*) from students
union all select 'course_sessions=' || count(*) from course_sessions
union all select 'questions=' || count(*) from questions;
"
echo "restore completed from $STAMP"
