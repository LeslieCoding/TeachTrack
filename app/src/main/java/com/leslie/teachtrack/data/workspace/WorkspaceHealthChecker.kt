package com.leslie.teachtrack.data.workspace

import com.leslie.teachtrack.core.workspace.WorkspaceFiles
import java.security.MessageDigest
import org.json.JSONObject

data class WorkspaceHealthReport(val workspaceId: String?, val checkedFiles: Int, val records: Int, val issues: List<String>)

class WorkspaceHealthChecker(private val manager: WorkspaceManager) {
    fun check(storage: WorkspaceStorage): WorkspaceHealthReport {
        val issues = mutableListOf<String>()
        val manifest = runCatching { manager.readManifest(storage) }.getOrElse { issues += it.message ?: "invalid workspace.json"; return WorkspaceHealthReport(null, 0, 0, issues) }
        val files = listOf(WorkspaceFiles.STUDENTS, WorkspaceFiles.COURSES, WorkspaceFiles.EXAMS, WorkspaceFiles.SCORES, WorkspaceFiles.KNOWLEDGE_POINTS, WorkspaceFiles.KNOWLEDGE_PROGRESS, WorkspaceFiles.HOMEWORK, WorkspaceFiles.PROBLEMS, WorkspaceFiles.COMMUNICATIONS, WorkspaceFiles.LESSON_PLANS, WorkspaceFiles.TASKS, WorkspaceFiles.ATTACHMENTS)
        var checked = 0; var records = 0
        files.forEach { file ->
            val text = storage.read("data/$file") ?: return@forEach
            checked++
            text.lineSequence().forEachIndexed { index, line -> if (line.isNotBlank()) runCatching { val o = JSONObject(line); require(o.optString("workspaceId", manifest.workspaceId) == manifest.workspaceId); require(o.optString("id", "row").isNotBlank()); records++ }.onFailure { issues += "$file:${index + 1}: ${it.message}" } }
        }
        storage.read("data/${WorkspaceFiles.ATTACHMENTS}")?.lineSequence()?.forEachIndexed { index, line ->
            if (line.isBlank()) return@forEachIndexed
            runCatching {
                val attachment = JSONObject(line)
                val relativePath = attachment.optString("relativePath")
                if (relativePath.isBlank()) return@runCatching
                require(storage.readBytes(relativePath) != null) { "attachment missing: $relativePath" }
                val expected = attachment.optString("sha256")
                if (expected.isNotBlank()) {
                    val actual = MessageDigest.getInstance("SHA-256").digest(storage.readBytes(relativePath)!!).joinToString("") { "%02x".format(it.toInt() and 0xff) }
                    require(actual.equals(expected, ignoreCase = true)) { "attachment hash mismatch: $relativePath" }
                }
            }.onFailure { issues += "${WorkspaceFiles.ATTACHMENTS}:${index + 1}: ${it.message}" }
        }
        return WorkspaceHealthReport(manifest.workspaceId, checked, records, issues)
    }
}
