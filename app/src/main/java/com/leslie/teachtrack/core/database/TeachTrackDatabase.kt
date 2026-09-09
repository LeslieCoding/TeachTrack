package com.leslie.teachtrack.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.leslie.teachtrack.core.model.*

@Database(entities = [StudentEntity::class, CourseSessionEntity::class, KnowledgePointEntity::class, StudentKnowledgeProgressEntity::class, StudentKnowledgeEventEntity::class, ExamEntity::class, StudentScoreEntity::class, HomeworkEntity::class, CommunicationEntity::class, LessonPlanEntity::class, TaskEntity::class, ParentContactEntity::class, ScoreItemEntity::class, ProblemRecordEntity::class, TemplateEntity::class, TagEntity::class, StudentTagEntity::class, AttachmentEntity::class], version = 3, exportSchema = true)
@TypeConverters(Converters::class)
abstract class TeachTrackDatabase : RoomDatabase() { abstract fun dao(): TeachTrackDao }

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `score_items` (`id` TEXT NOT NULL, `scoreId` TEXT NOT NULL, `category` TEXT NOT NULL, `lostScore` REAL NOT NULL, `knowledgePointId` TEXT, `notes` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_score_items_scoreId` ON `score_items` (`scoreId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_score_items_knowledgePointId` ON `score_items` (`knowledgePointId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `problem_records` (`id` TEXT NOT NULL, `studentId` TEXT NOT NULL, `knowledgePointId` TEXT, `courseId` TEXT, `examId` TEXT, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `date` TEXT NOT NULL, `status` TEXT NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_problem_records_studentId` ON `problem_records` (`studentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_problem_records_knowledgePointId` ON `problem_records` (`knowledgePointId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_problem_records_courseId` ON `problem_records` (`courseId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_problem_records_examId` ON `problem_records` (`examId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `templates` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_templates_type` ON `templates` (`type`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `student_tags` (`studentId` TEXT NOT NULL, `tagId` TEXT NOT NULL, PRIMARY KEY(`studentId`, `tagId`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_student_tags_tagId` ON `student_tags` (`tagId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `attachments` (`id` TEXT NOT NULL, `studentId` TEXT, `courseId` TEXT, `homeworkId` TEXT, `name` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `uri` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_studentId` ON `attachments` (`studentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_courseId` ON `attachments` (`courseId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_homeworkId` ON `attachments` (`homeworkId`)")
    }
}

val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `student_knowledge_events` (`id` TEXT NOT NULL, `studentId` TEXT NOT NULL, `knowledgePointId` TEXT NOT NULL, `level` INTEGER NOT NULL, `occurredAt` INTEGER NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_student_knowledge_events_studentId` ON `student_knowledge_events` (`studentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_student_knowledge_events_knowledgePointId` ON `student_knowledge_events` (`knowledgePointId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_student_knowledge_events_occurredAt` ON `student_knowledge_events` (`occurredAt`)")
    }
}

class Converters {
    @androidx.room.TypeConverter fun fromStatus(value: StudentStatus) = value.name
    @androidx.room.TypeConverter fun toStatus(value: String) = StudentStatus.valueOf(value)
}
