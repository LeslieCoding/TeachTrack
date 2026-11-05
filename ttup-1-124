package com.leslie.teachtrack.copilot

import com.leslie.teachtrack.core.model.StudentEntity
import com.leslie.teachtrack.data.TeachTrackRepository

enum class CommandRisk { READ_ONLY, SAFE_WRITE, SENSITIVE_WRITE, DESTRUCTIVE }

data class CommandSpec(val name: String, val risk: CommandRisk, val requiredParameters: Set<String> = emptySet())

/** Public business capabilities. Model output is restricted to this registry; it cannot invoke DAO, SQL or UI automation. */
object AppCommandRegistry {
    val specs = listOf(
        CommandSpec("SEARCH_STUDENTS", CommandRisk.READ_ONLY), CommandSpec("GET_STUDENT_OVERVIEW", CommandRisk.READ_ONLY),
        CommandSpec("CREATE_STUDENT", CommandRisk.SAFE_WRITE, setOf("studentName")), CommandSpec("UPDATE_STUDENT", CommandRisk.SENSITIVE_WRITE, setOf("studentId")),
        CommandSpec("ARCHIVE_STUDENT", CommandRisk.SENSITIVE_WRITE, setOf("studentId")), CommandSpec("CREATE_COURSE_SESSION", CommandRisk.SAFE_WRITE, setOf("studentName", "content")),
        CommandSpec("GET_COURSE_HISTORY", CommandRisk.READ_ONLY), CommandSpec("ADD_STUDENT_SCORE", CommandRisk.SAFE_WRITE, setOf("studentName", "score", "fullScore")),
        CommandSpec("GET_SCORE_TREND", CommandRisk.READ_ONLY), CommandSpec("UPDATE_KNOWLEDGE_PROGRESS", CommandRisk.SAFE_WRITE),
        CommandSpec("GET_KNOWLEDGE_PROGRESS", CommandRisk.READ_ONLY), CommandSpec("CREATE_HOMEWORK", CommandRisk.SAFE_WRITE),
        CommandSpec("GET_PENDING_HOMEWORK", CommandRisk.READ_ONLY), CommandSpec("CREATE_LESSON_PLAN", CommandRisk.SAFE_WRITE),
        CommandSpec("CREATE_COMMUNICATION_RECORD", CommandRisk.SAFE_WRITE), CommandSpec("CREATE_TASK", CommandRisk.SAFE_WRITE),
        CommandSpec("GET_PENDING_TASKS", CommandRisk.READ_ONLY), CommandSpec("GET_STUDENT_TIMELINE", CommandRisk.READ_ONLY),
        CommandSpec("ORGANIZE_STUDENT_DATA", CommandRisk.READ_ONLY), CommandSpec("GENERATE_FOLLOW_UP_PLAN", CommandRisk.READ_ONLY),
        CommandSpec("BULK_DATA_CHANGE", CommandRisk.DESTRUCTIVE)
    ).associateBy { it.name }
}

/** Typed boundary between model output and application capabilities. No SQL or DAO is exposed. */
sealed interface AppCommand {
    val spec: CommandSpec
    val parameters: Map<String, String>
    data class CreateCourse(override val parameters: Map<String, String>) : AppCommand {
        override val spec = CommandSpec("CREATE_COURSE_SESSION", CommandRisk.SAFE_WRITE, setOf("studentName", "content"))
    }
    data class AddScore(override val parameters: Map<String, String>) : AppCommand {
        override val spec = CommandSpec("ADD_STUDENT_SCORE", CommandRisk.SAFE_WRITE, setOf("studentName", "score", "fullScore"))
    }
    data class UpdateKnowledge(override val parameters: Map<String, String>) : AppCommand {
        override val spec = CommandSpec("UPDATE_KNOWLEDGE_PROGRESS", CommandRisk.SAFE_WRITE, setOf("studentName", "knowledgeName", "level"))
    }
    data class Query(val action: String, override val parameters: Map<String, String>) : AppCommand {
        override val spec = CommandSpec(action, CommandRisk.READ_ONLY)
    }
}

data class CommandPlan(val commands: List<AppCommand>, val originalText: String)

sealed interface EntityResolution {
    data class Resolved(val student: StudentEntity) : EntityResolution
    data class Ambiguous(val candidates: List<StudentEntity>) : EntityResolution
    data object NotFound : EntityResolution
}

/** Name resolution is deterministic and intentionally conservative. It never guesses similar names. */
class StudentEntityResolver(private val repository: TeachTrackRepository) {
    suspend fun resolve(query: String): EntityResolution {
        val q = query.trim()
        if (q.isBlank()) return EntityResolution.NotFound
        val direct = repository.searchStudents(q)
        if (direct.size == 1) return EntityResolution.Resolved(direct.first())
        if (direct.size > 1) return EntityResolution.Ambiguous(direct)
        val aliases = repository.searchStudents("").filter { student ->
            val display = student.displayName.ifBlank { if (student.name.length >= 3) student.name.takeLast(2) else student.name }
            display == q
        }
        return when (aliases.size) {
            1 -> EntityResolution.Resolved(aliases.first())
            0 -> EntityResolution.NotFound
            else -> EntityResolution.Ambiguous(aliases)
        }
    }
}

object AppCommandValidator {
    fun validate(command: AppCommand): String? {
        val missing = command.spec.requiredParameters.filter { command.parameters[it].isNullOrBlank() }
        if (missing.isNotEmpty()) return "缺少必要字段：${missing.joinToString("、")}"
        if (command is AppCommand.AddScore) {
            val score = command.parameters["score"]?.toDoubleOrNull() ?: return "成绩必须是数字"
            val full = command.parameters["fullScore"]?.toDoubleOrNull() ?: return "满分必须是数字"
            if (score < 0 || full <= 0 || score > full) return "成绩必须在 0 到满分之间"
        }
        return null
    }
}
