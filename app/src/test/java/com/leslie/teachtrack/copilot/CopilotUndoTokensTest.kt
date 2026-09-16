package com.leslie.teachtrack.copilot

import org.junit.Assert.*
import org.junit.Test

class CopilotUndoTokensTest {
    @Test fun taskStatusTokenRoundTrips() {
        val token = CopilotUndoTokens.taskStatus(mapOf("task-1" to "待处理", "task-2" to "待处理"))
        val json = CopilotUndoTokens.decode(token)!!
        assertEquals(CopilotUndoTokens.KIND_TASK_STATUS, json.getString("kind"))
        val items = json.getJSONArray("items")
        assertEquals(2, items.length())
        assertEquals("task-1", items.getJSONObject(0).getString("id"))
        assertEquals("待处理", items.getJSONObject(0).getString("prev"))
    }

    @Test fun studentArchiveTokenRoundTrips() {
        val token = CopilotUndoTokens.studentArchive("student-42")
        val json = CopilotUndoTokens.decode(token)!!
        assertEquals(CopilotUndoTokens.KIND_STUDENT_ARCHIVE, json.getString("kind"))
        assertEquals("student-42", json.getString("id"))
    }

    @Test fun malformedTokensDecodeToNull() {
        assertNull(CopilotUndoTokens.decode("not-base64!!!"))
        assertNull(CopilotUndoTokens.decode(""))
        assertNull(CopilotUndoTokens.decode("aGVsbG8")) // valid base64, not JSON
    }
}
