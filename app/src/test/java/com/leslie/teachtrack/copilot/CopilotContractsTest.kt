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
        assertEquals("此操作未被允许", validator.validate(CopilotCommand("danger.rawSql", true, emptyMap())))
    }

    @Test fun fakeProviderRecognizesOfflineTaskWithoutNetwork() = runBlocking {
        val candidate = FakeCopilotProvider().interpret("添加一个待办：周五检查作业", emptyMap())
        assertEquals("task.create", candidate.command?.action)
        assertTrue(candidate.command?.confirmationRequired == true)
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
