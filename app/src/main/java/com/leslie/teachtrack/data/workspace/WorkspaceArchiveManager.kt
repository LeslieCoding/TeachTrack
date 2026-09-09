package com.leslie.teachtrack.data.workspace

import android.content.ContentResolver
import android.net.Uri
import com.leslie.teachtrack.core.workspace.WorkspaceFiles
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject

class WorkspaceArchiveManager {
    private val dataFiles = listOf(
        WorkspaceFiles.MANIFEST, ".teachtrack/workspace.id", ".teachtrack/schema.version", ".teachtrack/state.json",
        "data/${WorkspaceFiles.STUDENTS}", "data/${WorkspaceFiles.COURSES}", "data/${WorkspaceFiles.EXAMS}", "data/${WorkspaceFiles.SCORES}",
        "data/${WorkspaceFiles.KNOWLEDGE_POINTS}", "data/${WorkspaceFiles.KNOWLEDGE_PROGRESS}", "data/${WorkspaceFiles.KNOWLEDGE_EVENTS}",
        "data/${WorkspaceFiles.HOMEWORK}", "data/${WorkspaceFiles.PROBLEMS}", "data/${WorkspaceFiles.COMMUNICATIONS}", "data/${WorkspaceFiles.LESSON_PLANS}",
        "data/${WorkspaceFiles.TASKS}", "data/${WorkspaceFiles.PARENT_CONTACTS}", "data/${WorkspaceFiles.SCORE_ITEMS}", "data/${WorkspaceFiles.TEMPLATES}",
        "data/${WorkspaceFiles.TAGS}", "data/${WorkspaceFiles.STUDENT_TAGS}", "data/${WorkspaceFiles.ATTACHMENTS}", "data/${WorkspaceFiles.HISTORICAL_RECORDS}",
        "data/${WorkspaceFiles.STUDENT_PROGRESS}", "data/${WorkspaceFiles.UNRESOLVED_RECORDS}", "sync/devices"
    )

    fun create(storage: WorkspaceStorage, resolver: ContentResolver, uri: Uri, workspaceId: String, appVersion: String, schemaVersion: Int, counts: JSONObject) {
        resolver.openOutputStream(uri, "wt")?.use { output ->
            ZipOutputStream(output).use { zip ->
                dataFiles.filter { storage.exists(it) }.forEach { path ->
                    val entry = ZipEntry(path); zip.putNextEntry(entry); zip.write((storage.read(path) ?: "").toByteArray(Charsets.UTF_8)); zip.closeEntry()
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
                if (!entry.isDirectory && name != "backup.json") { target.write(name, zip.readBytes().toString(Charsets.UTF_8)); files++ }
                zip.closeEntry()
            }
        } } ?: error("Cannot read .ttbackup")
        return files
    }
}
