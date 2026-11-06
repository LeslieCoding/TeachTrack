package com.leslie.teachtrack.core.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_outbox",
    indices = [Index("entityType", "entityId"), Index(value = ["operationId"], unique = true)]
)
data class SyncOutboxEntity(
    @PrimaryKey val operationId: String,
    val entityType: String,
    val entityId: String,
    val operation: String = "UPSERT",
    val baseVersion: Long = 0,
    val payload: String,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null
)

@Entity(tableName = "sync_state", primaryKeys = ["entityType", "entityId"])
data class SyncStateEntity(
    val entityType: String,
    val entityId: String,
    val serverVersion: Long = 0,
    val lastSyncedAt: Long = 0,
    val dirty: Boolean = false
)

@Entity(tableName = "sync_conflicts", indices = [Index("entityType", "entityId")])
data class SyncConflictEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val baseVersion: Long,
    val currentVersion: Long,
    val remotePayload: String,
    val createdAt: Long = System.currentTimeMillis(),
    val resolved: Boolean = false,
    val resolution: String? = null
)
