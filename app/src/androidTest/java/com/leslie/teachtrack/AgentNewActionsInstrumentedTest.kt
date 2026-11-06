package com.leslie.teachtrack

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.leslie.teachtrack.core.database.TeachTrackDatabase
import com.leslie.teachtrack.core.model.CommunicationEntity
import com.leslie.teachtrack.core.model.CourseSessionEntity
import com.leslie.teachtrack.core.model.ExamEntity
import com.leslie.teachtrack.core.model.HomeworkEntity
import com.leslie.teachtrack.core.model.KnowledgePointEntity
import com.leslie.teachtrack.core.model.LessonPlanEntity
import com.leslie.teachtrack.core.model.ProblemRecordEntity
import com.leslie.teachtrack.core.model.StudentEntity
import com.leslie.teachtrack.core.model.StudentKnowledgeProgressEntity
import com.leslie.teachtrack.core.model.StudentScoreEntity
import com.leslie.teachtrack.core.model.TaskEntity
import com.leslie.teachtrack.agent.TeachTrackIntegrationSettings
import com.leslie.teachtrack.agent.TeachTrackOffloadHandler
import com.leslie.teachtrack.copilot.CopilotCommand
import com.leslie.teachtrack.copilot.CopilotEffect
import com.leslie.teachtrack.copilot.CopilotService
import com.leslie.teachtrack.copilot.FakeCopilotProvider
import com.leslie.teachtrack.copilot.LocalCopilotActionRegistry
import com.leslie.teachtrack.data.TeachTrackRepository
import com.openminis.app.sandbox.NativeOffloadRequest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device acceptance coverage (spec §56-65) for the P2/P4 capability
 * additions and the question-bank UI bridge. Every data action is driven
 * through the SAME CopilotService →
 * LocalCopilotActionRegistry path the offload handler uses, against an
 * in-memory Room database seeded exactly like
 * [TeachTrackDatabaseInstrumentedTest].
 */
@RunWith(AndroidJUnit4::class)
class AgentNewActionsInstrumentedTest {
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), TeachTrackDatabase::class.java
    ).allowMainThreadQueries().build()

    @After fun closeDb() = db.close()

    private fun service(): CopilotService =
        CopilotService(FakeCopilotProvider(), LocalCopilotActionRegistry(TeachTrackRepository(db.dao(), database = db)))

    private suspend fun run(service: CopilotService, action: String, params: Map<String, String> = emptyMap()) =
        service.execute(CopilotCommand(action, false, params), confirmed = true)

    /** One student with one of every record type the query actions read. */
    private suspend fun seedFullStudent(studentId: String = "s-accept", name: String = "验收学生"): String {
        val dao = db.dao()
        dao.upsertStudent(StudentEntity(studentId, name, subject = "物理"))
        dao.upsertCourse(CourseSessionEntity("c-1", studentId, "2026-09-20", subject = "物理", title = "动态电路", nextLessonPlan = "继续范围题"))
        dao.upsertExam(ExamEntity("e-1", "九月周测", "2026-09-21", "物理"))
        dao.upsertScore(StudentScoreEntity("sc-1", studentId, "e-1", 153.0, 160.0))
        dao.upsertHomework(HomeworkEntity("h-1", studentId, name = "范围题练习", assignedDate = "2026-09-21", status = "未完成"))
        dao.upsertCommunication(CommunicationEntity("cm-1", studentId, "2026-09-21", contact = "妈妈", summary = "沟通近期状态"))
        dao.upsertProblemRecord(ProblemRecordEntity("pr-1", studentId, title = "滑动变阻器范围题", date = "2026-09-21", status = "待复习"))
        dao.upsertTask(TaskEntity("t-1", "检查范围题作业", studentId))
        dao.upsertLessonPlan(LessonPlanEntity("lp-1", studentId, "2026-09-27", focus = "范围题专项"))
        dao.upsertKnowledge(KnowledgePointEntity("kp-1", name = "动态电路"))
        dao.upsertProgress(StudentKnowledgeProgressEntity(studentId, "kp-1", level = 2))
        return studentId
    }

    @Test fun p2QueryActionsReturnSeededData() = runBlocking {
        val service = service()
        val studentId = seedFullStudent()
        val byName = mapOf("studentName" to "验收学生")

        val courseQuery = run(service, "course.query", byName)
        assertTrue(courseQuery.message, courseQuery.success)
        assertTrue(courseQuery.message.contains("动态电路"))
        assertEquals("1", courseQuery.data["count"])

        val examQuery = run(service, "exam.query")
        assertTrue(examQuery.message, examQuery.success)
        assertTrue(examQuery.message.contains("九月周测"))
        assertTrue(examQuery.data["examIds"]!!.contains("e-1"))

        val trend = run(service, "score.trend", byName)
        assertTrue(trend.message, trend.success)
        assertTrue(trend.message.contains("95%")) // 153/160 = 95.6%, truncated by toInt in the trend line
        assertEquals("1", trend.data["count"])

        val homework = run(service, "homework.query", byName)
        assertTrue(homework.message, homework.success)
        assertTrue(homework.message.contains("范围题练习"))

        val communication = run(service, "communication.query", byName)
        assertTrue(communication.message, communication.success)
        assertTrue(communication.message.contains("沟通近期状态"))

        val problems = run(service, "problem.query", byName)
        assertTrue(problems.message, problems.success)
        assertTrue(problems.message.contains("滑动变阻器"))
        assertTrue(problems.data["problemIds"]!!.contains("pr-1"))

        val tasks = run(service, "task.query", byName)
        assertTrue(tasks.message, tasks.success)
        assertTrue(tasks.message.contains("检查范围题作业"))
        assertTrue(tasks.data["taskIds"]!!.contains("t-1"))

        val plans = run(service, "lesson_plan.query", byName)
        assertTrue(plans.message, plans.success)
        assertTrue(plans.message.contains("范围题专项"))

        val timeline = run(service, "timeline.query", byName)
        assertTrue(timeline.message, timeline.success)
        assertTrue(timeline.message.contains("动态电路"))
        // Five seeded event sources: course + score + homework + communication + problem.
        assertEquals("5", timeline.data["count"])
    }

    @Test fun p2SearchGlobalAndStudentTags() = runBlocking {
        val service = service()
        val studentId = seedFullStudent()
        run(service, "tag.assign", mapOf("studentId" to studentId, "tagName" to "重点关注"))

        val global = run(service, "search.global", mapOf("query" to "验收"))
        assertTrue(global.message, global.success)
        assertEquals("1", global.data["students"])

        val knowledgeHit = run(service, "search.global", mapOf("query" to "动态电路"))
        assertTrue(knowledgeHit.message, knowledgeHit.success)
        assertEquals("1", knowledgeHit.data["knowledge"])

        val tags = run(service, "tag.student.query", mapOf("studentId" to studentId))
        assertTrue(tags.message, tags.success)
        assertTrue(tags.message.contains("重点关注"))
        assertEquals("1", tags.data["count"])
    }

    @Test fun p2NavigationActionsResolveEffects() = runBlocking {
        val service = service()
        val studentId = seedFullStudent()

        val tab = run(service, "ui.open.tab", mapOf("destination" to "统计"))
        assertTrue(tab.message, tab.success)
        assertTrue(tab.effect is CopilotEffect.OpenTab)
        assertEquals(3, (tab.effect as CopilotEffect.OpenTab).index)

        val agentTab = run(service, "navigation.open", mapOf("destination" to "agent"))
        assertTrue(agentTab.message, agentTab.success)
        assertEquals(5, (agentTab.effect as CopilotEffect.OpenTab).index)

        val student = run(service, "ui.open.student", mapOf("studentName" to "验收学生"))
        assertTrue(student.message, student.success)
        assertTrue(student.effect is CopilotEffect.OpenStudent)
        assertEquals(studentId, (student.effect as CopilotEffect.OpenStudent).studentId)
        assertEquals(studentId, student.data["studentId"])
    }

    @Test fun p4BatchScoresCreateWithPerItemFailureIsolation() = runBlocking {
        val service = service()
        seedFullStudent()
        val items = """[
            {"studentName":"验收学生","score":"140","fullScore":"160","examName":"周测二"},
            {"studentName":"不存在的学生","score":"90"},
            {"studentName":"验收学生","score":"not-a-number"}
        ]""".trimIndent()
        val result = run(service, "batch.scores.create", mapOf("items" to items))
        assertTrue(result.message, result.success)
        assertEquals("1", result.data["created"])
        assertEquals("2", result.data["failed"])
        val outcomes = JSONArray(result.data["items"])
        assertEquals(3, outcomes.length())
        assertTrue(outcomes.getJSONObject(0).getBoolean("ok"))
        assertTrue(!outcomes.getJSONObject(1).getBoolean("ok"))
        assertTrue(!outcomes.getJSONObject(2).getBoolean("ok"))
        // Only the one valid score landed; the seeded one is untouched.
        val scores = db.dao().scoresOnce("s-accept")
        assertEquals(2, scores.size)
        assertTrue(scores.any { it.score == 140.0 && it.fullScore == 160.0 })
        // The exam for the valid item was created by name, like score.create does.
        assertTrue(db.dao().allExams().any { it.name == "周测二" })
    }

    @Test fun p4BatchCoursesTagsAndTaskComplete() = runBlocking {
        val service = service()
        val dao = db.dao()
        dao.upsertStudent(StudentEntity("s-a", "学生甲"))
        dao.upsertStudent(StudentEntity("s-b", "学生乙"))
        dao.upsertTask(TaskEntity("t-open-1", "批改甲作业", "s-a"))
        dao.upsertTask(TaskEntity("t-open-2", "批改乙作业", "s-b"))

        val courses = run(service, "batch.courses.create", mapOf("items" to """[
            {"studentName":"学生甲","content":"讲完欧姆定律","subject":"物理"},
            {"studentName":"学生乙","content":"复习浮力","subject":"物理","nextLessonPlan":"浮力综合"}
        ]""".trimIndent()))
        assertTrue(courses.message, courses.success)
        assertEquals("2", courses.data["created"])
        assertEquals(2, dao.allCourses().size)
        // course.create's side rows apply per item too: nextLessonPlan auto-creates a lesson plan.
        assertTrue(dao.allLessonPlans().any { it.studentId == "s-b" })

        val tags = run(service, "batch.tags.assign", mapOf("items" to """[
            {"studentName":"学生甲","tagName":"重点关注"},
            {"studentName":"学生乙","tagName":"重点关注"},
            {"studentName":"不存在","tagName":"重点关注"}
        ]""".trimIndent()))
        assertTrue(tags.message, tags.success)
        assertEquals("2", tags.data["created"])
        assertEquals("1", tags.data["failed"])
        assertEquals(2, dao.allStudentTags().size)

        val complete = run(service, "batch.tasks.complete", mapOf("items" to """[
            {"taskId":"t-open-1"},
            {"title":"批改乙作业"},
            {"title":"不存在的待办"}
        ]""".trimIndent()))
        assertTrue(complete.message, complete.success)
        assertEquals("2", complete.data["created"])
        assertEquals("1", complete.data["failed"])
        assertEquals(2, dao.allTasks().count { it.status == "已完成" })
    }

    /** Spec §59: record a score (153/160) with history preserved. */
    @Test fun acceptanceScoreCreatePreservesHistory() = runBlocking {
        val service = service()
        val dao = db.dao()
        dao.upsertStudent(StudentEntity("s-hist", "历史学生"))
        dao.upsertExam(ExamEntity("e-old", "上周周测", "2026-09-14", "物理"))
        dao.upsertScore(StudentScoreEntity("sc-old", "s-hist", "e-old", 120.0, 160.0))

        val result = run(service, "score.create", mapOf(
            "studentName" to "历史学生", "examName" to "本周周测",
            "score" to "153", "fullScore" to "160",
        ))
        assertTrue(result.message, result.success)
        val scores = dao.scoresOnce("s-hist")
        assertEquals(2, scores.size) // old score preserved, new one added
        assertTrue(scores.any { it.score == 120.0 })
        assertTrue(scores.any { it.score == 153.0 && it.fullScore == 160.0 })
        val trend = run(service, "score.trend", mapOf("studentName" to "历史学生"))
        assertTrue(trend.success)
        assertTrue(trend.message.contains("75%"))
        assertTrue(trend.message.contains("95%")) // 153/160 = 95.6%, truncated
    }

    /** Spec §60: record a course with knowledge progress + next plan. */
    @Test fun acceptanceCourseWithKnowledgeAndNextPlan() = runBlocking {
        val service = service()
        val dao = db.dao()
        dao.upsertStudent(StudentEntity("s-course", "课程学生", subject = "物理"))

        val course = run(service, "course.create", mapOf(
            "studentName" to "课程学生", "content" to "动态电路与U-I图像",
            "subject" to "物理", "nextLessonPlan" to "滑动变阻器范围题",
        ))
        assertTrue(course.message, course.success)
        assertEquals(1, dao.allCourses().size)
        // Same side-effect the UI path has: nextLessonPlan auto-creates a lesson plan row.
        assertTrue(dao.allLessonPlans().any { it.focus == "滑动变阻器范围题" })

        // knowledge.update requires the point to exist first (same rule as the UI flow).
        val create = run(service, "knowledge.create", mapOf("knowledgeName" to "动态电路", "subject" to "物理"))
        assertTrue(create.message, create.success)
        val knowledge = run(service, "knowledge.update", mapOf(
            "studentName" to "课程学生", "knowledgeName" to "动态电路", "level" to "2", "notes" to "范围题仍出错",
        ))
        assertTrue(knowledge.message, knowledge.success)
        assertEquals(1, dao.allKnowledge().count { it.name == "动态电路" })
        assertEquals(2, dao.progressOnce("s-course").single().level)

        val plan = run(service, "lesson_plan.create", mapOf(
            "studentName" to "课程学生", "focus" to "滑动变阻器范围题专项",
        ))
        assertTrue(plan.message, plan.success)
        assertTrue(dao.allLessonPlans().any { it.focus == "滑动变阻器范围题专项" })
    }

    /** Spec §62: batch query + computed sort flow across students. */
    @Test fun acceptanceBatchQuerySortFlow() = runBlocking {
        val service = service()
        val dao = db.dao()
        dao.upsertStudent(StudentEntity("s-r1", "学生一"))
        dao.upsertStudent(StudentEntity("s-r2", "学生二"))
        dao.upsertExam(ExamEntity("e-r", "月考", "2026-09-22", "物理"))
        dao.upsertScore(StudentScoreEntity("sc-r1", "s-r1", "e-r", 150.0, 160.0))
        dao.upsertScore(StudentScoreEntity("sc-r2", "s-r2", "e-r", 112.0, 160.0))

        val students = run(service, "student.search", mapOf("status" to "在读"))
        assertTrue(students.success)
        assertEquals("2", students.data["count"])

        // The Agent computes the ranking itself from per-student queries.
        val ranking = listOf("学生一", "学生二").map { name ->
            val query = run(service, "score.query", mapOf("studentName" to name))
            assertTrue(query.message, query.success)
            name to dao.scoresOnce(if (name == "学生一") "s-r1" else "s-r2").map { it.scoreRate }.average()
        }.sortedByDescending { it.second }
        assertEquals(listOf("学生一", "学生二"), ranking.map { it.first })
        assertTrue(ranking[0].second > ranking[1].second)
    }

    /** P2 batch.tasks.create: JSON-array bulk create with id return. */
    @Test fun p2BatchTasksCreateFromJsonItems() = runBlocking {
        val service = service()
        val dao = db.dao()
        dao.upsertStudent(StudentEntity("s-bt", "待办学生"))
        val result = run(service, "batch.tasks.create", mapOf("items" to """[
            {"title":"整理错题本","studentName":"待办学生"},
            {"title":"打印试卷"},
            {"title":""}
        ]""".trimIndent()))
        assertTrue(result.message, result.success)
        assertEquals("2", result.data["created"])
        assertEquals("1", result.data["failed"])
        assertEquals(2, dao.allTasks().size)
        assertEquals(1, dao.allTasks().count { it.studentId == "s-bt" })
        assertEquals(2, result.data["taskIds"]!!.split(",").size)
    }

    /** Spec §55: task.status.update issues an undo token; undo restores the previous status. */
    @Test fun undoRestoresTaskStatus() = runBlocking {
        val service = service()
        val dao = db.dao()
        dao.upsertStudent(StudentEntity("s-u", "撤销学生"))
        dao.upsertTask(TaskEntity("t-u", "撤销测试待办", "s-u"))
        val result = service.execute(
            CopilotCommand("task.status.update", true, mapOf("taskId" to "t-u", "status" to "已完成")),
            confirmed = true,
        )
        assertTrue(result.message, result.success)
        assertEquals("已完成", dao.allTasks().first { it.id == "t-u" }.status)
        val token = result.undoToken
        assertTrue("undo token expected", token != null)
        val undo = service.undo(token!!)
        assertTrue(undo.message, undo.success)
        assertEquals("待处理", dao.allTasks().first { it.id == "t-u" }.status)
    }

    /** Spec §55: batch.tasks.complete issues one token covering every completed task. */
    @Test fun undoRestoresBatchTaskCompletion() = runBlocking {
        val service = service()
        val dao = db.dao()
        dao.upsertTask(TaskEntity("t-b1", "批量撤销一"))
        dao.upsertTask(TaskEntity("t-b2", "批量撤销二"))
        val result = service.execute(
            CopilotCommand("batch.tasks.complete", true, mapOf("items" to """[{"taskId":"t-b1"},{"taskId":"t-b2"}]""")),
            confirmed = true,
        )
        assertTrue(result.message, result.success)
        assertTrue("undo token expected", result.undoToken != null)
        val undo = service.undo(result.undoToken!!)
        assertTrue(undo.message, undo.success)
        assertEquals(setOf("待处理"), dao.allTasks().map { it.status }.toSet())
    }

    /** Spec §55: student.create undo archives the new student (soft path, same as student.archive). */
    @Test fun undoArchivesNewStudent() = runBlocking {
        val service = service()
        val dao = db.dao()
        val result = service.execute(
            CopilotCommand("student.create", true, mapOf("studentName" to "撤销新生")),
            confirmed = true,
        )
        assertTrue(result.message, result.success)
        val studentId = (result.effect as CopilotEffect.OpenStudent).studentId
        val undo = service.undo(result.undoToken!!)
        assertTrue(undo.message, undo.success)
        assertEquals(com.leslie.teachtrack.core.model.StudentStatus.ARCHIVED, dao.observeStudentOnce(studentId)?.status)
    }

    @Test fun examAnalysisTextFillRunsThroughOffloadInStandardMode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TeachTrackIntegrationSettings.setAccessMode(context, TeachTrackIntegrationSettings.MODE_STANDARD)
        val requests = com.leslie.teachtrack.agent.QuestionBankAgentBridge.openExamAnalysisRequests.value
        try {
            val handler = TeachTrackOffloadHandler(context, context.cacheDir)
            val result = handler.handle(
                NativeOffloadRequest(
                    0,
                    listOf("teachtrack", "rpc", "--json", """{"action":"exam.analysis.text.fill","params":{"text":"1. 液体压强题 2分","mode":"replace"}}"""),
                    emptyMap(),
                    "/",
                )
            )
            assertEquals(result.output, 0, result.exitCode)
            assertEquals("exam.analysis.text.fill", JSONObject(result.output).getString("operation"))
            assertEquals("1. 液体压强题 2分", com.leslie.teachtrack.agent.QuestionBankAgentBridge.examAnalysisText.value)
            assertEquals(requests + 1, com.leslie.teachtrack.agent.QuestionBankAgentBridge.openExamAnalysisRequests.value)
        } finally {
            com.leslie.teachtrack.agent.QuestionBankAgentBridge.updateExamAnalysisText("")
            TeachTrackIntegrationSettings.setAccessMode(context, TeachTrackIntegrationSettings.MODE_OWNER_TRUSTED)
        }
    }

    /** Spec §43/§45: standard access mode refuses bridge writes; read-only actions still run. */
    @Test fun standardAccessModeGatesOffloadWrites() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TeachTrackIntegrationSettings.setAccessMode(context, TeachTrackIntegrationSettings.MODE_STANDARD)
        try {
            val handler = TeachTrackOffloadHandler(context, context.cacheDir)
            fun rpc(json: String) = handler.handle(
                NativeOffloadRequest(0, listOf("teachtrack", "rpc", "--json", json), emptyMap(), "/")
            )
            val write = rpc("""{"action":"task.create","params":{"title":"不应执行"}}""")
            assertEquals(1, write.exitCode)
            assertEquals(
                "CONFIRMATION_REQUIRED",
                JSONObject(write.output).getJSONObject("error").getString("code"),
            )
            val read = rpc("""{"action":"dashboard.query","params":{}}""")
            assertEquals(read.output, 0, read.exitCode)
        } finally {
            TeachTrackIntegrationSettings.setAccessMode(context, TeachTrackIntegrationSettings.MODE_OWNER_TRUSTED)
        }
    }

    /** Spec §71: an ambiguous student name fails with a stable code and a candidate list. */
    @Test fun ambiguousStudentReturnsStableCodeAndCandidates() = runBlocking {
        val service = service()
        val dao = db.dao()
        dao.upsertStudent(StudentEntity("s-a1", "重名学生"))
        dao.upsertStudent(StudentEntity("s-a2", "重名学生"))
        val result = run(service, "score.query", mapOf("studentName" to "重名学生"))
        assertTrue(!result.success)
        assertEquals("AMBIGUOUS_STUDENT", result.errorCode)
        assertTrue(result.data["candidates"]!!.contains("重名学生"))
    }

    /** Spec §71: an unknown student name fails with the stable not-found code. */
    @Test fun notFoundStudentReturnsStableCode() = runBlocking {
        val service = service()
        val result = run(service, "score.query", mapOf("studentName" to "不存在的学生"))
        assertTrue(!result.success)
        assertEquals("STUDENT_NOT_FOUND", result.errorCode)
    }

    /** Spec §71: batch per-item failures carry the SAME codes as single actions. */
    @Test fun batchItemsCarrySameErrorCodes() = runBlocking {
        val service = service()
        val dao = db.dao()
        dao.upsertStudent(StudentEntity("s-b1", "重名批次"))
        dao.upsertStudent(StudentEntity("s-b2", "重名批次"))
        val result = run(service, "batch.scores.create", mapOf("items" to """[
            {"studentName":"重名批次","score":"120","fullScore":"160"},
            {"studentName":"不存在批次","score":"120"}
        ]""".trimIndent()))
        assertTrue(result.message, result.success) // per-item failure isolation: overall still ok
        assertEquals("2", result.data["failed"])
        val items = JSONArray(result.data["items"]!!)
        assertEquals("AMBIGUOUS_STUDENT", items.getJSONObject(0).getString("code"))
        assertEquals("STUDENT_NOT_FOUND", items.getJSONObject(1).getString("code"))
    }
}
