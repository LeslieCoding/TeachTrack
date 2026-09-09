package com.leslie.teachtrack.data.workspace

import android.content.Context
import android.net.Uri
import com.leslie.teachtrack.core.workspace.*
import java.time.Instant
import java.util.UUID
import org.json.JSONObject

class WorkspaceManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("teachtrack_workspace", Context.MODE_PRIVATE)
    val device = DeviceIdentity(context)
    fun currentUri(): Uri? = prefs.getString("rootUri", null)?.let(Uri::parse)
    fun attach(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        prefs.edit().putString("rootUri", uri.toString()).apply()
    }
    fun storage(uri: Uri? = currentUri()): WorkspaceStorage? = uri?.let { AndroidSafWorkspaceStorage(context, it) }
    fun createManifest(name: String = "My Students"): WorkspaceManifest = WorkspaceManifest(createdAt = Instant.now().toString(), updatedAt = Instant.now().toString(), name = name)
    fun ensureStructure(storage: WorkspaceStorage, manifest: WorkspaceManifest) {
        storage.write(WorkspaceFiles.MANIFEST, JSONObject().put("format", manifest.format).put("workspaceId", manifest.workspaceId).put("name", manifest.name).put("schemaVersion", manifest.schemaVersion).put("createdAt", manifest.createdAt).put("updatedAt", manifest.updatedAt).put("appMinVersion", manifest.appMinVersion).put("dataFormat", manifest.dataFormat).put("encryption", JSONObject().put("enabled", manifest.encryptionEnabled)).toString(2))
        storage.write(".teachtrack/workspace.id", manifest.workspaceId)
        storage.write(".teachtrack/schema.version", manifest.schemaVersion.toString())
        storage.write(".teachtrack/state.json", JSONObject().put("workspaceId", manifest.workspaceId).put("deviceId", device.deviceId).put("updatedAt", Instant.now().toString()).toString(2))
        storage.write("sync/devices/${device.deviceId}.json", device.json().toString(2))
    }
    fun readManifest(storage: WorkspaceStorage): WorkspaceManifest {
        val o = JSONObject(storage.read(WorkspaceFiles.MANIFEST) ?: error("workspace.json not found"))
        require(o.optString("format") == "TeachTrackWorkspace") { "Not a TeachTrack Workspace" }
        require(o.optString("workspaceId").isNotBlank()) { "Workspace ID is missing" }
        return WorkspaceManifest(o.getString("format"), o.getString("workspaceId"), o.optString("name", "My Students"), o.optInt("schemaVersion", 1), o.optString("createdAt"), o.optString("updatedAt"), o.optString("appMinVersion", "1.0.0"), o.optString("dataFormat", "jsonl"), o.optJSONObject("encryption")?.optBoolean("enabled", false) ?: false)
    }
    fun copyUriToWorkspace(uri: Uri, storage: WorkspaceStorage, relativePath: String) {
        context.contentResolver.openInputStream(uri)?.use { input -> storage.writeBytes(relativePath, input.readBytes()) } ?: error("Attachment cannot be read")
    }
}
