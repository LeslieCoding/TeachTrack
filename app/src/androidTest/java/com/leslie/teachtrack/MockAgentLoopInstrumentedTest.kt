package com.leslie.teachtrack

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.leslie.teachtrack.agent.TeachTrackAppContext
import com.openminis.app.MinisApp
import com.openminis.app.data.model.LLMModel
import com.openminis.app.data.model.ModelEntry
import com.openminis.app.data.model.ProviderCredential
import com.openminis.app.data.model.ProviderInstance
import com.openminis.app.data.model.ProviderType
import com.openminis.app.sandbox.PRootKernel
import com.openminis.app.ui.chat.ChatViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * §89 item 4 / §39 WITHOUT a real LLM key: prove the full OpenMinis agent loop
 * works end-to-end inside TeachTrack against a MOCK OpenAI-compatible provider
 * ([MockLlmServer], in-process on 127.0.0.1).
 *
 * The test configures a real provider instance + custom model entry through
 * the app's own ProviderRepository (custom base URL, empty API key — the same
 * "OpenAI-compatible local gateway" configuration a user would enter in
 * Settings), selects it in a real ChatViewModel, sends "列出我的学生", and
 * lets the genuine agent loop run: stream parse → tool_call → shell_execute
 * through the REAL PRoot sandbox → teachtrack CLI → NativeOffloadServer →
 * TeachTrack DB → tool result back to the "model" → final answer.
 *
 * Assertions that cannot be faked:
 *  1. the mock server received exactly the two expected requests (initial
 *     prompt, then prompt+tool-result) — logged for the evidence artifact;
 *  2. the final assistant message names a REAL student read live from the
 *     device database — the mock can only quote what the tool returned, so a
 *     correct name proves shell execution + data round-trip;
 *  3. the assistant message carries a shell tool block whose output also
 *     contains that student name.
 *
 * Cleanup restores the pre-test state: the mock provider instance (and its
 * model entries) are removed and the proof chat session is deleted.
 */
@RunWith(AndroidJUnit4::class)
class MockAgentLoopInstrumentedTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun bootSandbox() = runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            PRootKernel.boot(context)
            assertTrue("PRoot kernel must be booted", PRootKernel.isBooted)
        }
    }

    @Test
    fun mockLlmDrivesRealAgentLoop() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<MinisApp>()
        val context = app.applicationContext
        val runStamp = SimpleDateFormat("HHmmss", Locale.US).format(Date())
        File(context.filesDir, "mock-e2e-marker-$runStamp.txt").writeText("started ${System.currentTimeMillis()}\n")
        android.util.Log.i("MockE2E", "test body entered stamp=$runStamp")
        val providerRepo = app.providerRepository

        // Wait for the async provider-config load before mutating it.
        var waited = 0L
        while (!providerRepo.configLoaded.value && waited < 15_000) { delay(100); waited += 100 }
        assertTrue("provider config must finish loading", providerRepo.configLoaded.value)

        // Ground truth: a real student in the device DB (release device holds
        // real user data; any non-empty roster proves the data path).
        val ttRepo = TeachTrackAppContext.repository(context)
        val students = ttRepo.searchStudents("")
        assertTrue("device DB must hold at least one student for this proof", students.isNotEmpty())
        val knownName = students.first().name

        val mock = MockLlmServer()
        val port = mock.start()
        val instanceId = "mock-e2e-" + UUID.randomUUID().toString()
        var vm: ChatViewModel? = null
        var createdSessionId: String? = null
        try {
            // Configure the mock provider exactly like a user adding an
            // OpenAI-compatible local gateway in Settings (empty key allowed).
            providerRepo.addInstance(
                ProviderInstance(
                    id = instanceId,
                    label = "MOCK-LLM (delivery proof §89.4)",
                    providerType = ProviderType.openAI,
                    credentialType = ProviderCredential.apiKey,
                    customBaseURL = "http://127.0.0.1:$port",
                    appendV1Suffix = true,
                ),
            )
            val entry = ModelEntry(
                providerInstanceId = instanceId,
                baseModel = LLMModel(id = "mock-model", displayName = "Mock Model", provider = "OpenAI"),
                isCustom = true,
            )
            providerRepo.addEntry(entry)

            // saveConfig canonicalizes entry ids to "{instanceId}/{modelId}"
            // on emit — resolve the entry's id AFTER the round-trip instead
            // of trusting the pre-save uuid.
            val cfgNow = providerRepo.config.value
            assertTrue("instance must be in config", cfgNow.instances.any { it.id == instanceId })
            val canonicalEntryId = cfgNow.modelEntries
                .firstOrNull { it.providerInstanceId == instanceId && it.baseModel.id == "mock-model" }?.id
            assertTrue("entry must be in config", canonicalEntryId != null)

            vm = ChatViewModel(
                sessionId = "__new__${UUID.randomUUID()}",
                chatRepository = app.chatRepository,
                providerRepository = providerRepo,
                context = context,
                memoryRepository = app.memoryRepository,
                skillRepository = app.skillRepository,
                mcpRepository = app.mcpRepository,
            )
            vm.selectEntry(canonicalEntryId!!)
            android.util.Log.i("MockE2E", "after selectEntry modelName=${vm.modelName.value} error=${vm.error.value}")
            vm.sendMessage("列出我的学生")

            val deadline = System.currentTimeMillis() + 240_000
            while (System.currentTimeMillis() < deadline) {
                if (!vm.isStreaming.value) break
                delay(250)
            }
            assertTrue("agent loop must finish within 240s (error=${vm.error.value})", !vm.isStreaming.value)
            assertTrue("no chat error expected, got: ${vm.error.value}", vm.error.value == null)
            createdSessionId = vm.currentSessionId

            // The mock must have seen the two-turn tool-calling conversation.
            assertTrue(
                "mock server must receive >=2 requests, got ${mock.requestCount}",
                mock.requestCount >= 2,
            )
            val logText = synchronized(mock.requestLog) { mock.requestLog.toString() }
            assertTrue("turn 2 must carry a tool result back to the model", logText.contains("\"role\":\"tool\""))

            // The final assistant answer must name a REAL student — information
            // the mock could only obtain from the executed shell's output.
            val assistantMsgs = vm.messages.value.filter { it.role == "assistant" }
            val finalText = assistantMsgs.lastOrNull()?.content ?: ""
            assertTrue(
                "final answer must contain real student '$knownName'; got: $finalText",
                finalText.contains(knownName),
            )

            // And the shell tool block must show the real CLI output.
            val shellBlocks = assistantMsgs.flatMap { it.toolBlocks }
                .filter { it.kind == "tool_use" && it.toolName == "shell_execute" }
            assertTrue("a shell_execute tool block must exist", shellBlocks.isNotEmpty())
            assertTrue(
                "shell tool output must contain real student '$knownName'",
                shellBlocks.any { it.content.contains(knownName) },
            )

            // Evidence artifact: full mock request log + chat transcript.
            saveEvidence(mock, vm.messages.value, knownName)
        } finally {
            File(context.filesDir, "mock-e2e-marker-$runStamp.txt").appendText("finished ${System.currentTimeMillis()}\n")
            runCatching { providerRepo.removeInstance(instanceId) }
            createdSessionId?.let { sid -> runCatching { app.chatRepository.deleteSession(sid) } }
            mock.stop()
        }
    }

    private fun saveEvidence(mock: MockLlmServer, messages: List<com.openminis.app.ui.chat.ChatMessage>, knownName: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.append("§89 item 4 mock-LLM agent-loop proof — ").append(stamp).append('\n')
        sb.append("ground-truth student asserted: ").append(knownName).append('\n')
        sb.append("mock requests received: ").append(mock.requestCount).append("\n\n")
        sb.append("---- mock server request log ----\n")
        sb.append(synchronized(mock.requestLog) { mock.requestLog.toString() })
        sb.append("\n---- chat transcript ----\n")
        for (m in messages) {
            sb.append("[").append(m.role).append("] ").append(m.content).append('\n')
            for (b in m.toolBlocks) {
                sb.append("  <tool kind=").append(b.kind)
                    .append(" name=").append(b.toolName)
                    .append(" title=").append(b.toolTitle).append(">\n")
                sb.append("  args: ").append(b.toolArgs).append('\n')
                sb.append("  out: ").append(b.content.take(2000)).append('\n')
            }
        }
        val text = sb.toString()
        // Dual-write: app-internal dir (always writable, pull via run-as on
        // debug builds) and the external app dir when available.
        val targets = listOfNotNull(
            File(context.filesDir, "mock-agent-e2e-$stamp.txt"),
            context.getExternalFilesDir(null)?.let { File(it, "mock-agent-e2e-$stamp.txt") },
        )
        for (f in targets) {
            runCatching { f.writeText(text) }
                .onSuccess { android.util.Log.i("MockE2E", "evidence written: ${f.absolutePath} (${text.length} chars)") }
                .onFailure { android.util.Log.e("MockE2E", "evidence write failed: ${f.absolutePath}: ${it.message}") }
        }
    }
}
