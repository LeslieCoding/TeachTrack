package com.leslie.teachtrack.copilot

import org.json.JSONObject

/** Stable boundary between natural-language providers and local application actions. */
data class CopilotCommand(
    val action: String,
    val confirmationRequired: Boolean,
    val parameters: Map<String, String>,
    val clientRequestId: String = ""
)

data class CopilotCandidate(
    val command: CopilotCommand?,
    val explanation: String,
    val needsClarification: Boolean = false,
    val error: String? = null
)

data class CopilotResult(val success: Boolean, val message: String, val undoToken: String? = null)

interface CopilotProvider {
    suspend fun interpret(input: String, context: Map<String, String>): CopilotCandidate
}

/** Deterministic offline provider used by development, tests, and no-network fallback. */
class FakeCopilotProvider : CopilotProvider {
    override suspend fun interpret(input: String, context: Map<String, String>): CopilotCandidate {
        val text = input.trim()
        if (text.isBlank()) return CopilotCandidate(null, "", error = "请输入要执行的内容")
        val action = when {
            (text.contains("学生") || text.contains("student", true)) && (text.contains("加") || text.contains("添加") || text.contains("新") || text.contains("add", true)) -> "student.create"
            text.contains("成绩") || text.contains("分数") || text.contains("score", true) -> "score.create"
            text.contains("待办") || text.contains("任务") || text.contains("task", true) -> "task.create"
            text.contains("课程") || text.contains("上课") || text.contains("course", true) -> "course.create"
            text.contains("沟通") || text.contains("家长") || text.contains("communication", true) -> "communication.create"
            text.contains("作业") || text.contains("homework", true) -> "homework.create"
            text.contains("计划") || text.contains("plan", true) -> "lesson_plan.create"
            else -> return CopilotCandidate(null, "无法确定要执行的模块", needsClarification = true)
        }
        val name = Regex("(?:学生|给)\\s*([\\p{L}\\p{N}]{2,12})").find(text)?.groupValues?.getOrNull(1).orEmpty()
        val title = text.removePrefix("添加").removePrefix("新建").trim()
        val params = buildMap {
            if (name.isNotBlank()) put("studentName", name)
            if (title.isNotBlank()) put("rawText", title)
            context["selectedStudentId"]?.let { put("selectedStudentId", it) }
        }
        return CopilotCandidate(CopilotCommand(action, confirmationRequired = true, params), "识别为 $action，请确认后执行")
    }
}

object CopilotCommandCodec {
    const val SCHEMA = "teachtrack.command.v1"

    fun decode(raw: String): CopilotCommand {
        val root = JSONObject(raw)
        require(root.optString("schema") == SCHEMA) { "不支持的命令格式" }
        val action = root.getString("action")
        val parameters = buildMap {
            val p = root.optJSONObject("parameters") ?: JSONObject()
            p.keys().forEach { key -> put(key, p.optString(key)) }
        }
        return CopilotCommand(action, root.optBoolean("confirmationRequired", true), parameters, root.optString("clientRequestId"))
    }

    fun encode(command: CopilotCommand): String {
        val parameters = JSONObject().apply { command.parameters.forEach { (key, value) -> put(key, value) } }
        return JSONObject()
            .put("schema", SCHEMA)
            .put("action", command.action)
            .put("confirmationRequired", command.confirmationRequired)
            .put("clientRequestId", command.clientRequestId)
            .put("parameters", parameters)
            .toString()
    }
}

class CopilotCommandValidator(private val allowedActions: Set<String>) {
    fun validate(command: CopilotCommand): String? = when {
        command.action !in allowedActions -> "此操作未被允许"
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

/** Application-layer executor. Providers only produce commands; repositories execute validated actions. */
class CopilotService(
    private val provider: CopilotProvider,
    private val registry: CopilotActionRegistry
) {
    suspend fun interpret(input: String, context: Map<String, String> = emptyMap()): CopilotCandidate = provider.interpret(input, context)

    suspend fun execute(command: CopilotCommand, confirmed: Boolean): CopilotResult {
        val validation = CopilotCommandValidator(registry.allowedActions).validate(command)
        if (validation != null) return CopilotResult(false, validation)
        if (command.confirmationRequired && !confirmed) return registry.preview(command)
        return registry.execute(command)
    }

    suspend fun undo(token: String): CopilotResult = registry.undo(token)
}
