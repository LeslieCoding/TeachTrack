package com.leslie.teachtrack.copilot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CopilotAuditEntry(val id: String, val action: String, val timestamp: Long, val status: String, val undoToken: String?)

/** Local audit history. It deliberately persists action metadata only, never raw parameters. */
class CopilotAuditStore(context: Context) {
    private val prefs = context.getSharedPreferences("teachtrack_copilot_audit", Context.MODE_PRIVATE)

    fun record(command: CopilotCommand, result: CopilotResult) {
        val entries = JSONArray(prefs.getString("entries", "[]"))
        entries.put(JSONObject().put("id", command.clientRequestId.ifBlank { java.util.UUID.randomUUID().toString() }).put("action", command.action).put("timestamp", System.currentTimeMillis()).put("status", if (result.success) "SUCCESS" else "FAILURE").put("undoToken", result.undoToken))
        while (entries.length() > 100) entries.remove(0)
        prefs.edit().putString("entries", entries.toString()).apply()
    }

    fun list(): List<CopilotAuditEntry> {
        val entries = JSONArray(prefs.getString("entries", "[]"))
        return (0 until entries.length()).mapNotNull { index -> entries.optJSONObject(index)?.let { item -> CopilotAuditEntry(item.optString("id"), item.optString("action"), item.optLong("timestamp"), item.optString("status"), item.optString("undoToken").takeIf { it.isNotBlank() && it != "null" }) } }.reversed()
    }

    fun clear() { prefs.edit().remove("entries").apply() }
}
