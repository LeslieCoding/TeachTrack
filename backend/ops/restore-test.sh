#!/usr/bin/env bash
set -euo pipefail

# Restores the newest dump into a separate database and verifies row counts.
# Never touches the production database.
#
# Usage:
#   PG_BIN=/d/SoftWare/pgsql-16/bin ./restore-test.sh [dump-file]

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PG_BIN="${PG_BIN:-/d/SoftWare/pgsql-16/bin}"
HOST="${PGHOST:-127.0.0.1}"
PORT="${PGPORT:-55432}"
USER="${PGUSER:-teachtrack}"
TEST_DB="${TEST_DB:-teachtrack_restore_test}"
DUMP="${1:-$(ls -t "$ROOT"/backups/postgres/*.dump 2>/dev/null | head -1)}"

if [[ -z "${DUMP:-}" || ! -f "$DUMP" ]]; then
  echo "no dump file found" >&2
  exit 1
fi

echo "restoring $DUMP into $TEST_DB"

"$PG_BIN/dropdb.exe" -h "$HOST" -p "$PORT" -U "$USER" --if-exists "$TEST_DB"
"$PG_BIN/createdb.exe" -h "$HOST" -p "$PORT" -U "$USER" "$TEST_DB"
"$PG_BIN/pg_restore.exe" --no-owner --clean --if-exists -h "$HOST" -p "$PORT" -U "$USER" -d "$TEST_DB" "$DUMP"

echo "row counts:"
"$PG_BIN/psql.exe" -h "$HOST" -p "$PORT" -U "$USER" -d "$TEST_DB" -Atc "
select 'students=' || count(*) from students
union all select 'course_sessions=' || count(*) from course_sessions
union all select 'questions=' || count(*) from questions
union all select 'exam_papers=' || count(*) from exam_papers
union all select 'practice_sets=' || count(*) from practice_sets;
"

echo "restore verified in $TEST_DB"
