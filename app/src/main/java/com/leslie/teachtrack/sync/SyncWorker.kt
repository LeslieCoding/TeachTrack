package com.leslie.teachtrack.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.leslie.teachtrack.agent.TeachTrackAppContext
import com.leslie.teachtrack.core.model.StudentEntity
import com.leslie.teachtrack.core.model.StudentStatus
import com.leslie.teachtrack.core.model.SyncConflictEntity
import com.leslie.teachtrack.core.model.SyncStateEntity
import com.leslie.teachtrack.data.workspace.DeviceIdentity
import com.leslie.teachtrack.remote.CloudCredentials
import java.util.UUID

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val credentials = CloudCredentials(applicationContext)
        val baseUrl = credentials.baseUrl()
        val token = credentials.token()
        if (baseUrl.isBlank() || token.isNullOrBlank()) {
            return Result.success()
        }
        val database = TeachTrackAppContext.database(applicationContext)
        val syncDao = database.syncDao()
        val client = CloudSyncClient(baseUrl, token, DeviceIdentity(applicationContext).deviceId)

        val pending = syncDao.pending(100)
        if (pending.isNotEmpty()) {
            val pushed = try {
                client.push(pending)
            } catch (exception: Exception) {
                pending.forEach { syncDao.markRetry(it.operationId, exception.message ?: "network") }
                return retryOrGiveUp()
            }
            pushed.applied.forEach { applied ->
                syncDao.remove(applied.operationId)
                syncDao.upsertState(
                    SyncStateEntity(
                        entityType = applied.entityType,
                        entityId = applied.entityId,
                        serverVersion = applied.version,
                        lastSyncedAt = System.currentTimeMillis(),
                        dirty = false
                    )
                )
            }
            pushed.conflicts.forEach { conflict ->
                syncDao.insertConflict(
                    SyncConflictEntity(
                        id = UUID.randomUUID().toString(),
                        entityType = conflict.entityType,
                        entityId = conflict.entityId,
                        baseVersion = conflict.baseVersion,
                        currentVersion = conflict.currentVersion,
                        remotePayload = conflict.remotePayload
                    )
                )
                if (conflict.operationId.isNotBlank()) {
                    syncDao.remove(conflict.operationId)
                }
                syncDao.upsertState(
                    SyncStateEntity(
                        entityType = conflict.entityType,
                        entityId = conflict.entityId,
                        serverVersion = conflict.currentVersion,
                        lastSyncedAt = System.currentTimeMillis(),
                        dirty = true
                    )
                )
            }
        }

        val prefs = applicationContext.getSharedPreferences("teachtrack_cloud_sync", Context.MODE_PRIVATE)
        val cursor = prefs.getLong("cursor", 0L)
        val changes = try {
            client.changes(cursor)
        } catch (exception: Exception) {
            return retryOrGiveUp()
        }
        changes.items.forEach { change ->
            if (change.operation == "DELETE") {
                return@forEach
            }
            if (change.entityType != "student") {
                return@forEach
            }
            val state = syncDao.state("student", change.entityId)
            if (state?.dirty == true) {
                return@forEach
            }
            runCatching { client.student(change.entityId) }
                .onSuccess { json ->
                    val legacy = json.optString("legacyId").ifBlank { json.getString("id") }
                    database.dao().upsertStudent(json.toStudent(legacy))
                    syncDao.upsertState(
                        SyncStateEntity(
                            entityType = "student",
                            entityId = legacy,
                            serverVersion = change.version,
                            lastSyncedAt = System.currentTimeMillis(),
                            dirty = false
                        )
                    )
                }
        }
        prefs.edit().putLong("cursor", changes.nextCursor).apply()
        return Result.success()
    }

    private fun retryOrGiveUp(): Result =
        if (runAttemptCount < 8) Result.retry() else Result.failure()

    private fun org.json.JSONObject.toStudent(legacyId: String): StudentEntity {
        val display = optString("displayName").ifBlank {
            optString("fullName").ifBlank { "未命名" }
        }
        return StudentEntity(
            id = legacyId,
            name = optString("fullName").ifBlank { display },
            gender = optString("gender").takeIf { it.isNotBlank() },
            grade = optString("gradeAtRecord"),
            school = optString("school"),
            className = optString("className"),
            subject = "",
            status = runCatching {
                StudentStatus.valueOf(optString("status", "ACTIVE"))
            }.getOrDefault(StudentStatus.ACTIVE),
            notes = optString("notes"),
            updatedAt = System.currentTimeMillis(),
            displayName = display,
            currentGrade = optString("currentGrade").takeIf { it.isNotBlank() },
            gradeAtRecord = optString("gradeAtRecord").takeIf { it.isNotBlank() },
            nameCompleteness = optString("nameCompleteness", "CONFIRMED")
        )
    }
}
