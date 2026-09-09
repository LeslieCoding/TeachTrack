package com.leslie.teachtrack.data.workspace

import com.leslie.teachtrack.core.workspace.SyncEvent
import java.time.Instant
import java.util.UUID
import org.json.JSONObject

class SyncEngine(private val manager: WorkspaceManager) {
    fun appendEvent(storage: WorkspaceStorage, workspaceId: String, entityType: String, entityId: String, operation: String, baseRevision: Long, revision: Long, payload: JSONObject): SyncEvent {
        val event = SyncEvent(UUID.randomUUID().toString(), workspaceId, manager.device.deviceId, Instant.now().toString(), entityType, entityId, operation, baseRevision, revision, payload.toString())
        val month = Instant.now().toString().substring(0, 7).replace('-', '/')
        storage.append("sync/events/$month/${manager.device.deviceId}.jsonl", JSONObject().put("eventId", event.eventId).put("workspaceId", event.workspaceId).put("deviceId", event.deviceId).put("timestamp", event.timestamp).put("entityType", event.entityType).put("entityId", event.entityId).put("operation", event.operation).put("baseRevision", event.baseRevision).put("revision", event.revision).put("payload", JSONObject(event.payload)).toString() + "\n")
        return event
    }
    fun writeCheckpoint(storage: WorkspaceStorage, workspaceId: String, lastProcessedEvent: String) {
        storage.write("sync/checkpoints/${manager.device.deviceId}.json", JSONObject().put("deviceId", manager.device.deviceId).put("workspaceId", workspaceId).put("lastProcessedEvent", lastProcessedEvent).put("updatedAt", Instant.now().toString()).toString(2))
    }
}
