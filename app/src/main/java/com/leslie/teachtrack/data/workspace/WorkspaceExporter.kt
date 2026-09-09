package com.leslie.teachtrack.data.workspace

import com.leslie.teachtrack.core.workspace.*
import com.leslie.teachtrack.data.TeachTrackRepository
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

class WorkspaceExporter(private val repository: TeachTrackRepository, private val manager: WorkspaceManager) {
    suspend fun export(storage: WorkspaceStorage, name: String = "My Students"): WorkspaceExportResult {
        val manifest = manager.readManifest(storage)
        val backup = JSONObject(repository.exportBackupJson())
        val mapping = linkedMapOf(
            "students" to WorkspaceFiles.STUDENTS,
            "courses" to WorkspaceFiles.COURSES,
            "exams" to WorkspaceFiles.EXAMS,
            "scores" to WorkspaceFiles.SCORES,
            "knowledgePoints" to WorkspaceFiles.KNOWLEDGE_POINTS,
            "knowledgeProgress" to WorkspaceFiles.KNOWLEDGE_PROGRESS,
            "knowledgeEvents" to WorkspaceFiles.KNOWLEDGE_EVENTS,
            "homework" to WorkspaceFiles.HOMEWORK,
            "problemRecords" to WorkspaceFiles.PROBLEMS,
            "communications" to WorkspaceFiles.COMMUNICATIONS,
            "lessonPlans" to WorkspaceFiles.LESSON_PLANS,
            "tasks" to WorkspaceFiles.TASKS,
            "parentContacts" to WorkspaceFiles.PARENT_CONTACTS,
            "scoreItems" to WorkspaceFiles.SCORE_ITEMS,
            "templates" to WorkspaceFiles.TEMPLATES,
            "tags" to WorkspaceFiles.TAGS,
            "studentTags" to WorkspaceFiles.STUDENT_TAGS,
            "attachments" to WorkspaceFiles.ATTACHMENTS,
            "historicalLearningRecords" to WorkspaceFiles.HISTORICAL_RECORDS,
            "studentProgress" to WorkspaceFiles.STUDENT_PROGRESS,
            "unresolvedHistoricalRecords" to WorkspaceFiles.UNRESOLVED_RECORDS
        )
        var records = 0
        var files = 0
        mapping.forEach { (key, file) ->
            val lines = buildString {
                val array = backup.optJSONArray(key) ?: JSONArray()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    item.put("workspaceId", manifest.workspaceId)
                    item.put("revision", item.optLong("revision", 1L))
                    item.put("deletedAt", item.optString("deletedAt").takeIf { it.isNotBlank() })
                    item.put("updatedByDevice", manager.device.deviceId)
                    append(item.toString()).append('\n')
                    records++
                }
            }
            storage.write("data/$file", lines)
            files++
        }
        manager.ensureStructure(storage, manifest.copy(name = name, appMinVersion = "3.0.0", updatedAt = Instant.now().toString()))
        return WorkspaceExportResult(manifest.workspaceId, records, files)
    }
}
