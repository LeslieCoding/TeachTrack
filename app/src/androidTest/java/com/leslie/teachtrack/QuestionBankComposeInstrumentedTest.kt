@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.leslie.teachtrack

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.leslie.teachtrack.agent.QuestionBankAgentBridge
import com.leslie.teachtrack.remote.CloudCredentials
import com.leslie.teachtrack.remote.CloudQuestionDetail
import com.leslie.teachtrack.remote.CloudQuestionInput
import com.leslie.teachtrack.remote.QuestionApi
import com.openminis.app.sandbox.PRootKernel
import com.openminis.app.sandbox.ShellExecutor
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuestionBankComposeInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val credentials get() = CloudCredentials(context)
    private lateinit var originalBaseUrl: String
    private var originalToken: String? = null
    private lateinit var originalExamText: String

    @Before
    fun preserveExistingSettings() {
        originalBaseUrl = credentials.baseUrl()
        originalToken = credentials.token()
        originalExamText = QuestionBankAgentBridge.examAnalysisText.value
        assumeFalse("Existing cloud token must be readable before running UI acceptance", credentials.hasToken() && originalToken == null)
    }

    @After
    fun restoreExistingSettings() {
        credentials.saveBaseUrl(originalBaseUrl)
        if (originalToken == null) credentials.clear() else credentials.saveToken(originalToken!!)
        QuestionBankAgentBridge.updateExamAnalysisText(originalExamText)
    }

    @Test
    fun serverSettingsRemainEditableAfterConnection() {
        openBank("http://127.0.0.1:9")
        composeRule.onNodeWithContentDescription("服务器设置").performClick()
        composeRule.onNodeWithText("连接云端题库").assertIsDisplayed()
        field("后端地址，例如 http://192.168.1.10:8080").assertIsDisplayed()
        field("Owner Token").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("连接云端题库").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("服务器设置").assertIsDisplayed()
    }

    @Test
    fun agentTextFillUsesSandboxAndRemainsEditable() {
        openBank("http://127.0.0.1:9")
        composeRule.onNodeWithText("试卷分析").performClick()
        runBlocking { PRootKernel.boot(context) }
        assertTrue("PRoot kernel must boot", PRootKernel.isBooted)

        val agentText = "1. Device Agent input 2 points"
        fillThroughAgent(agentText, "replace")
        inputWithValue(agentText).performScrollTo().assertIsDisplayed()

        val manualText = "手工修改后的题干"
        inputWithValue(agentText).performTextReplacement(manualText)
        composeRule.onNodeWithText("同步").performClick()
        composeRule.onNodeWithText("试卷分析").performClick()
        inputWithValue(manualText).performScrollTo().assertIsDisplayed()

        val appendedText = "2. Appended by Agent 4 points"
        fillThroughAgent(appendedText, "append")
        inputWithValue("$manualText\n$appendedText").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("逐题分析").assertIsNotEnabled()
    }

    @Test
    fun questionEntryRejectsDuplicatesAndRefreshesVersionConflict() {
        val api = openBank(backendBaseUrl())
        val stem = "真机选择题${System.currentTimeMillis()}：液体内部向各方向的压强有什么关系？"

        composeRule.onNodeWithContentDescription("录入题目").performClick()
        fillChoiceEditor(stem)
        composeRule.onNodeWithText("保存草稿").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("题目已保存"), 20_000)
        val created = runBlocking { api.search(stem).single() }

        composeRule.onNodeWithContentDescription("录入题目").performClick()
        fillChoiceEditor(stem)
        composeRule.onNodeWithText("保存草稿").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("题库已有完全相同的题干", substring = true), 20_000)
        assertEquals(1, runBlocking { api.search(stem).size })
        composeRule.onNodeWithText("关闭").performClick()

        field("搜索题干 / 知识点").performTextReplacement(stem)
        composeRule.onNodeWithText("搜索").performClick()
        composeRule.waitUntilExactlyOneExists(hasText(stem), 20_000)
        composeRule.onNodeWithText(stem).performClick()
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("编辑题目 · v1"), 20_000)

        val detail = runBlocking {
            val current = api.detail(created.id)
            api.update(created.id, current.toInput().copy(difficulty = 4))
        }
        assertEquals(4, detail.question.difficulty)
        field("解析").performScrollTo().performTextReplacement("本次修改应遇到版本冲突")
        composeRule.onNodeWithText("保存草稿").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("题目已被其他端修改", substring = true), 20_000)
        composeRule.onNodeWithText("刷新到最新版本（放弃本次修改）").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("编辑题目 · v2"), 20_000)
        composeRule.onNodeWithText("关闭").performClick()
    }

    @Test
    fun importsFiftyQuestionsAndBlocksTheRepeatedBatch() {
        val api = openBank(backendBaseUrl())
        val marker = "真机批量验收${System.currentTimeMillis()}"
        val rawText = (1..50).joinToString("\n\n") { index ->
            if (index == 2) {
                "$index. $marker 计算题$index：液体密度为1000 kg/m³，g取10 N/kg，深度为0.2 m，求液体压强。\n" +
                    "答案：p=ρgh=2000 Pa\n解析：使用液体压强公式。\n题型：计算题\n难度：2\n知识点：液体压强"
            } else {
                "$index. $marker 选择题$index：同种液体同一深度向各方向的压强有什么关系？${if (index == 3) "（如图，图片待补）" else ""}\n" +
                    "A. 各方向相等\nB. 各方向不等\n答案：A\n解析：同种液体同一深度各方向压强相等。\n难度：3\n知识点：液体压强"
            }
        }

        composeRule.onNodeWithContentDescription("批量导入").performClick()
        field("粘贴题目纯文本").performTextReplacement(rawText)
        composeRule.onNodeWithText("解析预览并查重").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("共 50 条 · 可导入 50 · 错误 0 · 完全重复 0"), 120_000)
        composeRule.onNodeWithText("导入 50 道题").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("导入完成：新增 50，重复跳过 0，失败 0"), 120_000)

        val imported = runBlocking { api.search(marker) }
        assertEquals(50, imported.size)
        assertEquals("CALCULATION", imported.single { it.stem.contains("计算题2：") }.questionType)
        val imageQuestion = runBlocking { api.detail(imported.single { it.stem.contains("选择题3：") }.id) }
        assertTrue(imageQuestion.abilityTags.contains("含图待补"))

        composeRule.onNodeWithText("返回修改文本").performClick()
        composeRule.onNodeWithText("解析预览并查重").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("共 50 条 · 可导入 0 · 错误 0 · 完全重复 50"), 120_000)
        composeRule.onNodeWithText("导入 0 道题").assertIsNotEnabled()
        assertEquals(50, runBlocking { api.search(marker).size })
        composeRule.onNodeWithText("关闭").performClick()
    }

    private fun backendBaseUrl(): String {
        val baseUrl = InstrumentationRegistry.getArguments().getString("teachTrackTestBaseUrl")?.trimEnd('/').orEmpty()
        assumeTrue("Pass -e teachTrackTestBaseUrl pointing to an isolated test backend", baseUrl.isNotBlank())
        assumeTrue("UI write acceptance only supports an isolated loopback backend", baseUrl.startsWith("http://127.0.0.1:") || baseUrl.startsWith("http://localhost:"))
        val token = InstrumentationRegistry.getArguments().getString("teachTrackTestToken") ?: "device-test-token"
        runBlocking {
            val api = QuestionApi(baseUrl, token)
            val subject = api.subjects().first { it.name.contains("科学") }
            if (api.knowledgePoints(subject.id).none { it.name == "液体压强" }) {
                val body = JSONObject()
                    .put("subjectId", subject.id)
                    .put("name", "液体压强")
                    .put("grade", "八上")
                    .put("chapter", "压力与压强")
                    .toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("$baseUrl/api/v1/knowledge-points")
                    .header("Authorization", "Bearer $token")
                    .post(body)
                    .build()
                OkHttpClient().newCall(request).execute().use { response ->
                    assertTrue("Test knowledge-point setup failed: HTTP ${response.code}", response.isSuccessful)
                }
            }
        }
        return baseUrl
    }

    private fun openBank(baseUrl: String): QuestionApi {
        val token = InstrumentationRegistry.getArguments().getString("teachTrackTestToken") ?: "device-test-token"
        credentials.saveBaseUrl(baseUrl)
        credentials.saveToken(token)
        composeRule.activityRule.scenario.recreate()
        if (composeRule.onAllNodesWithText("更多").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("更多").performClick()
        }
        composeRule.onNodeWithText("题库").performClick()
        composeRule.onNodeWithText("云端工作台").assertIsDisplayed()
        return QuestionApi(baseUrl, token)
    }

    private fun fillThroughAgent(text: String, mode: String) {
        val payload = JSONObject()
            .put("action", "exam.analysis.text.fill")
            .put("params", JSONObject().put("text", text).put("mode", mode))
        val result = runBlocking {
            ShellExecutor.execute(context, "teachtrack rpc --json '$payload'", timeout = 60_000)
        }
        assertEquals(result.output, 0, result.exitCode)
        val line = result.output.lineSequence().first { it.trimStart().startsWith("{") }
        assertTrue(result.output, JSONObject(line).getBoolean("ok"))
    }

    private fun field(label: String) = composeRule.onNode(
        hasSetTextAction() and (hasText(label) or hasAnyDescendant(hasText(label)))
    )

    private fun inputWithValue(value: String) = composeRule.onNode(hasSetTextAction() and hasText(value))

    private fun fillChoiceEditor(stem: String) {
        field("年级，如八上").performTextReplacement("八上")
        field("题干").performScrollTo().performTextReplacement(stem)
        composeRule.onAllNodesWithText("删除").onFirst().performScrollTo().performClick()
        composeRule.onAllNodesWithText("删除").onFirst().performScrollTo().performClick()
        field("选项 A").performScrollTo().performTextReplacement("各方向相等")
        field("选项 B").performTextReplacement("各方向不等")
        field("标准答案").performScrollTo().performTextReplacement("A")
        field("解析").performScrollTo().performTextReplacement("同种液体同一深度，各方向压强相等。")
        field("搜索并选择知识点").performScrollTo().performTextReplacement("液体压强")
        composeRule.waitUntilExactlyOneExists(
            hasText("液体压强", substring = true) and androidx.compose.ui.test.hasClickAction(),
            20_000,
        )
        composeRule.onNode(hasText("液体压强", substring = true) and androidx.compose.ui.test.hasClickAction()).performClick()
    }

    private fun CloudQuestionDetail.toInput() = CloudQuestionInput(
        subjectId = question.subjectId,
        grade = question.grade,
        textbookVersion = question.textbookVersion,
        questionType = question.questionType,
        difficulty = question.difficulty,
        stem = question.stem,
        sourceType = question.sourceType,
        sourceName = question.sourceName,
        reviewStatus = question.reviewStatus,
        options = options,
        standardAnswer = standardAnswer,
        analysis = analysis,
        scoringPoints = scoringPoints,
        solutionSteps = solutionSteps,
        knowledgePointIds = knowledge.map { it.knowledgePointId },
        abilityTags = abilityTags,
        baseVersion = question.version,
    )
}
