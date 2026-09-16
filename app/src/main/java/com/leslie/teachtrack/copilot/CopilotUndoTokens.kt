package com.leslie.teachtrack.copilot

import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Undo tokens for agent write actions (spec §55).
 *
 * A token is URL-safe Base64 of a small JSON document describing how to
 * reverse ONE executed command through existing repository operations only —
 * no new delete business logic is introduced for undo. Two kinds exist:
 *
 *  - [KIND_TASK_STATUS]: revert N task-status writes; each item carries the
 *    task id and its status before the write. Issued by `task.status.update`
 *    and `batch.tasks.complete`.
 *  - [KIND_STUDENT_ARCHIVE]: archive a freshly created student (soft delete,
 *    the same path `student.archive` uses). Issued by `student.create`.
 *
 * Create actions whose reversal would need a hard delete (score/course/tag/
 * task creates, tag.assign, …) deliberately issue NO token — the project's
 * "archive before delete" principle keeps hard deletes out of the repository,
 * so those actions are explicitly unsupported for undo.
 */
object CopilotUndoTokens {
    const val KIND_TASK_STATUS = "task_status"
    const val KIND_STUDENT_ARCHIVE = "student_archive"

    fun taskStatus(previous: Map<String, String>): String =
        encode(JSONObject().put("kind", KIND_TASK_STATUS).put(
            "items",
            JSONArray().also { array ->
                previous.forEach { (id, prev) ->
                    array.put(JSONObject().put("id", id).put("prev", prev))
                }
            },
        ))

    fun studentArchive(studentId: String): String =
        encode(JSONObject().put("kind", KIND_STUDENT_ARCHIVE).put("id", studentId))

    /** Returns the decoded JSON, or null when the token is malformed/foreign. */
    fun decode(token: String): JSONObject? = runCatching {
        JSONObject(String(Base64.getUrlDecoder().decode(token), Charsets.UTF_8))
    }.getOrNull()

    private fun encode(json: JSONObject): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().toByteArray(Charsets.UTF_8))
}
