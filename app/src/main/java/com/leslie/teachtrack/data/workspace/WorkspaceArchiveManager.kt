package com.leslie.teachtrack.data.workspace

import android.content.ContentResolver
import android.net.Uri
import com.leslie.teachtrack.core.workspace.WorkspaceFiles
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject

class WorkspaceArchiveManager {
    fun create(storage: WorkspaceStorage, resolver: ContentResolver, uri: Uri, workspaceId: String, appVersion: String, schemaVersion: Int, counts: JSONObject) {
        resolver.openOutputStream(uri, "wt")?.use { output ->
            ZipOutputStream(output).use { zip ->
                collectFiles(storage, "").forEach { path ->
                    val bytes = storage.readBytes(path) ?: return@forEach
                    zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("backup.json")); zip.write(JSONObject().put("backupFormatVersion", 1).put("workspaceId", workspaceId).put("createdAt", java.time.Instant.now().toString()).put("appVersion", appVersion).put("schemaVersion", schemaVersion).put("counts", counts).toString(2).toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
        } ?: error("Cannot create .ttbackup")
    }

    fun restore(resolver: ContentResolver, uri: Uri, target: WorkspaceStorage): Int {
        var files = 0
        resolver.openInputStream(uri)?.use { input -> ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                require(!name.startsWith("/") && !name.contains("..")) { "Unsafe backup path" }
                if (!entry.isDirectory && name != "backup.json") { target.writeBytes(name, zip.readBytes()); files++ }
                zip.closeEntry()
            }
        } } ?: error("Cannot read .ttbackup")
        return files
    }

    private fun collectFiles(storage: WorkspaceStorage, directory: String): List<String> {
        return storage.list(directory).flatMap { path ->
            if (storage.readBytes(path) != null) listOf(path) else collectFiles(storage, path)
        }
    }
}
