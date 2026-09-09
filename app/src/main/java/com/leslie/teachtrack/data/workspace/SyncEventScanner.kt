package com.leslie.teachtrack.data.workspace

import com.leslie.teachtrack.core.workspace.SyncConflict
import java.time.Instant
import java.util.UUID
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
                        if (current != null && engine.detectConflict(current, base)) { conflicts++; engine.writeConflict(storage, SyncConflict(UUID.randomUUID().toString(), manifest.workspaceId, o.getString("entityType"), o.getString("entityId"), current, base, "{}", o.optJSONObject("payload")?.toString() ?: "{}", Instant.now().toString())) } else revisions[key] = maxOf(current ?: 0L, incoming)
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
    fun count(storage: WorkspaceStorage): Int = storage.list("sync/conflicts").count { it.endsWith(".json") }
    fun files(storage: WorkspaceStorage): List<String> = storage.list("sync/conflicts").filter { it.endsWith(".json") }
    fun summary(storage: WorkspaceStorage, file: String): String = runCatching {
        val o = JSONObject(storage.read(file) ?: return "")
        "${o.optString("entityType")} / ${o.optString("entityId")}：本地 revision ${o.optLong("localRevision")}，外部基于 ${o.optLong("incomingBaseRevision")}"
    }.getOrDefault(file)
    fun recordResolution(storage: WorkspaceStorage, conflictFile: String, choice: String) { storage.write("sync/conflicts/resolutions/${conflictFile.substringAfterLast('/').removeSuffix(".json")}.json", JSONObject().put("conflictFile", conflictFile).put("choice", choice).put("deviceId", manager.device.deviceId).put("resolvedAt", Instant.now().toString()).toString(2)) }
}
