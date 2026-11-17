#!/usr/bin/env bash
set -euo pipefail

# Encrypted cloud backup stored in the main TeachTrack repository (Contents API).
# No git clone/push: GitHub HTTPS git was timing out on this network, while
# `gh api` works. The database dump is encrypted locally; the key never leaves
# the machine.
#
# First run creates backend/.cloud-backup-key (gitignored).
# Requirements: gh CLI logged in, openssl, pg_dump, python.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PG_BIN="${PG_BIN:-/d/SoftWare/pgsql-16/bin}"
SOURCE_DB_URL="${SOURCE_DB_URL:-postgresql://teachtrack@127.0.0.1:55432/teachtrack}"
REPO_SLUG="${CLOUD_BACKUP_REPO_SLUG:-LeslieCoding/TeachTrack}"
BACKUP_ROOT="${CLOUD_BACKUP_REPO_PATH:-backend/backups/cloud}"
KEY_FILE="${CLOUD_BACKUP_KEY_FILE:-$ROOT/backend/.cloud-backup-key}"

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required" >&2
  exit 1
fi
if [[ ! -f "$KEY_FILE" ]]; then
  umask 077
  openssl rand -base64 48 > "$KEY_FILE"
  chmod 600 "$KEY_FILE" 2>/dev/null || true
  echo "generated backup key: $KEY_FILE"
  echo "SAVE THIS KEY SOMEWHERE SAFE. Without it the cloud backups cannot be restored."
fi

BRANCH="$(gh api "repos/$REPO_SLUG" --jq '.default_branch' 2>/dev/null || echo main)"
STAMP="$(date +%Y%m%d-%H%M%S)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "1/4 dump local database"
"$PG_BIN/pg_dump.exe" --format=custom --no-owner --file="$WORK/teachtrack.dump" "$SOURCE_DB_URL"

echo "2/4 encrypt"
KEY_WIN="$(cygpath -m "$KEY_FILE")"
IN_WIN="$(cygpath -m "$WORK/teachtrack.dump")"
OUT_WIN="$(cygpath -m "$WORK/teachtrack.dump.enc")"
openssl enc -aes-256-cbc -pbkdf2 -salt \
  -in "$IN_WIN" \
  -out "$OUT_WIN" \
  -pass file:"$KEY_WIN"
SHA="$(python - "$WORK/teachtrack.dump.enc" <<'PY'
import hashlib,sys
print(hashlib.sha256(open(sys.argv[1],'rb').read()).hexdigest())
PY
)"
printf '%s\n' "$SHA" > "$WORK/teachtrack.dump.enc.sha256"

echo "3/4 manifest (counts only, no names or contact data)"
cat > "$WORK/manifest.json" <<EOF
{
  "createdAt": "$(date -Iseconds)",
  "database": "teachtrack",
  "encryption": "AES-256-CBC/PBKDF2",
  "dumpFile": "teachtrack.dump.enc",
  "dumpSha256": "$SHA",
  "sourceHost": "local-development",
  "counts": {
    "students": $("$PG_BIN/psql.exe" -d "$SOURCE_DB_URL" -Atc "select count(*) from students" 2>/dev/null || echo null),
    "courseSessions": $("$PG_BIN/psql.exe" -d "$SOURCE_DB_URL" -Atc "select count(*) from course_sessions" 2>/dev/null || echo null),
    "questions": $("$PG_BIN/psql.exe" -d "$SOURCE_DB_URL" -Atc "select count(*) from questions" 2>/dev/null || echo null)
  }
}
EOF

cat > "$WORK/README.md" <<'EOF'
# TeachTrack cloud backup

This directory in the `LeslieCoding/TeachTrack` repository stores **encrypted** PostgreSQL backups of TeachTrack.

- No plaintext student data is committed.
- Encryption: AES-256-CBC with PBKDF2; the key is held by the owner outside this repository.
- Each backup lives under `backend/backups/cloud/dumps/<timestamp>/`.
- `manifest.json` contains only counts, never names, phones or answers.
- New backups are uploaded by `backend/ops/cloud-backup.sh` to this same repository and directory.

Restore (owner machine only, from the repository root):

```bash
openssl enc -d -aes-256-cbc -pbkdf2 \
  -in backend/backups/cloud/dumps/<timestamp>/teachtrack.dump.enc \
  -out teachtrack.dump -pass file:backend/.cloud-backup-key
pg_restore --clean --if-exists --no-owner -d <target-db> teachtrack.dump
```

Delete the decrypted `teachtrack.dump` after the restore. If the key is lost, the backups are unrecoverable by design.
EOF

echo "4/4 upload via GitHub Contents API"
upload() {
  python - "$1" "$2" "$STAMP" "$BRANCH" "$REPO_SLUG" <<'PY'
import base64, json, subprocess, sys
path, rel, stamp, branch, slug = sys.argv[1:6]
endpoint = f"repos/{slug}/contents/{rel}"
existing = subprocess.run(
    ["gh", "api", f"{endpoint}?ref={branch}"],
    check=False,
    stdout=subprocess.PIPE,
    stderr=subprocess.DEVNULL,
)
body = {
    "message": "backup " + stamp,
    "content": base64.b64encode(open(path, "rb").read()).decode("ascii"),
    "branch": branch,
}
if existing.returncode == 0:
    body["sha"] = json.loads(existing.stdout)["sha"]
subprocess.run(
    ["gh", "api", "--method", "PUT", endpoint, "--input", "-"],
    input=json.dumps(body).encode("utf-8"),
    check=True,
    stdout=subprocess.DEVNULL,
)
PY
}

upload "$WORK/teachtrack.dump.enc" "$BACKUP_ROOT/dumps/$STAMP/teachtrack.dump.enc"
upload "$WORK/teachtrack.dump.enc.sha256" "$BACKUP_ROOT/dumps/$STAMP/teachtrack.dump.enc.sha256"
upload "$WORK/manifest.json" "$BACKUP_ROOT/dumps/$STAMP/manifest.json"
upload "$WORK/README.md" "$BACKUP_ROOT/README.md"

echo "cloud backup completed: $STAMP"
echo "repo: https://github.com/$REPO_SLUG/tree/$BRANCH/$BACKUP_ROOT"
echo "key: $KEY_FILE (keep it safe; never commit it)"
