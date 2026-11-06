#!/usr/bin/env bash
set -euo pipefail

# Export Cloudflare D1, verify a real SQLite restore, encrypt, and upload to Baidu Netdisk.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CF_DIR="$ROOT/backend/cloudflare"
KEY_FILE="${CLOUD_BACKUP_KEY_FILE:-$ROOT/backend/.cloud-backup-key}"
BYPY_CONFIG_DIR="${BYPY_CONFIG_DIR:-D:/SoftWare/bypy-home/.bypy}"
REMOTE_ROOT="${BAIDU_BACKUP_ROOT:-teachtrack/backups}"
STAMP="$(date +%Y%m%d-%H%M%S)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

if [[ ! -f "$KEY_FILE" ]]; then
  echo "backup key not found: $KEY_FILE" >&2
  exit 1
fi

echo "1/5 export remote D1"
(cd "$CF_DIR" && npx wrangler d1 export teachtrack --remote -y --output "$(cygpath -m "$WORK/teachtrack-d1.sql")")

echo "2/5 restore into a temporary SQLite database and count rows"
python - "$WORK/teachtrack-d1.sql" "$WORK/manifest-counts.json" <<'PY'
import json, sqlite3, sys
sql = open(sys.argv[1], encoding="utf-8").read()
db = sqlite3.connect(":memory:")
db.executescript(sql)
counts = {
    "students": db.execute("select count(*) from students").fetchone()[0],
    "courseSessions": db.execute("select count(*) from course_sessions").fetchone()[0],
    "questions": db.execute("select count(*) from questions").fetchone()[0],
    "knowledgePoints": db.execute("select count(*) from knowledge_points").fetchone()[0],
}
assert counts["students"] > 0 and counts["courseSessions"] > 0
json.dump(counts, open(sys.argv[2], "w", encoding="utf-8"))
print(json.dumps(counts, ensure_ascii=False))
PY

echo "3/5 encrypt"
openssl enc -aes-256-cbc -pbkdf2 -salt \
  -in "$(cygpath -m "$WORK/teachtrack-d1.sql")" \
  -out "$(cygpath -m "$WORK/teachtrack-d1.sql.enc")" \
  -pass file:"$(cygpath -m "$KEY_FILE")"
SHA="$(sha256sum "$(cygpath -m "$WORK/teachtrack-d1.sql.enc")" | cut -d' ' -f1)"
printf '%s\n' "$SHA" > "$WORK/teachtrack-d1.sql.enc.sha256"

echo "4/5 manifest"
python - "$(cygpath -m "$WORK")" "$SHA" "$REMOTE_ROOT/$STAMP-d1" <<'PY'
import json, sys
from datetime import datetime, timezone
work, sha, remote = sys.argv[1:]
counts = json.load(open(work + "/manifest-counts.json", encoding="utf-8"))
manifest = {
    "createdAt": datetime.now(timezone.utc).isoformat(),
    "engine": "Cloudflare D1 / SQLite",
    "database": "teachtrack",
    "encryption": "AES-256-CBC/PBKDF2",
    "dumpFile": "teachtrack-d1.sql.enc",
    "dumpSha256": sha,
    "remotePath": remote,
    "counts": counts,
    "restoreVerifiedBeforeUpload": True,
}
json.dump(manifest, open(work + "/manifest.json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)
PY

echo "5/5 upload to Baidu Netdisk"
export MSYS_NO_PATHCONV=1
BYPY=(bypy --config-dir "$BYPY_CONFIG_DIR")
REMOTE_DIR="$REMOTE_ROOT/$STAMP-d1"
"${BYPY[@]}" mkdir "$REMOTE_ROOT" >/dev/null 2>&1 || true
"${BYPY[@]}" mkdir "$REMOTE_DIR" >/dev/null 2>&1 || true
for name in teachtrack-d1.sql.enc teachtrack-d1.sql.enc.sha256 manifest.json; do
  "${BYPY[@]}" upload "$(cygpath -w "$WORK/$name")" "$REMOTE_DIR/$name" overwrite >/dev/null
done

echo "D1 backup completed and pre-upload restore verified: $REMOTE_DIR"
