package com.leslie.teachtrack.copilot

import com.leslie.teachtrack.core.model.CourseSessionEntity
import com.leslie.teachtrack.core.model.HomeworkEntity
import com.leslie.teachtrack.core.model.KnowledgePointEntity
import com.leslie.teachtrack.core.model.ProblemRecordEntity
import com.leslie.teachtrack.core.model.StudentEntity
import com.leslie.teachtrack.core.model.StudentKnowledgeProgressEntity
import com.leslie.teachtrack.core.model.TaskEntity

/**
 * A local, explainable diagnostic for broad requests such as "整理所有学生数据".
 * Missing personal fields are never guessed and this report performs no write.
 */
data class StudentDataOrganizationSnapshot(
    val students: List<StudentEntity>, val courses: List<CourseSessionEntity>,
    val homework: List<HomeworkEntity>, val tasks: List<TaskEntity>,
    val problems: List<ProblemRecordEntity>, val knowledge: List<KnowledgePointEntity>,
    val progress: List<StudentKnowledgeProgressEntity>
)

data class StudentDataFinding(val studentId: String, val studentName: String, val facts: List<String>)

data class StudentDataOrganizationReport(val studentsScanned: Int, val findings: List<StudentDataFinding>) {
    val findingCount: Int get() = findings.sumOf { it.facts.size }
    fun toDisplayText(maxStudents: Int = 12): String = buildString {
        append("已基于本机档案检查 ").append(studentsScanned).append(" 名学生，发现 ").append(findingCount).append(" 项需要跟进的信息。")
        findings.take(maxStudents).forEach { finding -> append('\n').append("• ").append(finding.studentName).append("：").append(finding.facts.joinToString("；")) }
        if (findings.size > maxStudents) append("\n另有 ").append(findings.size - maxStudents).append(" 名学生的结果已收起。")
        append("\n未自动补全任何未知资料，也没有改写历史记录。")
    }
}

object StudentDataOrganizer {
    private val completeStatuses = setOf("已完成", "完成", "DONE")

    fun analyze(snapshot: StudentDataOrganizationSnapshot): StudentDataOrganizationReport {
        val courses = snapshot.courses.groupBy { it.studentId }
        val homework = snapshot.homework.groupBy { it.studentId }
        val tasks = snapshot.tasks.filter { it.status !in completeStatuses }.groupBy { it.studentId }
        val problems = snapshot.problems.filter { it.status !in completeStatuses }.groupBy { it.studentId }
        val knowledgeById = snapshot.knowledge.associateBy { it.id }
        val weakProgress = snapshot.progress.filter { it.level in 0..2 }.groupBy { it.studentId }
        val findings = snapshot.students.mapNotNull { student ->
            val facts = mutableListOf<String>()
            val studentCourses = courses[student.id].orEmpty()
            if (studentCourses.isEmpty()) facts += "暂无课程记录"
            else if (studentCourses.maxByOrNull { it.date }?.nextLessonPlan.isNullOrBlank()) facts += "最近课程未记录下节课计划"
            val pendingHomework = homework[student.id].orEmpty().count { it.status !in completeStatuses }
            if (pendingHomework > 0) facts += "有 $pendingHomework 项未完成作业"
            val pendingTasks = tasks[student.id].orEmpty().size
            if (pendingTasks > 0) facts += "有 $pendingTasks 项待办"
            val openProblems = problems[student.id].orEmpty().size
            if (openProblems > 0) facts += "有 $openProblems 条待复习问题"
            val weakNames = weakProgress[student.id].orEmpty().mapNotNull { knowledgeById[it.knowledgePointId]?.name }.distinct()
            if (weakNames.isNotEmpty()) facts += "需巩固：${weakNames.take(3).joinToString("、")}${if (weakNames.size > 3) "等" else ""}"
            if (facts.isEmpty()) null else StudentDataFinding(student.id, student.displayName.ifBlank { student.name }, facts)
        }
        return StudentDataOrganizationReport(snapshot.students.size, findings)
    }

    fun studentsWeakIn(snapshot: StudentDataOrganizationSnapshot, query: String): List<StudentEntity> {
        val matchingIds = snapshot.knowledge.filter { it.name.contains(query, ignoreCase = true) }.map { it.id }.toSet()
        if (matchingIds.isEmpty()) return emptyList()
        val weakStudentIds = snapshot.progress.filter { it.knowledgePointId in matchingIds && it.level in 0..2 }.map { it.studentId }.toSet()
        return snapshot.students.filter { it.id in weakStudentIds }
    }
}
