package com.leslie.teachtrack.copilot

import com.leslie.teachtrack.core.database.TeachTrackDao
import com.leslie.teachtrack.data.TeachTrackRepository
import java.io.File
import java.lang.reflect.Proxy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec §69 coverage guard: TEACHTRACK_CAPABILITIES.json (repo root) is the
 * machine-readable mirror of `LocalCopilotActionRegistry.allowedActions` and
 * must never drift from it. The registry is constructed with a null-returning
 * DAO proxy — `allowedActions` is a constant set that touches no DAO method,
 * and this test intentionally exercises none either.
 */
class CapabilitiesJsonSyncTest {

    private val registry by lazy {
        val dao = Proxy.newProxyInstance(
            TeachTrackDao::class.java.classLoader,
            arrayOf(TeachTrackDao::class.java),
        ) { _, _, _ -> null } as TeachTrackDao
        LocalCopilotActionRegistry(TeachTrackRepository(dao))
    }

    private fun capabilitiesFile(): File {
        // testDebugUnitTest runs with the app/ module dir as user.dir; walk up
        // so the test also passes when invoked from the repo root.
        var dir = File(System.getProperty("user.dir") ?: ".")
        repeat(4) {
            val candidate = File(dir, "TEACHTRACK_CAPABILITIES.json")
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("TEACHTRACK_CAPABILITIES.json not found above ${System.getProperty("user.dir")}")
    }

    @Test fun capabilitiesJsonMirrorsRegistryActions() {
        val root = JSONObject(capabilitiesFile().readText())
        val actions = root.getJSONArray("actions")
        val jsonNames = (0 until actions.length()).map { actions.getJSONObject(it).getString("name") }
        assertEquals(
            "TEACHTRACK_CAPABILITIES.json drifted from LocalCopilotActionRegistry.allowedActions",
            registry.allowedActions.sorted(),
            jsonNames.sorted(),
        )
    }

    @Test fun everyJsonEntryCarriesRequiredFields() {
        val root = JSONObject(capabilitiesFile().readText())
        val actions = root.getJSONArray("actions")
        for (i in 0 until actions.length()) {
            val entry = actions.getJSONObject(i)
            val name = entry.getString("name")
            assertTrue("$name: namespace must be non-empty", entry.getString("namespace").isNotBlank())
            assertTrue("$name: description must be non-empty", entry.getString("description").isNotBlank())
            assertNotNull("$name: params missing", entry.optJSONObject("params"))
            entry.getBoolean("readOnly")
            entry.getBoolean("destructive")
        }
    }

    @Test fun readOnlyFlagsMatchReadOnlyActionsSet() {
        val root = JSONObject(capabilitiesFile().readText())
        val actions = root.getJSONArray("actions")
        val jsonReadOnly = (0 until actions.length())
            .map { actions.getJSONObject(it) }
            .filter { it.getBoolean("readOnly") }
            .map { it.getString("name") }
            .toSet()
        assertEquals(READ_ONLY_ACTIONS, jsonReadOnly)
    }

    @Test fun everyRegistryActionIsClassifiedAsReadOnlyOrWrite() {
        // A registry action missing from READ_ONLY_ACTIONS is a write and must
        // require confirmation in the offload bridge; a read action missing
        // from the registry would 405. Both directions are covered here so
        // the two sets can't silently disagree.
        val unknown = READ_ONLY_ACTIONS - registry.allowedActions
        assertTrue("READ_ONLY_ACTIONS lists unregistered actions: $unknown", unknown.isEmpty())
    }
}
