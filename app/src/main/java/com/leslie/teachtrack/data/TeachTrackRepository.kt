package com.leslie.teachtrack.data

import com.leslie.teachtrack.core.database.TeachTrackDao
import com.leslie.teachtrack.core.database.TeachTrackDatabase
import com.leslie.teachtrack.core.model.*
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import com.leslie.teachtrack.core.util.TemplateRenderer
import java.util.UUID
import com.leslie.teachtrack.data.workspace.WorkspaceSyncCoordinator

data class BackupPreview(
    val exportedAt: Long,
    val students: Int,
    val courses: Int,
    val scores: Int,
    val scoreItems: Int,
    val knowledgePoints: Int,
    val homework: Int,
    val communications: Int,
    val tasks: Int,
    val tags: Int = 0,
    val studentTags: Int = 0,
    val attachments: Int = 0,
    val total: Int
)

class TeachTrackRepository(private val dao: TeachTrackDao, private val sync: WorkspaceSyncCoordinator? = null, private val database: TeachTrackDatabase? = null) {
    fun daoForSeed(): TeachTrackDao = dao
    fun previewBackupJson(json: String): BackupPreview {
        val root = JSONObject(json)
        require(root.optString("format") == "TeachTrack Backup") { "不支持的备份格式" }
        fun size(key: String) = root.optJSONArray(key)?.length() ?: 0
        val counts = listOf("students", "courses", "scores", "scoreItems", "knowledgePoints", "homework", "communications", "tasks", "tags", "studentTags", "attachments").map(::size)
        return BackupPreview(root.optLong("exportedAt"), counts[0], counts[1], counts[2], counts[3], counts[4], counts[5], counts[6], counts[7], counts[8], counts[9], counts[10], counts.sum())
    }

    suspend fun exportScoresCsv(): String {
        val students = dao.allStudents().associateBy { it.id }; val exams = dao.allExams().associateBy { it.id }
        val rows = mutableListOf("学生,考试名称,日期,科目,得分,满分,得分率,班级排名,年级排名")
        dao.allScores().forEach { score -> val exam = exams[score.examId]; rows += listOf(students[score.studentId]?.name.orEmpty(), exam?.name.orEmpty(), exam?.date.orEmpty(), exam?.subject.orEmpty(), score.score.toString(), score.fullScore.toString(), "%.2f%%".format(score.scoreRate * 100), score.classRank?.toString().orEmpty(), score.gradeRank?.toString().orEmpty()).joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" } }
        return rows.joinToString("\n")
    }
    suspend fun importBackupJson(json: String): Int = database?.withTransaction { importBackupJsonInternal(json) } ?: importBackupJsonInternal(json)
    private suspend fun importBackupJsonInternal(json: String): Int {
        val root = JSONObject(json); var count = 0
        fun str(o: JSONObject, key: String) = o.optString(key).takeIf { it.isNotBlank() }
        root.optJSONArray("students")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.upsertStudent(StudentEntity(o.getString("id"), o.getString("name"), gender = str(o, "gender"), grade = o.optString("grade"), school = o.optString("school"), className = o.optString("className"), subject = o.optString("subject"), status = runCatching { StudentStatus.valueOf(o.optString("status", "ACTIVE")) }.getOrDefault(StudentStatus.ACTIVE), startDate = str(o, "startDate"), notes = o.optString("notes"), createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"), displayName = o.optString("displayName"), currentGrade = str(o, "currentGrade"), gradeAtRecord = str(o, "gradeAtRecord"), nameCompleteness = o.optString("nameCompleteness", "CONFIRMED"))); count++ } }
        root.optJSONArray("courses")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.upsertCourse(CourseSessionEntity(o.getString("id"), o.getString("studentId"), o.getString("date"), o.optString("startTime"), o.optString("endTime"), o.optString("subject"), o.optString("title"), o.optString("content"), o.optString("performance"), o.optString("problems"), o.optString("homeworkSummary"), o.optString("nextLessonPlan"), o.optString("teacherNotes"))); count++ } }
        root.optJSONArray("knowledgePoints")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.upsertKnowledge(KnowledgePointEntity(o.getString("id"), str(o, "parentId"), o.getString("name"), o.optString("subject"), o.optString("grade"), o.optInt("orderIndex"), o.optString("description"))); count++ } }
        root.optJSONArray("knowledgeProgress")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.upsertProgress(StudentKnowledgeProgressEntity(o.getString("studentId"), o.getString("knowledgePointId"), o.optInt("level"), o.optLong("lastUpdated"), o.optString("notes"))); count++ } }
        root.optJSONArray("knowledgeEvents")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.insertKnowledgeEvent(StudentKnowledgeEventEntity(o.getString("id"), o.getString("studentId"), o.getString("knowledgePointId"), o.optInt("level"), o.optLong("occurredAt"), o.optString("notes"))); count++ } }
        root.optJSONArray("lessonPlans")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.upsertLessonPlan(LessonPlanEntity(o.getString("id"), o.getString("studentId"), o.getString("plannedDate"), o.optString("chapter"), o.optString("knowledgePoints"), o.optString("focus"), o.optString("difficulty"), o.optString("review"), o.optString("newContent"), o.optString("homework"), o.optString("notes"))); count++ } }
        root.optJSONArray("exams")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.upsertExam(ExamEntity(o.getString("id"), o.getString("name"), o.getString("date"), o.optString("subject"), o.optString("type"), o.optString("notes"))); count++ } }
        root.optJSONArray("scores")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.upsertScore(StudentScoreEntity(o.getString("id"), o.getString("studentId"), o.getString("examId"), o.optDouble("score"), o.optDouble("fullScore"), o.optInt("classRank").takeIf { o.has("classRank") }, o.optInt("gradeRank").takeIf { o.has("gradeRank") }, notes = o.optString("notes"), scoreLabel = o.optString("scoreLabel"), fullScoreLabel = o.optString("fullScoreLabel"), scoreAccuracy = o.optString("scoreAccuracy", "EXACT"))); count++ } }
        root.optJSONArray("scoreItems")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.upsertScoreItem(ScoreItemEntity(o.getString("id"), o.getString("scoreId"), o.optString("category"), o.optDouble("lostScore"), str(o, "knowledgePointId"), o.optString("notes"))); count++ } }
        root.optJSONArray("homework")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.upsertHomework(HomeworkEntity(o.getString("id"), o.getString("studentId"), str(o, "courseId"), o.getString("name"), o.getString("assignedDate"), str(o, "dueDate"), o.optString("content"), o.optString("status", "未完成"), o.optString("teacherComment"))); count++ } }
        root.optJSONArray("communications")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.upsertCommunication(CommunicationEntity(o.getString("id"), o.getString("studentId"), o.getString("date"), o.optString("contact"), o.optString("method", "微信"), o.optString("summary"), o.optString("parentFeedback"), o.optString("followUp"))); count++ } }
        root.optJSONArray("tasks")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.upsertTask(TaskEntity(o.getString("id"), o.getString("title"), str(o, "studentId"), o.optString("type", "其他"), str(o, "dueDate"), o.optString("priority", "普通"), o.optString("status", "待处理"), o.optString("notes"))); count++ } }
        root.optJSONArray("parentContacts")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.upsertParentContact(ParentContactEntity(o.getString("id"), o.getString("studentId"), o.optString("name"), o.optString("phone"), o.optString("relation"), o.optBoolean("isPrimary"), o.optString("notes"))); count++ } }
        root.optJSONArray("problemRecords")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.upsertProblemRecord(ProblemRecordEntity(o.getString("id"), o.getString("studentId"), str(o, "knowledgePointId"), str(o, "courseId"), str(o, "examId"), o.getString("title"), o.optString("description"), o.getString("date"), o.optString("status", "待复习"), o.optString("notes"))); count++ } }
        root.optJSONArray("templates")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.upsertTemplate(TemplateEntity(o.getString("id"), o.getString("name"), o.optString("type"), o.optString("content"), o.optLong("createdAt"), o.optLong("updatedAt"))); count++ } }
        root.optJSONArray("tags")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.upsertTag(TagEntity(o.getString("id"), o.getString("name"), o.optString("color", "#176B57"))); count++ } }
        root.optJSONArray("studentTags")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.upsertStudentTag(StudentTagEntity(o.getString("studentId"), o.getString("tagId"))); count++ } }
        root.optJSONArray("attachments")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.upsertAttachment(AttachmentEntity(o.getString("id"), str(o, "studentId"), str(o, "courseId"), str(o, "homeworkId"), o.getString("name"), o.optString("mimeType"), o.getString("uri"), o.optLong("createdAt"), o.optString("relativePath"), o.optLong("size"), o.optString("sha256"), o.optLong("updatedAt"), o.optLong("revision", 1), o.optLong("deletedAt").takeIf { o.has("deletedAt") })); count++ } }
        root.optJSONArray("historicalLearningRecords")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.upsertHistoricalLearningRecord(HistoricalLearningRecordEntity(o.getString("id"), o.getString("studentId"), o.optString("recordDate"), o.optString("dateAccuracy", "UNKNOWN"), o.optString("subject"), o.getString("title"), o.optString("content"), o.optString("source", "历史教学资料"))); count++ } }
        root.optJSONArray("studentProgress")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.upsertStudentProgress(StudentProgressEntity(o.getString("id"), o.getString("studentId"), o.optString("date"), o.optString("dateAccuracy", "UNKNOWN"), o.getString("category"), o.getString("title"), o.optString("detail"), o.optString("source", "历史教学资料"))); count++ } }
        root.optJSONArray("unresolvedHistoricalRecords")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i); dao.upsertUnresolvedHistoricalRecord(UnresolvedHistoricalRecordEntity(o.getString("id"), o.optString("recordDate"), o.optString("dateAccuracy", "UNKNOWN"), o.getString("title"), o.optString("content"), o.optString("source", "历史教学资料"))); count++ } }
        return count
    }
    suspend fun exportBackupJson(): String {
        fun array(values: List<Any>, fields: (Any) -> JSONObject) = JSONArray().also { result -> values.forEach { result.put(fields(it)) } }
        val root = JSONObject().put("format", "TeachTrack Backup").put("version", 1).put("exportedAt", System.currentTimeMillis())
        root.put("students", array(dao.allStudents()) { val x = it as StudentEntity; JSONObject().put("id", x.id).put("name", x.name).put("displayName", x.displayName).put("gender", x.gender).put("grade", x.grade).put("currentGrade", x.currentGrade).put("gradeAtRecord", x.gradeAtRecord).put("nameCompleteness", x.nameCompleteness).put("school", x.school).put("className", x.className).put("subject", x.subject).put("status", x.status.name).put("startDate", x.startDate).put("notes", x.notes).put("createdAt", x.createdAt).put("updatedAt", x.updatedAt) })
        root.put("courses", array(dao.allCourses()) { val x = it as CourseSessionEntity; JSONObject().put("id", x.id).put("studentId", x.studentId).put("date", x.date).put("startTime", x.startTime).put("endTime", x.endTime).put("subject", x.subject).put("title", x.title).put("content", x.content).put("performance", x.performance).put("problems", x.problems).put("homeworkSummary", x.homeworkSummary).put("nextLessonPlan", x.nextLessonPlan).put("teacherNotes", x.teacherNotes) })
        root.put("knowledgePoints", array(dao.allKnowledge()) { val x = it as KnowledgePointEntity; JSONObject().put("id", x.id).put("parentId", x.parentId).put("name", x.name).put("subject", x.subject).put("grade", x.grade).put("orderIndex", x.orderIndex).put("description", x.description) })
        root.put("knowledgeProgress", array(dao.allProgress()) { val x = it as StudentKnowledgeProgressEntity; JSONObject().put("studentId", x.studentId).put("knowledgePointId", x.knowledgePointId).put("level", x.level).put("lastUpdated", x.lastUpdated).put("notes", x.notes) })
        root.put("knowledgeEvents", array(dao.allKnowledgeEvents()) { val x = it as StudentKnowledgeEventEntity; JSONObject().put("id", x.id).put("studentId", x.studentId).put("knowledgePointId", x.knowledgePointId).put("level", x.level).put("occurredAt", x.occurredAt).put("notes", x.notes) })
        root.put("exams", array(dao.allExams()) { val x = it as ExamEntity; JSONObject().put("id", x.id).put("name", x.name).put("date", x.date).put("subject", x.subject).put("type", x.type).put("notes", x.notes) })
        root.put("scores", array(dao.allScores()) { val x = it as StudentScoreEntity; JSONObject().put("id", x.id).put("studentId", x.studentId).put("examId", x.examId).put("score", x.score).put("fullScore", x.fullScore).put("scoreRate", x.scoreRate).put("classRank", x.classRank).put("gradeRank", x.gradeRank).put("notes", x.notes).put("scoreLabel", x.scoreLabel).put("fullScoreLabel", x.fullScoreLabel).put("scoreAccuracy", x.scoreAccuracy) })
        root.put("scoreItems", array(dao.allScoreItems()) { val x = it as ScoreItemEntity; JSONObject().put("id", x.id).put("scoreId", x.scoreId).put("category", x.category).put("lostScore", x.lostScore).put("knowledgePointId", x.knowledgePointId).put("notes", x.notes) })
        root.put("homework", array(dao.allHomework()) { val x = it as HomeworkEntity; JSONObject().put("id", x.id).put("studentId", x.studentId).put("courseId", x.courseId).put("name", x.name).put("assignedDate", x.assignedDate).put("dueDate", x.dueDate).put("content", x.content).put("status", x.status).put("teacherComment", x.teacherComment) })
        root.put("communications", array(dao.allCommunications()) { val x = it as CommunicationEntity; JSONObject().put("id", x.id).put("studentId", x.studentId).put("date", x.date).put("contact", x.contact).put("method", x.method).put("summary", x.summary).put("parentFeedback", x.parentFeedback).put("followUp", x.followUp) })
        root.put("lessonPlans", array(dao.allLessonPlans()) { val x = it as LessonPlanEntity; JSONObject().put("id", x.id).put("studentId", x.studentId).put("plannedDate", x.plannedDate).put("chapter", x.chapter).put("knowledgePoints", x.knowledgePoints).put("focus", x.focus).put("difficulty", x.difficulty).put("review", x.review).put("newContent", x.newContent).put("homework", x.homework).put("notes", x.notes) })
        root.put("tasks", array(dao.allTasks()) { val x = it as TaskEntity; JSONObject().put("id", x.id).put("title", x.title).put("studentId", x.studentId).put("type", x.type).put("dueDate", x.dueDate).put("priority", x.priority).put("status", x.status).put("notes", x.notes) })
        root.put("parentContacts", array(dao.allParentContacts()) { val x = it as ParentContactEntity; JSONObject().put("id", x.id).put("studentId", x.studentId).put("name", x.name).put("phone", x.phone).put("relation", x.relation).put("isPrimary", x.isPrimary).put("notes", x.notes) })
        root.put("problemRecords", array(dao.allProblemRecords()) { val x = it as ProblemRecordEntity; JSONObject().put("id", x.id).put("studentId", x.studentId).put("knowledgePointId", x.knowledgePointId).put("courseId", x.courseId).put("examId", x.examId).put("title", x.title).put("description", x.description).put("date", x.date).put("status", x.status).put("notes", x.notes) })
        root.put("templates", array(dao.allTemplates()) { val x = it as TemplateEntity; JSONObject().put("id", x.id).put("name", x.name).put("type", x.type).put("content", x.content).put("createdAt", x.createdAt).put("updatedAt", x.updatedAt) })
        root.put("tags", array(dao.allTags()) { val x = it as TagEntity; JSONObject().put("id", x.id).put("name", x.name).put("color", x.color) })
        root.put("studentTags", array(dao.allStudentTags()) { val x = it as StudentTagEntity; JSONObject().put("studentId", x.studentId).put("tagId", x.tagId) })
        root.put("attachments", array(dao.allAttachments()) { val x = it as AttachmentEntity; JSONObject().put("id", x.id).put("studentId", x.studentId).put("courseId", x.courseId).put("homeworkId", x.homeworkId).put("name", x.name).put("mimeType", x.mimeType).put("uri", x.uri).put("createdAt", x.createdAt).put("relativePath", x.relativePath).put("size", x.size).put("sha256", x.sha256).put("updatedAt", x.updatedAt).put("revision", x.revision).put("deletedAt", x.deletedAt) })
        root.put("historicalLearningRecords", array(dao.allHistoricalLearningRecords()) { val x = it as HistoricalLearningRecordEntity; JSONObject().put("id", x.id).put("studentId", x.studentId).put("recordDate", x.recordDate).put("dateAccuracy", x.dateAccuracy).put("subject", x.subject).put("title", x.title).put("content", x.content).put("source", x.source) })
        root.put("studentProgress", array(dao.allStudentProgress()) { val x = it as StudentProgressEntity; JSONObject().put("id", x.id).put("studentId", x.studentId).put("date", x.date).put("dateAccuracy", x.dateAccuracy).put("category", x.category).put("title", x.title).put("detail", x.detail).put("source", x.source) })
        root.put("unresolvedHistoricalRecords", array(dao.allUnresolvedHistoricalRecords()) { val x = it as UnresolvedHistoricalRecordEntity; JSONObject().put("id", x.id).put("recordDate", x.recordDate).put("dateAccuracy", x.dateAccuracy).put("title", x.title).put("content", x.content).put("source", x.source) })
        return root.toString(2)
    }
    val students: Flow<List<StudentEntity>> = dao.observeStudents()
    val activeStudentCount: Flow<Int> = dao.observeActiveCount()
    val courseCount: Flow<Int> = dao.observeCourseCount()
    val scoreCount: Flow<Int> = dao.observeScoreCount()
    val pendingTaskCount: Flow<Int> = dao.observePendingTaskCount()
    val tasks: Flow<List<TaskEntity>> = dao.observeTasks()
    val allCourses: Flow<List<CourseSessionEntity>> = dao.observeAllCourses()
    val allLessonPlans: Flow<List<LessonPlanEntity>> = dao.observeAllLessonPlans()
    fun student(id: String) = dao.observeStudent(id)
    suspend fun studentOnce(id: String) = dao.observeStudentOnce(id)
    fun courses(studentId: String) = dao.observeCourses(studentId)
    suspend fun coursesOnce(studentId: String) = dao.allCourses().filter { it.studentId == studentId }
    fun scores(studentId: String) = dao.observeScores(studentId)
    suspend fun scoresOnce(studentId: String) = dao.allScores().filter { it.studentId == studentId }
    val allScores: Flow<List<StudentScoreEntity>> = dao.observeAllScores()
    val allExams: Flow<List<ExamEntity>> = dao.observeAllExams()
    val allHomework: Flow<List<HomeworkEntity>> = dao.observeAllHomework()
    val allCommunications: Flow<List<CommunicationEntity>> = dao.observeAllCommunications()
    fun progress(studentId: String) = dao.observeProgress(studentId)
    fun knowledgeEvents(studentId: String) = dao.observeKnowledgeEvents(studentId)
    fun homework(studentId: String) = dao.observeHomework(studentId)
    fun communications(studentId: String) = dao.observeCommunications(studentId)
    fun problemRecords(studentId: String) = dao.observeProblemRecords(studentId)
    fun attachments(studentId: String) = dao.observeAttachments(studentId)
    fun knowledge() = dao.observeKnowledge()
    val templates: Flow<List<TemplateEntity>> = dao.observeTemplates()
    suspend fun saveStudent(name: String, grade: String, school: String, subject: String, notes: String = ""): String { val id = UUID.randomUUID().toString(); dao.upsertStudent(StudentEntity(id, name = name, grade = grade, school = school, subject = subject, notes = notes)); sync?.record("Student", id, "CREATE", JSONObject().put("name", name).put("grade", grade).put("school", school).put("subject", subject)); return id }
    suspend fun updateStudent(student: StudentEntity) { val updated = student.copy(updatedAt = System.currentTimeMillis()); dao.upsertStudent(updated); sync?.record("Student", updated.id, "UPDATE", JSONObject().put("name", updated.name).put("updatedAt", updated.updatedAt)) }
    suspend fun archiveStudent(id: String) { dao.observeStudentOnce(id)?.let { dao.upsertStudent(it.copy(status = StudentStatus.ARCHIVED, updatedAt = System.currentTimeMillis())); sync?.record("Student", id, "DELETE", JSONObject().put("deletedAt", System.currentTimeMillis())) } }
    suspend fun searchStudents(query: String) = dao.searchStudents(query)
    suspend fun allTags() = dao.allTags()
    suspend fun allAttachments() = dao.allAttachments()
    suspend fun saveCourse(course: CourseSessionEntity) { dao.upsertCourse(course); sync?.record("CourseSession", course.id, "CREATE", JSONObject().put("studentId", course.studentId).put("date", course.date).put("title", course.title)); if (course.nextLessonPlan.isNotBlank()) dao.upsertLessonPlan(LessonPlanEntity(UUID.randomUUID().toString(), course.studentId, course.date, chapter = course.subject, focus = course.nextLessonPlan, notes = "由课程记录自动生成")); if (course.problems.isNotBlank()) dao.upsertProblemRecord(ProblemRecordEntity(UUID.randomUUID().toString(), course.studentId, courseId = course.id, title = course.problems.take(80), description = course.problems, date = course.date)) }
    suspend fun saveScore(exam: ExamEntity, score: StudentScoreEntity, item: ScoreItemEntity? = null) { dao.upsertExam(exam); dao.upsertScore(score); sync?.record("StudentScore", score.id, "CREATE", JSONObject().put("studentId", score.studentId).put("examId", score.examId).put("score", score.score).put("fullScore", score.fullScore)); if (item != null) dao.upsertScoreItem(item) }
    suspend fun saveExam(exam: ExamEntity) = dao.upsertExam(exam)
    suspend fun saveTask(task: TaskEntity) { dao.upsertTask(task); sync?.record("Task", task.id, "CREATE", JSONObject().put("title", task.title).put("studentId", task.studentId)) }
    suspend fun updateTaskStatus(id: String, status: String) = dao.updateTaskStatus(id, status)
    suspend fun saveLessonPlan(plan: LessonPlanEntity) = dao.upsertLessonPlan(plan)
    suspend fun saveProblemRecord(record: ProblemRecordEntity) = dao.upsertProblemRecord(record)
    suspend fun saveKnowledge(point: KnowledgePointEntity) = dao.upsertKnowledge(point)
    suspend fun saveTag(tag: TagEntity) = dao.upsertTag(tag)
    suspend fun saveProgress(progress: StudentKnowledgeProgressEntity) { dao.upsertProgress(progress); dao.insertKnowledgeEvent(StudentKnowledgeEventEntity(UUID.randomUUID().toString(), progress.studentId, progress.knowledgePointId, progress.level, progress.lastUpdated, progress.notes)) }
    suspend fun saveHomework(homework: HomeworkEntity) { dao.upsertHomework(homework); sync?.record("Homework", homework.id, "CREATE", JSONObject().put("studentId", homework.studentId).put("name", homework.name)) }
    suspend fun saveCommunication(record: CommunicationEntity) { dao.upsertCommunication(record); sync?.record("CommunicationRecord", record.id, "CREATE", JSONObject().put("studentId", record.studentId).put("date", record.date)) }
    suspend fun saveParentContact(contact: ParentContactEntity) = dao.upsertParentContact(contact)
    suspend fun saveTemplate(template: TemplateEntity) = dao.upsertTemplate(template)
    suspend fun deleteTemplate(id: String) = dao.deleteTemplate(id)
    suspend fun saveAttachment(attachment: AttachmentEntity) { dao.upsertAttachment(attachment); sync?.record("Attachment", attachment.id, "CREATE", JSONObject().put("relativePath", attachment.uri).put("name", attachment.name)) }
    suspend fun deleteAttachment(id: String) { sync?.record("Attachment", id, "DELETE", JSONObject().put("deletedAt", System.currentTimeMillis())); dao.deleteAttachment(id) }
    suspend fun updateProblemStatus(id: String, status: String, notes: String = "") = dao.updateProblemStatus(id, status, notes)
    fun renderTemplate(template: TemplateEntity, values: Map<String, String>): String = TemplateRenderer.render(template.content, values)
    suspend fun latestCourse(studentId: String) = dao.latestCourse(studentId)
}
