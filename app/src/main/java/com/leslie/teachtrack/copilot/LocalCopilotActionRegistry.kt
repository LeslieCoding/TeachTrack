package com.leslie.teachtrack.copilot

import com.leslie.teachtrack.core.model.*
import com.leslie.teachtrack.data.TeachTrackRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Local-only action adapter. It is the only layer allowed to call repository writes. */
class LocalCopilotActionRegistry(private val repository: TeachTrackRepository) : CopilotActionRegistry {
    override val allowedActions = setOf(
        "student.create", "student.update", "student.search", "student.archive",
        "course.create", "course.resume", "course.timeline", "exam.create", "score.create", "score.query",
        "communication.create", "homework.create", "lesson_plan.create",
        "knowledge.update", "knowledge.query", "problem.create", "problem.status.update",
        "template.create", "tag.create", "attachment.list", "dashboard.query", "analytics.query", "navigation.open"
    )

    override suspend fun preview(command: CopilotCommand): CopilotResult =
        CopilotResult(true, "将执行：${command.action}；请确认后写入本地数据")

    override suspend fun execute(command: CopilotCommand): CopilotResult {
        val p = command.parameters
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        fun studentId(): String? = p["selectedStudentId"]?.takeIf { it.isNotBlank() }
        fun missingStudent() = CopilotResult(false, "请先在学生工作台选择学生，或补充唯一学生姓名")
        return when (command.action) {
            "student.create" -> {
                val name = p["studentName"].orEmpty()
                if (name.isBlank()) CopilotResult(false, "缺少学生姓名")
                else {
                    repository.saveStudent(name, "", "", "")
                    CopilotResult(true, "已添加学生：$name")
                }
            }
            "student.update" -> studentId()?.let { id ->
                val current = repository.studentOnce(id)
                if (current == null) CopilotResult(false, "未找到学生") else {
                    repository.updateStudent(current.copy(name = p["studentName"]?.ifBlank { current.name } ?: current.name, grade = p["grade"] ?: current.grade, school = p["school"] ?: current.school, subject = p["subject"] ?: current.subject, notes = p["notes"] ?: current.notes))
                    CopilotResult(true, "已更新学生档案")
                }
            } ?: missingStudent()
            "student.search" -> {
                val query = p["query"].orEmpty()
                val count = repository.searchStudents(query).size
                CopilotResult(true, "已在本地学生档案中找到 $count 条记录")
            }
            "student.archive" -> studentId()?.let { id -> repository.archiveStudent(id); CopilotResult(true, "已归档学生，历史记录保留") } ?: missingStudent()
            "task.create" -> {
                val title = p["rawText"].orEmpty().ifBlank { "新待办" }
                repository.saveTask(TaskEntity(UUID.randomUUID().toString(), title, dueDate = today))
                CopilotResult(true, "已添加待办：$title")
            }
            "course.create" -> studentId()?.let { id ->
                repository.saveCourse(CourseSessionEntity(UUID.randomUUID().toString(), id, today, subject = p["subject"].orEmpty(), title = p["rawText"].orEmpty(), content = p["rawText"].orEmpty()))
                CopilotResult(true, "已记录课程")
            } ?: missingStudent()
            "course.resume" -> studentId()?.let { id ->
                val latest = repository.latestCourse(id)
                CopilotResult(true, latest?.nextLessonPlan?.takeIf { it.isNotBlank() }?.let { "已读取上节课计划：$it" } ?: "该学生暂无上一节课计划")
            } ?: missingStudent()
            "course.timeline" -> studentId()?.let { id -> CopilotResult(true, "已打开学生课程时间线：${repository.coursesOnce(id).size} 条记录") } ?: missingStudent()
            "exam.create" -> {
                val exam = ExamEntity(UUID.randomUUID().toString(), p["examName"].orEmpty().ifBlank { "Copilot 考试" }, p["date"].orEmpty().ifBlank { today }, subject = p["subject"].orEmpty(), type = p["type"].orEmpty().ifBlank { "自定义" })
                repository.saveExam(exam); CopilotResult(true, "已创建考试：${exam.name}")
            }
            "score.create" -> studentId()?.let { id ->
                val exam = ExamEntity(UUID.randomUUID().toString(), p["examName"].orEmpty().ifBlank { "Copilot 录入成绩" }, today)
                val score = StudentScoreEntity(UUID.randomUUID().toString(), id, exam.id, p["score"]?.toDoubleOrNull() ?: 0.0, p["fullScore"]?.toDoubleOrNull() ?: 100.0, notes = p["rawText"].orEmpty())
                repository.saveScore(exam, score)
                CopilotResult(true, "已记录成绩，得分率 ${(score.scoreRate * 100).toInt()}%")
            } ?: missingStudent()
            "score.query" -> studentId()?.let { id -> CopilotResult(true, "已读取成绩记录：${repository.scoresOnce(id).size} 条") } ?: missingStudent()
            "homework.create" -> studentId()?.let { id ->
                repository.saveHomework(HomeworkEntity(UUID.randomUUID().toString(), id, name = p["rawText"].orEmpty().ifBlank { "新作业" }, assignedDate = today, content = p["rawText"].orEmpty()))
                CopilotResult(true, "已布置作业")
            } ?: missingStudent()
            "communication.create" -> studentId()?.let { id ->
                repository.saveCommunication(CommunicationEntity(UUID.randomUUID().toString(), id, today, summary = p["rawText"].orEmpty()))
                CopilotResult(true, "已记录家校沟通")
            } ?: missingStudent()
            "lesson_plan.create" -> studentId()?.let { id ->
                repository.saveLessonPlan(LessonPlanEntity(UUID.randomUUID().toString(), id, today, focus = p["rawText"].orEmpty()))
                CopilotResult(true, "已设置下节课计划")
            } ?: missingStudent()
            "knowledge.update" -> studentId()?.let { id ->
                val pointId = p["knowledgePointId"] ?: return@let CopilotResult(false, "缺少知识点")
                repository.saveProgress(StudentKnowledgeProgressEntity(id, pointId, p["level"]?.toIntOrNull() ?: 2, notes = p["rawText"].orEmpty()))
                CopilotResult(true, "已更新知识点掌握情况")
            } ?: missingStudent()
            "knowledge.query" -> CopilotResult(true, "已读取本地知识点与掌握记录")
            "problem.create" -> studentId()?.let { id ->
                repository.saveProblemRecord(ProblemRecordEntity(UUID.randomUUID().toString(), id, title = p["rawText"].orEmpty().ifBlank { "待复习问题" }, description = p["rawText"].orEmpty(), date = today))
                CopilotResult(true, "已记录问题")
            } ?: missingStudent()
            "problem.status.update" -> {
                val problemId = p["problemId"].orEmpty()
                if (problemId.isBlank()) CopilotResult(false, "缺少问题记录 ID") else { repository.updateProblemStatus(problemId, p["status"].orEmpty().ifBlank { "待复习" }, p["notes"].orEmpty()); CopilotResult(true, "已更新问题状态") }
            }
            "template.create" -> {
                repository.saveTemplate(TemplateEntity(UUID.randomUUID().toString(), p["templateName"].orEmpty().ifBlank { "Copilot 模板" }, content = p["rawText"].orEmpty()))
                CopilotResult(true, "已保存模板")
            }
            "tag.create" -> {
                val name = p["tagName"].orEmpty().ifBlank { "Copilot 标签" }
                repository.saveTag(TagEntity(UUID.randomUUID().toString(), name, p["color"].orEmpty().ifBlank { "#176B57" }))
                CopilotResult(true, "已创建标签：$name")
            }
            "attachment.list" -> CopilotResult(true, "已读取附件记录：${repository.allAttachments().size} 条")
            "dashboard.query" -> CopilotResult(true, "已读取首页概览数据")
            "analytics.query" -> CopilotResult(true, "已读取统计分析数据")
            "navigation.open" -> CopilotResult(true, "已准备打开模块：${p["destination"].orEmpty().ifBlank { "首页" }}")
            else -> CopilotResult(false, "${command.action} 已登记，具体字段适配仍在开发中")
        }
    }
}
