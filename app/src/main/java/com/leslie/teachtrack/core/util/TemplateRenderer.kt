package com.leslie.teachtrack.core.util

/** Deterministic, offline template expansion. Unknown variables are preserved for manual editing. */
object TemplateRenderer {
    private val variablePattern = Regex("\\{\\{([a-zA-Z0-9_]+)}}")

    fun render(template: String, values: Map<String, String>): String =
        variablePattern.replace(template) { match -> values[match.groupValues[1]] ?: match.value }
}
