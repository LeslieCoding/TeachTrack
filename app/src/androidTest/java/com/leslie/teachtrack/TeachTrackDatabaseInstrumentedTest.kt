package com.leslie.teachtrack

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.leslie.teachtrack.core.database.TeachTrackDatabase
import com.leslie.teachtrack.core.model.KnowledgePointEntity
import com.leslie.teachtrack.core.model.StudentEntity
import com.leslie.teachtrack.core.model.StudentKnowledgeEventEntity
import com.leslie.teachtrack.core.model.StudentKnowledgeProgressEntity
import com.leslie.teachtrack.core.model.CourseSessionEntity
import com.leslie.teachtrack.core.model.StudentStatus
import com.leslie.teachtrack.data.TeachTrackRepository
import com.leslie.teachtrack.data.workspace.SyncEngine
import com.leslie.teachtrack.data.workspace.WorkspaceManager
import com.leslie.teachtrack.data.workspace.WorkspaceStorage
import com.leslie.teachtrack.data.workspace.WorkspaceHealthChecker
import com.leslie.teachtrack.data.workspace.SyncEventScanner
import org.json.JSONObject
import com.leslie.teachtrack.copilot.CopilotService
import com.leslie.teachtrack.copilot.FakeCopilotProvider
import com.leslie.teachtrack.copilot.LocalCopilotActionRegistry
import com.leslie.teachtrack.copilot.CopilotCommand
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
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

    @Test fun syncRevisionConflictRuleMatchesTwoDeviceEdits() {
        val manager = WorkspaceManager(ApplicationProvider.getApplicationContext())
        val engine = SyncEngine(manager)
        assertEquals(false, engine.detectConflict(localRevision = 5, incomingBaseRevision = 5))
        assertEquals(true, engine.detectConflict(localRevision = 6, incomingBaseRevision = 5))
        assertEquals(true, engine.detectConflict(localRevision = 5, incomingBaseRevision = 4))
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
}
