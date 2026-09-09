package com.leslie.teachtrack.data.workspace

import android.content.Context
import org.json.JSONObject

/** Best-effort event journal: local Room writes never fail because a workspace is unavailable. */
class WorkspaceSyncCoordinator(context: Context) {
    private val manager = WorkspaceManager(context)
    private val engine = SyncEngine(manager)

    fun record(entityType: String, entityId: String, operation: String, payload: JSONObject, baseRevision: Long = 0L, revision: Long = 1L) {
        runCatching {
            val storage = manager.storage() ?: return
            val manifest = manager.readManifest(storage)
            engine.appendEvent(storage, manifest.workspaceId, entityType, entityId, operation, baseRevision, revision, payload)
        }
    }
}
