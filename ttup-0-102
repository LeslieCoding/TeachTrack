package com.leslie.teachtrack

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.leslie.teachtrack.core.database.TeachTrackDatabase
import com.leslie.teachtrack.core.model.KnowledgePointEntity
import com.leslie.teachtrack.core.model.StudentEntity
import com.leslie.teachtrack.core.model.StudentKnowledgeEventEntity
import com.leslie.teachtrack.core.model.StudentKnowledgeProgressEntity
import com.leslie.teachtrack.core.model.ExamEntity
import com.leslie.teachtrack.core.model.StudentScoreEntity
import com.leslie.teachtrack.core.model.CourseSessionEntity
import com.leslie.teachtrack.core.model.HomeworkEntity
import com.leslie.teachtrack.core.model.StudentStatus
import com.leslie.teachtrack.core.model.TaskEntity
import com.leslie.teachtrack.data.TeachTrackRepository
import com.leslie.teachtrack.data.workspace.SyncEngine
import com.leslie.teachtrack.data.workspace.WorkspaceManager
import com.leslie.teachtrack.data.workspace.WorkspaceStorage
import com.leslie.teachtrack.data.workspace.WorkspaceHealthChecker
import com.leslie.teachtrack.data.workspace.SyncEventScanner
import com.leslie.teachtrack.data.workspace.ConflictResolutionStore
import org.json.JSONObject
import com.leslie.teachtrack.copilot.CopilotService
import com.leslie.teachtrack.copilot.FakeCopilotProvider
import com.leslie.teachtrack.copilot.LocalCopilotActionRegistry
import com.leslie.teachtrack.copilot.CopilotCommand
import com.leslie.teachtrack.copilot.EntityResolution
import com.leslie.teachtrack.copilot.StudentEntityResolver
import com.leslie.teachtrack.agent.TeachTrackOffloadHandler
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TeachTrackDatabaseInstrumentedTest {
    private class MemoryWorkspaceStorage : WorkspaceStorage {
        private val files = linkedMapOf<String, ByteArray>()
        override fun read(name: String): String? = files[name]?.toString(Charsets.UTF_8)
        override fun readBytes(name: String): ByteArray? = files[name]
        override fun write(name: String, content: String) { files[name] = content.toByteArray(Charsets.UTF_8) }
        override fun writeBytes(name: String, content: ByteArray) { files[name] = content }
        override fun append(relativePath: String, content: String) { files[relativePath] = (read(relativePath).orEmpty() + content).toByteArray(Charsets.UTF_8) }
        override fun exists(name: String): Boolean = files.containsKey(name)
        override fun list(directory: String): List<String> = files.keys.filter { it.startsWith("$directory/") }
    }
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), TeachTrackDatabase::class.java
    ).allowMainThreadQueries().build()

    @After fun closeDb() = db.close()

    @Test fun studentAndKnowledgeHistoryRoundTrip() = runBlocking {
        val dao = db.dao()
        val studentId = UUID.randomUUID().toString()
        val pointId = UUID.randomUUID().toString()
        dao.upsertStudent(StudentEntity(studentId, "测试学生"))
        dao.upsertKnowledge(KnowledgePointEntity(pointId, name = "动态电路"))
        dao.upsertProgress(StudentKnowledgeProgressEntity(studentId, pointId, level = 2))
        dao.insertKnowledgeEvent(StudentKnowledgeEventEntity(UUID.randomUUID().toString(), studentId, pointId, level = 2))
        assertEquals(1, dao.allStudents().size)
        assertEquals(1, dao.allKnowledge().size)
        assertEquals(1, dao.allProgress().size)
        assertEquals(1, dao.allKnowledgeEvents().size)
    }

    @Test fun hundredStudentsWithTwoHundredCoursesEachStayQueryable() = runBlocking {
        val dao = db.dao()
        val students = (0 until 100).map { index ->
            StudentEntity("student-$index", "Student $index")
        }
        dao.upsertStudents(students)
        val courses = students.flatMap { student ->
            (0 until 200).map { index ->
                CourseSessionEntity(
                    id = "${student.id}-course-$index",
                    studentId = student.id,
                    date = "2026-09-${(index % 28 + 1).toString().padStart(2, '0')}",
                    title = "lesson $index"
                )
            }
        }
        val start = System.nanoTime()
        dao.upsertCourses(courses)
        val loaded = dao.allCourses()
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(20_000, loaded.size)
        check(elapsedMs < 10_000) { "20,000 course round trip took ${elapsedMs}ms" }
    }

    @Test fun archiveKeepsStudentHistoryAndHidesActiveListEntry() = runBlocking {
        val dao = db.dao()
        val studentId = UUID.randomUUID().toString()
        dao.upsertStudent(StudentEntity(studentId, "可归档学生"))
        dao.upsertCourse(CourseSessionEntity(UUID.randomUUID().toString(), studentId, "2026-09-09", title = "历史课程"))
        dao.upsertStudent(StudentEntity(studentId, "可归档学生", status = StudentStatus.ARCHIVED))
        assertEquals(StudentStatus.ARCHIVED, dao.observeStudentOnce(studentId)?.status)
        assertEquals(1, dao.observeCourses(studentId).first().size)
        assertEquals(0, dao.observeActiveCount().first())
    }

    @Test fun backupJsonRoundTripIsIdempotentAndPreservesIds() = runBlocking {
        val dao = db.dao()
        val repository = TeachTrackRepository(dao, database = db)
        val studentId = UUID.randomUUID().toString()
        val courseId = UUID.randomUUID().toString()
        dao.upsertStudent(StudentEntity(studentId, "Round Trip"))
        dao.upsertCourse(CourseSessionEntity(courseId, studentId, "2026-09-11", title = "History"))
        val backup = repository.exportBackupJson()
        repository.importBackupJson(backup)
        assertEquals(listOf(studentId), dao.allStudents().map { it.id })
        assertEquals(listOf(courseId), dao.allCourses().map { it.id })
        assertEquals(1, dao.allStudents().size)
        assertEquals(1, dao.allCourses().size)
    }

    @Test fun naturalLanguageCommandWritesThroughLocalActionRegistry() = runBlocking {
        val dao = db.dao()
        val repository = TeachTrackRepository(dao, database = db)
        val service = CopilotService(FakeCopilotProvider(), LocalCopilotActionRegistry(repository))
        val candidate = service.interpret("\u6dfb\u52a0\u5b66\u751f\u738b\u6668\u7136")
        val command = requireNotNull(candidate.command)
        val result = service.execute(command, confirmed = true)
        check(result.success) { result.message }
        assertEquals(1, dao.allStudents().count { it.name == "王晨然" })
        val studentId = dao.allStudents().single { it.name == "王晨然" }.id
        val taskCandidate = service.interpret("\u7ed9\u5b66\u751f\u738b\u6668\u7136\u6dfb\u52a0\u5f85\u529e\u68c0\u67e5\u9519\u9898")
        val taskCommand = requireNotNull(taskCandidate.command).copy(parameters = taskCandidate.command.parameters + ("selectedStudentId" to studentId))
        assertEquals("task.create", taskCommand.action)
        val taskResult = service.execute(taskCommand, confirmed = true)
        check(taskResult.success) { "${taskCommand.action}: ${taskResult.message}" }
        assertEquals(1, dao.allTasks().size)
    }

    @Test fun displayNameOnlyHistoricalStudentIsSearchableAndResolvable() = runBlocking {
        val dao = db.dao()
        val repository = TeachTrackRepository(dao, database = db)
        // A partial historical name is intentionally not replaced with a guessed surname.
        dao.upsertStudent(StudentEntity("partial-name", name = "", displayName = "永进", nameCompleteness = "PARTIAL"))

        assertEquals(listOf("partial-name"), repository.searchStudents("永进").map { it.id })
        val resolution = StudentEntityResolver(repository).resolve("永进")
        assertTrue(resolution is EntityResolution.Resolved)
        assertEquals("partial-name", (resolution as EntityResolution.Resolved).student.id)
    }

    @Test fun agentRpcFilePayloadCannotEscapeRootfs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rootfs = java.io.File(context.cacheDir, "agent-rootfs-${UUID.randomUUID()}")
        val expected = java.io.File(rootfs, "tmp/payload.json")
        expected.parentFile?.mkdirs()
        expected.writeText("{\"action\":\"student.search\"}")
        try {
            assertEquals(expected.canonicalPath, TeachTrackOffloadHandler
                .resolveRootfsFile(rootfs, "/tmp", "payload.json").canonicalPath)
            assertFalse(runCatching {
                TeachTrackOffloadHandler.resolveRootfsFile(rootfs, "/tmp", "../../outside.json")
            }.isSuccess)
            assertFalse(runCatching {
                TeachTrackOffloadHandler.resolveRootfsFile(rootfs, "/", "/../../outside.json")
            }.isSuccess)
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test fun organizeAllStudentsReadsRealRoomDataWithoutUnauthorizedMutation() = runBlocking {
        val dao = db.dao()
        val repository = TeachTrackRepository(dao, database = db)
        val studentId = "organize-student"
        dao.upsertStudent(StudentEntity(studentId, "Organize Student", school = ""))
        dao.upsertCourse(CourseSessionEntity("organize-course", studentId, "2026-09-12", nextLessonPlan = ""))
        dao.upsertHomework(HomeworkEntity("organize-homework", studentId, name = "Practice", assignedDate = "2026-09-12", status = "未完成"))
        val service = CopilotService(FakeCopilotProvider(), LocalCopilotActionRegistry(repository))
        val candidate = service.interpret("整理一下所有学生的数据")
        val command = requireNotNull(candidate.command)
        assertEquals("student.data.organize.preview", command.action)
        assertEquals(false, command.confirmationRequired)
        val result = service.execute(command, confirmed = true)
        assertTrue(result.success)
        assertEquals("1", result.data["studentsScanned"])
        assertTrue(result.message.contains("未完成作业"))
        // A report is read-only: the existing Room rows remain exactly the rows written above.
        assertEquals(1, dao.allStudents().size)
        assertEquals(1, dao.allCourses().size)
        assertEquals(1, dao.allHomework().size)
        assertEquals(0, dao.allTasks().size)
    }

    @Test fun confirmedOrganizationFollowUpWritesOneDeduplicatedTaskPerStudent() = runBlocking {
        val dao = db.dao()
        val repository = TeachTrackRepository(dao, database = db)
        val studentId = "follow-up-student"
        dao.upsertStudent(StudentEntity(studentId, "Follow Up Student"))
        val service = CopilotService(FakeCopilotProvider(), LocalCopilotActionRegistry(repository))
        val command = requireNotNull(service.interpret("把所有学生整理结果生成跟进待办").command)
        assertTrue(command.confirmationRequired)
        assertTrue(service.execute(command, confirmed = true).success)
        assertEquals(1, dao.allTasks().size)
        assertTrue(service.execute(command, confirmed = true).success)
        assertEquals(1, dao.allTasks().size)
    }

    @Test fun syncRevisionConflictRuleMatchesTwoDeviceEdits() {
        val manager = WorkspaceManager(ApplicationProvider.getApplicationContext())
        val engine = SyncEngine(manager)
        assertEquals(false, engine.detectConflict(localRevision = 5, incomingBaseRevision = 5))
        assertEquals(true, engine.detectConflict(localRevision = 6, incomingBaseRevision = 5))
        assertEquals(true, engine.detectConflict(localRevision = 5, incomingBaseRevision = 4))
    }

    @Test fun twoDeviceConcurrentEditsCreateOneConflictAndPersistResolution() {
        val storage = MemoryWorkspaceStorage()
        val manager = WorkspaceManager(ApplicationProvider.getApplicationContext())
        val manifest = manager.createManifest("two-device-test")
        manager.ensureStructure(storage, manifest)

        fun event(id: String, deviceId: String, base: Long, revision: Long, name: String) = JSONObject()
            .put("eventId", id)
            .put("workspaceId", manifest.workspaceId)
            .put("deviceId", deviceId)
            .put("timestamp", "2026-09-14T10:00:00Z")
            .put("entityType", "Student")
            .put("entityId", "shared-student")
            .put("operation", "UPDATE")
            .put("baseRevision", base)
            .put("revision", revision)
            .put("payload", JSONObject().put("name", name))
            .toString()

        // Device A first creates and then updates revision 2. Device B independently
        // updates from revision 1, so its revision-2 event must not silently win.
        storage.write(
            "sync/events/2026/09/device-a.jsonl",
            event("a-create", "device-a", 0, 1, "Original") + "\n" +
                event("a-update", "device-a", 1, 2, "Device A") + "\n"
        )
        storage.write("sync/events/2026/09/device-b.jsonl", event("b-update", "device-b", 1, 2, "Device B") + "\n")

        val scan = SyncEventScanner(manager).scan(storage)
        assertEquals(3, scan.events)
        assertEquals(1, scan.conflicts)
        val conflicts = ConflictResolutionStore(manager).files(storage)
        assertEquals(1, conflicts.size)
        val conflict = JSONObject(requireNotNull(storage.read(conflicts.single())))
        assertEquals("Student", conflict.getString("entityType"))
        assertEquals("shared-student", conflict.getString("entityId"))
        assertEquals(2L, conflict.getLong("localRevision"))
        assertEquals(1L, conflict.getLong("incomingBaseRevision"))

        ConflictResolutionStore(manager).recordResolution(storage, conflicts.single(), "VERSION_B")
        val resolution = storage.read("sync/conflicts/resolutions/event-b-update-conflict.json")
        assertEquals("VERSION_B", JSONObject(requireNotNull(resolution)).getString("choice"))
        val store = ConflictResolutionStore(manager)
        assertTrue(runCatching { store.recordResolution(storage, conflicts.single(), "VERSION_A") }.isSuccess)
        assertTrue(runCatching { store.recordResolution(storage, conflicts.single(), "MANUAL_MERGE") }.isSuccess)
        assertFalse(runCatching { store.recordResolution(storage, conflicts.single(), "SILENT_LAST_WRITE_WINS") }.isSuccess)
        assertFalse(runCatching { store.recordResolution(storage, "sync/conflicts/missing.json", "VERSION_A") }.isSuccess)
    }

    @Test fun duplicateSyncEventAcrossDevicesIsReadOnceWithoutConflict() {
        val storage = MemoryWorkspaceStorage()
        val manager = WorkspaceManager(ApplicationProvider.getApplicationContext())
        val manifest = manager.createManifest("duplicate-event-test")
        manager.ensureStructure(storage, manifest)
        val event = JSONObject()
            .put("eventId", "same-event")
            .put("workspaceId", manifest.workspaceId)
            .put("deviceId", "device-a")
            .put("timestamp", "2026-09-14T10:00:00Z")
            .put("entityType", "Task")
            .put("entityId", "task-1")
            .put("operation", "CREATE")
            .put("baseRevision", 0)
            .put("revision", 1)
            .put("payload", JSONObject().put("title", "Review"))
            .toString() + "\n"
        storage.write("sync/events/2026/09/device-a.jsonl", event)
        storage.write("sync/events/2026/09/device-b-copy.jsonl", event)

        val scan = SyncEventScanner(manager).scan(storage)
        assertEquals(1, scan.events)
        assertEquals(0, scan.conflicts)
        assertEquals(0, scan.issues)
    }

    @Test fun backupRestoresRelatedRecordsIntoFreshDatabaseWithStableIds() = runBlocking {
        val sourceDao = db.dao()
        val source = TeachTrackRepository(sourceDao, database = db)
        val studentId = "restore-student"
        val knowledgeId = "restore-knowledge"
        val courseId = "restore-course"
        val examId = "restore-exam"
        val scoreId = "restore-score"
        sourceDao.upsertStudent(StudentEntity(studentId, "Restore Student"))
        sourceDao.upsertKnowledge(KnowledgePointEntity(knowledgeId, name = "Dynamic circuit"))
        sourceDao.upsertProgress(StudentKnowledgeProgressEntity(studentId, knowledgeId, level = 2, notes = "review"))
        sourceDao.insertKnowledgeEvent(StudentKnowledgeEventEntity("restore-progress-event", studentId, knowledgeId, 2, notes = "review"))
        sourceDao.upsertCourse(CourseSessionEntity(courseId, studentId, "2026-09-14", title = "Circuit review"))
        sourceDao.upsertExam(ExamEntity(examId, "Weekly test", "2026-09-14"))
        sourceDao.upsertScore(StudentScoreEntity(scoreId, studentId, examId, 152.0, 160.0))
        sourceDao.upsertHomework(HomeworkEntity("restore-homework", studentId, courseId = courseId, name = "Circuit practice", assignedDate = "2026-09-14"))
        sourceDao.upsertCommunication(com.leslie.teachtrack.core.model.CommunicationEntity("restore-communication", studentId, "2026-09-14", summary = "Parent updated"))
        sourceDao.upsertLessonPlan(com.leslie.teachtrack.core.model.LessonPlanEntity("restore-plan", studentId, "2026-09-20", focus = "Range analysis"))
        sourceDao.upsertTask(TaskEntity("restore-task", "Check homework", studentId = studentId))
        sourceDao.upsertHistoricalLearningRecord(com.leslie.teachtrack.core.model.HistoricalLearningRecordEntity("restore-history", studentId, title = "Historical course"))
        sourceDao.upsertStudentProgress(com.leslie.teachtrack.core.model.StudentProgressEntity("restore-growth", studentId, category = "LEARNING_HABIT", title = "More focused"))
        val backup = source.exportBackupJson()

        val restoredDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), TeachTrackDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val restoredDao = restoredDb.dao()
            TeachTrackRepository(restoredDao, database = restoredDb).importBackupJson(backup)
            assertEquals(listOf(studentId), restoredDao.allStudents().map { it.id })
            assertEquals(listOf(courseId), restoredDao.allCourses().map { it.id })
            assertEquals(listOf(knowledgeId), restoredDao.allKnowledge().map { it.id })
            assertEquals(1, restoredDao.allProgress().size)
            assertEquals(1, restoredDao.allKnowledgeEvents().size)
            assertEquals(listOf(examId), restoredDao.allExams().map { it.id })
            assertEquals(listOf(scoreId), restoredDao.allScores().map { it.id })
            assertEquals(1, restoredDao.allHomework().size)
            assertEquals(1, restoredDao.allCommunications().size)
            assertEquals(1, restoredDao.allLessonPlans().size)
            assertEquals(1, restoredDao.allTasks().size)
            assertEquals(1, restoredDao.allHistoricalLearningRecords().size)
            assertEquals(1, restoredDao.allStudentProgress().size)
        } finally {
            restoredDb.close()
        }
    }

    @Test fun malformedBackupIsRejectedWithoutPartialImport() = runBlocking {
        val dao = db.dao()
        val repository = TeachTrackRepository(dao, database = db)
        val valid = "{\"format\":\"TeachTrack Backup\",\"students\":[{\"id\":\"s1\",\"name\":\"Before\"}]}"
        repository.importBackupJson(valid)
        runCatching { repository.importBackupJson("{\"format\":\"TeachTrack Backup\",\"students\":[{\"id\":\"s2\"},{\"id\":\"s3\",\"name\":\"Broken\"}]") }
            .onSuccess { error("malformed backup was accepted") }
        assertEquals(listOf("s1"), dao.allStudents().map { it.id })
    }

    @Test fun workspaceHealthReportsCorruptJsonAndMissingAttachment() {
        val storage = MemoryWorkspaceStorage()
        val manager = WorkspaceManager(ApplicationProvider.getApplicationContext())
        val manifest = manager.createManifest("health-test")
        manager.ensureStructure(storage, manifest)
        storage.write("data/students.jsonl", "{\"id\":\"s1\",\"workspaceId\":\"${manifest.workspaceId}\"}\nnot-json\n")
        storage.write("data/attachments.jsonl", JSONObject().put("id", "a1").put("relativePath", "attachments/students/s1/missing.pdf").toString())
        val report = WorkspaceHealthChecker(manager).check(storage)
        assertEquals(manifest.workspaceId, report.workspaceId)
        assertEquals(2, report.records)
        assertEquals(true, report.issues.any { it.contains("not-json") || it.contains("attachment missing") })
    }

    @Test fun repeatedSyncScanKeepsOneConflictFilePerEvent() {
        val storage = MemoryWorkspaceStorage()
        val manager = WorkspaceManager(ApplicationProvider.getApplicationContext())
        val manifest = manager.createManifest("sync-test")
        manager.ensureStructure(storage, manifest)
        val engine = SyncEngine(manager)
        engine.appendEvent(storage, manifest.workspaceId, "Student", "s1", "UPDATE", 0, 1, JSONObject().put("name", "A"))
        engine.appendEvent(storage, manifest.workspaceId, "Student", "s1", "UPDATE", 0, 1, JSONObject().put("name", "B"))
        val scanner = SyncEventScanner(manager)
        val first = scanner.scan(storage)
        val second = scanner.scan(storage)
        assertEquals(1, first.conflicts)
        assertTrue(second.conflicts <= 1)
        assertTrue(storage.list("sync/conflicts").count { it.endsWith(".json") } <= 2)
    }

    @Test fun deleteEventWritesTombstoneForOfflineDevices() {
        val storage = MemoryWorkspaceStorage()
        val manager = WorkspaceManager(ApplicationProvider.getApplicationContext())
        val manifest = manager.createManifest("tombstone-test")
        manager.ensureStructure(storage, manifest)
        SyncEngine(manager).appendEvent(storage, manifest.workspaceId, "CourseSession", "course-1", "DELETE", 3, 4, JSONObject())
        val tombstone = storage.read("sync/tombstones/CourseSession-course-1.json")
        assertTrue(tombstone?.contains("course-1") == true)
        assertEquals(4L, JSONObject(tombstone!!).getLong("revision"))
    }

    @Test fun dashboardCourseProjectionIsBoundedAtTwoHundredRows() = runBlocking {
        val dao = db.dao()
        val studentId = "dashboard-student"
        dao.upsertStudent(StudentEntity(studentId, "Dashboard"))
        dao.upsertCourses((0..250).map { index ->
            CourseSessionEntity("dashboard-course-$index", studentId, "2026-09-11", title = "Course $index")
        })
        val projection = dao.observeDashboardCourses().first()
        assertEquals(200, projection.size)
    }

    @Test fun scoreCsvEscapesCommasAndQuotes() = runBlocking {
        val dao = db.dao()
        val studentId = "csv-student"
        val examId = "csv-exam"
        dao.upsertStudent(StudentEntity(studentId, "李,老师"))
        dao.upsertExam(ExamEntity(examId, "期中\"测验", "2026-09-11"))
        dao.upsertScore(StudentScoreEntity("csv-score", studentId, examId, 88.0, 100.0))
        val csv = TeachTrackRepository(dao, database = db).exportScoresCsv()
        assertTrue(csv.contains("\"李,老师\""))
        assertTrue(csv.contains("\"期中\"\"测验\""))
    }

    @Test fun knowledgeCreateAddsPointAndRejectsDuplicate() = runBlocking {
        val dao = db.dao()
        val repository = TeachTrackRepository(dao, database = db)
        val service = CopilotService(FakeCopilotProvider(), LocalCopilotActionRegistry(repository))
        val created = service.execute(CopilotCommand("knowledge.create", true, mapOf("knowledgeName" to "二次函数")), confirmed = true)
        assertTrue(created.success)
        assertEquals(1, dao.allKnowledge().count { it.name == "二次函数" })
        val duplicate = service.execute(CopilotCommand("knowledge.create", true, mapOf("knowledgeName" to "二次函数")), confirmed = true)
        assertFalse(duplicate.success)
        assertEquals(1, dao.allKnowledge().count { it.name == "二次函数" })
    }

    @Test fun taskStatusUpdateResolvesByTitleAndFlipsStatus() = runBlocking {
        val dao = db.dao()
        val repository = TeachTrackRepository(dao, database = db)
        dao.upsertTask(TaskEntity("task-1", "周五检查作业", priority = "普通"))
        val service = CopilotService(FakeCopilotProvider(), LocalCopilotActionRegistry(repository))
        val result = service.execute(CopilotCommand("task.status.update", true, mapOf("rawText" to "把周五检查作业那条待办标记为完成")), confirmed = true)
        assertTrue(result.message, result.success)
        assertEquals("已完成", dao.allTasks().single { it.id == "task-1" }.status)
    }

    @Test fun parentContactCreateAndQueryRoundTrip() = runBlocking {
        val dao = db.dao()
        val repository = TeachTrackRepository(dao, database = db)
        val studentId = "contact-student"
        dao.upsertStudent(StudentEntity(studentId, "Contact Student"))
        val service = CopilotService(FakeCopilotProvider(), LocalCopilotActionRegistry(repository))
        val create = service.execute(CopilotCommand("parent_contact.create", true, mapOf("selectedStudentId" to studentId, "contactName" to "王女士", "phone" to "13800000000", "relation" to "母亲")), confirmed = true)
        assertTrue(create.success)
        val query = service.execute(CopilotCommand("parent_contact.query", false, mapOf("selectedStudentId" to studentId)), confirmed = true)
        assertTrue(query.success)
        assertEquals("1", query.data["count"])
        assertTrue(query.message.contains("王女士"))
    }

    @Test fun templateListUpdateRenderAndDeleteRoundTrip() = runBlocking {
        val dao = db.dao()
        val repository = TeachTrackRepository(dao, database = db)
        dao.upsertTemplate(com.leslie.teachtrack.core.model.TemplateEntity("template-1", "标准反馈", content = "{{student_name}} 在 {{date}} 表现：{{performance}}"))
        val service = CopilotService(FakeCopilotProvider(), LocalCopilotActionRegistry(repository))
        val list = service.execute(CopilotCommand("template.list", false, emptyMap()), confirmed = true)
        assertTrue(list.message.contains("标准反馈"))
        val update = service.execute(CopilotCommand("template.update", true, mapOf("templateName" to "标准反馈", "content" to "更新后的内容 {{student_name}}")), confirmed = true)
        assertTrue(update.success)
        assertEquals("更新后的内容 {{student_name}}", dao.templateByName("标准反馈")?.content)
        val render = service.execute(CopilotCommand("template.render", false, mapOf("templateName" to "标准反馈", "studentName" to "王晨然")), confirmed = true)
        assertTrue(render.success)
        assertTrue(render.message.contains("王晨然"))
        val delete = service.execute(CopilotCommand("template.delete", true, mapOf("templateName" to "标准反馈")), confirmed = true)
        assertTrue(delete.success)
        assertEquals(0, dao.allTemplates().size)
    }

    @Test fun tagAssignCreatesTagWhenMissingAndWritesStudentTag() = runBlocking {
        val dao = db.dao()
        val repository = TeachTrackRepository(dao, database = db)
        val studentId = "tag-student"
        dao.upsertStudent(StudentEntity(studentId, "Tag Student"))
        val service = CopilotService(FakeCopilotProvider(), LocalCopilotActionRegistry(repository))
        val assign = service.execute(CopilotCommand("tag.assign", true, mapOf("selectedStudentId" to studentId, "tagName" to "中考冲刺")), confirmed = true)
        assertTrue(assign.success)
        assertEquals(1, dao.allTags().count { it.name == "中考冲刺" })
        assertEquals(1, dao.studentTagsOnce(studentId).size)
        val query = service.execute(CopilotCommand("tag.query", false, emptyMap()), confirmed = true)
        assertTrue(query.message.contains("中考冲刺"))
    }

    @Test fun attachmentQueryIsScopedPerStudentAndDeleteSoftRemoves() = runBlocking {
        val dao = db.dao()
        val repository = TeachTrackRepository(dao, database = db)
        val studentId = "attachment-student"
        dao.upsertStudent(StudentEntity(studentId, "Attachment Student"))
        dao.upsertAttachment(com.leslie.teachtrack.core.model.AttachmentEntity("att-1", studentId = studentId, name = "作业照片.jpg", uri = "content://test/att1"))
        val other = "other-student"
        dao.upsertStudent(StudentEntity(other, "Other Student"))
        dao.upsertAttachment(com.leslie.teachtrack.core.model.AttachmentEntity("att-2", studentId = other, name = "无关文件.jpg", uri = "content://test/att2"))
        val service = CopilotService(FakeCopilotProvider(), LocalCopilotActionRegistry(repository))
        val query = service.execute(CopilotCommand("attachment.query", false, mapOf("selectedStudentId" to studentId)), confirmed = true)
        assertEquals("1", query.data["count"])
        assertTrue(query.message.contains("作业照片"))
        val delete = service.execute(CopilotCommand("attachment.delete", true, mapOf("attachmentId" to "att-1")), confirmed = true)
        assertTrue(delete.success)
        assertTrue(dao.attachmentOnce("att-1")?.deletedAt != null)
    }

    @Test fun scoreCreateAcceptsCategoryLostScoreAndRanks() = runBlocking {
        val dao = db.dao()
        val repository = TeachTrackRepository(dao, database = db)
        val studentId = "score-detail-student"
        dao.upsertStudent(StudentEntity(studentId, "Score Detail Student"))
        val service = CopilotService(FakeCopilotProvider(), LocalCopilotActionRegistry(repository))
        val result = service.execute(CopilotCommand("score.create", true, mapOf("selectedStudentId" to studentId, "examName" to "月考", "score" to "126", "fullScore" to "160", "category" to "计算失误", "lostScore" to "8", "classRank" to "3", "gradeRank" to "20")), confirmed = true)
        assertTrue(result.success)
        val score = dao.allScores().single { it.studentId == studentId }
        assertEquals(3, score.classRank)
        assertEquals(20, score.gradeRank)
        assertEquals(1, dao.allScoreItems().count { it.scoreId == score.id && it.category == "计算失误" })
    }

    @Test fun studentOverviewQueryReturnsRealAggregatedSummary() = runBlocking {
        val dao = db.dao()
        val repository = TeachTrackRepository(dao, database = db)
        val studentId = "overview-student"
        dao.upsertStudent(StudentEntity(studentId, "Overview Student", grade = "九年级"))
        dao.upsertCourse(CourseSessionEntity("overview-course", studentId, "2026-09-12", title = "二次函数复习"))
        dao.upsertHomework(HomeworkEntity("overview-hw", studentId, name = "练习册", assignedDate = "2026-09-12", status = "未完成"))
        val service = CopilotService(FakeCopilotProvider(), LocalCopilotActionRegistry(repository))
        val result = service.execute(CopilotCommand("student.overview.query", false, mapOf("selectedStudentId" to studentId)), confirmed = true)
        assertTrue(result.message, result.success)
        assertEquals("1", result.data["pendingHomework"])
        assertTrue(result.message.contains("二次函数复习"))
    }

    @Test fun backupExportListAndRestoreRoundTripThroughCopilot(): Unit = runBlocking {
        val dao = db.dao()
        val repository = TeachTrackRepository(dao, database = db)
        val backupDir = java.io.File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "copilot-backup-test-${UUID.randomUUID()}")
        val registry = LocalCopilotActionRegistry(repository, backupDirectory = backupDir)
        val service = CopilotService(FakeCopilotProvider(), registry)
        dao.upsertStudent(StudentEntity("backup-student", "Backup Student"))
        val exportResult = service.execute(CopilotCommand("backup.export", true, emptyMap()), confirmed = true)
        assertTrue(exportResult.message, exportResult.success)
        val listResult = service.execute(CopilotCommand("backup.list", false, emptyMap()), confirmed = true)
        assertEquals("1", listResult.data["count"])
        dao.upsertStudent(StudentEntity("backup-student", "Backup Student Renamed"))
        val restoreResult = service.execute(CopilotCommand("backup.restore", true, emptyMap()), confirmed = true)
        assertTrue(restoreResult.message, restoreResult.success)
        assertEquals("Backup Student", dao.observeStudentOnce("backup-student")?.name)
        backupDir.deleteRecursively()
    }

    @Test fun csvExportScoresWritesFileThroughCopilot(): Unit = runBlocking {
        val dao = db.dao()
        val repository = TeachTrackRepository(dao, database = db)
        val backupDir = java.io.File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "copilot-csv-test-${UUID.randomUUID()}")
        val registry = LocalCopilotActionRegistry(repository, backupDirectory = backupDir)
        val service = CopilotService(FakeCopilotProvider(), registry)
        val studentId = "csv-copilot-student"; val examId = "csv-copilot-exam"
        dao.upsertStudent(StudentEntity(studentId, "CSV Student"))
        dao.upsertExam(ExamEntity(examId, "期末", "2026-09-12"))
        dao.upsertScore(StudentScoreEntity("csv-copilot-score", studentId, examId, 90.0, 100.0))
        val result = service.execute(CopilotCommand("csv.export.scores", true, emptyMap()), confirmed = true)
        assertTrue(result.success)
        val fileName = requireNotNull(result.data["fileName"])
        assertTrue(java.io.File(backupDir, fileName).exists())
        backupDir.deleteRecursively()
    }

    @Test fun workspaceActionsGuideUserWhenNoWorkspaceIsConnected() = runBlocking {
        val dao = db.dao()
        val repository = TeachTrackRepository(dao, database = db)
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        // Never clear the real app's persisted SAF grant: instrumentation runs
        // against the installed package and must not mutate the teacher's
        // actual Workspace merely to exercise the disconnected branch.
        val isolatedPrefs = "instrumentation_workspace_${UUID.randomUUID()}"
        val isolatedContext = object : ContextWrapper(appContext) {
            override fun getSharedPreferences(name: String, mode: Int) =
                appContext.getSharedPreferences("${isolatedPrefs}_$name", mode)
        }
        val manager = WorkspaceManager(isolatedContext)
        val registry = LocalCopilotActionRegistry(repository, workspaceManager = manager)
        val service = CopilotService(FakeCopilotProvider(), registry)
        try {
            val status = service.execute(CopilotCommand("workspace.status.query", false, emptyMap()), confirmed = true)
            assertTrue(status.success)
            assertTrue(status.message.contains("尚未连接"))
            val export = service.execute(CopilotCommand("workspace.export", true, emptyMap()), confirmed = true)
            assertFalse(export.success)
        } finally {
            isolatedContext.getSharedPreferences("teachtrack_workspace", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
