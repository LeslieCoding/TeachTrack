package com.leslie.teachtrack.data.workspace

import com.leslie.teachtrack.core.workspace.SyncEvent
import java.time.Instant
import java.util.UUID
import org.json.JSONObject

class SyncEngine(private val manager: WorkspaceManager) {
    fun detectConflict(localRevision: Long, incomingBaseRevision: Long): Boolean = localRevision != incomingBaseRevision
    fun appendEvent(storage: WorkspaceStorage, workspaceId: String, entityType: String, entityId: String, operation: String, baseRevision: Long, revision: Long, payload: JSONObject): SyncEvent {
        val event = SyncEvent(UUID.randomUUID().toString(), workspaceId, manager.device.deviceId, Instant.now().toString(), entityType, entityId, operation, baseRevision, revision, payload.toString())
        val month = Instant.now().toString().substring(0, 7).replace('-', '/')
        storage.append("sync/events/$month/${manager.device.deviceId}.jsonl", JSONObject().put("eventId", event.eventId).put("workspaceId", event.workspaceId).put("deviceId", event.deviceId).put("timestamp", event.timestamp).put("entityType", event.entityType).put("entityId", event.entityId).put("operation", event.operation).put("baseRevision", event.baseRevision).put("revision", event.revision).put("payload", JSONObject(event.payload)).toString() + "\n")
        if (operation == "DELETE") storage.write("sync/tombstones/$entityType-$entityId.json", JSONObject().put("workspaceId", workspaceId).put("entityType", entityType).put("entityId", entityId).put("deletedAt", event.timestamp).put("revision", revision).put("deviceId", event.deviceId).toString(2))
        return event
    }
    fun writeCheckpoint(storage: WorkspaceStorage, workspaceId: String, lastProcessedEvent: String) {
        storage.write("sync/checkpoints/${manager.device.deviceId}.json", JSONObject().put("deviceId", manager.device.deviceId).put("workspaceId", workspaceId).put("lastProcessedEvent", lastProcessedEvent).put("updatedAt", Instant.now().toString()).toString(2))
    }
    fun writeConflict(storage: WorkspaceStorage, conflict: com.leslie.teachtrack.core.workspace.SyncConflict) {
        storage.write("sync/conflicts/${conflict.conflictId}.json", JSONObject().put("conflictId", conflict.conflictId).put("workspaceId", conflict.workspaceId).put("entityType", conflict.entityType).put("entityId", conflict.entityId).put("localRevision", conflict.localRevision).put("incomingBaseRevision", conflict.incomingBaseRevision).put("localPayload", JSONObject(conflict.localPayload)).put("incomingPayload", JSONObject(conflict.incomingPayload)).put("detectedAt", conflict.detectedAt).toString(2))
    }
}
