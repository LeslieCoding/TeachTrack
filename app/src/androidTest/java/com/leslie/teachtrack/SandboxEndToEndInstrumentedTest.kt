package com.leslie.teachtrack

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.leslie.teachtrack.agent.TeachTrackAppContext
import com.openminis.app.sandbox.NativeOffloadServer
import com.openminis.app.sandbox.PRootKernel
import com.openminis.app.sandbox.ShellExecutor
import com.openminis.app.sandbox.RootfsManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec §58 literal end-to-end: drive the `teachtrack` CLI through the REAL
 * sandbox path — PRoot exec → native_offload extension → abstract socket →
 * NativeOffloadServer → TeachTrackOffloadHandler → registry — instead of
 * calling the registry directly. Also covers §57/§48 native-ability smoke
 * (rootfs boot, busybox, bundled skill install) and §52(c) register/start
 * idempotency, all in the app's own process.
 */
@RunWith(AndroidJUnit4::class)
class SandboxEndToEndInstrumentedTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun bootSandbox() = runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            // MinisApp.onCreate registers handlers + starts the offload server;
            // boot() is a no-op when the kernel is already up, and otherwise
            // installs/reuses the extracted Alpine rootfs on disk.
            PRootKernel.boot(context)
            assertTrue("PRoot kernel must be booted", PRootKernel.isBooted)
            assertTrue(
                "teachtrack handler must be registered",
                NativeOffloadServer.registeredHandlers.contains("teachtrack"),
            )
        }

        private fun sh(command: String, timeout: Long = 120_000): ShellExecutor.ShellResult = runBlocking {
            ShellExecutor.execute(
                ApplicationProvider.getApplicationContext<Context>(),
                command,
                timeout = timeout,
            )
        }
    }

    /** §58: the in-sandbox help text documents the real subcommands. */
    @Test fun sandboxHelpMentionsCapabilitiesAndRpc() {
        val result = sh("teachtrack --help")
        assertEquals(result.output, 0, result.exitCode)
        assertTrue(result.output, result.output.contains("capabilities"))
        assertTrue(result.output, result.output.contains("rpc"))
    }

    /** §58: capabilities through the real offload socket returns strict JSON. */
    @Test fun sandboxCapabilitiesReturnsStrictJson() {
        val result = sh("teachtrack capabilities")
        assertEquals(result.output, 0, result.exitCode)
        val line = result.output.lineSequence().first { it.trimStart().startsWith("{") }
        val json = JSONObject(line)
        assertTrue(json.getBoolean("ok"))
        val actions = json.getJSONObject("result").getJSONArray("actions")
        assertEquals(68, actions.length())
        val names = (0 until actions.length()).map { actions.getString(it) }
        assertTrue("score.create" in names)
        assertTrue("workspace.check" in names)
    }

    /** §58: a real seeded student round-trips through the sandbox rpc path. */
    @Test fun sandboxRpcSeesRealSeededStudent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = TeachTrackAppContext.repository(context)
        val marker = "沙箱验收学生"
        runBlocking { repository.saveStudent(marker, "", "", "", "§58 sandbox e2e") }
        try {
            val result = sh("""teachtrack rpc --json '{"action":"student.search","params":{"query":"$marker"}}'""")
            assertEquals(result.output, 0, result.exitCode)
            val line = result.output.lineSequence().first { it.trimStart().startsWith("{") }
            val json = JSONObject(line)
            assertTrue(json.toString(), json.getBoolean("ok"))
            assertTrue(json.toString(), json.toString().contains(marker))
        } finally {
            // Soft cleanup through the same repository op the UI uses.
            runBlocking {
                repository.searchStudents(marker).forEach { repository.archiveStudent(it.id) }
            }
        }
    }

    /** §57/§48: the sandbox itself works — Alpine userspace + busybox exec. */
    @Test fun sandboxNativeAbilitySmoke() {
        val uname = sh("uname -a")
        assertEquals(uname.output, 0, uname.exitCode)
        assertTrue(uname.output, uname.output.contains("Linux"))

        val busybox = sh("busybox | head -3")
        assertEquals(busybox.output, 0, busybox.exitCode)
        assertTrue(busybox.output, busybox.output.contains("BusyBox"))

        val rootfs = sh("cat /etc/alpine-release")
        assertEquals(rootfs.output, 0, rootfs.exitCode)
    }

    /** §57/§48: the bundled teachtrack skill 1.7.1 is installed into the rootfs-visible skills dir. */
    @Test fun bundledTeachtrackSkillInstalled() {
        val result = sh("head -5 /var/minis/skills/teachtrack/SKILL.md")
        assertEquals(result.output, 0, result.exitCode)
        assertTrue(result.output, result.output.contains("name: teachtrack"))
        val version = sh("grep '^version:' /var/minis/skills/teachtrack/SKILL.md")
        assertTrue(version.output, version.output.contains("version: 1.7.2"))
    }

    /** §52(c): re-registering the handler and re-starting the server are idempotent no-ops. */
    @Test fun registerAndStartAreIdempotent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val before = NativeOffloadServer.registeredHandlers
        // Same handler instance the app registered; register() is a map put,
        // start() returns early on the live socket — neither may crash or
        // drop the handler.
        NativeOffloadServer.register(
            "teachtrack",
            com.leslie.teachtrack.agent.TeachTrackOffloadHandler(context, RootfsManager.getInstance(context).rootfsDir),
        )
        NativeOffloadServer.start(RootfsManager.getInstance(context).rootfsDir)
        assertEquals(before, NativeOffloadServer.registeredHandlers)
        assertTrue(NativeOffloadServer.isRunning)

        // The bridge still answers after the churn.
        val result = sh("teachtrack capabilities")
        assertEquals(result.output, 0, result.exitCode)
        assertTrue(result.output.contains("\"ok\":true"))
    }
}
