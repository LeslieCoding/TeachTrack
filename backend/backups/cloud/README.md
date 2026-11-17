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
