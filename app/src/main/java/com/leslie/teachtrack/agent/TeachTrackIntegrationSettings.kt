package com.leslie.teachtrack.agent

import android.content.Context

/**
 * Persisted settings for the TeachTrack ↔ OpenMinis integration (spec §43/§45).
 *
 * Access mode gates the Agent Bridge (`TeachTrackOffloadHandler`):
 *  - [MODE_OWNER_TRUSTED] (default — this is the owner's personal app): the
 *    bridge executes write actions with `confirmed = true`, as before.
 *  - [MODE_STANDARD]: write actions are refused with a CONFIRMATION_REQUIRED
 *    JSON error the agent can relay; read-only/navigation actions
 *    (`READ_ONLY_ACTIONS`) always run.
 *
 * Stored in its own SharedPreferences file so both the offload handler (any
 * thread) and the Compose settings screen read the same value.
 */
object TeachTrackIntegrationSettings {
    private const val PREFS = "teachtrack_integration"
    const val KEY_ACCESS_MODE = "access_mode"
    const val MODE_STANDARD = "standard"
    const val MODE_OWNER_TRUSTED = "owner_trusted"

    fun accessMode(context: Context): String =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACCESS_MODE, MODE_OWNER_TRUSTED)
            .takeIf { it == MODE_STANDARD || it == MODE_OWNER_TRUSTED }
            ?: MODE_OWNER_TRUSTED

    fun setAccessMode(context: Context, mode: String) {
        require(mode == MODE_STANDARD || mode == MODE_OWNER_TRUSTED) { "unknown access mode: $mode" }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACCESS_MODE, mode).apply()
    }
}
