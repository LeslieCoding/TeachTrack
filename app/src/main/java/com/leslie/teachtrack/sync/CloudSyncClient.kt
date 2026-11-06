package com.leslie.teachtrack.sync

import com.leslie.teachtrack.core.model.SyncOutboxEntity
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class CloudSyncClient(
    private val baseUrl: String,
    private val token: String,
    private val deviceId: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    data class Applied(
        val operationId: String,
        val entityType: String,
        val entityId: String,
        val version: Long
    )

    data class Conflict(
        val operationId: String,
        val entityType: String,
        val entityId: String,
        val baseVersion: Long,
        val currentVersion: Long,
        val remotePayload: String
    )

    data class PushResult(val applied: List<Applied>, val conflicts: List<Conflict>)

    data class Change(
        val seq: Long,
        val entityType: String,
        val entityId: String,
        val operation: String,
        val version: Long
    )

    data class Changes(val items: List<Change>, val nextCursor: Long)

    fun push(operations: List<SyncOutboxEntity>): PushResult {
        val json = JSONObject().put("deviceId", deviceId)
        val array = JSONArray()
        operations.forEach { operation ->
            array.put(
                JSONObject()
                    .put("operationId", operation.operationId)
                    .put("entityType", operation.entityType)
                    .put("entityId", operation.entityId)
                    .put("operation", operation.operation)
                    .put("baseVersion", operation.baseVersion)
                    .put("payload", JSONObject(operation.payload))
            )
        }
        json.put("operations", array)
        val body = execute(
            Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/v1/sync/operations")
                .post(json.toString().toRequestBody(JSON))
        )
        val applied = mutableListOf<Applied>()
        body.optJSONArray("applied")?.let { items ->
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                applied += Applied(
                    operationId = item.getString("operationId"),
                    entityType = item.optString("entityType"),
                    entityId = item.optString("entityId"),
                    version = item.optLong("version", 0)
                )
            }
        }
        val conflicts = mutableListOf<Conflict>()
        body.optJSONArray("conflicts")?.let { items ->
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                conflicts += Conflict(
                    operationId = item.optString("operationId"),
                    entityType = item.optString("entityType"),
                    entityId = item.optString("entityId"),
                    baseVersion = item.optLong("baseVersion", 0),
                    currentVersion = item.optLong("currentVersion", 0),
                    remotePayload = item.optJSONObject("remote")?.toString() ?: "{}"
                )
            }
        }
        return PushResult(applied, conflicts)
    }

    fun changes(cursor: Long, limit: Int = 200): Changes {
        val body = execute(
            Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/v1/sync/changes?cursor=$cursor&limit=$limit")
                .get()
        )
        val items = mutableListOf<Change>()
        body.optJSONArray("changes")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                items += Change(
                    seq = item.optLong("seq"),
                    entityType = item.optString("entityType"),
                    entityId = item.optString("entityId"),
                    operation = item.optString("operation"),
                    version = item.optLong("version", 0)
                )
            }
        }
        return Changes(items, body.optLong("nextCursor", cursor))
    }

    fun student(id: String): JSONObject = execute(
        Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/v1/students/$id")
            .get()
    )

    private fun execute(builder: Request.Builder): JSONObject {
        val request = builder.header("Authorization", "Bearer $token").build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(text).getJSONObject("error").optString("message")
                }.getOrNull()
                throw IOException(message?.takeIf { it.isNotBlank() } ?: "HTTP ${response.code}")
            }
            return JSONObject(text)
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
