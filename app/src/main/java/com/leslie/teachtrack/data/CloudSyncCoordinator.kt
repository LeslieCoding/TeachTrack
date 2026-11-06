package com.leslie.teachtrack.data

import android.content.Context
import com.leslie.teachtrack.core.database.SyncDao
import com.leslie.teachtrack.core.model.CourseSessionEntity
import com.leslie.teachtrack.core.model.StudentEntity
import com.leslie.teachtrack.core.model.SyncOutboxEntity
import com.leslie.teachtrack.remote.CloudCredentials
import com.leslie.teachtrack.sync.SyncScheduler
import java.util.UUID
import org.json.JSONObject

/**
 * Writes local changes into the sync outbox and schedules a background push.
 * It never touches the network on the caller's thread, and never blocks a local
 * Room write when the cloud is not configured.
 */
class CloudSyncCoordinator(
    private val context: Context,
    private val syncDao: SyncDao
) {
    private val credentials = CloudCredentials(context)

    fun enabled(): Boolean = credentials.hasToken() && credentials.baseUrl().isNotBlank()

    suspend fun enqueueStudent(student: StudentEntity) {
        if (!enabled()) return
        val payload = JSONObject()
            .put("id", student.id)
            .put("legacyId", student.id)
            .put("displayName", student.displayName.ifBlank { student.name })
            .put("fullName", student.name)
            .put("status", student.status.name)
            .put("gender", student.gender)
            .put("currentGrade", student.currentGrade)
            .put("gradeAtRecord", student.gradeAtRecord)
            .put("nameCompleteness", student.nameCompleteness)
            .put("school", student.school)
            .put("className", student.className)
            .put("notes", student.notes)
        enqueue("student", student.id, payload)
    }

    suspend fun enqueueCourse(course: CourseSessionEntity) {
        if (!enabled()) return
        val payload = JSONObject()
            .put("id", course.id)
            .put("legacyId", course.id)
            .put("studentId", course.studentId)
            .put("date", course.date)
            .put("startTime", course.startTime)
            .put("endTime", course.endTime)
            .put("lessonTitle", course.title)
            .put("lessonContent", course.content)
            .put("performance", course.performance)
            .put("problems", course.problems)
            .put("homeworkSummary", course.homeworkSummary)
            .put("nextLessonPlan", course.nextLessonPlan)
            .put("teacherNotes", course.teacherNotes)
        enqueue("courseSession", course.id, payload)
    }

    private suspend fun enqueue(entityType: String, entityId: String, payload: JSONObject) {
        val state = syncDao.state(entityType, entityId)
        syncDao.enqueue(
            SyncOutboxEntity(
                operationId = UUID.randomUUID().toString(),
                entityType = entityType,
                entityId = entityId,
                operation = "UPSERT",
                baseVersion = state?.serverVersion ?: 0L,
                payload = payload.toString()
            )
        )
        syncDao.upsertState(
            com.leslie.teachtrack.core.model.SyncStateEntity(
                entityType = entityType,
                entityId = entityId,
                serverVersion = state?.serverVersion ?: 0L,
                lastSyncedAt = state?.lastSyncedAt ?: 0L,
                dirty = true
            )
        )
        SyncScheduler.schedule(context)
    }
}
