# TeachTrack cloud backup

This private repository stores **encrypted** PostgreSQL backups of TeachTrack.

- No plaintext student data is committed.
- Encryption: AES-256-CBC with PBKDF2; the key is held by the owner outside this repo.
- Each backup lives under `dumps/<timestamp>/`.
- `manifest.json` contains only counts, never names, phones or answers.

Restore (owner machine only):

```bash
openssl enc -d -aes-256-cbc -pbkdf2 -in dumps/<timestamp>/teachtrack.dump.enc \
  -out teachtrack.dump -pass file:/path/to/.cloud-backup-key
pg_restore --clean --if-exists --no-owner -d <target-db> teachtrack.dump
```

If the key is lost, the backups are unrecoverable by design.
