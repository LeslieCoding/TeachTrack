# TeachTrack 3.0 Workspace Architecture

```text
SAF tree URI -> AndroidSafWorkspaceStorage -> Workspace Manager
             -> JSONL Importer / Exporter -> Room local database
             -> Repository -> Domain and Compose UI
```

The exporter converts the Room backup projection into one JSON object per line, preserving entity IDs. The importer validates the manifest and each line, continues after malformed lines, and performs idempotent Room upserts. `SyncEngine` owns event/checkpoint wire formats and never shares the SQLite file.

Room migrations remain non-destructive. If a workspace cannot be read, the app continues against existing local data and reports the workspace error.
