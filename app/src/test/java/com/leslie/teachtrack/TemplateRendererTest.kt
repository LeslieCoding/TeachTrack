package com.leslie.teachtrack

import com.leslie.teachtrack.core.util.TemplateRenderer
import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateRendererTest {
    @Test fun replacesKnownVariablesAndKeepsUnknownVariables() {
        val result = TemplateRenderer.render(
            "{{student_name}} {{date}} / {{next_plan}}",
            mapOf("student_name" to "王晨然", "date" to "2026-09-08")
        )
        assertEquals("王晨然 2026-09-08 / {{next_plan}}", result)
    }

    @Test fun supportsRepeatedVariables() {
        assertEquals("A A", TemplateRenderer.render("{{x}} {{x}}", mapOf("x" to "A")))
    }
}
