package com.leslie.teachtrack.data.workspace

import com.leslie.teachtrack.core.workspace.*
import com.leslie.teachtrack.data.TeachTrackRepository
import java.util.LinkedHashMap
import org.json.JSONArray
import org.json.JSONObject

class WorkspaceImporter(private val repository: TeachTrackRepository, private val manager: WorkspaceManager) {
    suspend fun import(storage: WorkspaceStorage): WorkspaceImportResult {
        val manifest = manager.readManifest(storage)
        require(manifest.dataFormat == "jsonl") { "Unsupported workspace data format" }
        val files = linkedMapOf(
            WorkspaceFiles.STUDENTS to "students", WorkspaceFiles.COURSES to "courses", WorkspaceFiles.EXAMS to "exams", WorkspaceFiles.SCORES to "scores",
            WorkspaceFiles.KNOWLEDGE_POINTS to "knowledgePoints", WorkspaceFiles.KNOWLEDGE_PROGRESS to "knowledgeProgress", WorkspaceFiles.KNOWLEDGE_EVENTS to "knowledgeEvents",
            WorkspaceFiles.HOMEWORK to "homework", WorkspaceFiles.PROBLEMS to "problemRecords", WorkspaceFiles.COMMUNICATIONS to "communications", WorkspaceFiles.LESSON_PLANS to "lessonPlans",
            WorkspaceFiles.TASKS to "tasks", WorkspaceFiles.PARENT_CONTACTS to "parentContacts", WorkspaceFiles.SCORE_ITEMS to "scoreItems", WorkspaceFiles.TEMPLATES to "templates",
            WorkspaceFiles.TAGS to "tags", WorkspaceFiles.STUDENT_TAGS to "studentTags", WorkspaceFiles.ATTACHMENTS to "attachments", WorkspaceFiles.HISTORICAL_RECORDS to "historicalLearningRecords",
            WorkspaceFiles.STUDENT_PROGRESS to "studentProgress", WorkspaceFiles.UNRESOLVED_RECORDS to "unresolvedHistoricalRecords"
        )
        val backup = JSONObject().put("format", "TeachTrack Backup").put("version", 1).put("exportedAt", System.currentTimeMillis())
        val issues = mutableListOf<WorkspaceImportIssue>()
        var records = 0
        files.forEach { (file, key) ->
            val text = storage.read("data/$file") ?: return@forEach
            val array = JSONArray()
            text.lineSequence().forEachIndexed { lineIndex, line ->
                if (line.isBlank()) return@forEachIndexed
                try {
                    val item = JSONObject(line)
                    require(item.optString("workspaceId", manifest.workspaceId) == manifest.workspaceId) { "workspaceId mismatch" }
                    require(item.optString("id", "row-${lineIndex + 1}").isNotBlank()) { "missing entity id" }
                    array.put(item)
                    records++
                } catch (e: Exception) {
                    issues += WorkspaceImportIssue(file, lineIndex + 1, e.message ?: "invalid JSONL record")
                }
            }
            backup.put(key, array)
        }
        repository.importBackupJson(backup.toString())
        manager.ensureStructure(storage, manifest)
        return WorkspaceImportResult(manifest.workspaceId, records, files.size, issues)
    }
}
