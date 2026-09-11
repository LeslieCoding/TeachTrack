package com.leslie.teachtrack.copilot

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopilotContractsTest {
    @Test fun validatorAcceptsOnlyRegisteredActions() {
        val validator = CopilotCommandValidator(setOf("task.create"))
        assertEquals(null, validator.validate(CopilotCommand("task.create", true, emptyMap())))
        assertEquals("此操作未被允许：danger.rawSql", validator.validate(CopilotCommand("danger.rawSql", true, emptyMap())))
    }

    @Test fun fakeProviderRecognizesChineseTaskWithoutNetwork() = runBlocking {
        val candidate = FakeCopilotProvider().interpret("\u6dfb\u52a0\u4e00\u4e2a\u5f85\u529e\uff1a\u5468\u4e94\u68c0\u67e5\u4f5c\u4e1a", emptyMap())
        assertEquals("task.create", candidate.command?.action)
        assertTrue(candidate.command?.confirmationRequired == true)
    }

    @Test fun codecNormalizesProviderActionAliases() {
        assertEquals("student.create", CopilotCommandCodec.normalizeAction("students.add"))
        assertEquals("score.create", CopilotCommandCodec.normalizeAction("scores.add"))
    }

    @Test fun fakeProviderExtractsStudentAndScoreFields() = runBlocking {
        val candidate = FakeCopilotProvider().interpret("\u7ed9\u5b66\u751f\u738b\u6668\u7136\u5f55\u5165\u6210\u7ee9 126\u5206\uff0c\u6ee1\u5206 160", emptyMap())
        assertEquals("score.create", candidate.command?.action)
        assertEquals("王晨然", candidate.command?.parameters?.get("studentName"))
        assertEquals("126", candidate.command?.parameters?.get("score"))
        assertEquals("160", candidate.command?.parameters?.get("fullScore"))
    }

    @Test fun fakeProviderCoversCommonChineseOperations() = runBlocking {
        val provider = FakeCopilotProvider()
        assertEquals("student.create", provider.interpret("\u6dfb\u52a0\u5b66\u751f\u738b\u6668\u7136", emptyMap()).command?.action)
        assertEquals("course.create", provider.interpret("\u7ed9\u5b66\u751f\u738b\u6668\u7136\u8bb0\u5f55\u8bfe\u7a0b\u52a8\u6001\u7535\u8def", emptyMap()).command?.action)
        assertEquals("task.create", provider.interpret("\u7ed9\u738b\u6668\u7136\u6dfb\u52a0\u5f85\u529e\u6574\u7406\u9519\u9898", emptyMap()).command?.action)
        assertEquals("student.search", provider.interpret("\u67e5\u627e\u5b66\u751f\u738b\u6668\u7136", emptyMap()).command?.action)
        val knowledge = provider.interpret("\u628a\u77e5\u8bc6\u70b9\u52a8\u6001\u7535\u8def\u6807\u8bb0\u4e3a\u9700\u5de9\u56fa", mapOf("selectedStudentId" to "student-1"))
        assertEquals("knowledge.update", knowledge.command?.action)
        assertEquals("2", knowledge.command?.parameters?.get("level"))
    }

    @Test fun serviceRejectsUnknownActionBeforeExecution() = runBlocking {
        var executed = false
        val registry = object : CopilotActionRegistry {
            override val allowedActions = setOf("task.create")
            override suspend fun preview(command: CopilotCommand) = CopilotResult(true, "preview")
            override suspend fun execute(command: CopilotCommand): CopilotResult { executed = true; return CopilotResult(true, "ok") }
        }
        val result = CopilotService(FakeCopilotProvider(), registry).execute(CopilotCommand("danger.rawSql", true, emptyMap()), true)
        assertFalse(result.success)
        assertFalse(executed)
    }
}
