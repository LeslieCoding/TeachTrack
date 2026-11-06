#!/usr/bin/env bash
set -euo pipefail

# Copy the local TeachTrack database to a managed PostgreSQL instance.
#
#   SOURCE_DB_URL=postgresql://teachtrack@127.0.0.1:55432/teachtrack \
#   CLOUD_DB_URL='postgresql://user:pass@host:5432/teachtrack?sslmode=require' \
#   CONFIRM=YES \
#   PG_BIN=/d/SoftWare/pgsql-16/bin ./migrate-to-cloud.sh
#
# The script REFUSES to run without CONFIRM=YES because pg_restore --clean
# drops existing objects in the target database. Always point CLOUD_DB_URL at
# a NEW/EMPTY cloud database, never at a database with other data.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PG_BIN="${PG_BIN:-/d/SoftWare/pgsql-16/bin}"
SOURCE_DB_URL="${SOURCE_DB_URL:-postgresql://teachtrack@127.0.0.1:55432/teachtrack}"
CLOUD_DB_URL="${CLOUD_DB_URL:-}"

if [[ -z "$CLOUD_DB_URL" ]]; then
  echo "CLOUD_DB_URL is required (managed PostgreSQL connection string)" >&2
  exit 2
fi
if [[ "${CONFIRM:-}" != "YES" ]]; then
  echo "Refusing to restore without CONFIRM=YES." >&2
  echo "Target: ${CLOUD_DB_URL%%\?*}" >&2
  echo "This runs pg_restore --clean and drops existing objects in the target database." >&2
  exit 3
fi

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="$ROOT/backups/cloud-migration"
mkdir -p "$OUT_DIR"
DUMP="$OUT_DIR/teachtrack-local-$STAMP.dump"

echo "1/5 dump local database"
"$PG_BIN/pg_dump.exe" --format=custom --no-owner --file="$DUMP" "$SOURCE_DB_URL"

echo "2/5 verify cloud connectivity"
MSYS_NO_PATHCONV=1 "$PG_BIN/psql.exe" -d "$CLOUD_DB_URL" -Atc "select current_database(), current_user, coalesce(inet_server_addr()::text,'local');"

echo "3/5 restore into cloud database (clean)"
"$PG_BIN/pg_restore.exe" --clean --if-exists --no-owner --no-privileges -d "$CLOUD_DB_URL" "$DUMP"

echo "4/5 verify schema and Flyway history"
MSYS_NO_PATHCONV=1 "$PG_BIN/psql.exe" -d "$CLOUD_DB_URL" -Atc "select count(*) from information_schema.tables where table_schema='public';"
MSYS_NO_PATHCONV=1 "$PG_BIN/psql.exe" -d "$CLOUD_DB_URL" -Atc "select version, description, success from flyway_schema_history order by installed_rank desc limit 3;"

echo "5/5 compare row counts"
for table in students course_sessions questions exam_papers practice_sets; do
  SRC=$(MSYS_NO_PATHCONV=1 "$PG_BIN/psql.exe" -d "$SOURCE_DB_URL" -Atc "select count(*) from $table" 2>/dev/null || echo "n/a")
  DST=$(MSYS_NO_PATHCONV=1 "$PG_BIN/psql.exe" -d "$CLOUD_DB_URL" -Atc "select count(*) from $table" 2>/dev/null || echo "n/a")
  printf "%-20s local=%-8s cloud=%s\n" "$table" "$SRC" "$DST"
done

echo "dump kept at: $DUMP"
echo "cloud migration completed"
