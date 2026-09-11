package com.leslie.teachtrack.data.workspace

import android.content.Context
import org.json.JSONObject

/** Best-effort event journal: local Room writes never fail because a workspace is unavailable. */
class WorkspaceSyncCoordinator(context: Context) {
    private val manager = WorkspaceManager(context)
    private val engine = SyncEngine(manager)
    private val revisionStore = context.getSharedPreferences("teachtrack_sync_revisions", Context.MODE_PRIVATE)

    @Synchronized
    fun record(entityType: String, entityId: String, operation: String, payload: JSONObject, baseRevision: Long? = null, revision: Long? = null) {
        runCatching {
            val storage = manager.storage() ?: return
            val manifest = manager.readManifest(storage)
            val key = "${manifest.workspaceId}:$entityType:$entityId"
            val previous = revisionStore.getLong(key, 0L)
            val eventBase = baseRevision ?: previous
            val eventRevision = revision ?: (previous + 1L)
            engine.appendEvent(storage, manifest.workspaceId, entityType, entityId, operation, eventBase, eventRevision, payload)
            revisionStore.edit().putLong(key, maxOf(previous, eventRevision)).apply()
        }
    }
}
