package com.leslie.teachtrack.agent

import android.content.Context
import com.leslie.teachtrack.copilot.CopilotAuditStore
import com.leslie.teachtrack.copilot.CopilotCommand
import com.leslie.teachtrack.copilot.CopilotCommandValidator
import com.leslie.teachtrack.copilot.CopilotResult
import com.leslie.teachtrack.copilot.CopilotService
import com.leslie.teachtrack.copilot.ConfigurableCopilotProvider
import com.leslie.teachtrack.copilot.READ_ONLY_ACTIONS
import com.openminis.app.sandbox.NativeOffloadHandler
import com.openminis.app.sandbox.NativeOffloadRequest
import com.openminis.app.sandbox.NativeOffloadResult
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The ONE bridge between the OpenMinis Agent's Linux shell and TeachTrack's
 * real business logic (LAEP MASTER TASK sections 9-13). Registered as
 * `NativeOffloadServer.register("teachtrack", TeachTrackOffloadHandler(...))`
 * in `MinisApp.kt`, alongside OpenMinis's own `android-*`/`minis-*` handlers.
 *
 * This class intentionally contains NO business logic of its own. It only:
 *  1. parses the `teachtrack <subcommand> ...` argv the guest shell issued,
 *  2. turns it into the exact same [CopilotCommand] the existing in-app
 *     Copilot chat window already builds and sends through [CopilotService],
 *  3. runs it with `confirmed = true` in OWNER_TRUSTED access mode (the
 *     default — spec section 43); in STANDARD mode every non-read-only
 *     action is refused with a CONFIRMATION_REQUIRED JSON error instead
 *     (see `TeachTrackIntegrationSettings`),
 *  4. formats the [CopilotResult] as the strict JSON envelope spec section
 *     35 defines, so the Agent never has to parse natural-language errors.
 *
 * `handle()` runs on the NativeOffloadServer's own worker thread, synchronously
 * (the offload IPC server writes the result to a rootfs tmpfile before
 * replying), so suspend calls are bridged with `runBlocking` — the same
 * pattern OpenMinis's own `OffloadGate` uses for the same reason.
 */
class TeachTrackOffloadHandler(private val context: Context, private val rootfsDir: File) : NativeOffloadHandler {

    private val service: CopilotService by lazy {
        val appContext = context.applicationContext
        val audit = CopilotAuditStore(appContext)
        CopilotService(
            ConfigurableCopilotProvider(appContext),
            TeachTrackAppContext.actionRegistry(appContext),
        ) { command, result -> audit.record(command, result) }
    }
    private val registry get() = TeachTrackAppContext.actionRegistry(context.applicationContext)

    override fun handle(request: NativeOffloadRequest): NativeOffloadResult {
        val args = request.argv.drop(1) // argv[0] is "teachtrack" itself
        return try {
            when (args.firstOrNull()) {
                "capabilities" -> handleCapabilities()
                "rpc" -> handleRpc(args.drop(1), request)
                null, "--help", "-h" -> NativeOffloadResult(0, HELP)
                else -> errorResult(2, "UNKNOWN_SUBCOMMAND", "Unknown subcommand '${args.firstOrNull()}'. Usage:\n$HELP")
            }
        } catch (e: Exception) {
            errorResult(1, "INTERNAL_ERROR", e.message ?: e.toString())
        }
    }

    private fun handleCapabilities(): NativeOffloadResult {
        val body = JSONObject()
            .put("ok", true)
            .put("operation", "capabilities")
            .put("result", JSONObject().put("actions", JSONArray(registry.allowedActions.sorted())))
        return NativeOffloadResult(0, body.toString() + "\n")
    }

    private fun handleRpc(rpcArgs: List<String>, request: NativeOffloadRequest): NativeOffloadResult {
        val jsonText = readRpcPayload(rpcArgs, request.cwd)
            ?: return errorResult(2, "BAD_ARGS", "rpc requires --json '<json>' or --file <path>.\n$HELP")

        val parsed = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            return errorResult(2, "PARSE_ERROR", "Invalid JSON: ${e.message}")
        }

        val action = parsed.optString("action").takeIf { it.isNotBlank() }
            ?: return errorResult(2, "BAD_ARGS", "Missing required field: action")
        val paramsObj = parsed.optJSONObject("params") ?: JSONObject()
        val params = mutableMapOf<String, String>()
        paramsObj.keys().forEach { key -> params[key] = paramsObj.optString(key) }

        val command = CopilotCommand(
            action = action,
            confirmationRequired = action !in READ_ONLY_ACTIONS,
            parameters = params,
            clientRequestId = request.sessionId ?: "",
        )

        val validationError = CopilotCommandValidator(registry.allowedActions).validate(command)
        if (validationError != null) {
            return errorResult(1, "VALIDATION_ERROR", validationError, action)
        }

        // Access mode (spec §43/§45): OWNER_TRUSTED (the default for the
        // owner's personal app) auto-confirms writes because the Agent has no
        // UI to show an interactive confirmation dialog to. STANDARD refuses
        // every non-read-only action with CONFIRMATION_REQUIRED so the agent
        // can relay the refusal instead of silently writing. Read-only and
        // navigation actions always run in both modes.
        // Section 44's short hard-boundary list (permanent delete, DB wipe,
        // Workspace overwrite/restore) has no corresponding entry in
        // TeachTrack's own allowedActions today — the project's standing
        // "archive before delete" principle already keeps destructive
        // operations out of this surface (see STATE/DECISIONS.md DEC-001).
        if (TeachTrackIntegrationSettings.accessMode(context) == TeachTrackIntegrationSettings.MODE_STANDARD
            && action !in READ_ONLY_ACTIONS
        ) {
            return errorResult(
                1, "CONFIRMATION_REQUIRED",
                "Access mode is 'standard': write action '$action' was not executed. " +
                    "Ask the user to switch to Owner Trusted in Settings → TeachTrack Integration, or confirm the change in the app.",
                action,
            )
        }
        val result = runBlocking { service.execute(command, confirmed = true) }
        return formatResult(action, result)
    }

    /**
     * Guest paths live inside the PRoot rootfs, which is a real Android
     * directory ([rootfsDir]) this handler can read directly — no PRoot
     * involvement needed on the host side. A relative `--file` path resolves
     * against the guest's own cwd, matching normal shell semantics.
     */
    private fun readRpcPayload(rpcArgs: List<String>, guestCwd: String): String? {
        var i = 0
        while (i < rpcArgs.size) {
            when (rpcArgs[i]) {
                "--json" -> return rpcArgs.getOrNull(i + 1)
                "--file" -> {
                    val guestPath = rpcArgs.getOrNull(i + 1) ?: return null
                    val hostPath = resolveRootfsFile(rootfsDir, guestCwd, guestPath)
                    return if (hostPath.isFile) hostPath.readText() else null
                }
            }
            i++
        }
        return null
    }

    private fun formatResult(action: String, result: CopilotResult): NativeOffloadResult {
        val body = if (result.success) {
            JSONObject()
                .put("ok", true)
                .put("operation", action)
                .put("message", result.message)
                .put("result", JSONObject(result.data))
                .also { obj -> result.undoToken?.let { obj.put("undoToken", it) } }
        } else {
            JSONObject()
                .put("ok", false)
                .put("operation", action)
                .put("error", JSONObject().put("code", "EXECUTION_FAILED").put("message", result.message))
        }
        return NativeOffloadResult(if (result.success) 0 else 1, body.toString() + "\n")
    }

    private fun errorResult(exitCode: Int, code: String, message: String, action: String? = null): NativeOffloadResult {
        val body = JSONObject().put("ok", false)
        if (action != null) body.put("operation", action)
        body.put("error", JSONObject().put("code", code).put("message", message))
        return NativeOffloadResult(exitCode, body.toString() + "\n")
    }

    companion object {
        /**
         * Maps a PRoot guest path to its host-side file without allowing a
         * guest `..` segment (or a crafted absolute path) to escape the
         * sandbox rootfs.  Native Offload executes in-process, so this
         * containment check is a security boundary rather than a cosmetic
         * path-normalisation step.
         */
        internal fun resolveRootfsFile(rootfsDir: File, guestCwd: String, guestPath: String): File {
            val root = rootfsDir.canonicalFile
            val guestAbsolute = if (guestPath.startsWith("/")) guestPath else "$guestCwd/$guestPath"
            // A guest-leading slash is rooted inside PRoot, not Android's filesystem.
            val guestRelative = guestAbsolute.trimStart('/')
            val candidate = File(root, guestRelative).canonicalFile
            require(
                candidate.path == root.path || candidate.path.startsWith(root.path + File.separator)
            ) { "RPC payload path escapes the TeachTrack Agent rootfs" }
            return candidate
        }

        private const val HELP = """teachtrack — operate TeachTrack from the Agent shell

Usage:
  teachtrack capabilities              List every action this bridge supports.
  teachtrack rpc --json '<json>'       Run one action: {"action":"...","params":{...}}
  teachtrack rpc --file <path>         Same, JSON payload read from a file (for large params).

Result is always one line of strict JSON:
  success: {"ok":true,"operation":"...","message":"...","result":{...}}
  failure: {"ok":false,"operation":"...","error":{"code":"...","message":"..."}}

See the teachtrack skill for the full capability list, ID rules, and examples.
"""
    }
}
