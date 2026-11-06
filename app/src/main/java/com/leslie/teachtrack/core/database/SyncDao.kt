package com.leslie.teachtrack.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.leslie.teachtrack.core.model.SyncConflictEntity
import com.leslie.teachtrack.core.model.SyncOutboxEntity
import com.leslie.teachtrack.core.model.SyncStateEntity

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(operation: SyncOutboxEntity)

    @Query("SELECT * FROM sync_outbox ORDER BY createdAt LIMIT :limit")
    suspend fun pending(limit: Int): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE operationId = :operationId")
    suspend fun remove(operationId: String)

    @Query("UPDATE sync_outbox SET retryCount = retryCount + 1, lastError = :error WHERE operationId = :operationId")
    suspend fun markRetry(operationId: String, error: String)

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun pendingCount(): Int

    @Query("SELECT * FROM sync_state WHERE entityType = :entityType AND entityId = :entityId LIMIT 1")
    suspend fun state(entityType: String, entityId: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: SyncStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConflict(conflict: SyncConflictEntity)

    @Query("SELECT COUNT(*) FROM sync_conflicts WHERE resolved = 0")
    suspend fun openConflictCount(): Int

    @Query("SELECT * FROM sync_conflicts WHERE resolved = 0 ORDER BY createdAt DESC LIMIT :limit")
    suspend fun openConflicts(limit: Int = 50): List<SyncConflictEntity>

    @Query("UPDATE sync_conflicts SET resolved = 1, resolution = :resolution WHERE id = :id")
    suspend fun resolveConflict(id: String, resolution: String)
}
