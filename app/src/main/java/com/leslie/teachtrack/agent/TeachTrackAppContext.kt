package com.leslie.teachtrack.agent

import android.content.Context
import androidx.room.Room
import com.leslie.teachtrack.copilot.LocalCopilotActionRegistry
import com.leslie.teachtrack.core.database.MIGRATION_1_2
import com.leslie.teachtrack.core.database.MIGRATION_2_3
import com.leslie.teachtrack.core.database.MIGRATION_3_4
import com.leslie.teachtrack.core.database.MIGRATION_4_5
import com.leslie.teachtrack.core.database.MIGRATION_5_6
import com.leslie.teachtrack.core.database.MIGRATION_6_7
import com.leslie.teachtrack.core.database.TeachTrackDatabase
import com.leslie.teachtrack.data.TeachTrackRepository
import com.leslie.teachtrack.data.workspace.WorkspaceManager
import com.leslie.teachtrack.data.workspace.WorkspaceSyncCoordinator
import java.io.File

/**
 * Single shared holder for TeachTrack's Room database / Repository / Copilot
 * Action Registry, so the UI (MainActivity/TeachTrackViewModel) and the
 * OpenMinis Agent Bridge (TeachTrackOffloadHandler) never build two competing
 * Room instances against the same `teachtrack.db` file. Room recommends
 * exactly one instance per database file per process.
 */
object TeachTrackAppContext {
    @Volatile private var repositoryInstance: TeachTrackRepository? = null
    @Volatile private var registryInstance: LocalCopilotActionRegistry? = null

    fun repository(context: Context): TeachTrackRepository {
        repositoryInstance?.let { return it }
        synchronized(this) {
            repositoryInstance?.let { return it }
            val appContext = context.applicationContext
            val db = Room.databaseBuilder(appContext, TeachTrackDatabase::class.java, "teachtrack.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
            val repo = TeachTrackRepository(db.dao(), WorkspaceSyncCoordinator(appContext), db)
            repositoryInstance = repo
            return repo
        }
    }

    fun actionRegistry(context: Context): LocalCopilotActionRegistry {
        registryInstance?.let { return it }
        synchronized(this) {
            registryInstance?.let { return it }
            val appContext = context.applicationContext
            val repo = repository(appContext)
            val workspaceManager = WorkspaceManager(appContext)
            val backupDirectory = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "ai-backups")
            val registry = LocalCopilotActionRegistry(repo, workspaceManager, backupDirectory)
            registryInstance = registry
            return registry
        }
    }
}
