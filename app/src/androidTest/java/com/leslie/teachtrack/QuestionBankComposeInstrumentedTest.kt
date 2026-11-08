@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.leslie.teachtrack

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.leslie.teachtrack.agent.QuestionBankAgentBridge
import com.leslie.teachtrack.agent.TeachTrackAppContext
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
    @get:Rule
    val intentsRule = IntentsRule()

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
        confirmDuplicateIfAsked("仍要存草稿")
        composeRule.waitUntilExactlyOneExists(hasText("题目已保存"), 20_000)
        val created = runBlocking { api.search(stem).single() }

        composeRule.onNodeWithContentDescription("录入题目").performClick()
        fillChoiceEditor(stem)
        composeRule.onNodeWithText("保存草稿").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("题库已有完全相同的题干", substring = true), 20_000)
        assertEquals(1, runBlocking { api.search(stem).size })
        composeRule.onNodeWithText("关闭").performClick()
    }

    @Test
    fun searchButtonFindsQuestions() {
        openBank(backendBaseUrl())
        field("搜索题干 / 知识点").performTextReplacement("UI 练习题库题目")
        hideKeyboard()
        composeRule.onNodeWithText("搜索").performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText("UI 练习题库题目", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
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

    @Test
    fun filterBulkAndDetailButtonsWork() {
        val base = backendBaseUrl()
        val seedApi = QuestionApi(base, testToken())
        val stamp = System.currentTimeMillis()
        val stems = listOf("按钮验收 $stamp 论述题一", "按钮验收 $stamp 论述题二")
        val subject = runBlocking { seedApi.subjects().first { it.name.contains("科学") } }
        val created = stems.map { stem ->
            runBlocking {
                seedApi.create(
                    CloudQuestionInput(
                        subjectId = subject.id, grade = "八上", textbookVersion = "", questionType = "ESSAY",
                        difficulty = 3, stem = stem, sourceType = "SELF_CREATED", sourceName = "",
                        reviewStatus = "DRAFT", options = emptyList(), standardAnswer = "", analysis = ""
                    )
                ).question
            }
        }
        val api = openBank(base)
        composeRule.waitUntilExactlyOneExists(hasText(stems.first()), 20_000)

        composeRule.onNodeWithContentDescription("题库筛选").performClick()
        composeRule.onNodeWithText("题库筛选").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithContentDescription("题库筛选").performClick()
        composeRule.onNodeWithTag("filter-type-row").performScrollToIndex(6)
        composeRule.onNodeWithTag("filter-type-ESSAY").performClick()
        composeRule.onNodeWithText("应用筛选").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("已启用 1 个筛选"), 10_000)
        composeRule.waitUntilExactlyOneExists(hasText(stems.first()), 20_000)

        composeRule.onNodeWithContentDescription("批量操作").performClick()
        composeRule.onNodeWithText(stems[0]).performScrollTo().performClick()
        composeRule.onNodeWithText(stems[1]).performScrollTo().performClick()
        composeRule.waitUntilExactlyOneExists(hasText("已选 2"), 10_000)
        composeRule.onNodeWithText("通过").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("批量操作完成", substring = true), 20_000)
        created.forEach { assertEquals("APPROVED", runBlocking { api.detail(it.id).question.reviewStatus }) }

        composeRule.onNodeWithContentDescription("批量操作").performClick()
        composeRule.onNodeWithText(stems[0]).performScrollTo().performClick()
        composeRule.onNodeWithText(stems[1]).performScrollTo().performClick()
        composeRule.onNodeWithText("拒绝").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("批量操作完成", substring = true), 20_000)
        created.forEach { assertEquals("REJECTED", runBlocking { api.detail(it.id).question.reviewStatus }) }

        composeRule.onNodeWithContentDescription("批量操作").performClick()
        composeRule.onNodeWithText(stems[0]).performScrollTo().performClick()
        composeRule.onNodeWithText(stems[1]).performScrollTo().performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("批量删除题目").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        assertEquals(2, runBlocking { api.search("按钮验收 $stamp").size })

        composeRule.onNodeWithText("删除").performClick()
        composeRule.onAllNodesWithText("删除").onLast().performClick()
        composeRule.waitUntil(20_000) { runBlocking { api.search("按钮验收 $stamp").isEmpty() } }
        composeRule.onNodeWithText("清除筛选").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("已启用 1 个筛选").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun editorApprovalKnowledgeAndOptionButtonsWork() {
        val api = openBank(backendBaseUrl())
        val stem = "按钮验收编辑 ${System.currentTimeMillis()}：同种液体同一深度压强"
        val point = "按钮知识点${System.currentTimeMillis()}"

        composeRule.onNodeWithContentDescription("录入题目").performClick()
        composeRule.onNodeWithText("录入题目").assertIsDisplayed()
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.onNodeWithContentDescription("录入题目").performClick()

        composeRule.onNodeWithText("新建").performScrollTo().performClick()
        composeRule.waitUntilExactlyOneExists(hasText("新建知识点"), 10_000)
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("新建").performScrollTo().performClick()
        field("知识点名称").performTextReplacement(point)
        field("章节，如第三章").performTextReplacement("按钮测试章节")
        field("小节，如第 7 节").performTextReplacement("按钮测试小节")
        composeRule.onNodeWithText("创建并选中").performClick()
        composeRule.waitUntilExactlyOneExists(hasText(point), 20_000)

        fillChoiceEditor(stem)
        composeRule.onNodeWithText("添加选项").performScrollTo().performClick()
        field("选项 C").performScrollTo().performTextReplacement("各方向可能变化")
        hideKeyboard()
        composeRule.onNodeWithText("保存并审核通过").performClick()
        confirmDuplicateIfAsked("仍要保存通过")
        composeRule.waitUntilExactlyOneExists(hasText("题目已保存"), 20_000)
        val saved = runBlocking { api.search(stem).single() }
        assertEquals("APPROVED", saved.reviewStatus)
    }

    @Test
    fun csvImportAutoApprovesAndBackButtonWorks() {
        val api = openBank(backendBaseUrl())
        val stem = "按钮验收CSV ${System.currentTimeMillis()}，液体压强"
        composeRule.onNodeWithContentDescription("批量导入").performClick()
        composeRule.onNodeWithText("CSV").performClick()
        field("粘贴 CSV").performTextReplacement(
            "题干,optionA,optionB,答案,解析,题型,难度,知识点\n" +
                "\"$stem，深度越大压强如何变化？\",\"变大\",\"不变\",\"A\",\"p=ρgh\",\"单选\",2,\"液体压强\""
        )
        composeRule.onNodeWithText("解析预览并查重").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("共 1 条 · 可导入 1 · 错误 0 · 完全重复 0"), 30_000)
        composeRule.onNodeWithText("返回修改文本").performClick()
        composeRule.onNodeWithText("解析预览并查重").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("共 1 条 · 可导入 1 · 错误 0 · 完全重复 0"), 30_000)
        composeRule.onNodeWithText("导入 1 道题").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("导入完成：新增 1，重复跳过 0，失败 0"), 30_000)
        composeRule.onNodeWithText("关闭").performClick()
        val imported = runBlocking { api.search(stem).single() }
        assertEquals("APPROVED", imported.reviewStatus)
    }

    @Test
    fun detailApproveRejectCloseButtonsWork() {
        val base = backendBaseUrl()
        val seedApi = QuestionApi(base, testToken())
        val subject = runBlocking { seedApi.subjects().first { it.name.contains("科学") } }
        val stem = "按钮验收详情 ${System.currentTimeMillis()} 同种液体压强"
        val created = runBlocking {
            seedApi.create(
                CloudQuestionInput(
                    subjectId = subject.id, grade = "八上", textbookVersion = "", questionType = "ESSAY",
                    difficulty = 3, stem = stem, sourceType = "SELF_CREATED", sourceName = "",
                    reviewStatus = "DRAFT", options = emptyList(), standardAnswer = "", analysis = ""
                )
            ).question
        }
        val api = openBank(base)
        composeRule.waitUntilExactlyOneExists(hasText(stem), 20_000)
        composeRule.onNodeWithText(stem).performClick()
        composeRule.waitUntilExactlyOneExists(hasText("题目详情"), 10_000)
        composeRule.onNodeWithText("通过").performClick()
        composeRule.waitUntil(20_000) { runBlocking { api.detail(created.id).question.reviewStatus == "APPROVED" } }

        composeRule.waitUntilExactlyOneExists(hasText(stem), 20_000)
        composeRule.onNodeWithText(stem).performClick()
        composeRule.onNodeWithText("拒绝").performClick()
        composeRule.waitUntil(20_000) { runBlocking { api.detail(created.id).question.reviewStatus == "REJECTED" } }

        composeRule.waitUntilExactlyOneExists(hasText(stem), 20_000)
        composeRule.onNodeWithText(stem).performClick()
        composeRule.waitUntilExactlyOneExists(hasText("题目详情"), 10_000)
        composeRule.waitUntilExactlyOneExists(hasText("关闭"), 10_000)
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.onNodeWithText("题目详情").assertDoesNotExist()
    }

    @Test
    fun syncExamAndPracticeButtonsWork() {
        val api = openBank(backendBaseUrl())
        pullRoster()

        composeRule.onNodeWithText("试卷分析").performClick()
        val studentTag = firstStudentTag()
        composeRule.waitUntilExactlyOneExists(hasTestTag(studentTag), 20_000)
        composeRule.onNodeWithTag(studentTag).performClick()
        composeRule.onNodeWithText("初中科学").performClick()
        field("试卷名称").performTextReplacement("按钮验收期中卷")
        field("考试日期 yyyy-MM-dd").performTextReplacement("2026-11-07")
        composeRule.onNodeWithText("导入试卷").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("重新导入"), 20_000)
        field("题目文本（Agent、OCR 或手工）").performTextReplacement(
            "1. 液体压强选择题 2分\n2. 液体压强计算题 4分"
        )
        hideKeyboard()
        composeRule.onNodeWithText("逐题分析").performScrollTo().performClick()
        composeRule.waitUntilExactlyOneExists(hasText("状态：AI 已解析，待审核", substring = true), 30_000)
        composeRule.onNodeWithText("教师审核通过并入库").performScrollTo().performClick()
        composeRule.waitUntilExactlyOneExists(hasText("状态：已审核", substring = true), 30_000)

        composeRule.onNodeWithText("精准练习").performClick()
        composeRule.waitUntilExactlyOneExists(hasTestTag(studentTag), 20_000)
        composeRule.onNodeWithTag(studentTag).performClick()
        composeRule.onNodeWithText("初中科学").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("该学科可用已审核题目", substring = true), 20_000)
        composeRule.onNodeWithText("生成练习草稿").performScrollTo().performClick()
        composeRule.waitUntilExactlyOneExists(hasText("审核通过"), 30_000)
        composeRule.onNodeWithText("审核通过").performScrollTo().performClick()
        composeRule.waitUntilExactlyOneExists(hasText("提交并自动判分"), 30_000)

        val answerFields = composeRule.onAllNodes(
            hasSetTextAction() and (hasText("学生答案") or hasAnyDescendant(hasText("学生答案")))
        ).fetchSemanticsNodes()
        assertTrue("practice answer fields should exist", answerFields.isNotEmpty())
        repeat(answerFields.size) { index ->
            composeRule.onAllNodes(
                hasSetTextAction() and (hasText("学生答案") or hasAnyDescendant(hasText("学生答案")))
            )[index].performTextReplacement("A")
        }
        composeRule.onNodeWithText("提交并自动判分").performScrollTo().performClick()
        composeRule.waitUntilExactlyOneExists(hasText("练习已提交并自动判分", substring = true), 30_000)

        intending(hasAction(Intent.ACTION_VIEW)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        listOf("学生版 PDF", "教师版 PDF", "学生版 DOCX", "教师版 DOCX").forEach { label ->
            composeRule.onNodeWithText(label).performScrollTo().performClick()
            composeRule.waitUntil(30_000) {
                composeRule.onAllNodes(hasText(label) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
        }
        composeRule.waitForIdle()
        assertTrue(Intents.getIntents().count { it.action == Intent.ACTION_VIEW } >= 4)
    }

    @Test
    fun bulkSelectAllAndFilterGroupsWork() {
        val api = openBank(backendBaseUrl())
        val subject = runBlocking { api.subjects().first { it.name.contains("科学") } }

        composeRule.onNodeWithContentDescription("批量操作").performClick()
        composeRule.onNodeWithText("通过").assertIsNotEnabled()
        composeRule.onNodeWithText("拒绝").assertIsNotEnabled()
        composeRule.onNodeWithText("全选").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("取消全选"), 10_000)
        composeRule.onNodeWithText("通过").assertIsEnabled()
        composeRule.onNodeWithText("取消全选").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("全选"), 10_000)
        composeRule.onNodeWithText("通过").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("批量操作").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("题库筛选").performClick()
        composeRule.onNodeWithTag("filter-subject-${subject.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("filter-grade-八上").performScrollTo().performClick()
        composeRule.onNodeWithTag("filter-difficulty-3").performScrollTo().performClick()
        composeRule.onNodeWithText("应用筛选").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("已启用 3 个筛选"), 20_000)
        field("搜索题干 / 知识点").performTextReplacement("UI 练习题库题目")
        hideKeyboard()
        composeRule.onNodeWithText("搜索").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("UI 练习题库题目 1：同种液体同一深度压强"), 20_000)

        composeRule.onNodeWithContentDescription("题库筛选").performClick()
        composeRule.onNodeWithText("清除").performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText("已启用 3 个筛选").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun detailEditAndImageUploadWork() {
        val base = backendBaseUrl()
        val seed = QuestionApi(base, testToken())
        val subject = runBlocking { seed.subjects().first { it.name.contains("科学") } }
        val stem = "按钮验收题图 ${System.currentTimeMillis()} 液体压强实验图"
        val created = runBlocking {
            seed.create(
                CloudQuestionInput(
                    subjectId = subject.id, grade = "八上", textbookVersion = "", questionType = "SINGLE_CHOICE",
                    difficulty = 2, stem = stem, sourceType = "SELF_CREATED", sourceName = "",
                    reviewStatus = "DRAFT",
                    options = listOf("A" to "相等", "B" to "不等"),
                    standardAnswer = "A", analysis = "题图验收"
                )
            ).question
        }
        val api = openBank(base)
        composeRule.waitUntilExactlyOneExists(hasText(stem), 20_000)

        composeRule.onNodeWithText(stem).performScrollTo().performClick()
        composeRule.waitUntilExactlyOneExists(hasText("题目详情"), 10_000)
        composeRule.onNodeWithText("编辑").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("编辑题目", substring = true), 10_000)
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("编辑题目", substring = true).fetchSemanticsNodes().isEmpty()
        }

        val png = writeTinyPng()
        intending(hasAction(Intent.ACTION_GET_CONTENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(png))))
        if (composeRule.onAllNodesWithText("题目详情").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithText(stem).performScrollTo().performClick()
        }
        composeRule.onNodeWithText("添加题图").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("题图已上传"), 30_000)
        assertEquals(1, runBlocking { api.images(created.id) }.size)
    }

    @Test
    fun examPanelReimportButtonWorks() {
        openBank(backendBaseUrl())
        pullRoster()
        composeRule.onNodeWithText("试卷分析").performClick()
        val studentTag = firstStudentTag()
        composeRule.waitUntilExactlyOneExists(hasTestTag(studentTag), 20_000)
        composeRule.onNodeWithTag(studentTag).performClick()
        composeRule.onNodeWithText("初中科学").performClick()
        field("试卷名称").performTextReplacement("按钮验收重导入卷")
        field("考试日期 yyyy-MM-dd").performTextReplacement("2026-11-07")
        composeRule.onNodeWithText("导入试卷").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("重新导入"), 20_000)
        composeRule.onNodeWithText("重新导入").performScrollTo().performClick()
        composeRule.waitUntilExactlyOneExists(hasText("已导入"), 20_000)
        composeRule.waitForIdle()
        assertTrue(
            "重新导入不应报错",
            composeRule.onAllNodesWithText("导入失败", substring = true).fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            "重新导入后仍应显示重新导入入口",
            composeRule.onAllNodesWithText("重新导入").fetchSemanticsNodes().isNotEmpty()
        )
    }

    private fun writeTinyPng(): java.io.File {
        val bytes = android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==",
            android.util.Base64.DEFAULT
        )
        val file = java.io.File(context.cacheDir, "button-acceptance.png")
        file.writeBytes(bytes)
        return file
    }

    @Test
    fun importDraftAndSourceChipsWork() {
        val api = openBank(backendBaseUrl())
        val stem = "按钮验收草稿导入 ${System.currentTimeMillis()}，液体压强随深度变化"
        composeRule.onNodeWithContentDescription("批量导入").performClick()
        field("默认年级").performTextReplacement("九上")
        field("粘贴题目纯文本").performTextReplacement(
            listOf("$stem", "A. 变大", "B. 不变", "答案：A", "解析：p=ρgh", "难度：2").joinToString("\n")
        )
        field("来源名称（可选）").performScrollTo()
        composeRule.onNodeWithText("学生试卷").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("学生试卷").assertIsSelected()
        composeRule.onNodeWithText("导入为草稿").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("导入为草稿").assertIsSelected()
        composeRule.onNodeWithText("解析预览并查重").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("共 1 条 · 可导入 1 · 错误 0 · 完全重复 0"), 30_000)
        composeRule.onNodeWithText("导入 1 道题").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("导入完成：新增 1，重复跳过 0，失败 0"), 30_000)
        val imported = runBlocking { api.search(stem).single() }
        assertEquals("DRAFT", imported.reviewStatus)
        assertEquals("九上", imported.grade)
        assertEquals("STUDENT_EXAM", imported.sourceType)
    }

    @Test
    fun questionBankPaginatesLargeResult() {
        val base = backendBaseUrl()
        val seed = QuestionApi(base, testToken())
        val subject = runBlocking { seed.subjects().first { it.name.contains("科学") } }
        val marker = "分页验收${System.currentTimeMillis()}"
        runBlocking {
            repeat(120) { index ->
                seed.create(
                    CloudQuestionInput(
                        subjectId = subject.id, grade = "八上", textbookVersion = "", questionType = "SINGLE_CHOICE",
                        difficulty = 2, stem = "$marker 第${index + 1}题：液体压强随深度增大",
                        sourceType = "SELF_CREATED", sourceName = "", reviewStatus = "DRAFT",
                        options = listOf("A" to "增大", "B" to "减小"), standardAnswer = "A", analysis = "p=ρgh"
                    )
                )
            }
        }
        openBank(base)
        field("搜索题干 / 知识点").performTextReplacement(marker)
        hideKeyboard()
        composeRule.onNodeWithText("搜索").performClick()
        composeRule.waitUntil(90_000) {
            composeRule.onAllNodesWithTag("question-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("question-list").performScrollToIndex(99)
        composeRule.waitUntilExactlyOneExists(hasText("加载更多（已显示 100 / 共 120）"), 90_000)
        composeRule.onNodeWithText("加载更多（已显示 100 / 共 120）").performScrollTo().performClick()
        composeRule.waitUntil(90_000) {
            composeRule.onAllNodesWithText("加载更多", substring = true).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("question-list").performScrollToIndex(119)
        composeRule.waitUntil(30_000) {
            composeRule.onAllNodes(hasText(marker, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun firstStudentTag(): String {
        val first = runBlocking {
            TeachTrackAppContext.database(context).dao().allStudents().maxByOrNull { it.updatedAt }
        } ?: error("No local students after pull")
        return "student-chip-${first.id}"
    }

    private fun createEssayThroughUi(
        api: QuestionApi,
        stem: String,
        approved: Boolean
    ): CloudQuestionDetail {
        composeRule.onNodeWithContentDescription("录入题目").performClick()
        field("年级，如八上").performTextReplacement("八上")
        composeRule.onNodeWithTag("editor-type-row").performScrollToIndex(6)
        composeRule.onNodeWithTag("editor-type-ESSAY").performClick()
        field("题干").performScrollTo().performTextReplacement(stem)
        field("解析").performScrollTo().performTextReplacement("按钮验收解析")
        hideKeyboard()
        composeRule.onNodeWithText(if (approved) "保存并审核通过" else "保存草稿").performClick()
        confirmDuplicateIfAsked(if (approved) "仍要保存通过" else "仍要存草稿")
        composeRule.waitUntilExactlyOneExists(hasText("题目已保存"), 20_000)
        val question = runBlocking { api.search(stem).single() }
        return runBlocking { api.detail(question.id) }
    }

    private fun confirmDuplicateIfAsked(label: String) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val asked = composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
            if (asked) {
                runCatching { composeRule.onNodeWithText(label).performScrollTo() }
                composeRule.onNodeWithText(label).performClick()
                return
            }
            Thread.sleep(200)
        }
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(InputMethodManager::class.java)
        composeRule.activity.runOnUiThread {
            imm.hideSoftInputFromWindow(composeRule.activity.window.decorView.windowToken, 0)
        }
        composeRule.waitForIdle()
    }

    private fun pullRoster() {
        composeRule.onNodeWithText("同步").performClick()
        composeRule.onAllNodesWithContentDescription("刷新").onLast().performClick()
        composeRule.onNodeWithText("立即同步").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("已排队", substring = true), 10_000)
        composeRule.onNodeWithText("从云端拉取学生 / 课堂").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("已拉取学生", substring = true), 60_000)
        composeRule.onAllNodesWithText("题库", substring = false).onLast().performClick()
    }

    private fun testToken(): String =
        InstrumentationRegistry.getArguments().getString("teachTrackTestToken") ?: "device-test-token"

    private fun backendBaseUrl(): String {
        val baseUrl = InstrumentationRegistry.getArguments().getString("teachTrackTestBaseUrl")?.trimEnd('/').orEmpty()
        assumeTrue("Pass -e teachTrackTestBaseUrl pointing to an isolated test backend", baseUrl.isNotBlank())
        assumeTrue("UI write acceptance only supports an isolated loopback backend", baseUrl.startsWith("http://127.0.0.1:") || baseUrl.startsWith("http://localhost:"))
        val token = testToken()
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
            composeRule.onAllNodesWithText("题库", substring = false).onLast().performClick()
        } else {
            composeRule.onAllNodesWithText("题库", substring = false).onLast().performClick()
        }
        composeRule.onNodeWithText("云端工作台").assertIsDisplayed()
        composeRule.onAllNodesWithText("题库", substring = false).onFirst().performClick()
        field("搜索题干 / 知识点").performTextReplacement("")
        hideKeyboard()
        composeRule.onNodeWithText("搜索").performClick()
        if (composeRule.onAllNodesWithText("清除筛选").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("清除筛选").performClick()
        }
        composeRule.waitForIdle()
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
        hideKeyboard()
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
