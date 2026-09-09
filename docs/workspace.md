# Portable Workspace

TeachTrack 3.0 stores cross-device data in a user-selected folder, not in a shared SQLite file.

Use **Settings → 数据与同步 / Portable Workspace → 创建工作区** to create a folder. Select **打开已有工作区** on another device. The folder contains `workspace.json`, UTF-8 JSONL in `data/`, `.teachtrack/` metadata and `sync/` device/event state. Room continues to provide fast local and offline operation.

The historical student seed is deliberately not packaged into the APK. Copy `seed/teacher_seed_v1.json` to a device and use **历史学生数据库 → 选择 JSON 并导入**.

Use **立即导出到当前工作区** after local changes. Use **创建 .ttbackup 备份** for a ZIP backup. The app does not synchronize `teachtrack.db`, `teachtrack.db-wal` or `teachtrack.db-shm`.
