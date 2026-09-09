package com.leslie.teachtrack.data

import com.leslie.teachtrack.core.database.TeachTrackDao
import com.leslie.teachtrack.core.model.*
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class SeedImportResult(
    val students: Int,
    val courses: Int,
    val exams: Int,
    val scores: Int,
    val knowledgePoints: Int,
    val progress: Int,
    val progressEvents: Int,
    val communications: Int,
    val historicalRecords: Int,
    val unresolvedRecords: Int,
    val problems: Int
) {
    val total: Int get() = students + courses + exams + scores + knowledgePoints + progress + progressEvents + communications + historicalRecords + unresolvedRecords + problems
}

/** Imports owner-provided historical teaching data. IDs are deterministic, so reruns are idempotent. */
class HistoricalSeedImporter(private val dao: TeachTrackDao) {
    suspend fun import(json: String): SeedImportResult {
        val root = JSONObject(json)
        require(root.optString("format") == "TeachTrack Historical Seed") { "不支持的历史 Seed 格式" }
        val existing = dao.allStudents()
        val studentIds = linkedMapOf<String, String>()
        var students = 0
        root.optJSONArray("students").forEachObject { item ->
            val seedId = item.getString("id")
            val fullName = item.optString("fullName").takeIf { it.isNotBlank() }
            val displayName = item.optString("displayName").ifBlank { fullName.orEmpty() }
            val aliases = item.stringArray("aliases") + listOfNotNull(fullName, displayName)
            val existingStudent = existing.firstOrNull { candidate ->
                val candidateNames = listOf(candidate.name, candidate.displayName).filter { it.isNotBlank() }
                candidateNames.any { it in aliases } && (candidate.subject.isBlank() || candidate.subject == item.optString("subject"))
            }
            val id = existingStudent?.id ?: seedId
            studentIds[seedId] = id
            val historicalGrade = item.optString("historicalGrade")
            val notes = buildNotes(item)
            val status = runCatching { StudentStatus.valueOf(item.optString("status", "UNKNOWN")) }.getOrDefault(StudentStatus.UNKNOWN)
            dao.upsertStudent(StudentEntity(id = id, name = fullName ?: displayName, displayName = displayName, gender = item.optString("gender").takeIf { it.isNotBlank() }, grade = historicalGrade, currentGrade = null, gradeAtRecord = historicalGrade.takeIf { it.isNotBlank() }, school = item.optString("school"), subject = item.optString("subject"), status = status, notes = notes, nameCompleteness = item.optString("nameCompleteness", if (fullName == null) "PARTIAL" else "CONFIRMED")))
            students++
        }

        val knowledgeIds = linkedMapOf<String, String>()
        var knowledgePoints = 0
        var progress = 0
        var progressEvents = 0
        var courses = 0
        var exams = 0
        var scores = 0
        var communications = 0
        var historicalRecords = 0
        var problems = 0
        root.optJSONArray("students").forEachObject { item ->
            val seedStudentId = item.getString("id")
            val studentId = studentIds.getValue(seedStudentId)
            val subject = item.optString("subject")
            val weaknessNames = item.stringArray("weaknesses")
            item.stringArray("knowledge").forEachIndexed { index, raw ->
                val pieces = raw.split("/", limit = 2)
                val group = pieces.firstOrNull().orEmpty()
                val name = pieces.getOrNull(1) ?: pieces.first()
                val key = "$subject|$group|$name"
                val pointId = knowledgeIds.getOrPut(key) {
                    val id = "seed-kp-${UUID.nameUUIDFromBytes(key.toByteArray(StandardCharsets.UTF_8))}"
                    val parentId = if (group.isBlank()) null else "seed-group-${UUID.nameUUIDFromBytes("$subject|$group".toByteArray(StandardCharsets.UTF_8))}"
                    if (parentId != null) dao.upsertKnowledge(KnowledgePointEntity(parentId, name = group, subject = subject, grade = item.optString("historicalGrade"), description = "历史 Seed 自动建立的知识分类"))
                    dao.upsertKnowledge(KnowledgePointEntity(id, parentId = parentId, name = name, subject = subject, grade = item.optString("historicalGrade"), orderIndex = index))
                    knowledgePoints++
                    id
                }
                val level = if (weaknessNames.any { raw.contains(it) || it.contains(name) }) MasteryLevel.REVIEW.value else MasteryLevel.LEARNED.value
                dao.upsertProgress(StudentKnowledgeProgressEntity(studentId, pointId, level, notes = "来自历史 Seed；不是 AI 推断")); progress++
                val eventId = "seed-kpe-${UUID.nameUUIDFromBytes("$seedStudentId|$key".toByteArray(StandardCharsets.UTF_8))}"
                dao.insertKnowledgeEvent(StudentKnowledgeEventEntity(eventId, studentId, pointId, level, occurredAt = 0L, notes = "历史记录日期未明确")); progressEvents++
            }
            item.optJSONArray("courses").forEachObject { course ->
                val courseId = "seed-course-${seedStudentId}-${course.optInt("index", courses)}"
                val date = course.optString("recordDate")
                dao.upsertCourse(CourseSessionEntity(courseId, studentId, date, subject = subject, title = course.getString("title"), content = course.optString("content"), performance = course.optString("performance"), problems = course.optString("problems"), nextLessonPlan = course.optString("nextLessonPlan"), teacherNotes = "历史教学资料；日期精度：${course.optString("dateAccuracy", "UNKNOWN")}")); courses++
                dao.upsertHistoricalLearningRecord(HistoricalLearningRecordEntity("seed-hlr-$courseId", studentId, date, course.optString("dateAccuracy", "UNKNOWN"), subject, course.getString("title"), course.optString("content"), "历史教学资料")); historicalRecords++
            }
            item.optJSONArray("scores").forEachObject { scoreItem ->
                val examId = "seed-exam-${seedStudentId}-${scoreItem.optInt("index", exams)}"
                val scoreId = "seed-score-${seedStudentId}-${scoreItem.optInt("index", scores)}"
                val scoreValue = scoreItem.optDouble("score", 0.0)
                val fullScoreValue = scoreItem.optDouble("fullScore", 0.0)
                dao.upsertExam(ExamEntity(examId, scoreItem.optString("type", "历史成绩"), scoreItem.optString("exactDate"), subject, notes = scoreItem.optString("note")))
                dao.upsertScore(StudentScoreEntity(scoreId, studentId, examId, scoreValue, fullScoreValue, notes = scoreItem.optString("note"), scoreLabel = scoreItem.optString("scoreText"), fullScoreLabel = scoreItem.optString("fullScoreText"), scoreAccuracy = scoreItem.optString("scoreAccuracy", "UNKNOWN"))); exams++; scores++
            }
            item.optJSONArray("communications").forEachObject { communication ->
                dao.upsertCommunication(CommunicationEntity("seed-communication-${seedStudentId}-${communication.optInt("index", communications)}", studentId, communication.optString("date"), contact = communication.optString("contact"), method = communication.optString("method", "其他"), summary = communication.optString("summary"), parentFeedback = communication.optString("parentFeedback"), followUp = communication.optString("followUp"))); communications++
            }
            item.stringArray("weaknesses").forEachIndexed { index, weakness ->
                dao.upsertProblemRecord(ProblemRecordEntity("seed-problem-${seedStudentId}-$index", studentId, title = weakness, description = weakness, date = "", notes = "历史资料中的薄弱点，未推断日期")); problems++
            }
            item.optJSONArray("progress").forEachObject { record ->
                dao.upsertStudentProgress(StudentProgressEntity("seed-progress-${seedStudentId}-${record.optInt("index", 0)}", studentId, record.optString("date"), record.optString("dateAccuracy", "UNKNOWN"), record.optString("category"), record.getString("title"), record.optString("detail"), "历史教学资料"))
            }
        }
        var unresolvedRecords = 0
        root.optJSONArray("unresolvedHistoricalRecords").forEachObject { item ->
            dao.upsertUnresolvedHistoricalRecord(UnresolvedHistoricalRecordEntity(item.getString("id"), item.optString("recordDate"), item.optString("dateAccuracy", "UNKNOWN"), item.getString("title"), item.optString("content"), "匿名历史教学资料")); unresolvedRecords++
        }
        return SeedImportResult(students, courses, exams, scores, knowledgePoints, progress, progressEvents, communications, historicalRecords, unresolvedRecords, problems)
    }

    private fun buildNotes(item: JSONObject): String = buildList {
        item.stringArray("strengths").takeIf { it.isNotEmpty() }?.let { add("优势：${it.joinToString("；")}") }
        item.stringArray("weaknesses").takeIf { it.isNotEmpty() }?.let { add("薄弱点：${it.joinToString("；")}") }
        item.stringArray("focus").takeIf { it.isNotEmpty() }?.let { add("教学重点：${it.joinToString("；")}") }
        item.optString("notes").takeIf { it.isNotBlank() }?.let { add(it) }
        add("历史年级仅表示记录发生时年级；当前年级未知时保持为空。")
    }.joinToString("\n")
}

private suspend fun JSONArray?.forEachObject(block: suspend (JSONObject) -> Unit) { if (this == null) return; for (index in 0 until length()) block(getJSONObject(index)) }
private fun JSONObject.stringArray(key: String): List<String> = buildList {
    val array = optJSONArray(key) ?: return@buildList
    for (i in 0 until array.length()) {
        val value = array.opt(i)
        if (value is JSONObject) add(value.optString("value")) else if (value != null && value != JSONObject.NULL) add(value.toString())
    }
}
