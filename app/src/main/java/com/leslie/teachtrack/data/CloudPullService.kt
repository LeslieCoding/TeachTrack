package com.leslie.teachtrack.data

import android.content.Context
import com.leslie.teachtrack.core.database.TeachTrackDao
import com.leslie.teachtrack.core.model.CourseSessionEntity
import com.leslie.teachtrack.core.model.StudentEntity
import com.leslie.teachtrack.core.model.StudentStatus
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * One-shot pull of the server's student roster and course sessions into Room.
 * This is the bootstrap path for a new device; incremental sync still uses
 * sync_outbox/sync_changes.
 */
class CloudPullService(
    context: Context,
    private val baseUrl: String,
    private val token: String,
    private val dao: TeachTrackDao,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    data class Result(val students: Int, val sessions: Int, val failedStudents: Int)

    private val appContext = context.applicationContext

    suspend fun pullAll(): Result = withContext(Dispatchers.IO) {
        val students = fetchStudents()
        val idMap = mutableMapOf<String, String>()
        var sessions = 0
        var failed = 0
        for (student in students) {
            val localId = student.legacyId.ifBlank { student.id }
            idMap[student.id] = localId
            dao.upsertStudent(student.toEntity(localId))
        }
        for (student in students) {
            val localId = idMap[student.id] ?: student.legacyId.ifBlank { student.id }
            try {
                fetchSessions(student.id).forEach { session ->
                    dao.upsertCourse(session.toEntity(localId))
                    sessions++
                }
            } catch (exception: Exception) {
                failed++
            }
        }
        Result(students.size, sessions, failed)
    }

    private fun fetchStudents(): List<CloudStudent> {
        val root = getJsonObject("/api/v1/students?limit=200&offset=0")
        val items = root.optJSONArray("items") ?: return emptyList()
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                add(
                    CloudStudent(
                        id = item.optString("id"),
                        legacyId = item.optString("legacyId"),
                        fullName = item.optString("fullName"),
                        displayName = item.optString("displayName"),
                        status = item.optString("status", "ACTIVE"),
                        gender = item.optString("gender"),
                        currentGrade = item.optString("currentGrade"),
                        gradeAtRecord = item.optString("gradeAtRecord"),
                        nameCompleteness = item.optString("nameCompleteness", "CONFIRMED"),
                        school = item.optString("school"),
                        className = item.optString("className"),
                        notes = item.optString("notes")
                    )
                )
            }
        }
    }

    private fun fetchSessions(serverStudentId: String): List<CloudSession> {
        val encoded = java.net.URLEncoder.encode(serverStudentId, "UTF-8")
        val array = getJsonArray("/api/v1/students/$encoded/sessions?limit=500")
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    CloudSession(
                        id = item.optString("id"),
                        legacyId = item.optString("legacyId"),
                        date = item.optString("date"),
                        startTime = item.optString("startTime"),
                        endTime = item.optString("endTime"),
                        lessonTitle = item.optString("lessonTitle"),
                        lessonContent = item.optString("lessonContent"),
                        performance = item.optString("performance"),
                        problems = item.optString("problems"),
                        homeworkSummary = item.optString("homeworkSummary"),
                        nextLessonPlan = item.optString("nextLessonPlan"),
                        teacherNotes = item.optString("teacherNotes")
                    )
                )
            }
        }
    }

    private fun getJsonObject(path: String): JSONObject {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException(errorMessage(text, response.code))
            return JSONObject(text)
        }
    }

    private fun getJsonArray(path: String): org.json.JSONArray {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException(errorMessage(text, response.code))
            return org.json.JSONArray(text)
        }
    }

    private fun errorMessage(body: String, code: Int): String = runCatching {
        JSONObject(body).getJSONObject("error").optString("message")
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP $code"

    private data class CloudStudent(
        val id: String,
        val legacyId: String,
        val fullName: String,
        val displayName: String,
        val status: String,
        val gender: String,
        val currentGrade: String,
        val gradeAtRecord: String,
        val nameCompleteness: String,
        val school: String,
        val className: String,
        val notes: String
    ) {
        fun toEntity(localId: String) = StudentEntity(
            id = localId,
            name = fullName.ifBlank { displayName },
            gender = gender.takeIf { it.isNotBlank() },
            grade = gradeAtRecord,
            school = school,
            className = className,
            subject = "",
            status = runCatching { StudentStatus.valueOf(status) }.getOrDefault(StudentStatus.ACTIVE),
            notes = notes,
            updatedAt = System.currentTimeMillis(),
            displayName = displayName.ifBlank { fullName },
            currentGrade = currentGrade.takeIf { it.isNotBlank() },
            gradeAtRecord = gradeAtRecord.takeIf { it.isNotBlank() },
            nameCompleteness = nameCompleteness
        )
    }

    private data class CloudSession(
        val id: String,
        val legacyId: String,
        val date: String,
        val startTime: String,
        val endTime: String,
        val lessonTitle: String,
        val lessonContent: String,
        val performance: String,
        val problems: String,
        val homeworkSummary: String,
        val nextLessonPlan: String,
        val teacherNotes: String
    ) {
        fun toEntity(localStudentId: String) = CourseSessionEntity(
            id = legacyId.ifBlank { id },
            studentId = localStudentId,
            date = date,
            startTime = startTime,
            endTime = endTime,
            subject = "",
            title = lessonTitle,
            content = lessonContent,
            performance = performance,
            problems = problems,
            homeworkSummary = homeworkSummary,
            nextLessonPlan = nextLessonPlan,
            teacherNotes = teacherNotes
        )
    }
}
