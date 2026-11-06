#!/usr/bin/env bash
set -euo pipefail

# Download a D1 backup, verify SHA-256, decrypt, restore to SQLite, and compare counts.
# Never modifies the production D1 database.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
KEY_FILE="${CLOUD_BACKUP_KEY_FILE:-$ROOT/backend/.cloud-backup-key}"
BYPY_CONFIG_DIR="${BYPY_CONFIG_DIR:-D:/SoftWare/bypy-home/.bypy}"
REMOTE_DIR="${1:?usage: d1-restore-test.sh teachtrack/backups/YYYYMMDD-HHMMSS-d1}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
export MSYS_NO_PATHCONV=1
BYPY=(bypy --config-dir "$BYPY_CONFIG_DIR")

for name in teachtrack-d1.sql.enc teachtrack-d1.sql.enc.sha256 manifest.json; do
  "${BYPY[@]}" download "$REMOTE_DIR/$name" "$(cygpath -w "$WORK")" >/dev/null
done

ACTUAL="$(sha256sum "$(cygpath -m "$WORK/teachtrack-d1.sql.enc")" | cut -d' ' -f1)"
EXPECTED="$(tr -d ' \r\n' < "$WORK/teachtrack-d1.sql.enc.sha256")"
[[ "$ACTUAL" == "$EXPECTED" ]] || { echo "sha256 mismatch" >&2; exit 1; }

openssl enc -d -aes-256-cbc -pbkdf2 \
  -in "$(cygpath -m "$WORK/teachtrack-d1.sql.enc")" \
  -out "$(cygpath -m "$WORK/teachtrack-d1.sql")" \
  -pass file:"$(cygpath -m "$KEY_FILE")"

python - "$(cygpath -m "$WORK")" <<'PY'
import json, sqlite3, sys
work = sys.argv[1]
manifest = json.load(open(work + "/manifest.json", encoding="utf-8"))
db = sqlite3.connect(":memory:")
db.executescript(open(work + "/teachtrack-d1.sql", encoding="utf-8").read())
actual = {
    "students": db.execute("select count(*) from students").fetchone()[0],
    "courseSessions": db.execute("select count(*) from course_sessions").fetchone()[0],
    "questions": db.execute("select count(*) from questions").fetchone()[0],
    "knowledgePoints": db.execute("select count(*) from knowledge_points").fetchone()[0],
}
assert actual == manifest["counts"], (actual, manifest["counts"])
print("D1 download/decrypt/restore verified:", json.dumps(actual, ensure_ascii=False))
PY
