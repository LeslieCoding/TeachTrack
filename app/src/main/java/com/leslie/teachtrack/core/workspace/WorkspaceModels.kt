package com.leslie.teachtrack.core.workspace

import java.util.UUID

data class WorkspaceManifest(
    val format: String = "TeachTrackWorkspace",
    val workspaceId: String = UUID.randomUUID().toString(),
    val name: String = "My Students",
    val schemaVersion: Int = 1,
    val createdAt: String,
    val updatedAt: String,
    val appMinVersion: String = "3.0.0",
    val dataFormat: String = "jsonl",
    val encryptionEnabled: Boolean = false
)

data class WorkspaceDevice(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val appVersion: String,
    val lastSeenAt: String
)

data class WorkspaceImportIssue(val file: String, val line: Int, val message: String)

data class WorkspaceImportResult(
    val workspaceId: String,
    val records: Int,
    val files: Int,
    val issues: List<WorkspaceImportIssue>
)

data class WorkspaceExportResult(
    val workspaceId: String,
    val records: Int,
    val files: Int
)

data class SyncEvent(
    val eventId: String,
    val workspaceId: String,
    val deviceId: String,
    val timestamp: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val baseRevision: Long,
    val revision: Long,
    val payload: String
)

object WorkspaceFiles {
    const val MANIFEST = "workspace.json"
    const val STUDENTS = "students.jsonl"
    const val COURSES = "courses.jsonl"
    const val EXAMS = "exams.jsonl"
    const val SCORES = "scores.jsonl"
    const val KNOWLEDGE_POINTS = "knowledge_points.jsonl"
    const val KNOWLEDGE_PROGRESS = "knowledge_progress.jsonl"
    const val KNOWLEDGE_EVENTS = "knowledge_events.jsonl"
    const val HOMEWORK = "homework.jsonl"
    const val PROBLEMS = "problems.jsonl"
    const val COMMUNICATIONS = "communications.jsonl"
    const val LESSON_PLANS = "lesson_plans.jsonl"
    const val TASKS = "tasks.jsonl"
    const val PARENT_CONTACTS = "parent_contacts.jsonl"
    const val SCORE_ITEMS = "score_items.jsonl"
    const val TEMPLATES = "templates.jsonl"
    const val TAGS = "tags.jsonl"
    const val STUDENT_TAGS = "student_tags.jsonl"
    const val ATTACHMENTS = "attachments.jsonl"
    const val HISTORICAL_RECORDS = "historical_learning_records.jsonl"
    const val STUDENT_PROGRESS = "student_progress.jsonl"
    const val UNRESOLVED_RECORDS = "unresolved_historical_records.jsonl"
}
