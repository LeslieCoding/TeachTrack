package com.leslie.teachtrack.copilot

import org.json.JSONObject

data class CopilotCommand(val action: String, val confirmationRequired: Boolean, val parameters: Map<String, String>, val clientRequestId: String = "")
data class CopilotCandidate(val command: CopilotCommand?, val explanation: String, val needsClarification: Boolean = false, val error: String? = null)
data class CopilotResult(val success: Boolean, val message: String, val undoToken: String? = null)

interface CopilotProvider { suspend fun interpret(input: String, context: Map<String, String>): CopilotCandidate }

/** Deterministic fallback parser. Unicode escapes keep Chinese matching stable across source encodings. */
class FakeCopilotProvider : CopilotProvider {
    override suspend fun interpret(input: String, context: Map<String, String>): CopilotCandidate {
        val text = input.trim()
        if (text.isBlank()) return CopilotCandidate(null, "请输入要执行的内容", error = "EMPTY_INPUT")
        val action = when {
            has(text, "\u5b66\u751f", "student") && has(text, "\u6dfb\u52a0", "\u65b0\u589e", "\u65b0\u5efa", "add", "create") -> "student.create"
            has(text, "\u6210\u7ee9", "\u5206\u6570", "score", "grade") -> "score.create"
            has(text, "\u5f85\u529e", "\u4efb\u52a1", "task", "todo") -> "task.create"
            has(text, "\u8bfe\u7a0b", "\u4e0a\u8bfe", "\u6388\u8bfe", "course", "lesson") -> "course.create"
            has(text, "\u6c9f\u901a", "\u5bb6\u957f", "\u5bb6\u6821", "communication", "parent") -> "communication.create"
            has(text, "\u4f5c\u4e1a", "homework", "assignment") -> "homework.create"
            has(text, "\u8ba1\u5212", "\u4e0b\u8282\u8bfe", "plan", "next lesson") -> "lesson_plan.create"
            else -> return CopilotCandidate(null, "我没有识别出要操作的模块，请说明要添加学生、课程、成绩、作业、待办或沟通。", needsClarification = true)
        }
        val name = Regex("(?:\u5b66\u751f|student)\\s*[:：]?\\s*([\\p{L}\\p{N}·]{2,12})", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1).orEmpty().ifBlank { context["selectedStudentName"].orEmpty() }
        val score = Regex("(?:\u6210\u7ee9|\u5206\u6570|score)\\s*[:：]?\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1).orEmpty()
        val fullScore = Regex("(?:\u6ee1\u5206|full\\s*score)\\s*[:：]?\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1).orEmpty()
        val grade = Regex("(?:\u5e74\u7ea7|grade)\\s*[:：]?\\s*([^，,\\s]+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1).orEmpty()
        val subject = Regex("(?:\u79d1\u76ee|subject)\\s*[:：]?\\s*([^，,\\s]+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1).orEmpty()
        val params = buildMap {
            if (name.isNotBlank()) put("studentName", name)
            put("rawText", text)
            if (score.isNotBlank()) put("score", score)
            if (fullScore.isNotBlank()) put("fullScore", fullScore)
            if (grade.isNotBlank()) put("grade", grade)
            if (subject.isNotBlank()) put("subject", subject)
            context["selectedStudentId"]?.let { put("selectedStudentId", it) }
        }
        return CopilotCandidate(CopilotCommand(action, true, params), "已识别为 $action，请确认后执行")
    }
    private fun has(text: String, vararg words: String) = words.any { text.contains(it, ignoreCase = true) }
}

object CopilotCommandCodec {
    const val SCHEMA = "teachtrack.command.v1"
    fun decode(raw: String): CopilotCommand {
        val root = JSONObject(raw)
        require(root.optString("schema") == SCHEMA) { "不支持的命令格式" }
        val p = root.optJSONObject("parameters") ?: JSONObject()
        val parameters = buildMap { p.keys().forEach { key -> put(key, p.optString(key)) } }
        return CopilotCommand(normalizeAction(root.getString("action")), root.optBoolean("confirmationRequired", true), parameters, root.optString("clientRequestId"))
    }
    fun encode(command: CopilotCommand): String = JSONObject().put("schema", SCHEMA).put("action", normalizeAction(command.action)).put("confirmationRequired", command.confirmationRequired).put("clientRequestId", command.clientRequestId).put("parameters", JSONObject().apply { command.parameters.forEach { (key, value) -> put(key, value) } }).toString()
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
        command.action !in allowedActions -> "此操作未被允许：${command.action}"
        command.parameters.keys.any { it.length > 64 } -> "参数名称无效"
        command.parameters.values.any { it.length > 4000 } -> "参数内容过长"
        else -> null
    }
}

interface CopilotActionRegistry {
    val allowedActions: Set<String>
    suspend fun preview(command: CopilotCommand): CopilotResult
    suspend fun execute(command: CopilotCommand): CopilotResult
    suspend fun undo(token: String): CopilotResult = CopilotResult(false, "此操作暂不支持撤销")
}

class CopilotService(private val provider: CopilotProvider, private val registry: CopilotActionRegistry) {
    suspend fun interpret(input: String, context: Map<String, String> = emptyMap()) = provider.interpret(input, context)
    suspend fun execute(command: CopilotCommand, confirmed: Boolean): CopilotResult {
        val normalized = command.copy(action = CopilotCommandCodec.normalizeAction(command.action))
        CopilotCommandValidator(registry.allowedActions).validate(normalized)?.let { return CopilotResult(false, it) }
        if (normalized.confirmationRequired && !confirmed) return registry.preview(normalized)
        return registry.execute(normalized)
    }
    suspend fun undo(token: String) = registry.undo(token)
}
