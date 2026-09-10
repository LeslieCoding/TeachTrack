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
