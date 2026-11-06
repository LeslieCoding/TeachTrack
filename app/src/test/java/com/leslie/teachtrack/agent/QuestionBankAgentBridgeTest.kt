package com.leslie.teachtrack.agent

import com.leslie.teachtrack.copilot.CopilotCommand
import com.leslie.teachtrack.copilot.CopilotCommandValidator
import com.leslie.teachtrack.copilot.LocalCopilotActionRegistry
import com.leslie.teachtrack.core.database.TeachTrackDao
import com.leslie.teachtrack.data.TeachTrackRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuestionBankAgentBridgeTest {
    private val registry by lazy {
        val dao = Proxy.newProxyInstance(
            TeachTrackDao::class.java.classLoader,
            arrayOf(TeachTrackDao::class.java)
        ) { _, _, _ -> null } as TeachTrackDao
        LocalCopilotActionRegistry(TeachTrackRepository(dao))
    }

    @Before fun resetText() {
        QuestionBankAgentBridge.updateExamAnalysisText("")
    }

    @Test fun agentActionFillsTextAndRequestsExamPanel() = runBlocking {
        val requests = QuestionBankAgentBridge.openExamAnalysisRequests.value
        val result = registry.execute(
            CopilotCommand(
                action = "exam.analysis.text.fill",
                confirmationRequired = false,
                parameters = mapOf("text" to "1. 液体压强选择题 2分")
            )
        )
        assertTrue(result.success)
        assertEquals("1. 液体压强选择题 2分", QuestionBankAgentBridge.examAnalysisText.value)
        assertEquals(requests + 1, QuestionBankAgentBridge.openExamAnalysisRequests.value)
        assertTrue(result.message.contains("尚未执行逐题分析"))
    }

    @Test fun appendKeepsExistingTextAndManualEditsDoNotNavigate() = runBlocking {
        QuestionBankAgentBridge.updateExamAnalysisText("第一题")
        val requests = QuestionBankAgentBridge.openExamAnalysisRequests.value
        val result = registry.execute(
            CopilotCommand(
                action = "exam.analysis.text.fill",
                confirmationRequired = false,
                parameters = mapOf("content" to "第二题", "mode" to "append")
            )
        )
        assertTrue(result.success)
        assertEquals("第一题\n第二题", QuestionBankAgentBridge.examAnalysisText.value)
        QuestionBankAgentBridge.updateExamAnalysisText("手工修改")
        assertEquals(requests + 1, QuestionBankAgentBridge.openExamAnalysisRequests.value)
        assertEquals("手工修改", QuestionBankAgentBridge.examAnalysisText.value)
    }

    @Test fun invalidInputDoesNotTouchTextBox() = runBlocking {
        QuestionBankAgentBridge.updateExamAnalysisText("保留原文")
        val blank = registry.execute(
            CopilotCommand("exam.analysis.text.fill", false, mapOf("text" to " "))
        )
        val badMode = registry.execute(
            CopilotCommand("exam.analysis.text.fill", false, mapOf("text" to "新内容", "mode" to "overwrite"))
        )
        assertFalse(blank.success)
        assertFalse(badMode.success)
        assertEquals("保留原文", QuestionBankAgentBridge.examAnalysisText.value)
    }

    @Test fun examTextAcceptsLongOcrPayloadWithoutRelaxingOtherActions() {
        val validator = CopilotCommandValidator(setOf("exam.analysis.text.fill", "task.create"))
        assertNull(validator.validate(CopilotCommand(
            "exam.analysis.text.fill", false, mapOf("text" to "题".repeat(5000))
        )))
        assertTrue(validator.validate(CopilotCommand(
            "task.create", true, mapOf("title" to "题".repeat(4001))
        ))!!.contains("过长"))
    }
}
