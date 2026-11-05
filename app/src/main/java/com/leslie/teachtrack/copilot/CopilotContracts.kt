package com.leslie.teachtrack.copilot

import org.json.JSONObject

data class CopilotCommand(val action: String, val confirmationRequired: Boolean, val parameters: Map<String, String>, val clientRequestId: String = "")
data class CopilotCandidate(val command: CopilotCommand?, val explanation: String, val needsClarification: Boolean = false, val error: String? = null, val commands: List<CopilotCommand> = emptyList())

sealed interface CopilotEffect {
    data class OpenTab(val index: Int) : CopilotEffect
    data class OpenStudent(val studentId: String, val startCourse: Boolean = false, val startScore: Boolean = false) : CopilotEffect
}

data class CopilotResult(val success: Boolean, val message: String, val undoToken: String? = null, val effect: CopilotEffect? = null, val data: Map<String, String> = emptyMap())

/**
 * Single source of truth for which actions are read-only (no confirmation needed
 * in the interactive UI). Shared by [FakeCopilotProvider]'s NLP parsing and by
 * the Agent Bridge (`com.leslie.teachtrack.agent.TeachTrackOffloadHandler`), so
 * the two entry points never disagree about which actions are writes.
 */
val READ_ONLY_ACTIONS = setOf(
    "student.search", "student.data.organize.preview", "knowledge.weakness.query", "score.query", "course.timeline",
    "dashboard.query", "analytics.query", "navigation.open", "attachment.list", "knowledge.query",
    "student.overview.query", "parent_contact.query", "template.list", "template.render", "tag.query", "attachment.query",
    "backup.list", "workspace.status.query", "workspace.check", "workspace.sync.scan", "workspace.conflict.list", "workspace.conflict.resolve"
)

interface CopilotProvider { suspend fun interpret(input: String, context: Map<String, String>): CopilotCandidate }

/** Offline fallback. It extracts a conservative command and leaves execution to the local registry. */
class FakeCopilotProvider : CopilotProvider {
    override suspend fun interpret(input: String, context: Map<String, String>): CopilotCandidate {
        val text = input.trim()
        if (text.isBlank()) return CopilotCandidate(null, "请输入要执行的内容", error = "EMPTY_INPUT")
        val action = when {
            has(text, "\u6574\u7406", "\u68b3\u7406") && has(text, "\u5f85\u529e", "\u8ddf\u8fdb") && has(text, "\u5b66\u751f", "\u6863\u6848", "\u6570\u636e", "\u7ed3\u679c") -> "student.followup.tasks.create"
            has(text, "\u6574\u7406", "\u68b3\u7406") && has(text, "\u5b66\u751f", "\u6863\u6848", "\u6570\u636e") -> "student.data.organize.preview"
            has(text, "\u67e5\u8be2", "\u67e5\u770b", "\u54ea\u4e2a", "\u54ea\u4e9b") && has(text, "\u8584\u5f31", "\u9700\u5de9\u56fa", "\u638c\u63e1\u4e0d\u597d") && has(text, "\u77e5\u8bc6\u70b9", "\u52a8\u6001\u7535\u8def", "\u7535\u8def", "\u6d6e\u529b", "\u5149\u5b66") -> "knowledge.weakness.query"
            has(text, "工作区", "workspace") && has(text, "状态", "情况") -> "workspace.status.query"
            has(text, "工作区", "同步") && has(text, "扫描") -> "workspace.sync.scan"
            has(text, "工作区") && has(text, "导出", "同步") -> "workspace.export"
            has(text, "工作区") && has(text, "检查", "完整性", "健康") -> "workspace.check"
            has(text, "冲突") && has(text, "处理", "解决", "合并") -> "workspace.conflict.resolve"
            has(text, "冲突") && has(text, "查看", "列表", "有哪些") -> "workspace.conflict.list"
            has(text, "备份") && has(text, "恢复", "还原") -> "backup.restore"
            has(text, "备份") && has(text, "查看", "列表", "有哪些", "几个") -> "backup.list"
            has(text, "备份") && has(text, "导出", "生成", "创建", "新建") -> "backup.export"
            has(text, "成绩") && has(text, "导出") && has(text, "csv", "CSV", "表格", "Excel", "excel") -> "csv.export.scores"
            has(text, "新建", "创建", "新增") && has(text, "知识点") -> "knowledge.create"
            has(text, "待办", "任务") && (has(text, "标记") || has(text, "搞定")) -> "task.status.update"
            has(text, "家长") && has(text, "联系人", "联系方式") -> "parent_contact.create"
            has(text, "查看", "了解") && has(text, "情况", "概况", "怎么样", "整体") -> "student.overview.query"
            has(text, "打开", "进入", "导航") && has(text, "学生") -> "navigation.open"
            has(text, "打开", "进入") && has(text, "计划", "课程") -> "navigation.open"
            has(text, "打开", "进入") && has(text, "统计", "分析") -> "navigation.open"
            has(text, "打开", "进入") && has(text, "设置", "数据") -> "navigation.open"
            (text.contains("\u8bb2") && text.contains("\u8bfe")) -> "course.create"
            has(text, "首页", "dashboard") && has(text, "查看", "打开") -> "dashboard.query"
            has(text, "时间线", "历史课程", "课程历史") -> "course.timeline"
            has(text, "续课", "接着上次", "上节课继续") -> "course.resume"
            has(text, "待办", "任务", "todo") -> "task.create"
            has(text, "学生", "student") && has(text, "查", "找", "搜索", "查看") -> "student.search"
            has(text, "新学生", "添加学生", "新增学生", "student.create") -> "student.create"
            has(text, "考试", "exam") && has(text, "创建", "新建", "添加") -> "exam.create"
            has(text, "成绩", "分数", "score", "grade") && has(text, "查", "看", "查询") -> "score.query"
            has(text, "成绩", "分数", "score", "grade") -> "score.create"
            has(text, "课程", "上课", "授课", "lesson") -> "course.create"
            has(text, "沟通", "家长", "家校", "communication", "parent") -> "communication.create"
            has(text, "作业", "homework", "assignment") -> "homework.create"
            has(text, "下节课", "计划", "lesson plan") -> "lesson_plan.create"
            has(text, "知识点", "掌握", "巩固", "knowledge") -> "knowledge.update"
            has(text, "统计", "分析", "analytics") -> "analytics.query"
            has(text, "问题", "错题", "problem") -> "problem.create"
            has(text, "模板") && has(text, "列表", "有哪些") -> "template.list"
            has(text, "模板") && has(text, "删除") -> "template.delete"
            has(text, "模板") && has(text, "修改", "更新", "编辑") -> "template.update"
            has(text, "模板") && has(text, "使用", "渲染", "生成反馈") -> "template.render"
            has(text, "模板", "template") -> "template.create"
            has(text, "标签") && has(text, "查看", "有哪些") -> "tag.query"
            has(text, "标签") && has(text, "给", "打上", "加上", "贴") -> "tag.assign"
            has(text, "标签", "tag") -> "tag.create"
            has(text, "附件") && has(text, "删除") -> "attachment.delete"
            has(text, "附件") && has(text, "查看", "有哪些", "查询") -> "attachment.query"
            has(text, "附件", "attachment") -> "attachment.list"
            else -> return CopilotCandidate(null, "请说明要添加、查询或打开哪个 TeachTrack 模块", needsClarification = true)
        }
        val name = extractName(text).ifBlank { context["selectedStudentName"].orEmpty() }
        val scoreMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)").find(text)
        val labeledScore = Regex("(?:成绩|分数|score)\\s*[:： ]*(\\d+(?:\\.\\d+)?)").find(text)?.groupValues?.getOrNull(1)
            ?: Regex("(?<!满分)(\\d+(?:\\.\\d+)?)\\s*分").find(text)?.groupValues?.getOrNull(1)
        val labeledFullScore = Regex("(?:满分|full\\s*score)\\s*[:： ]*(\\d+(?:\\.\\d+)?)").find(text)?.groupValues?.getOrNull(1)
        val examName = Regex("(?:考试|测验|周测|月考)(?:叫|名称|名为)?[:： ]*([^，,。；; ]{1,20})").find(text)?.groupValues?.getOrNull(1).orEmpty()
        val knowledge = Regex("(?:知识点|把)[：: ]*([\\p{L}\\p{N}一-龥]{2,20})").find(text)?.groupValues?.getOrNull(1).orEmpty()
            .ifBlank { Regex("(?:讲|复习|巩固)([\\p{L}\\p{N}一-龥]{2,12})").find(text)?.groupValues?.getOrNull(1).orEmpty() }
        val weakKnowledge = Regex("([一-龥A-Za-z0-9-]{2,20})(?:比较|较为|很)?(?:薄弱|需巩固|掌握不好)").find(text)?.groupValues?.getOrNull(1).orEmpty()
        val level = when { has(text, "熟练") -> "4"; has(text, "基本掌握") -> "3"; has(text, "巩固", "较弱", "薄弱") -> "2"; has(text, "已学习", "学会") -> "1"; else -> "" }
        val destination = when { has(text, "学生") -> "学生"; has(text, "计划", "课程") -> "计划"; has(text, "统计", "分析") -> "统计"; has(text, "设置", "数据") -> "设置"; else -> "首页" }
        val params = buildMap {
            if (name.isNotBlank()) put("studentName", name)
            put("rawText", text)
            if (action == "student.search") put("query", name.ifBlank { text })
            if (action == "knowledge.weakness.query") put("knowledgeName", weakKnowledge.ifBlank { text })
            if (scoreMatch != null) { put("score", scoreMatch.groupValues[1]); put("fullScore", scoreMatch.groupValues[2]) }
            else { labeledScore?.let { put("score", it) }; labeledFullScore?.let { put("fullScore", it) } }
            if (examName.isNotBlank()) put("examName", examName)
            if (knowledge.isNotBlank()) put("knowledgeName", knowledge)
            if (level.isNotBlank()) put("level", level)
            if (action == "navigation.open") put("destination", destination)
            context["selectedStudentId"]?.let { put("selectedStudentId", it) }
            context["selectedStudentName"]?.let { if (name.isBlank()) put("studentName", it) }
            when (action) {
                "course.create" -> put("content", text.substringAfter("课程", text).trim().ifBlank { text })
                "homework.create" -> put("content", text.substringAfter("作业", text).trim().ifBlank { text })
                "communication.create" -> put("summary", text.substringAfter("沟通", text).trim().ifBlank { text })
                "lesson_plan.create" -> put("focus", text.substringAfter("计划", text).trim().ifBlank { text })
                "task.create" -> put("title", text.substringAfter("待办", text).trim().ifBlank { text })
            }
        }
        val readOnly = action in READ_ONLY_ACTIONS
        val primary = CopilotCommand(action, !readOnly, params)
        val plan = buildList {
            add(primary)
            if (action == "course.create" && level.isNotBlank() && knowledge.isNotBlank()) {
                add(CopilotCommand("knowledge.update", true, params + mapOf("knowledgeName" to knowledge, "level" to level)))
            }
            if (action == "course.create" && has(text, "布置", "作业")) {
                add(CopilotCommand("homework.create", true, params + mapOf("content" to text.substringAfter("作业", text).trim().ifBlank { text })))
            }
            if (action == "course.create" && has(text, "下节课", "下次课", "继续复习")) {
                add(CopilotCommand("lesson_plan.create", true, params + mapOf("focus" to text.substringAfter("下节课", text).trim().ifBlank { text })))
            }
        }
        return CopilotCandidate(primary, "已识别为 $action${if (plan.size > 1) "，包含 ${plan.size} 个关联动作" else if (readOnly) "，将读取本地数据" else "，确认后写入本地数据"}", commands = plan)
    }

    private fun extractName(text: String): String {
        val pattern = "[\\p{L}\\p{N}一-龥]{2,4}?"
        val boundary = "(?=录入|记录|添加|查看|查询|今天|成绩|课程|有|打上|加上|贴|标签|的|情况|概况|怎么样|整体|\u0020|$)"
        val readPrefix = "打开|查看|查询|记录|布置|联系|了解"
        val first = Regex("(?:给|为|对)\\s*(?:学生)?[:： ]*($pattern)$boundary").find(text)?.groupValues?.getOrNull(1).orEmpty()
        return first.ifBlank { Regex("(?:添加|新增|新建)\\s*(?:学生)?($pattern)$boundary").find(text)?.groupValues?.getOrNull(1).orEmpty() }
            .ifBlank { Regex("(?:$readPrefix)\\s*(?:一下)?\\s*(?:学生)?($pattern)$boundary").find(text)?.groupValues?.getOrNull(1).orEmpty() }
            .ifBlank { Regex("(?:$readPrefix)\\s*(?:一下)?\\s*($pattern)").find(text)?.groupValues?.getOrNull(1).orEmpty() }
    }
    private fun has(text: String, vararg words: String) = words.any { text.contains(it, ignoreCase = true) }
}

object CopilotCommandCodec {
    const val SCHEMA = "teachtrack.command.v1"
    fun decode(raw: String): CopilotCommand {
        val root = JSONObject(raw); require(root.optString("schema") == SCHEMA) { "不支持的命令格式" }
        val p = root.optJSONObject("parameters") ?: JSONObject()
        val params = buildMap { p.keys().forEach { key -> put(key, p.optString(key)) } }
        return CopilotCommand(normalizeAction(root.getString("action")), root.optBoolean("confirmationRequired", true), params, root.optString("clientRequestId"))
    }
    fun encode(command: CopilotCommand): String = JSONObject().put("schema", SCHEMA).put("action", normalizeAction(command.action)).put("confirmationRequired", command.confirmationRequired).put("clientRequestId", command.clientRequestId).put("parameters", JSONObject().apply { command.parameters.forEach { (k, v) -> put(k, v) } }).toString()
    fun normalizeAction(action: String): String = when (action.trim()) {
        "students.add", "student.add" -> "student.create"
        "students.update" -> "student.update"
        "courses.add", "course.add" -> "course.create"
        "scores.add", "score.add" -> "score.create"
        "tasks.add", "task.add", "todo.create" -> "task.create"
        "homeworks.add", "homework.add" -> "homework.create"
        "communications.add", "communication.add" -> "communication.create"
        "plans.add", "lessonPlan.create" -> "lesson_plan.create"
        else -> action
    }
}

class CopilotCommandValidator(private val allowedActions: Set<String>) {
    fun validate(command: CopilotCommand): String? = when {
        command.action !in allowedActions -> "不允许的动作：${command.action}"
        command.parameters.keys.any { it.length > 64 } -> "参数名无效"
        command.parameters.values.any { it.length > 4000 } -> "参数内容过长"
        else -> null
    }
}

interface CopilotActionRegistry {
    val allowedActions: Set<String>
    suspend fun preview(command: CopilotCommand): CopilotResult
    suspend fun execute(command: CopilotCommand): CopilotResult
    suspend fun undo(token: String): CopilotResult = CopilotResult(false, "暂不支持撤销")
}

class CopilotService(private val provider: CopilotProvider, private val registry: CopilotActionRegistry, private val audit: ((CopilotCommand, CopilotResult) -> Unit)? = null) {
    suspend fun interpret(input: String, context: Map<String, String> = emptyMap()) = provider.interpret(input, context)
    suspend fun execute(command: CopilotCommand, confirmed: Boolean): CopilotResult {
        val normalized = command.copy(action = CopilotCommandCodec.normalizeAction(command.action))
        CopilotCommandValidator(registry.allowedActions).validate(normalized)?.let { return CopilotResult(false, it).also { result -> audit?.invoke(normalized, result) } }
        if (normalized.confirmationRequired && !confirmed) return registry.preview(normalized).also { result -> audit?.invoke(normalized, result) }
        return registry.execute(normalized).also { result -> audit?.invoke(normalized, result) }
    }
    suspend fun executePlan(commands: List<CopilotCommand>, confirmed: Boolean): List<CopilotResult> {
        val results = mutableListOf<CopilotResult>()
        for (command in commands) {
            val result = execute(command, confirmed)
            results += result
            if (!result.success) break
        }
        return results
    }
    suspend fun undo(token: String) = registry.undo(token)
}
