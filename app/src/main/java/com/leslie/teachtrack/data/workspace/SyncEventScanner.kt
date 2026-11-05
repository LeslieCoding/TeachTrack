package com.leslie.teachtrack.data.workspace

import com.leslie.teachtrack.core.workspace.SyncConflict
import org.json.JSONObject

data class SyncScanResult(val events: Int, val conflicts: Int, val issues: Int)

class SyncEventScanner(private val manager: WorkspaceManager) {
    fun scan(storage: WorkspaceStorage): SyncScanResult {
        val manifest = manager.readManifest(storage); val engine = SyncEngine(manager)
        val seen = mutableSetOf<String>(); val revisions = mutableMapOf<String, Long>(); var events = 0; var conflicts = 0; var issues = 0; var last = ""
        fun walk(path: String) {
            storage.list(path).forEach { item ->
                val text = storage.read(item)
                if (text == null) walk(item) else if (item.endsWith(".jsonl")) text.lineSequence().forEach { line ->
                    if (line.isBlank()) return@forEach
                    runCatching {
                        val o = JSONObject(line); val eventId = o.getString("eventId"); if (!seen.add(eventId)) return@runCatching
                        val key = "${o.getString("entityType")}:${o.getString("entityId")}"; val base = o.optLong("baseRevision"); val incoming = o.optLong("revision"); val current = revisions[key]
                        if (current != null && engine.detectConflict(current, base)) {
                            val conflictFile = "sync/conflicts/event-$eventId-conflict.json"
                            if (!storage.exists(conflictFile)) {
                                conflicts++
                                engine.writeConflict(storage, SyncConflict("event-$eventId-conflict", manifest.workspaceId, o.getString("entityType"), o.getString("entityId"), current, base, "{}", o.optJSONObject("payload")?.toString() ?: "{}", IsoTimestamp.now()))
                            }
                        } else revisions[key] = maxOf(current ?: 0L, incoming)
                        events++; last = eventId
                    }.onFailure { issues++ }
                }
            }
        }
        walk("sync/events"); if (last.isNotBlank()) engine.writeCheckpoint(storage, manifest.workspaceId, last)
        return SyncScanResult(events, conflicts, issues)
    }
}

class ConflictResolutionStore(private val manager: WorkspaceManager) {
    companion object {
        val allowedChoices = setOf("VERSION_A", "VERSION_B", "MANUAL_MERGE")
    }
    fun count(storage: WorkspaceStorage): Int = storage.list("sync/conflicts").count { it.endsWith(".json") }
    fun files(storage: WorkspaceStorage): List<String> = storage.list("sync/conflicts").filter { it.endsWith(".json") }
    fun summary(storage: WorkspaceStorage, file: String): String = runCatching {
        val o = JSONObject(storage.read(file) ?: return "")
        "${o.optString("entityType")} / ${o.optString("entityId")}：本地 revision ${o.optLong("localRevision")}，外部基于 ${o.optLong("incomingBaseRevision")}"
    }.getOrDefault(file)
    fun recordResolution(storage: WorkspaceStorage, conflictFile: String, choice: String) {
        require(choice in allowedChoices) { "Unsupported conflict resolution choice" }
        require(conflictFile in files(storage)) { "Conflict file does not exist" }
        storage.write(
            "sync/conflicts/resolutions/${conflictFile.substringAfterLast('/').removeSuffix(".json")}.json",
            JSONObject()
                .put("conflictFile", conflictFile)
                .put("choice", choice)
                .put("deviceId", manager.device.deviceId)
                .put("resolvedAt", IsoTimestamp.now())
                .toString(2)
        )
    }
}
