package com.leslie.teachtrack.copilot

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import com.leslie.teachtrack.core.model.StudentEntity
import com.leslie.teachtrack.core.model.CourseSessionEntity
import com.leslie.teachtrack.core.model.HomeworkEntity

class CopilotContractsTest {
    @Test fun validatorAcceptsOnlyRegisteredActions() {
        val validator = CopilotCommandValidator(setOf("task.create"))
        assertNull(validator.validate(CopilotCommand("task.create", true, emptyMap())))
        assertTrue(validator.validate(CopilotCommand("danger.rawSql", true, emptyMap()))!!.contains("danger.rawSql"))
    }
    @Test fun fakeProviderRecognizesChineseTaskWithoutNetwork() = runBlocking {
        val candidate = FakeCopilotProvider().interpret("\u6dfb\u52a0\u4e00\u4e2a\u5f85\u529e\uff1a\u5468\u4e94\u68c0\u67e5\u4f5c\u4e1a", emptyMap())
        assertEquals("task.create", candidate.command?.action); assertTrue(candidate.command?.confirmationRequired == true)
    }
    @Test fun codecNormalizesProviderActionAliases() {
        assertEquals("student.create", CopilotCommandCodec.normalizeAction("students.add")); assertEquals("score.create", CopilotCommandCodec.normalizeAction("scores.add"))
    }
    @Test fun fakeProviderExtractsStudentAndScoreFields() = runBlocking {
        val command = FakeCopilotProvider().interpret("\u7ed9\u5b66\u751f\u738b\u6668\u7136\u5f55\u5165\u6210\u7ee9 126\u5206\uff0c\u6ee1\u5206 160", emptyMap()).command!!
        assertEquals("score.create", command.action); assertEquals("\u738b\u6668\u7136", command.parameters["studentName"]); assertEquals("126", command.parameters["score"]); assertEquals("160", command.parameters["fullScore"])
    }
    @Test fun fakeProviderCoversCommonChineseOperations() = runBlocking {
        val provider = FakeCopilotProvider()
        assertEquals("student.create", provider.interpret("\u6dfb\u52a0\u5b66\u751f\u738b\u6668\u7136", emptyMap()).command?.action)
        assertEquals("course.create", provider.interpret("\u7ed9\u5b66\u751f\u738b\u6668\u7136\u8bb0\u5f55\u8bfe\u7a0b\u52a8\u6001\u7535\u8def", emptyMap()).command?.action)
        assertEquals("task.create", provider.interpret("\u7ed9\u738b\u6668\u7136\u6dfb\u52a0\u5f85\u529e\u6574\u7406\u9519\u9898", emptyMap()).command?.action)
        assertEquals("student.search", provider.interpret("\u67e5\u627e\u5b66\u751f\u738b\u6668\u7136", emptyMap()).command?.action)
        val knowledge = provider.interpret("\u628a\u77e5\u8bc6\u70b9\u52a8\u6001\u7535\u8def\u6807\u8bb0\u4e3a\u9700\u5de9\u56fa", mapOf("selectedStudentId" to "student-1"))
        assertEquals("knowledge.update", knowledge.command?.action); assertEquals("2", knowledge.command?.parameters?.get("level"))
        assertEquals("problem.create", provider.interpret("\u8bb0\u5f55\u4e00\u4e2a\u9519\u9898\u52a8\u6001\u7535\u8def", emptyMap()).command?.action)
        assertEquals("tag.create", provider.interpret("\u521b\u5efa\u6807\u7b7e\u4e2d\u8003\u51b2\u523a", emptyMap()).command?.action)
        assertEquals("dashboard.query", provider.interpret("\u67e5\u770b\u9996\u9875\u6570\u636e", emptyMap()).command?.action)
    }
    @Test fun readOnlyCommandsDoNotRequireConfirmation() = runBlocking {
        val command = FakeCopilotProvider().interpret("\u67e5\u770b\u738b\u6668\u7136\u6700\u8fd1\u6210\u7ee9", emptyMap()).command!!
        assertEquals("score.query", command.action); assertFalse(command.confirmationRequired)
    }
    @Test fun serviceRejectsUnknownActionBeforeExecution() = runBlocking {
        var executed = false
        val registry = object : CopilotActionRegistry {
            override val allowedActions = setOf("task.create")
            override suspend fun preview(command: CopilotCommand) = CopilotResult(true, "preview")
            override suspend fun execute(command: CopilotCommand): CopilotResult { executed = true; return CopilotResult(true, "ok") }
        }
        val result = CopilotService(FakeCopilotProvider(), registry).execute(CopilotCommand("danger.rawSql", true, emptyMap()), true)
        assertFalse(result.success); assertFalse(executed)
    }
    @Test fun typedCommandValidatorRejectsImpossibleScore() {
        assertEquals("成绩必须在 0 到满分之间", AppCommandValidator.validate(AppCommand.AddScore(mapOf("studentName" to "\u695a\u742a", "score" to "170", "fullScore" to "160"))))
    }
    @Test fun typedCommandValidatorRequiresCourseContent() {
        assertEquals("缺少必要字段：content", AppCommandValidator.validate(AppCommand.CreateCourse(mapOf("studentName" to "\u6668\u7136"))))
    }

    @Test fun courseRequestProducesOrderedCommandPlan() = runBlocking {
        val candidate = FakeCopilotProvider().interpret(
            "\u7ed9\u738b\u6668\u7136\u8bb0\u5f55\u4eca\u5929\u7684\u8bfe\uff0c\u8bb2\u52a8\u6001\u7535\u8def\uff0c\u9700\u8981\u7ee7\u7eed\u5de9\u56fa\uff0c\u5e03\u7f6e\u4f5c\u4e1a\uff0c\u4e0b\u8282\u8bfe\u7ee7\u7eed\u590d\u4e60\u6ed1\u52a8\u53d8\u963b\u5668\u8303\u56f4\u9898",
            emptyMap()
        )
        assertEquals("course.create", candidate.commands[0].action)
        assertEquals("knowledge.update", candidate.commands[1].action)
        assertEquals("homework.create", candidate.commands[2].action)
        assertEquals("lesson_plan.create", candidate.commands[3].action)
    }

    @Test fun organizeAllStudentDataUsesReadOnlyOrganizationPlan() = runBlocking {
        val command = FakeCopilotProvider().interpret("整理一下所有学生的数据", emptyMap()).command!!
        assertEquals("student.data.organize.preview", command.action)
        assertFalse(command.confirmationRequired)
    }

    @Test fun explicitOrganizationFollowUpRequiresConfirmation() = runBlocking {
        val command = FakeCopilotProvider().interpret("把所有学生整理结果生成跟进待办", emptyMap()).command!!
        assertEquals("student.followup.tasks.create", command.action)
        assertTrue(command.confirmationRequired)
    }

    @Test fun fakeProviderRecognizesFullCoverageActions() = runBlocking {
        val provider = FakeCopilotProvider()
        assertEquals("knowledge.create", provider.interpret("新建知识点：二次函数", emptyMap()).command?.action)
        assertEquals("task.status.update", provider.interpret("把检查作业那条待办标记为完成", emptyMap()).command?.action)
        assertEquals("parent_contact.create", provider.interpret("给学生王晨然添加家长联系人", emptyMap()).command?.action)
        assertEquals("template.list", provider.interpret("查看模板列表", emptyMap()).command?.action)
        assertEquals("tag.assign", provider.interpret("给学生王晨然打上标签中考冲刺", emptyMap()).command?.action)
        assertEquals("attachment.query", provider.interpret("查看王晨然有哪些附件", emptyMap()).command?.action)
        assertEquals("backup.export", provider.interpret("导出一份数据备份", emptyMap()).command?.action)
        assertEquals("backup.list", provider.interpret("查看有哪些备份", emptyMap()).command?.action)
        assertEquals("backup.restore", provider.interpret("恢复上次的备份", emptyMap()).command?.action)
        assertEquals("csv.export.scores", provider.interpret("把成绩导出成csv表格", emptyMap()).command?.action)
        assertEquals("workspace.status.query", provider.interpret("查看工作区状态", emptyMap()).command?.action)
        assertEquals("workspace.check", provider.interpret("检查工作区完整性", emptyMap()).command?.action)
        assertEquals("workspace.sync.scan", provider.interpret("扫描工作区同步事件", emptyMap()).command?.action)
        assertEquals("workspace.conflict.list", provider.interpret("查看同步冲突列表", emptyMap()).command?.action)
        assertEquals("student.overview.query", provider.interpret("了解一下王晨然的整体情况", emptyMap()).command?.action)
    }

    @Test fun fullCoverageActionsGetCorrectConfirmationGating() = runBlocking {
        val provider = FakeCopilotProvider()
        assertTrue(provider.interpret("新建知识点：二次函数", emptyMap()).command!!.confirmationRequired)
        assertFalse(provider.interpret("查看模板列表", emptyMap()).command!!.confirmationRequired)
        assertFalse(provider.interpret("查看工作区状态", emptyMap()).command!!.confirmationRequired)
        assertTrue(provider.interpret("导出一份数据备份", emptyMap()).command!!.confirmationRequired)
        assertFalse(provider.interpret("了解一下王晨然的整体情况", emptyMap()).command!!.confirmationRequired)
    }

    @Test fun taskCreationPhraseIsNotMisroutedToStatusUpdate() = runBlocking {
        // A bare "完成" inside a new task's own title must not steal task.create's territory;
        // only an explicit 标记/搞定 status-transition cue should route to task.status.update.
        assertEquals("task.create", FakeCopilotProvider().interpret("添加待办：完成作业批改", emptyMap()).command?.action)
    }

    @Test fun studentNameExtractionWorksForNewTriggerVerbs() = runBlocking {
        assertEquals("王晨然", FakeCopilotProvider().interpret("给学生王晨然打上标签中考冲刺", emptyMap()).command?.parameters?.get("studentName"))
        assertEquals("王晨然", FakeCopilotProvider().interpret("查看王晨然有哪些附件", emptyMap()).command?.parameters?.get("studentName"))
        assertEquals("王晨然", FakeCopilotProvider().interpret("了解一下王晨然的整体情况", emptyMap()).command?.parameters?.get("studentName"))
    }

    @Test fun organizationReportUsesOnlyActualDataAndDoesNotInventProfileFields() {
        val snapshot = StudentDataOrganizationSnapshot(
            students = listOf(StudentEntity("s1", "王晨然", school = "")),
            courses = listOf(CourseSessionEntity("c1", "s1", "2026-09-11", nextLessonPlan = "")),
            homework = listOf(HomeworkEntity("h1", "s1", name = "作业", assignedDate = "2026-09-11", status = "未完成")),
            tasks = emptyList(), problems = emptyList(), knowledge = emptyList(), progress = emptyList()
        )
        val report = StudentDataOrganizer.analyze(snapshot)
        assertEquals(1, report.studentsScanned)
        assertTrue(report.findings.single().facts.any { it.contains("未完成作业") })
        assertFalse(report.toDisplayText().contains("学校为"))
    }
}
