package com.leslie.teachtrack.copilot

import com.leslie.teachtrack.core.model.*
import com.leslie.teachtrack.data.TeachTrackRepository
import com.leslie.teachtrack.data.workspace.ConflictResolutionStore
import com.leslie.teachtrack.data.workspace.SyncEventScanner
import com.leslie.teachtrack.data.workspace.WorkspaceExporter
import com.leslie.teachtrack.data.workspace.WorkspaceHealthChecker
import com.leslie.teachtrack.data.workspace.WorkspaceManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Local-only action adapter. Providers can plan commands, but never call Room directly.
 * [workspaceManager] and [backupDirectory] are optional: JVM unit tests exercise the
 * Room-only actions with both null; the Compose ViewModel supplies both real instances.
 */
class LocalCopilotActionRegistry(
    private val repository: TeachTrackRepository,
    private val workspaceManager: WorkspaceManager? = null,
    private val backupDirectory: File? = null
) : CopilotActionRegistry {
    private val entityResolver = StudentEntityResolver(repository)

    // student.update accepts a free-text status so the Agent can pass 在读/暂停/结束
    // directly; ARCHIVED is deliberately excluded — that state only goes through
    // student.archive, which also records the archive-specific sync/audit entry.
    private fun parseStudentStatus(input: String): StudentStatus? = when (input.trim()) {
        "在读", "ACTIVE", "active" -> StudentStatus.ACTIVE
        "暂停", "PAUSED", "paused" -> StudentStatus.PAUSED
        "结束", "ENDED", "ended" -> StudentStatus.ENDED
        else -> null
    }
    // The real Tasks UI only ever writes/reads these two exact Chinese strings
    // (`FeatureScreens.kt`'s AssistChip toggle: 已完成 <-> 待处理) — any other
    // literal value (e.g. "done") is invisible to the app's own "is this task
    // still open" filters, so task.status.update must normalize into this
    // vocabulary rather than store the caller's raw string.
    private fun parseTaskStatus(input: String): String? = when (input.trim()) {
        "已完成", "完成", "done", "DONE", "complete", "completed" -> "已完成"
        "待处理", "未完成", "pending", "todo", "open" -> "待处理"
        else -> null
    }
    // ── Shared per-record writers ─────────────────────────────────────────
    // Single and batch actions MUST enforce identical validation, so both go
    // through these helpers (P4/spec §41: batch = N × the single op, never a
    // parallel implementation). Each takes the already-resolved student id
    // and returns the same CopilotResult the single action would.
    private fun itemValue(p: Map<String, String>, key: String, fallback: String = "") =
        p[key].orEmpty().ifBlank { fallback }

    private suspend fun createCourseRecord(p: Map<String, String>, id: String, today: String): CopilotResult {
        val content = itemValue(p, "content", itemValue(p, "rawText"))
        if (content.isBlank()) return CopilotResult(false, "缺少课程内容")
        repository.saveCourse(CourseSessionEntity(UUID.randomUUID().toString(), id, itemValue(p, "date", today), subject = itemValue(p, "subject"), title = itemValue(p, "title", content.take(40)), content = content, problems = itemValue(p, "problems"), nextLessonPlan = itemValue(p, "nextLessonPlan"), teacherNotes = itemValue(p, "teacherNotes")))
        return CopilotResult(true, "已记录课程", effect = CopilotEffect.OpenStudent(id))
    }

    private suspend fun createScoreRecord(p: Map<String, String>, id: String, today: String): CopilotResult {
        val score = itemValue(p, "score").toDoubleOrNull() ?: return CopilotResult(false, "缺少有效成绩，例如 126"); val full = itemValue(p, "fullScore", "100").toDoubleOrNull() ?: return CopilotResult(false, "满分必须是数字"); val name = itemValue(p, "examName", "TeachTrack 成绩")
        val exam = repository.examByName(name) ?: ExamEntity(UUID.randomUUID().toString(), name, itemValue(p, "date", today), itemValue(p, "subject"))
        val scoreId = UUID.randomUUID().toString()
        val category = itemValue(p, "category"); val lostScore = itemValue(p, "lostScore").toDoubleOrNull()
        val item = if (category.isNotBlank() && lostScore != null) ScoreItemEntity(UUID.randomUUID().toString(), scoreId, category, lostScore) else null
        repository.saveScore(exam, StudentScoreEntity(scoreId, id, exam.id, score, full, itemValue(p, "classRank").toIntOrNull(), itemValue(p, "gradeRank").toIntOrNull(), notes = itemValue(p, "rawText")), item)
        return CopilotResult(true, "已记录成绩：$score/$full", effect = CopilotEffect.OpenStudent(id, startScore = true))
    }

    private suspend fun assignTagToStudent(id: String, tagName: String): CopilotResult {
        val tag = repository.tagByName(tagName) ?: TagEntity(UUID.randomUUID().toString(), tagName).also { repository.saveTag(it) }
        repository.assignTag(id, tag.id)
        return CopilotResult(true, "已为学生添加标签：$tagName", effect = CopilotEffect.OpenStudent(id))
    }

    /** [task.status.update]'s resolution rule: explicit taskId wins, else a unique substring match among open tasks. */
    private suspend fun resolveOpenTaskId(explicitId: String, query: String): String? {
        if (explicitId.isNotBlank()) return explicitId
        val open = repository.allTasksOnce().filter { it.status !in setOf("已完成", "完成", "DONE") }
        // The spoken sentence usually contains the task's exact title as a substring, not the other way round.
        return open.filter { query.isNotBlank() && (query.contains(it.title) || it.title.contains(query)) }.singleOrNull()?.id
    }

    /** Resolve a per-item student reference (studentId, else studentName via the shared resolver). */
    private data class ItemStudentResolution(val id: String?, val errorCode: String?, val message: String)

    private suspend fun resolveItemStudent(p: Map<String, String>): ItemStudentResolution {
        p["studentId"]?.takeIf { it.isNotBlank() }?.let { return ItemStudentResolution(it, null, "") }
        val query = p["studentName"]?.takeIf { it.isNotBlank() }
            ?: return ItemStudentResolution(null, ERROR_STUDENT_NOT_FOUND, "未能定位学生（studentId 或唯一 studentName 必填）")
        return when (val result = entityResolver.resolve(query)) {
            is EntityResolution.Resolved -> ItemStudentResolution(result.student.id, null, "")
            is EntityResolution.Ambiguous -> ItemStudentResolution(
                null, ERROR_AMBIGUOUS_STUDENT,
                "找到多个匹配学生：${result.candidates.joinToString("、") { it.displayName.ifBlank { it.name } }}",
            )
            EntityResolution.NotFound -> ItemStudentResolution(null, ERROR_STUDENT_NOT_FOUND, "未找到学生：$query")
        }
    }

    private fun jsonItemParams(item: org.json.JSONObject): Map<String, String> = buildMap {
        item.keys().forEach { key -> put(key, item.optString(key)) }
    }

    /** Strict per-item outcome array every batch action returns in `data["items"]` (spec §41). */
    private fun batchData(items: org.json.JSONArray, created: Int, failed: Int, extraIdKey: String? = null, ids: List<String> = emptyList()): Map<String, String> = buildMap {
        put("created", created.toString())
        put("failed", failed.toString())
        put("items", items.toString())
        if (extraIdKey != null) put(extraIdKey, ids.joinToString(","))
    }

    /** One per-item outcome entry; carries the stable error code when the item failed with one (spec §71). */
    private fun batchOutcome(index: Int, result: CopilotResult): org.json.JSONObject =
        org.json.JSONObject().put("index", index).put("ok", result.success).put("message", result.message)
            .also { entry -> result.errorCode?.let { entry.put("code", it) } }

    override val allowedActions = setOf(
        "student.create", "student.update", "student.search", "student.archive",
        "student.overview.query", "student.data.organize.preview", "student.followup.tasks.create", "knowledge.weakness.query",
        "course.create", "course.resume", "course.timeline", "course.query", "exam.create", "exam.query", "score.create", "score.query", "score.trend",
        "communication.create", "communication.query", "homework.create", "homework.query", "lesson_plan.create", "lesson_plan.query",
        "task.create", "task.status.update", "task.query", "batch.tasks.create",
        "batch.scores.create", "batch.courses.create", "batch.tags.assign", "batch.tasks.complete",
        "knowledge.create", "knowledge.update", "knowledge.query", "problem.create", "problem.status.update", "problem.query",
        "parent_contact.create", "parent_contact.query", "timeline.query", "search.global",
        "template.create", "template.list", "template.update", "template.delete", "template.render",
        "tag.create", "tag.assign", "tag.query", "tag.student.query", "attachment.list", "attachment.query", "attachment.delete",
        "backup.export", "backup.list", "backup.restore", "csv.export.scores",
        "workspace.status.query", "workspace.export", "workspace.check", "workspace.sync.scan",
        "workspace.conflict.list", "workspace.conflict.resolve",
        "dashboard.query", "analytics.query", "navigation.open", "ui.open.tab", "ui.open.student"
    )

    override suspend fun preview(command: CopilotCommand) = CopilotResult(true, "将执行 ${command.action}，请确认后写入本地数据")

    /**
     * Spec §55 undo. Only token kinds issued by this registry are reversible,
     * and only through existing repository operations (status revert / soft
     * archive) — no hard-delete path is added for undo. See [CopilotUndoTokens].
     */
    override suspend fun undo(token: String): CopilotResult {
        val json = CopilotUndoTokens.decode(token) ?: return CopilotResult(false, "撤销令牌无效或已过期")
        return when (json.optString("kind")) {
            CopilotUndoTokens.KIND_TASK_STATUS -> {
                val items = json.optJSONArray("items")
                    ?: return CopilotResult(false, "撤销令牌无效或已过期")
                var reverted = 0
                var failed = 0
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i)
                    if (item == null) { failed++; continue }
                    val id = item.optString("id")
                    val prev = item.optString("prev")
                    if (id.isBlank() || prev.isBlank()) { failed++; continue }
                    runCatching { repository.updateTaskStatus(id, prev) }
                        .onSuccess { reverted++ }
                        .onFailure { failed++ }
                }
                if (failed == 0) CopilotResult(true, "已撤销：$reverted 项待办状态已恢复")
                else CopilotResult(false, "部分撤销失败：恢复 $reverted 项，失败 $failed 项")
            }
            CopilotUndoTokens.KIND_STUDENT_ARCHIVE -> {
                val id = json.optString("id")
                if (id.isBlank()) return CopilotResult(false, "撤销令牌无效或已过期")
                runCatching { repository.archiveStudent(id) }.fold(
                    onSuccess = { CopilotResult(true, "已撤销：新建学生已归档") },
                    onFailure = { CopilotResult(false, "撤销失败：${it.message ?: "请重试"}") },
                )
            }
            else -> CopilotResult(false, "该操作不支持撤销")
        }
    }

    override suspend fun execute(command: CopilotCommand): CopilotResult {
        val p = command.parameters
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        var resolutionMessage = ""
        var resolutionCode: String? = null
        var resolutionCandidates = emptyList<String>()
        suspend fun studentId(): String? = p["selectedStudentId"]?.takeIf { it.isNotBlank() }
            ?: p["studentId"]?.takeIf { it.isNotBlank() }
            ?: p["studentName"]?.takeIf { it.isNotBlank() }?.let { query ->
                when (val result = entityResolver.resolve(query)) {
                    is EntityResolution.Resolved -> result.student.id
                    is EntityResolution.Ambiguous -> {
                        resolutionCode = ERROR_AMBIGUOUS_STUDENT
                        resolutionCandidates = result.candidates.map { it.displayName.ifBlank { it.name } }
                        resolutionMessage = "找到多个匹配学生：${resolutionCandidates.joinToString("、")}；请在学生工作台选择后重试"
                        null
                    }
                    EntityResolution.NotFound -> {
                        resolutionCode = ERROR_STUDENT_NOT_FOUND
                        resolutionMessage = "未找到学生：$query"
                        null
                    }
                }
            }
        fun v(key: String, fallback: String = "") = p[key].orEmpty().ifBlank { fallback }
        fun missing() = CopilotResult(
            false, resolutionMessage.ifBlank { "请提供唯一学生姓名，或先在学生工作台选择学生" },
            errorCode = resolutionCode,
            data = if (resolutionCandidates.isNotEmpty()) mapOf("candidates" to resolutionCandidates.joinToString(",")) else emptyMap(),
        )

        when (command.action) {
            "student.followup.tasks.create" -> {
                val data = repository.copilotOrganizationData()
                val report = StudentDataOrganizer.analyze(StudentDataOrganizationSnapshot(
                    data.students, data.courses, data.homework, data.tasks, data.problems, data.knowledge, data.progress
                ))
                val openTasks = data.tasks.filter { it.status !in setOf("已完成", "完成", "DONE") }
                var created = 0
                var skipped = 0
                report.findings.take(50).forEach { finding ->
                    val title = "AI 跟进：${finding.studentName}"
                    if (openTasks.any { it.studentId == finding.studentId && it.title == title }) {
                        skipped++
                    } else {
                        repository.saveTask(TaskEntity(
                            id = UUID.randomUUID().toString(), title = title, studentId = finding.studentId,
                            type = "教学跟进", priority = "普通", notes = finding.facts.joinToString("；")
                        ))
                        created++
                    }
                }
                return CopilotResult(true, "已按整理结果创建 $created 项学生跟进待办${if (skipped > 0) "，跳过 $skipped 项已有待办" else ""}", effect = CopilotEffect.OpenTab(2), data = mapOf("created" to created.toString(), "skipped" to skipped.toString()))
            }
            "student.data.organize.preview" -> {
                val data = repository.copilotOrganizationData()
                val report = StudentDataOrganizer.analyze(StudentDataOrganizationSnapshot(
                    data.students, data.courses, data.homework, data.tasks, data.problems, data.knowledge, data.progress
                ))
                return CopilotResult(
                    true,
                    report.toDisplayText(),
                    data = mapOf(
                        "studentsScanned" to report.studentsScanned.toString(),
                        "findingCount" to report.findingCount.toString(),
                        "report" to report.toDisplayText(50)
                    )
                )
            }
            "knowledge.weakness.query" -> {
                val query = v("knowledgeName", v("query", v("rawText"))).trim()
                if (query.isBlank()) return CopilotResult(false, "请说明要查询的知识点")
                val data = repository.copilotOrganizationData()
                val matches = StudentDataOrganizer.studentsWeakIn(StudentDataOrganizationSnapshot(
                    data.students, data.courses, data.homework, data.tasks, data.problems, data.knowledge, data.progress
                ), query)
                return CopilotResult(
                    true,
                    if (matches.isEmpty()) "本地档案中没有“$query”掌握较弱的学生记录" else "“$query”需要巩固的学生：${matches.joinToString("、") { it.displayName.ifBlank { it.name } }}",
                    data = mapOf("count" to matches.size.toString(), "studentIds" to matches.joinToString(",") { it.id })
                )
            }
            "student.create" -> {
                val name = v("studentName", v("name"))
                if (name.isBlank()) return CopilotResult(false, "缺少学生姓名")
                if (repository.searchStudents(name).any { it.name.equals(name, true) }) return CopilotResult(false, "学生已存在：$name")
                val id = repository.saveStudent(name, v("grade"), v("school"), v("subject"), v("notes"))
                return CopilotResult(true, "已添加学生：$name", undoToken = CopilotUndoTokens.studentArchive(id), effect = CopilotEffect.OpenStudent(id))
            }
            "student.update" -> {
                val id = studentId() ?: return missing(); val old = repository.studentOnce(id) ?: return CopilotResult(false, "未找到学生", errorCode = ERROR_STUDENT_NOT_FOUND)
                val statusInput = v("status")
                val newStatus = if (statusInput.isBlank()) old.status else parseStudentStatus(statusInput)
                    ?: return CopilotResult(false, "无法识别的学生状态：$statusInput，请使用 在读/暂停/结束")
                repository.updateStudent(old.copy(name = v("studentName", old.name), grade = v("grade", old.grade), school = v("school", old.school), subject = v("subject", old.subject), notes = v("notes", old.notes), status = newStatus))
                return CopilotResult(true, "已更新学生档案", effect = CopilotEffect.OpenStudent(id))
            }
            "student.search" -> {
                var matches = repository.searchStudents(v("query", v("studentName", v("rawText"))))
                val statusInput = v("status")
                if (statusInput.isNotBlank()) {
                    val filterStatus = parseStudentStatus(statusInput)
                        ?: return CopilotResult(false, "无法识别的学生状态：$statusInput，请使用 在读/暂停/结束")
                    matches = matches.filter { it.status == filterStatus }
                }
                val effect = matches.singleOrNull()?.let { CopilotEffect.OpenStudent(it.id) }
                return CopilotResult(true, if (matches.isEmpty()) "未找到匹配学生" else "找到 ${matches.size} 名学生：${matches.take(8).joinToString("、") { it.name }}", effect = effect, data = mapOf("count" to matches.size.toString(), "studentIds" to matches.take(20).joinToString(",") { it.id }))
            }
            "student.archive" -> { val id = studentId() ?: return missing(); repository.archiveStudent(id); return CopilotResult(true, "已归档学生，历史记录保留") }
            "student.overview.query" -> {
                val id = studentId() ?: return missing(); val student = repository.studentOnce(id) ?: return CopilotResult(false, "未找到学生", errorCode = ERROR_STUDENT_NOT_FOUND)
                val latest = repository.latestCourse(id); val scores = repository.scoresOnce(id)
                val pendingHomework = repository.homeworkOnce(id).count { it.status !in setOf("已完成", "完成", "DONE", "已检查") }
                val openProblems = repository.problemRecordsOnce(id).count { it.status !in setOf("已改善", "已解决") }
                val weakKnowledge = repository.progressOnce(id).count { it.level in 0..2 }
                val summary = buildString {
                    append(student.displayName.ifBlank { student.name })
                    append(" · ").append(listOf(student.grade, student.school, student.subject).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "档案待完善" })
                    append("；最近课程：").append(latest?.let { "${it.date} ${it.title.ifBlank { it.subject }}".trim() } ?: "暂无")
                    append("；最近成绩：").append(scores.take(3).joinToString("，") { "${it.score}/${it.fullScore}" }.ifBlank { "暂无" })
                    append("；待完成作业 ").append(pendingHomework).append(" 项")
                    append("；待复习问题 ").append(openProblems).append(" 条")
                    append("；需巩固知识点 ").append(weakKnowledge).append(" 个")
                }
                return CopilotResult(true, summary, effect = CopilotEffect.OpenStudent(id), data = mapOf("pendingHomework" to pendingHomework.toString(), "openProblems" to openProblems.toString(), "weakKnowledge" to weakKnowledge.toString()))
            }
            "course.create" -> {
                val id = studentId() ?: return missing()
                return createCourseRecord(p, id, today)
            }
            "course.resume" -> { val id = studentId() ?: return missing(); val c = repository.latestCourse(id); return CopilotResult(true, c?.nextLessonPlan?.takeIf { it.isNotBlank() }?.let { "已带入上节课计划：$it" } ?: "该学生暂无上节课计划", effect = CopilotEffect.OpenStudent(id, startCourse = true)) }
            "course.timeline" -> { val id = studentId() ?: return missing(); return CopilotResult(true, "已打开学生时间线，共 ${repository.coursesOnce(id).size} 条课程记录", effect = CopilotEffect.OpenStudent(id)) }
            "exam.create" -> { val name = v("examName", "TeachTrack 考试"); repository.saveExam(ExamEntity(UUID.randomUUID().toString(), name, v("date", today), v("subject"), v("type", "自定义"), v("notes"))); return CopilotResult(true, "已创建考试：$name") }
            "score.create" -> {
                val id = studentId() ?: return missing()
                return createScoreRecord(p, id, today)
            }
            "score.query" -> { val id = studentId() ?: return missing(); val rows = repository.scoresOnce(id); return CopilotResult(true, if (rows.isEmpty()) "该学生暂无成绩" else "最近成绩：${rows.take(8).joinToString("，") { "${it.score}/${it.fullScore}" }}", effect = CopilotEffect.OpenStudent(id), data = mapOf("count" to rows.size.toString())) }
            "homework.create" -> { val id = studentId() ?: return missing(); val content = v("content", v("rawText")); if (content.isBlank()) return CopilotResult(false, "缺少作业内容"); repository.saveHomework(HomeworkEntity(UUID.randomUUID().toString(), id, name = v("title", content.take(40)), assignedDate = today, dueDate = p["dueDate"], content = content)); return CopilotResult(true, "已布置作业", effect = CopilotEffect.OpenStudent(id)) }
            "communication.create" -> { val id = studentId() ?: return missing(); val summary = v("summary", v("rawText")); if (summary.isBlank()) return CopilotResult(false, "缺少沟通内容"); repository.saveCommunication(CommunicationEntity(UUID.randomUUID().toString(), id, today, contact = v("contact"), summary = summary, followUp = v("followUp"))); return CopilotResult(true, "已记录家校沟通", effect = CopilotEffect.OpenStudent(id)) }
            "parent_contact.create" -> {
                val id = studentId() ?: return missing(); val name = v("contactName", v("name"))
                repository.saveParentContact(ParentContactEntity(UUID.randomUUID().toString(), id, name = name, phone = v("phone"), relation = v("relation"), isPrimary = v("isPrimary").equals("true", true), notes = v("notes")))
                return CopilotResult(true, "已记录家长联系人${if (name.isNotBlank()) "：$name" else ""}", effect = CopilotEffect.OpenStudent(id))
            }
            "parent_contact.query" -> {
                val id = studentId() ?: return missing(); val contacts = repository.parentContactsOnce(id)
                return CopilotResult(true, if (contacts.isEmpty()) "该学生暂无家长联系人记录" else "家长联系人：${contacts.joinToString("、") { "${it.name.ifBlank { "未命名" }}(${it.relation.ifBlank { "关系未填" }})" }}", data = mapOf("count" to contacts.size.toString()))
            }
            "lesson_plan.create" -> { val id = studentId() ?: return missing(); repository.saveLessonPlan(LessonPlanEntity(UUID.randomUUID().toString(), id, v("date", today), focus = v("focus", v("rawText")), homework = v("homework"), notes = v("notes"))); return CopilotResult(true, "已设置下一节课计划", effect = CopilotEffect.OpenStudent(id)) }
            "task.create" -> { val title = v("title", v("rawText", "TeachTrack 待办")); val taskId = UUID.randomUUID().toString(); repository.saveTask(TaskEntity(taskId, title, studentId(), dueDate = p["dueDate"], priority = v("priority", "普通"))); return CopilotResult(true, "已添加待办：$title", effect = CopilotEffect.OpenTab(2), data = mapOf("taskId" to taskId)) }
            "task.status.update" -> {
                val statusInput = v("status", "已完成")
                val status = parseTaskStatus(statusInput)
                    ?: return CopilotResult(false, "无法识别的待办状态：$statusInput，请使用 已完成/待处理")
                val resolvedId = resolveOpenTaskId(v("taskId"), v("title", v("rawText")).trim()).orEmpty()
                if (resolvedId.isBlank()) return CopilotResult(false, "未能定位到唯一待办，请提供更具体的标题，或先在待办页选择后重试")
                val previousStatus = repository.allTasksOnce().firstOrNull { it.id == resolvedId }?.status
                repository.updateTaskStatus(resolvedId, status)
                return CopilotResult(
                    true, "已将待办状态更新为：$status",
                    undoToken = previousStatus?.takeIf { it != status }?.let { CopilotUndoTokens.taskStatus(mapOf(resolvedId to it)) },
                    effect = CopilotEffect.OpenTab(2),
                )
            }
            "knowledge.create" -> {
                val name = v("knowledgeName", v("name")); if (name.isBlank()) return CopilotResult(false, "缺少知识点名称")
                if (repository.knowledgeByName(name) != null) return CopilotResult(false, "知识点已存在：$name")
                val point = KnowledgePointEntity(UUID.randomUUID().toString(), name = name, subject = v("subject"), grade = v("grade"), description = v("description"))
                repository.saveKnowledge(point)
                return CopilotResult(true, "已创建知识点：$name", data = mapOf("knowledgePointId" to point.id))
            }
            "knowledge.update" -> {
                val id = studentId() ?: return missing()
                val pointId = v("knowledgePointId")
                val point = (if (pointId.isNotBlank()) repository.knowledgeById(pointId) else null)
                    ?: repository.knowledgeByName(v("knowledgeName")).takeIf { v("knowledgeName").isNotBlank() }
                    ?: return CopilotResult(false, "未找到该知识点，请先用「新建知识点」创建")
                repository.saveProgress(StudentKnowledgeProgressEntity(id, point.id, v("level", "2").toIntOrNull() ?: 2, notes = v("notes", v("rawText"))))
                return CopilotResult(true, "已更新知识点：${point.name}", effect = CopilotEffect.OpenStudent(id))
            }
            "knowledge.query" -> {
                val id = studentId() ?: return CopilotResult(true, "本地知识点共 ${repository.knowledgeCount()} 个")
                val progress = repository.progressOnce(id)
                if (progress.isEmpty()) return CopilotResult(true, "该学生暂无知识点记录", effect = CopilotEffect.OpenStudent(id), data = mapOf("count" to "0"))
                val names = repository.allKnowledge().associate { it.id to it.name }
                val rows = progress.sortedByDescending { it.lastUpdated }.take(20).joinToString("；") { p ->
                    val name = names[p.knowledgePointId] ?: p.knowledgePointId
                    "$name ${p.level}/5" + if (p.notes.isNotBlank()) "（${p.notes}）" else ""
                }
                return CopilotResult(true, "该学生知识点记录：$rows", effect = CopilotEffect.OpenStudent(id), data = mapOf("count" to progress.size.toString()))
            }
            "problem.create" -> { val id = studentId() ?: return missing(); val title = v("title", v("rawText", "待复习问题")); val problemId = UUID.randomUUID().toString(); repository.saveProblemRecord(ProblemRecordEntity(problemId, id, title = title, description = v("description", v("rawText")), date = today)); return CopilotResult(true, "已记录问题", effect = CopilotEffect.OpenStudent(id), data = mapOf("problemId" to problemId)) }
            "problem.status.update" -> { val problemId = v("problemId"); if (problemId.isBlank()) return CopilotResult(false, "缺少问题记录 ID"); repository.updateProblemStatus(problemId, v("status", "待复习"), v("notes")); return CopilotResult(true, "已更新问题状态") }
            "template.create" -> {
                val name = v("templateName", v("name")); if (name.isBlank()) return CopilotResult(false, "缺少模板名称")
                repository.saveTemplate(TemplateEntity(UUID.randomUUID().toString(), name, type = v("type", v("templateType", "课后反馈")), content = v("content", v("rawText")))); return CopilotResult(true, "已保存模板")
            }
            "template.list" -> { val templates = repository.allTemplatesOnce(); return CopilotResult(true, if (templates.isEmpty()) "暂无课后反馈模板" else "共 ${templates.size} 个模板：${templates.take(10).joinToString("、") { it.name }}", data = mapOf("count" to templates.size.toString())) }
            "template.update" -> {
                val name = v("templateName", v("name")); if (name.isBlank()) return CopilotResult(false, "缺少模板名称")
                val existing = repository.templateByName(name) ?: return CopilotResult(false, "未找到模板：$name")
                repository.saveTemplate(existing.copy(content = v("content", existing.content), updatedAt = System.currentTimeMillis()))
                return CopilotResult(true, "已更新模板：$name")
            }
            "template.delete" -> {
                val name = v("templateName", v("name")); if (name.isBlank()) return CopilotResult(false, "缺少模板名称")
                val existing = repository.templateByName(name) ?: return CopilotResult(false, "未找到模板：$name")
                repository.deleteTemplate(existing.id)
                return CopilotResult(true, "已删除模板：$name")
            }
            "template.render" -> {
                val name = v("templateName", v("name")); if (name.isBlank()) return CopilotResult(false, "缺少模板名称")
                val existing = repository.templateByName(name) ?: return CopilotResult(false, "未找到模板：$name")
                val rendered = repository.renderTemplate(existing, mapOf("student_name" to v("studentName"), "date" to v("date", today), "course_content" to v("content"), "performance" to v("performance"), "next_plan" to v("nextPlan")))
                return CopilotResult(true, rendered, data = mapOf("rendered" to rendered))
            }
            "tag.create" -> { val name = v("tagName", "TeachTrack 标签"); repository.saveTag(TagEntity(UUID.randomUUID().toString(), name, v("color", "#16866A"))); return CopilotResult(true, "已创建标签：$name") }
            "tag.assign" -> {
                val id = studentId() ?: return missing(); val tagName = v("tagName", v("name")); if (tagName.isBlank()) return CopilotResult(false, "缺少标签名称")
                return assignTagToStudent(id, tagName)
            }
            "tag.query" -> { val tags = repository.allTags(); return CopilotResult(true, if (tags.isEmpty()) "暂无标签" else "共 ${tags.size} 个标签：${tags.joinToString("、") { it.name }}", data = mapOf("count" to tags.size.toString())) }
            "attachment.list" -> { val count = repository.allAttachments().size; return CopilotResult(true, "本地附件共 $count 个", data = mapOf("count" to count.toString())) }
            "attachment.query" -> {
                val id = studentId() ?: return missing(); val files = repository.attachmentsOnce(id)
                return CopilotResult(true, if (files.isEmpty()) "该学生暂无附件" else "共 ${files.size} 个附件：${files.take(10).joinToString("、") { it.name }}", data = mapOf("count" to files.size.toString()))
            }
            "attachment.delete" -> {
                val attachmentId = v("attachmentId"); if (attachmentId.isBlank()) return CopilotResult(false, "缺少附件 ID")
                repository.deleteAttachment(attachmentId)
                return CopilotResult(true, "已删除附件")
            }
            "dashboard.query" -> return CopilotResult(true, "首页：在读学生 ${repository.activeStudentCountOnce()} 人，待办 ${repository.pendingTaskCountOnce()} 项", effect = CopilotEffect.OpenTab(0))
            "analytics.query" -> return CopilotResult(true, "统计：课程 ${repository.courseCountOnce()} 条，考试 ${repository.examCountOnce()} 场，成绩 ${repository.scoreCountOnce()} 条", effect = CopilotEffect.OpenTab(3))
            "backup.export" -> {
                val dir = backupDirectory ?: return CopilotResult(false, "当前环境不支持本地备份导出")
                dir.mkdirs()
                val json = repository.exportBackupJson(); val preview = repository.previewBackupJson(json)
                val fileName = "TeachTrack-AI-Backup-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())}.json"
                File(dir, fileName).writeText(json)
                return CopilotResult(true, "已在本机创建备份：$fileName（学生 ${preview.students}，课程 ${preview.courses}，成绩 ${preview.scores}，共 ${preview.total} 条核心记录）", data = mapOf("fileName" to fileName, "total" to preview.total.toString()))
            }
            "backup.list" -> {
                val files = backupDirectory?.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.sortedByDescending { it.lastModified() }.orEmpty()
                return CopilotResult(true, if (files.isEmpty()) "本机暂无 AI 创建的备份文件" else "共 ${files.size} 个本地备份，最新：${files.first().name}", data = mapOf("count" to files.size.toString(), "latest" to files.firstOrNull()?.name.orEmpty()))
            }
            "backup.restore" -> {
                val dir = backupDirectory ?: return CopilotResult(false, "当前环境不支持本地备份恢复")
                val requested = v("fileName")
                val target = (if (requested.isNotBlank()) File(dir, requested).takeIf { it.exists() } else dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.maxByOrNull { it.lastModified() })
                    ?: return CopilotResult(false, "未找到可恢复的本地备份文件，请先创建一次备份")
                val count = repository.importBackupJson(target.readText())
                return CopilotResult(true, "已从 ${target.name} 恢复，按 ID 合并写入 $count 条记录，不会删除现有数据")
            }
            "csv.export.scores" -> {
                val dir = backupDirectory ?: return CopilotResult(false, "当前环境不支持本地导出")
                dir.mkdirs()
                val fileName = "TeachTrack-Scores-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())}.csv"
                File(dir, fileName).writeText(repository.exportScoresCsv())
                return CopilotResult(true, "已导出成绩 CSV：$fileName", data = mapOf("fileName" to fileName))
            }
            "workspace.status.query" -> {
                val manager = workspaceManager ?: return CopilotResult(false, "当前环境不支持 Workspace 操作")
                val storage = manager.storage() ?: return CopilotResult(true, "尚未连接任何 Workspace，请先在设置页创建或打开工作区", effect = CopilotEffect.OpenTab(4))
                return runCatching { manager.readManifest(storage) }.fold(
                    onSuccess = { m -> CopilotResult(true, "已连接工作区：${m.name}（${m.workspaceId.take(8)}），Schema v${m.schemaVersion}") },
                    onFailure = { CopilotResult(false, "工作区状态读取失败：${it.message ?: "请检查目录"}") }
                )
            }
            "workspace.export" -> {
                val manager = workspaceManager ?: return CopilotResult(false, "当前环境不支持 Workspace 操作")
                val storage = manager.storage() ?: return CopilotResult(false, "请先在设置页连接工作区", effect = CopilotEffect.OpenTab(4))
                return runCatching { WorkspaceExporter(repository, manager).export(storage) }.fold(
                    onSuccess = { r -> CopilotResult(true, "已导出 ${r.records} 条记录到工作区", data = mapOf("records" to r.records.toString())) },
                    onFailure = { CopilotResult(false, "工作区导出失败：${it.message ?: "请重试"}") }
                )
            }
            "workspace.check" -> {
                val manager = workspaceManager ?: return CopilotResult(false, "当前环境不支持 Workspace 操作")
                val storage = manager.storage() ?: return CopilotResult(false, "请先在设置页连接工作区", effect = CopilotEffect.OpenTab(4))
                return runCatching { WorkspaceHealthChecker(manager).check(storage) }.fold(
                    onSuccess = { r -> CopilotResult(true, "工作区检查：${r.checkedFiles} 个文件、${r.records} 条记录、${r.issues.size} 个问题", data = mapOf("issues" to r.issues.size.toString())) },
                    onFailure = { CopilotResult(false, "工作区检查失败：${it.message ?: "请重试"}") }
                )
            }
            "workspace.sync.scan" -> {
                val manager = workspaceManager ?: return CopilotResult(false, "当前环境不支持 Workspace 操作")
                val storage = manager.storage() ?: return CopilotResult(false, "请先在设置页连接工作区", effect = CopilotEffect.OpenTab(4))
                return runCatching { SyncEventScanner(manager).scan(storage) }.fold(
                    onSuccess = { r -> CopilotResult(true, "同步扫描：${r.events} 个事件、${r.conflicts} 个冲突、${r.issues} 个损坏事件", data = mapOf("conflicts" to r.conflicts.toString())) },
                    onFailure = { CopilotResult(false, "同步扫描失败：${it.message ?: "请重试"}") }
                )
            }
            "workspace.conflict.list" -> {
                val manager = workspaceManager ?: return CopilotResult(false, "当前环境不支持 Workspace 操作")
                val storage = manager.storage() ?: return CopilotResult(false, "请先在设置页连接工作区", effect = CopilotEffect.OpenTab(4))
                val store = ConflictResolutionStore(manager); val files = store.files(storage)
                return CopilotResult(true, if (files.isEmpty()) "当前没有未处理的同步冲突" else "共 ${files.size} 个冲突：${files.take(5).joinToString("；") { store.summary(storage, it) }}", data = mapOf("count" to files.size.toString(), "files" to files.joinToString(",")))
            }
            "workspace.conflict.resolve" -> {
                val manager = workspaceManager ?: return CopilotResult(false, "当前环境不支持 Workspace 操作")
                val storage = manager.storage() ?: return CopilotResult(false, "请先在设置页连接工作区", effect = CopilotEffect.OpenTab(4))
                val file = v("conflictFile"); val choice = v("choice", "MANUAL_MERGE")
                if (file.isBlank()) return CopilotResult(false, "缺少冲突文件标识，请先查看冲突列表")
                if (choice !in ConflictResolutionStore.allowedChoices) return CopilotResult(false, "处理方式必须是 VERSION_A、VERSION_B 或 MANUAL_MERGE")
                ConflictResolutionStore(manager).recordResolution(storage, file, choice)
                return CopilotResult(true, "冲突处理选择已记录：$choice")
            }
            // ── Read/query gap-closers (P2 coverage audit) ────────────────
            // Every branch below reads through the SAME repository accessors
            // the Compose UI screens use; no parallel query logic lives here
            // beyond list shaping (filter/take/join) for the text envelope.
            "course.query" -> {
                val id = studentId()
                if (id != null) {
                    val rows = repository.coursesOnce(id)
                    return CopilotResult(true, if (rows.isEmpty()) "该学生暂无课程记录" else "最近课程：${rows.take(10).joinToString("；") { "${it.date} ${it.title.ifBlank { it.subject }}" }}", effect = CopilotEffect.OpenStudent(id), data = mapOf("count" to rows.size.toString()))
                }
                val names = repository.searchStudents("").associate { it.id to it.name }
                val rows = repository.allCoursesOnce().sortedWith(compareByDescending<CourseSessionEntity> { it.date }.thenByDescending { it.startTime }).take(10)
                return CopilotResult(true, if (rows.isEmpty()) "暂无课程记录" else "最近课程：${rows.joinToString("；") { "${it.date} ${names[it.studentId] ?: "学生"} ${it.title.ifBlank { it.subject }}" }}", data = mapOf("count" to rows.size.toString()))
            }
            "exam.query" -> {
                val rows = repository.allExamsOnce().sortedByDescending { it.date }.take(15)
                return CopilotResult(true, if (rows.isEmpty()) "暂无考试记录" else "最近考试：${rows.joinToString("；") { "${it.date} ${it.name}${if (it.subject.isNotBlank()) "（${it.subject}）" else ""}" }}", data = mapOf("count" to rows.size.toString(), "examIds" to rows.joinToString(",") { it.id }))
            }
            "score.trend" -> {
                val id = studentId() ?: return missing()
                val rows = repository.scoresOnce(id)
                if (rows.isEmpty()) return CopilotResult(true, "该学生暂无成绩记录", effect = CopilotEffect.OpenStudent(id), data = mapOf("count" to "0"))
                // scoresOnce is newest-first; chronological order reads better in a trend line.
                val chronological = rows.take(10).reversed()
                val trendLine = chronological.joinToString(" → ") { "${(it.scoreRate * 100).toInt()}%" }
                val recentAvg = chronological.takeLast(3).map { it.scoreRate }.average()
                val previousAvg = chronological.dropLast(3).takeLast(3).map { it.scoreRate }.average()
                val direction = when {
                    chronological.size < 4 -> "样本不足"
                    recentAvg > previousAvg + 0.02 -> "上升"
                    recentAvg < previousAvg - 0.02 -> "下降"
                    else -> "平稳"
                }
                return CopilotResult(true, "最近 ${chronological.size} 次得分率：$trendLine；趋势：$direction", effect = CopilotEffect.OpenStudent(id), data = mapOf("count" to rows.size.toString(), "direction" to direction, "recentAvgRate" to "%.3f".format(recentAvg)))
            }
            "homework.query" -> {
                val id = studentId()
                val rows = if (id != null) repository.homeworkOnce(id) else repository.allHomeworkOnce()
                val onlyPending = v("status").isBlank() || v("status") in setOf("未完成", "待处理", "pending")
                val filtered = if (onlyPending) rows.filter { it.status !in setOf("已完成", "完成", "DONE", "已检查") } else rows
                return CopilotResult(true, if (filtered.isEmpty()) "没有匹配的作业记录" else "作业：${filtered.take(10).joinToString("；") { "${it.name}（${it.status}${it.dueDate?.let { d -> "，截止 $d" } ?: ""}）" }}", effect = id?.let { CopilotEffect.OpenStudent(it) }, data = mapOf("count" to filtered.size.toString()))
            }
            "communication.query" -> {
                val id = studentId() ?: return missing()
                val rows = repository.communicationsOnce(id)
                return CopilotResult(true, if (rows.isEmpty()) "该学生暂无家校沟通记录" else "最近沟通：${rows.take(8).joinToString("；") { "${it.date} ${it.summary.take(40)}" }}", effect = CopilotEffect.OpenStudent(id), data = mapOf("count" to rows.size.toString()))
            }
            "problem.query" -> {
                val id = studentId()
                val rows = if (id != null) repository.problemRecordsOnce(id) else repository.copilotOrganizationData().problems
                val onlyOpen = v("status").isBlank()
                val filtered = if (onlyOpen) rows.filter { it.status !in setOf("已改善", "已解决") } else rows.filter { it.status == v("status") }
                return CopilotResult(true, if (filtered.isEmpty()) "没有匹配的问题记录" else "问题记录：${filtered.take(10).joinToString("；") { "${it.title}（${it.status}）" }}", effect = id?.let { CopilotEffect.OpenStudent(it) }, data = mapOf("count" to filtered.size.toString(), "problemIds" to filtered.take(20).joinToString(",") { it.id }))
            }
            "task.query" -> {
                val id = studentId()
                val onlyPending = v("status").isBlank() || v("status") in setOf("待处理", "未完成", "pending")
                val rows = repository.allTasksOnce()
                    .filter { id == null || it.studentId == id }
                    .filter { !onlyPending || it.status !in setOf("已完成", "完成", "DONE") }
                return CopilotResult(true, if (rows.isEmpty()) "没有匹配的待办" else "待办：${rows.take(10).joinToString("；") { "${it.title}（${it.status}${it.dueDate?.let { d -> "，截止 $d" } ?: ""}）" }}", effect = CopilotEffect.OpenTab(2), data = mapOf("count" to rows.size.toString(), "taskIds" to rows.take(20).joinToString(",") { it.id }))
            }
            "lesson_plan.query" -> {
                val id = studentId()
                val rows = repository.allLessonPlansOnce().filter { id == null || it.studentId == id }.take(10)
                return CopilotResult(true, if (rows.isEmpty()) "暂无下一节课计划" else "下一节课计划：${rows.joinToString("；") { "${it.plannedDate} ${it.focus.ifBlank { it.chapter }.take(40)}" }}", effect = id?.let { CopilotEffect.OpenStudent(it) } ?: CopilotEffect.OpenTab(2), data = mapOf("count" to rows.size.toString()))
            }
            "timeline.query" -> {
                // Mirrors StudentTimeline in MainActivity.kt: same five event
                // sources, same sort, so the Agent reads what the UI shows.
                val id = studentId() ?: return missing()
                data class Event(val date: String, val type: String, val detail: String)
                val events = (repository.coursesOnce(id).map { Event(it.date, "课程", it.title.ifBlank { it.subject }.ifBlank { "课程记录" }) } +
                    repository.scoresOnce(id).map { Event("", "成绩", "得分 ${it.score}/${it.fullScore} · 得分率 ${(it.scoreRate * 100).toInt()}%") } +
                    repository.homeworkOnce(id).map { Event(it.assignedDate, "作业", "${it.name} · ${it.status}") } +
                    repository.communicationsOnce(id).map { Event(it.date, "沟通", it.summary.ifBlank { "家校沟通记录" }) } +
                    repository.problemRecordsOnce(id).map { Event(it.date, "问题", it.title.ifBlank { it.description }) }).sortedByDescending { it.date }
                return CopilotResult(true, if (events.isEmpty()) "该学生暂无历史事件" else "时间线：${events.take(15).joinToString("；") { "${it.date.ifBlank { "未标日期" }} ${it.type} ${it.detail.take(40)}" }}", effect = CopilotEffect.OpenStudent(id), data = mapOf("count" to events.size.toString()))
            }
            "search.global" -> {
                val query = v("query", v("rawText")).trim()
                if (query.isBlank()) return CopilotResult(false, "缺少搜索关键词")
                val students = repository.searchStudents(query)
                val knowledge = repository.allKnowledge().filter { it.name.contains(query, true) }
                val templates = repository.allTemplatesOnce().filter { it.name.contains(query, true) }
                val tags = repository.allTags().filter { it.name.contains(query, true) }
                val parts = buildList {
                    if (students.isNotEmpty()) add("学生 ${students.size} 名：${students.take(5).joinToString("、") { it.name }}")
                    if (knowledge.isNotEmpty()) add("知识点 ${knowledge.size} 个：${knowledge.take(5).joinToString("、") { it.name }}")
                    if (templates.isNotEmpty()) add("模板 ${templates.size} 个：${templates.take(5).joinToString("、") { it.name }}")
                    if (tags.isNotEmpty()) add("标签 ${tags.size} 个：${tags.take(5).joinToString("、") { it.name }}")
                }
                val effect = students.singleOrNull()?.let { CopilotEffect.OpenStudent(it.id) }
                return CopilotResult(true, if (parts.isEmpty()) "没有找到与“$query”相关的内容" else parts.joinToString("；"), effect = effect, data = mapOf("students" to students.size.toString(), "knowledge" to knowledge.size.toString(), "templates" to templates.size.toString(), "tags" to tags.size.toString(), "studentIds" to students.take(20).joinToString(",") { it.id }))
            }
            "tag.student.query" -> {
                val id = studentId() ?: return missing()
                val names = repository.allTags().associate { it.id to it.name }
                val tags = repository.studentTagsOnce(id).mapNotNull { names[it.tagId] }
                return CopilotResult(true, if (tags.isEmpty()) "该学生暂无标签" else "该学生标签：${tags.joinToString("、")}", effect = CopilotEffect.OpenStudent(id), data = mapOf("count" to tags.size.toString()))
            }
            "ui.open.student" -> {
                val id = studentId() ?: return missing()
                val student = repository.studentOnce(id) ?: return CopilotResult(false, "未找到学生", errorCode = ERROR_STUDENT_NOT_FOUND)
                return CopilotResult(true, "已打开学生工作台：${student.displayName.ifBlank { student.name }}", effect = CopilotEffect.OpenStudent(id), data = mapOf("studentId" to id))
            }
            "batch.tasks.create" -> {
                val raw = v("items")
                if (raw.isBlank()) return CopilotResult(false, "缺少 items 参数（JSON 数组，例如 [{\"title\":\"…\",\"studentName\":\"…\"}]）")
                val array = runCatching { org.json.JSONArray(raw) }.getOrNull()
                    ?: return CopilotResult(false, "items 必须是 JSON 数组")
                var created = 0; var failed = 0
                val taskIds = mutableListOf<String>()
                for (i in 0 until array.length().coerceAtMost(50)) {
                    val item = array.optJSONObject(i)
                    val title = item?.optString("title")?.trim().orEmpty()
                    if (item == null || title.isBlank()) { failed++; continue }
                    val itemStudentId = item.optString("studentName").takeIf { it.isNotBlank() }?.let { query ->
                        when (val result = entityResolver.resolve(query)) {
                            is EntityResolution.Resolved -> result.student.id
                            else -> null
                        }
                    }
                    val taskId = UUID.randomUUID().toString()
                    repository.saveTask(TaskEntity(taskId, title, itemStudentId, dueDate = item.optString("dueDate").takeIf { it.isNotBlank() }, priority = item.optString("priority", "普通"), notes = item.optString("notes")))
                    taskIds += taskId; created++
                }
                return CopilotResult(true, "已批量创建 $created 项待办${if (failed > 0) "，跳过 $failed 项无效条目" else ""}", effect = CopilotEffect.OpenTab(2), data = mapOf("created" to created.toString(), "failed" to failed.toString(), "taskIds" to taskIds.joinToString(",")))
            }
            "batch.scores.create" -> {
                // P4/spec §41: N × score.create. Identical validation because
                // every item goes through createScoreRecord; a bad item never
                // aborts the rest (per-item failure isolation).
                val raw = v("items")
                if (raw.isBlank()) return CopilotResult(false, "缺少 items 参数（JSON 数组，例如 [{\"studentName\":\"…\",\"score\":\"126\",\"fullScore\":\"160\",\"examName\":\"周测\"}]）")
                val array = runCatching { org.json.JSONArray(raw) }.getOrNull()
                    ?: return CopilotResult(false, "items 必须是 JSON 数组")
                val outcomes = org.json.JSONArray(); var created = 0; var failed = 0
                for (i in 0 until array.length().coerceAtMost(50)) {
                    val item = array.optJSONObject(i)
                    val ip = item?.let { jsonItemParams(it) }.orEmpty()
                    val resolution = resolveItemStudent(ip)
                    val result = when {
                        item == null -> CopilotResult(false, "条目不是 JSON 对象")
                        resolution.id == null -> CopilotResult(false, resolution.message, errorCode = resolution.errorCode)
                        else -> createScoreRecord(ip, resolution.id, today)
                    }
                    if (result.success) created++ else failed++
                    outcomes.put(batchOutcome(i, result))
                }
                return CopilotResult(true, "已批量录入 $created 条成绩${if (failed > 0) "，失败 $failed 条" else ""}", data = batchData(outcomes, created, failed))
            }
            "batch.courses.create" -> {
                // P4/spec §41: N × course.create via createCourseRecord.
                val raw = v("items")
                if (raw.isBlank()) return CopilotResult(false, "缺少 items 参数（JSON 数组，例如 [{\"studentName\":\"…\",\"content\":\"…\",\"subject\":\"物理\"}]）")
                val array = runCatching { org.json.JSONArray(raw) }.getOrNull()
                    ?: return CopilotResult(false, "items 必须是 JSON 数组")
                val outcomes = org.json.JSONArray(); var created = 0; var failed = 0
                for (i in 0 until array.length().coerceAtMost(50)) {
                    val item = array.optJSONObject(i)
                    val ip = item?.let { jsonItemParams(it) }.orEmpty()
                    val resolution = resolveItemStudent(ip)
                    val result = when {
                        item == null -> CopilotResult(false, "条目不是 JSON 对象")
                        resolution.id == null -> CopilotResult(false, resolution.message, errorCode = resolution.errorCode)
                        else -> createCourseRecord(ip, resolution.id, today)
                    }
                    if (result.success) created++ else failed++
                    outcomes.put(batchOutcome(i, result))
                }
                return CopilotResult(true, "已批量记录 $created 条课程${if (failed > 0) "，失败 $failed 条" else ""}", data = batchData(outcomes, created, failed))
            }
            "batch.tags.assign" -> {
                // P4/spec §41: N × tag.assign via assignTagToStudent — e.g.
                // "把这几个学生都标记为重点关注".
                val raw = v("items")
                if (raw.isBlank()) return CopilotResult(false, "缺少 items 参数（JSON 数组，例如 [{\"studentName\":\"…\",\"tagName\":\"重点关注\"}]）")
                val array = runCatching { org.json.JSONArray(raw) }.getOrNull()
                    ?: return CopilotResult(false, "items 必须是 JSON 数组")
                val outcomes = org.json.JSONArray(); var created = 0; var failed = 0
                for (i in 0 until array.length().coerceAtMost(50)) {
                    val item = array.optJSONObject(i)
                    val ip = item?.let { jsonItemParams(it) }.orEmpty()
                    val resolution = resolveItemStudent(ip)
                    val tagName = ip["tagName"].orEmpty().ifBlank { ip["name"].orEmpty() }
                    val result = when {
                        item == null -> CopilotResult(false, "条目不是 JSON 对象")
                        resolution.id == null -> CopilotResult(false, resolution.message, errorCode = resolution.errorCode)
                        tagName.isBlank() -> CopilotResult(false, "缺少标签名称")
                        else -> assignTagToStudent(resolution.id, tagName)
                    }
                    if (result.success) created++ else failed++
                    outcomes.put(batchOutcome(i, result))
                }
                return CopilotResult(true, "已为 $created 名学生添加标签${if (failed > 0) "，失败 $failed 项" else ""}", data = batchData(outcomes, created, failed))
            }
            "batch.tasks.complete" -> {
                // P4/spec §41: N × task.status.update(已完成) via
                // resolveOpenTaskId — same taskId-wins / unique-title-match
                // resolution as the single action.
                val raw = v("items")
                if (raw.isBlank()) return CopilotResult(false, "缺少 items 参数（JSON 数组，例如 [{\"taskId\":\"…\"}] 或 [{\"title\":\"…\"}]）")
                val array = runCatching { org.json.JSONArray(raw) }.getOrNull()
                    ?: return CopilotResult(false, "items 必须是 JSON 数组")
                val outcomes = org.json.JSONArray(); var created = 0; var failed = 0
                val previousStatuses = mutableMapOf<String, String>()
                for (i in 0 until array.length().coerceAtMost(50)) {
                    val item = array.optJSONObject(i)
                    val ip = item?.let { jsonItemParams(it) }.orEmpty()
                    val resolvedId = resolveOpenTaskId(ip["taskId"].orEmpty(), ip["title"].orEmpty().trim())
                    val result = when {
                        item == null -> CopilotResult(false, "条目不是 JSON 对象")
                        resolvedId == null -> CopilotResult(false, "未能定位到唯一待办")
                        else -> {
                            repository.allTasksOnce().firstOrNull { it.id == resolvedId }?.status
                                ?.takeIf { it != "已完成" }
                                ?.let { previousStatuses[resolvedId] = it }
                            repository.updateTaskStatus(resolvedId, "已完成")
                            CopilotResult(true, "已将待办状态更新为：已完成")
                        }
                    }
                    if (result.success) created++ else failed++
                    outcomes.put(batchOutcome(i, result))
                }
                return CopilotResult(
                    true, "已批量完成 $created 项待办${if (failed > 0) "，失败 $failed 项" else ""}",
                    undoToken = previousStatuses.takeIf { it.isNotEmpty() }?.let { CopilotUndoTokens.taskStatus(it) },
                    effect = CopilotEffect.OpenTab(2), data = batchData(outcomes, created, failed),
                )
            }
            "navigation.open", "ui.open.tab" -> { val d = v("destination", "首页").lowercase(); val index = when { d.contains("student") || d.contains("学生") -> 1; d.contains("plan") || d.contains("计划") || d.contains("课程") -> 2; d.contains("analytic") || d.contains("统计") || d.contains("分析") -> 3; d.contains("setting") || d.contains("设置") || d.contains("数据") -> 4; d.contains("agent") -> 5; else -> 0 }; return CopilotResult(true, "已打开模块：$d", effect = CopilotEffect.OpenTab(index), data = mapOf("tab" to index.toString())) }
        }
        return CopilotResult(false, "暂不支持动作：${command.action}")
    }
}
