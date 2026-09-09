# TeachTrack 3.0 Portable Workspace Requirements

- `V3-REQ-001`: Workspace owns portable data; Room remains the local offline query database.
- `V3-REQ-002`: A valid workspace is identified by `workspace.json` with `format=TeachTrackWorkspace` and a permanent `workspaceId`.
- `V3-REQ-003`: Business data is exchanged as UTF-8 JSONL under `data/`; SQLite/WAL/SHM are never shared.
- `V3-REQ-004`: Android uses Storage Access Framework tree URIs with persisted permission.
- `V3-REQ-005`: Export preserves IDs and writes revisions, device metadata and tombstone-compatible fields.
- `V3-REQ-006`: Import is idempotent, validates workspace identity and continues after malformed JSONL lines with an issue report.
- `V3-REQ-007`: Device identity is a generated UUID stored locally and represented under `sync/devices/`.
- `V3-REQ-008`: Basic sync events and per-device checkpoints are written under `sync/`.
- `V3-REQ-009`: `.ttbackup` is a ZIP container containing workspace metadata and JSONL data.
- `V3-REQ-010`: Existing Room data is never deleted by workspace operations.

Automatic cloud sync, WebDAV/Syncthing adapters, full conflict editor, AES encryption, desktop clients and physical-delete garbage collection remain subsequent phases.
