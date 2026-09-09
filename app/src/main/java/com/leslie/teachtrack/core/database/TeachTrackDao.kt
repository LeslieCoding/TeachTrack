package com.leslie.teachtrack.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.leslie.teachtrack.core.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TeachTrackDao {
    @Query("SELECT * FROM students") suspend fun allStudents(): List<StudentEntity>
    @Query("SELECT * FROM course_sessions") suspend fun allCourses(): List<CourseSessionEntity>
    @Query("SELECT * FROM knowledge_points") suspend fun allKnowledge(): List<KnowledgePointEntity>
    @Query("SELECT * FROM student_knowledge_progress") suspend fun allProgress(): List<StudentKnowledgeProgressEntity>
    @Query("SELECT * FROM exams") suspend fun allExams(): List<ExamEntity>
    @Query("SELECT * FROM student_scores") suspend fun allScores(): List<StudentScoreEntity>
    @Query("SELECT * FROM score_items") suspend fun allScoreItems(): List<ScoreItemEntity>
    @Query("SELECT * FROM homework") suspend fun allHomework(): List<HomeworkEntity>
    @Query("SELECT * FROM communications") suspend fun allCommunications(): List<CommunicationEntity>
    @Query("SELECT * FROM lesson_plans") suspend fun allLessonPlans(): List<LessonPlanEntity>
    @Query("SELECT * FROM tasks") suspend fun allTasks(): List<TaskEntity>
    @Query("SELECT * FROM parent_contacts") suspend fun allParentContacts(): List<ParentContactEntity>
    @Query("SELECT * FROM problem_records") suspend fun allProblemRecords(): List<ProblemRecordEntity>
    @Query("SELECT * FROM templates") suspend fun allTemplates(): List<TemplateEntity>
    @Query("SELECT * FROM tags") suspend fun allTags(): List<TagEntity>
    @Query("SELECT * FROM student_tags") suspend fun allStudentTags(): List<StudentTagEntity>
    @Query("SELECT * FROM attachments") suspend fun allAttachments(): List<AttachmentEntity>
    @Query("SELECT * FROM historical_learning_records") suspend fun allHistoricalLearningRecords(): List<HistoricalLearningRecordEntity>
    @Query("SELECT * FROM student_progress") suspend fun allStudentProgress(): List<StudentProgressEntity>
    @Query("SELECT * FROM unresolved_historical_records") suspend fun allUnresolvedHistoricalRecords(): List<UnresolvedHistoricalRecordEntity>
    @Query("SELECT * FROM student_progress WHERE studentId = :studentId ORDER BY date DESC") fun observeStudentProgress(studentId: String): Flow<List<StudentProgressEntity>>
    @Query("SELECT * FROM problem_records WHERE studentId = :studentId ORDER BY date DESC") fun observeProblemRecords(studentId: String): Flow<List<ProblemRecordEntity>>
    @Query("SELECT * FROM templates ORDER BY updatedAt DESC") fun observeTemplates(): Flow<List<TemplateEntity>>
    @Query("SELECT * FROM attachments WHERE studentId = :studentId ORDER BY createdAt DESC") fun observeAttachments(studentId: String): Flow<List<AttachmentEntity>>
    @Query("SELECT * FROM students WHERE status != 'ARCHIVED' ORDER BY updatedAt DESC") fun observeStudents(): Flow<List<StudentEntity>>
    @Query("SELECT * FROM students WHERE id = :id LIMIT 1") fun observeStudent(id: String): Flow<StudentEntity?>
    @Query("SELECT * FROM students WHERE id = :id LIMIT 1") suspend fun observeStudentOnce(id: String): StudentEntity?
    @Query("SELECT * FROM students WHERE name LIKE '%' || :query || '%' ORDER BY updatedAt DESC") suspend fun searchStudents(query: String): List<StudentEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertStudent(student: StudentEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertStudents(students: List<StudentEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCourse(course: CourseSessionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCourses(courses: List<CourseSessionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertProblemRecord(record: ProblemRecordEntity)
    @Query("UPDATE problem_records SET status = :status, notes = :notes WHERE id = :id") suspend fun updateProblemStatus(id: String, status: String, notes: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTemplate(template: TemplateEntity)
    @Query("DELETE FROM templates WHERE id = :id") suspend fun deleteTemplate(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTag(tag: TagEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertStudentTag(studentTag: StudentTagEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAttachment(attachment: AttachmentEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertHistoricalLearningRecord(record: HistoricalLearningRecordEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertHistoricalLearningRecords(records: List<HistoricalLearningRecordEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertStudentProgress(record: StudentProgressEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertStudentProgressRecords(records: List<StudentProgressEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertUnresolvedHistoricalRecord(record: UnresolvedHistoricalRecordEntity)
    @Query("DELETE FROM attachments WHERE id = :id") suspend fun deleteAttachment(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLessonPlan(plan: LessonPlanEntity)
    @Query("SELECT * FROM lesson_plans WHERE studentId = :studentId ORDER BY plannedDate DESC") fun observeLessonPlans(studentId: String): Flow<List<LessonPlanEntity>>
    @Query("SELECT * FROM lesson_plans ORDER BY plannedDate DESC") fun observeAllLessonPlans(): Flow<List<LessonPlanEntity>>
    @Query("SELECT * FROM course_sessions WHERE studentId = :studentId ORDER BY date DESC, startTime DESC") fun observeCourses(studentId: String): Flow<List<CourseSessionEntity>>
    @Query("SELECT * FROM course_sessions ORDER BY date DESC, startTime DESC") fun observeAllCourses(): Flow<List<CourseSessionEntity>>
    @Query("SELECT * FROM course_sessions WHERE studentId = :studentId ORDER BY date DESC, startTime DESC LIMIT 1") suspend fun latestCourse(studentId: String): CourseSessionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertKnowledge(point: KnowledgePointEntity)
    @Query("SELECT * FROM knowledge_points ORDER BY orderIndex, name") fun observeKnowledge(): Flow<List<KnowledgePointEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertProgress(progress: StudentKnowledgeProgressEntity)
    @Query("SELECT * FROM student_knowledge_progress WHERE studentId = :studentId") fun observeProgress(studentId: String): Flow<List<StudentKnowledgeProgressEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertKnowledgeEvent(event: StudentKnowledgeEventEntity)
    @Query("SELECT * FROM student_knowledge_events WHERE studentId = :studentId ORDER BY occurredAt DESC LIMIT 20") fun observeKnowledgeEvents(studentId: String): Flow<List<StudentKnowledgeEventEntity>>
    @Query("SELECT * FROM student_knowledge_events") suspend fun allKnowledgeEvents(): List<StudentKnowledgeEventEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertExam(exam: ExamEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertScore(score: StudentScoreEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertScoreItem(item: ScoreItemEntity)
    @Query("SELECT * FROM student_scores WHERE studentId = :studentId ORDER BY id DESC") fun observeScores(studentId: String): Flow<List<StudentScoreEntity>>
    @Query("SELECT * FROM student_scores ORDER BY id DESC") fun observeAllScores(): Flow<List<StudentScoreEntity>>
    @Query("SELECT * FROM exams ORDER BY date DESC") fun observeAllExams(): Flow<List<ExamEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTask(task: TaskEntity)
    @Query("UPDATE tasks SET status = :status WHERE id = :id") suspend fun updateTaskStatus(id: String, status: String)
    @Query("SELECT * FROM tasks WHERE status != '已完成' ORDER BY dueDate IS NULL, dueDate") fun observeTasks(): Flow<List<TaskEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertHomework(homework: HomeworkEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertParentContact(contact: ParentContactEntity)
    @Query("SELECT * FROM homework WHERE studentId = :studentId ORDER BY assignedDate DESC") fun observeHomework(studentId: String): Flow<List<HomeworkEntity>>
    @Query("SELECT * FROM homework ORDER BY assignedDate DESC") fun observeAllHomework(): Flow<List<HomeworkEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCommunication(record: CommunicationEntity)
    @Query("SELECT * FROM communications WHERE studentId = :studentId ORDER BY date DESC") fun observeCommunications(studentId: String): Flow<List<CommunicationEntity>>
    @Query("SELECT * FROM communications ORDER BY date DESC") fun observeAllCommunications(): Flow<List<CommunicationEntity>>
    @Query("SELECT COUNT(*) FROM course_sessions") fun observeCourseCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM student_scores") fun observeScoreCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM tasks WHERE status != '已完成'") fun observePendingTaskCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM students WHERE status = 'ACTIVE'") fun observeActiveCount(): Flow<Int>
}
