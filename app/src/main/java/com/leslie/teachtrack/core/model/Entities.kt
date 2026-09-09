package com.leslie.teachtrack.core.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class StudentStatus { ACTIVE, PAUSED, ENDED, ARCHIVED }
enum class MasteryLevel(val value: Int, val label: String) { NOT_STARTED(0, "未学习"), LEARNED(1, "已学习"), REVIEW(2, "需巩固"), BASIC(3, "基本掌握"), SKILLED(4, "熟练掌握") }

@Entity(tableName = "students", indices = [Index("name"), Index("status")])
data class StudentEntity(@PrimaryKey val id: String, val name: String, val gender: String? = null, val birthday: String? = null, val grade: String = "", val school: String = "", val className: String = "", val subject: String = "", val textbook: String = "", val status: StudentStatus = StudentStatus.ACTIVE, val startDate: String? = null, val notes: String = "", val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis())

@Entity(tableName = "course_sessions", indices = [Index("studentId"), Index("date")])
data class CourseSessionEntity(@PrimaryKey val id: String, val studentId: String, val date: String, val startTime: String = "", val endTime: String = "", val subject: String = "", val title: String = "", val content: String = "", val performance: String = "", val problems: String = "", val homeworkSummary: String = "", val nextLessonPlan: String = "", val teacherNotes: String = "", val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis())

@Entity(tableName = "knowledge_points", indices = [Index("parentId"), Index("subject")])
data class KnowledgePointEntity(@PrimaryKey val id: String, val parentId: String? = null, val name: String, val subject: String = "", val grade: String = "", val orderIndex: Int = 0, val description: String = "")

@Entity(tableName = "student_knowledge_progress", primaryKeys = ["studentId", "knowledgePointId"], indices = [Index("knowledgePointId")])
data class StudentKnowledgeProgressEntity(val studentId: String, val knowledgePointId: String, val level: Int = 0, val lastUpdated: Long = System.currentTimeMillis(), val notes: String = "")

@Entity(tableName = "student_knowledge_events", indices = [Index("studentId"), Index("knowledgePointId"), Index("occurredAt")])
data class StudentKnowledgeEventEntity(@PrimaryKey val id: String, val studentId: String, val knowledgePointId: String, val level: Int, val occurredAt: Long = System.currentTimeMillis(), val notes: String = "")

@Entity(tableName = "exams", indices = [Index("date")])
data class ExamEntity(@PrimaryKey val id: String, val name: String, val date: String, val subject: String = "", val type: String = "自定义", val notes: String = "")

@Entity(tableName = "student_scores", indices = [Index("studentId"), Index("examId")])
data class StudentScoreEntity(@PrimaryKey val id: String, val studentId: String, val examId: String, val score: Double, val fullScore: Double, val classRank: Int? = null, val gradeRank: Int? = null, val classSize: Int? = null, val gradeSize: Int? = null, val notes: String = "") { val scoreRate: Double get() = if (fullScore > 0) score / fullScore else 0.0 }

@Entity(tableName = "homework", indices = [Index("studentId"), Index("courseId")])
data class HomeworkEntity(@PrimaryKey val id: String, val studentId: String, val courseId: String? = null, val name: String, val assignedDate: String, val dueDate: String? = null, val content: String = "", val status: String = "未完成", val teacherComment: String = "")

@Entity(tableName = "communications", indices = [Index("studentId"), Index("date")])
data class CommunicationEntity(@PrimaryKey val id: String, val studentId: String, val date: String, val contact: String = "", val method: String = "微信", val summary: String = "", val parentFeedback: String = "", val followUp: String = "")

@Entity(tableName = "lesson_plans", indices = [Index("studentId"), Index("plannedDate")])
data class LessonPlanEntity(@PrimaryKey val id: String, val studentId: String, val plannedDate: String, val chapter: String = "", val knowledgePoints: String = "", val focus: String = "", val difficulty: String = "", val review: String = "", val newContent: String = "", val homework: String = "", val notes: String = "")

@Entity(tableName = "tasks", indices = [Index("studentId"), Index("dueDate"), Index("status")])
data class TaskEntity(@PrimaryKey val id: String, val title: String, val studentId: String? = null, val type: String = "其他", val dueDate: String? = null, val priority: String = "普通", val status: String = "待处理", val notes: String = "")

@Entity(tableName = "parent_contacts", indices = [Index("studentId")])
data class ParentContactEntity(@PrimaryKey val id: String, val studentId: String, val name: String = "", val phone: String = "", val relation: String = "", val isPrimary: Boolean = false, val notes: String = "")

@Entity(tableName = "score_items", indices = [Index("scoreId"), Index("knowledgePointId")])
data class ScoreItemEntity(@PrimaryKey val id: String, val scoreId: String, val category: String = "", val lostScore: Double = 0.0, val knowledgePointId: String? = null, val notes: String = "")

@Entity(tableName = "problem_records", indices = [Index("studentId"), Index("knowledgePointId"), Index("courseId"), Index("examId")])
data class ProblemRecordEntity(@PrimaryKey val id: String, val studentId: String, val knowledgePointId: String? = null, val courseId: String? = null, val examId: String? = null, val title: String, val description: String = "", val date: String, val status: String = "待复习", val notes: String = "")

@Entity(tableName = "templates", indices = [Index("type")])
data class TemplateEntity(@PrimaryKey val id: String, val name: String, val type: String = "课后反馈", val content: String = "", val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis())

@Entity(tableName = "tags", indices = [Index("name", unique = true)])
data class TagEntity(@PrimaryKey val id: String, val name: String, val color: String = "#176B57")

@Entity(tableName = "student_tags", primaryKeys = ["studentId", "tagId"], indices = [Index("tagId")])
data class StudentTagEntity(val studentId: String, val tagId: String)

@Entity(tableName = "attachments", indices = [Index("studentId"), Index("courseId"), Index("homeworkId")])
data class AttachmentEntity(@PrimaryKey val id: String, val studentId: String? = null, val courseId: String? = null, val homeworkId: String? = null, val name: String, val mimeType: String = "application/octet-stream", val uri: String, val createdAt: Long = System.currentTimeMillis())
