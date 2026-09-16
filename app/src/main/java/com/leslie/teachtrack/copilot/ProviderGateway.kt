package com.leslie.teachtrack.copilot

import android.content.Context
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ProviderConfig(
    val enabled: Boolean = false,
    val vendor: String = "Fake / Offline",
    val endpoint: String = "",
    val model: String = "",
    val timeoutSeconds: Int = 30,
    /** Stable provider identity; display name is not used for routing. */
    val providerId: String = "custom",
    /** Cloud Provider currently accepts API_KEY only. OAuth is handled by account adapters. */
    val authMode: String = "API_KEY"
) {
    fun normalized() = copy(vendor = vendor.trim().ifBlank { "OpenAI-compatible" }, endpoint = endpoint.trim(), model = model.trim(), timeoutSeconds = timeoutSeconds.coerceIn(5, 120), providerId = providerId.trim().ifBlank { "custom" }, authMode = authMode.trim().ifBlank { "API_KEY" })
}

sealed interface ProviderResponse {
    data class Success(val candidate: CopilotCandidate) : ProviderResponse
    data class Failure(val code: String, val message: String) : ProviderResponse
}

interface ProviderGateway {
    suspend fun interpret(input: String, context: Map<String, String>, config: ProviderConfig): ProviderResponse
    suspend fun testConnection(config: ProviderConfig): ProviderResponse
}

class OfflineProviderGateway(private val provider: CopilotProvider = FakeCopilotProvider()) : ProviderGateway {
    override suspend fun interpret(input: String, context: Map<String, String>, config: ProviderConfig): ProviderResponse = ProviderResponse.Success(provider.interpret(input, context))
    override suspend fun testConnection(config: ProviderConfig): ProviderResponse = ProviderResponse.Success(CopilotCandidate(null, "离线 Provider 可用；不会访问网络"))
}

class UnconfiguredCloudProviderGateway : ProviderGateway {
    override suspend fun interpret(input: String, context: Map<String, String>, config: ProviderConfig): ProviderResponse = ProviderResponse.Failure("NOT_CONFIGURED", "云端 Provider 尚未配置完整，已保留离线功能")
    override suspend fun testConnection(config: ProviderConfig): ProviderResponse = ProviderResponse.Failure("NOT_CONFIGURED", "请提供 endpoint、model 和 API Key")
}

/** Provider metadata is local; the API key is stored separately using Android Keystore. */
class ProviderPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("teachtrack_provider", Context.MODE_PRIVATE)
    private val secretStore = SecureApiKeyStore(context.applicationContext)
    fun load(): ProviderConfig = ProviderConfig(prefs.getBoolean("enabled", false), prefs.getString("vendor", "Fake / Offline").orEmpty(), prefs.getString("endpoint", "").orEmpty(), prefs.getString("model", "").orEmpty(), prefs.getInt("timeoutSeconds", 30), prefs.getString("providerId", "custom").orEmpty(), prefs.getString("authMode", "API_KEY").orEmpty()).normalized()
    fun save(config: ProviderConfig) { val safe = config.normalized(); prefs.edit().putBoolean("enabled", safe.enabled).putString("vendor", safe.vendor).putString("endpoint", safe.endpoint).putString("model", safe.model).putInt("timeoutSeconds", safe.timeoutSeconds).putString("providerId", safe.providerId).putString("authMode", safe.authMode).apply() }
    fun hasApiKey(): Boolean = secretStore.hasKey()
    fun saveApiKey(value: String) { secretStore.save(value) }
    fun clearApiKey() { secretStore.clear() }
    internal fun loadApiKey(): String? = secretStore.load()
}

private class SecureApiKeyStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("teachtrack_provider_secret", Context.MODE_PRIVATE)
    private val alias = "TeachTrack.Provider.ApiKey"
    fun hasKey(): Boolean = !prefs.getString("ciphertext", null).isNullOrBlank()
    fun save(value: String) {
        if (value.isBlank()) return
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        prefs.edit().putString("ciphertext", Base64.encodeToString(cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)).putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).apply()
    }
    fun load(): String? = runCatching {
        val ciphertext = prefs.getString("ciphertext", null) ?: return null
        val iv = Base64.decode(prefs.getString("iv", ""), Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(Base64.decode(ciphertext, Base64.DEFAULT)), StandardCharsets.UTF_8)
    }.getOrNull()
    fun clear() { prefs.edit().clear().apply() }
    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
}

/** Generic OpenAI-compatible chat-completions adapter. */
class OpenAiCompatibleProviderGateway(private val preferences: ProviderPreferences) : ProviderGateway {
    override suspend fun interpret(input: String, context: Map<String, String>, config: ProviderConfig): ProviderResponse = request(config, input, context)
    override suspend fun testConnection(config: ProviderConfig): ProviderResponse = request(config, "Return a valid empty TeachTrack command envelope.", emptyMap())
    private suspend fun request(config: ProviderConfig, input: String, context: Map<String, String>): ProviderResponse = withContext(Dispatchers.IO) {
        val safe = config.normalized(); val apiKey = preferences.loadApiKey()
        if (safe.authMode != "API_KEY") return@withContext ProviderResponse.Failure("AUTH_MODE_MISMATCH", "当前 Provider 仅支持 API Key 调用；账号 OAuth 请使用 OAuth 登录模块")
        if (safe.endpoint.isBlank() || safe.model.isBlank() || apiKey.isNullOrBlank()) return@withContext ProviderResponse.Failure("NOT_CONFIGURED", "请先配置 endpoint、model 和 API Key")
        runCatching {
            val connection = (URL(safe.endpoint).openConnection() as HttpURLConnection).apply { requestMethod = "POST"; connectTimeout = safe.timeoutSeconds * 1000; readTimeout = safe.timeoutSeconds * 1000; doOutput = true; setRequestProperty("Authorization", "Bearer $apiKey"); setRequestProperty("Content-Type", "application/json"); setRequestProperty("Accept", "application/json") }
            val body = JSONObject().put("model", safe.model).put("temperature", 0).put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT + EXTENDED_COMMAND_POLICY + FULL_COVERAGE_COMMAND_POLICY)).put(JSONObject().put("role", "user").put("content", "用户请求：$input\n当前上下文：${JSONObject(context)}\n只返回符合 schema 的 JSON，不要 Markdown，不要解释。"))).toString()
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val status = connection.responseCode; val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.use { BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText() }.orEmpty(); connection.disconnect()
            if (status !in 200..299) ProviderResponse.Failure("HTTP_$status", "云端 Provider 请求失败（HTTP $status）") else ProviderResponse.Success(parseCandidate(responseBody))
        }.getOrElse { error -> ProviderResponse.Failure("NETWORK_ERROR", "云端 Provider 暂时不可用：${error.javaClass.simpleName}") }
    }
    internal fun parseCandidate(raw: String): CopilotCandidate {
        val content = JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return CopilotCandidate(CopilotCommandCodec.decode(content), "已由云端 Provider 解析为可确认命令")
    }
    companion object {
        const val SYSTEM_PROMPT = "You are the TeachTrack command planner. Return exactly one JSON object with schema teachtrack.command.v1, action, confirmationRequired, clientRequestId, and string parameters. Allowed actions: student.create/update/search/archive; course.create/resume/timeline; exam.create; score.create/query; homework.create; problem.create/status.update; knowledge.update/query; communication.create; lesson_plan.create; task.create; template.create; tag.create; attachment.list; dashboard.query; analytics.query; navigation.open. Parameters may include studentName, selectedStudentId, studentId, grade, school, subject, score, fullScore, examName, date, rawText, content, title, summary, contact, followUp, nextLessonPlan, focus, homework, dueDate, priority, knowledgeName, knowledgePointId, level, problemId, status, notes, destination. Use exact singular action names. Never invent IDs, names, scores, or family information. For a missing required field, return the action with known fields and let the app clarify. Read-only and navigation actions may set confirmationRequired false; every write must set it true. Never return Markdown, SQL, code, or explanations."
        const val EXTENDED_COMMAND_POLICY = " Additional allowed actions: student.data.organize.preview, student.followup.tasks.create and knowledge.weakness.query. Use student.data.organize.preview for requests to organize, audit, summarize, or produce a follow-up view for all student data. It is read-only and must set confirmationRequired false. Use student.followup.tasks.create only when the user explicitly asks to turn organization findings into follow-up tasks; it requires confirmation. Use knowledge.weakness.query for cross-student questions about who needs reinforcement for a named knowledge point; include knowledgeName and set confirmationRequired false. Never convert a broad organization request into bulk edits, archive, deletion, or guessed profile changes."
        const val FULL_COVERAGE_COMMAND_POLICY = " Further allowed actions covering every remaining TeachTrack module: student.overview.query (read-only single-student summary; confirmationRequired false); knowledge.create (studentName not required; needs knowledgeName, optional subject/grade/description; confirmationRequired true); task.status.update (needs status such as 已完成, and either taskId or enough of the original sentence in rawText/title to identify one existing task; confirmationRequired true); parent_contact.create (needs studentName; optional contactName, phone, relation, isPrimary, notes; confirmationRequired true) and parent_contact.query (read-only; confirmationRequired false); template.list and template.render (read-only; confirmationRequired false; template.render needs templateName and may use studentName/date/content/performance/nextPlan) and template.update/template.delete (need templateName; confirmationRequired true); tag.query (read-only; confirmationRequired false) and tag.assign (needs studentName and tagName; confirmationRequired true); attachment.query (read-only, needs studentName; confirmationRequired false) and attachment.delete (needs attachmentId; confirmationRequired true); backup.list (read-only; confirmationRequired false), backup.export (creates a local backup file; confirmationRequired true), backup.restore (needs optional fileName, defaults to the most recent local backup; confirmationRequired true), and csv.export.scores (creates a local CSV file; confirmationRequired true); workspace.status.query, workspace.check, workspace.sync.scan, workspace.conflict.list (all read-only; confirmationRequired false); workspace.export (confirmationRequired true) and workspace.conflict.resolve (needs conflictFile and choice, one of VERSION_A/VERSION_B/MANUAL_MERGE; confirmationRequired true). Never emit an action to change the app's own Provider or OAuth configuration — that surface is intentionally not exposed to the model and stays manual in Settings."
    }
}

/** Kimi Code account OAuth adapter. It never reads the API-key keystore. */
class KimiCodeOAuthProviderGateway(private val oauth: OAuthPreferences) : ProviderGateway {
    override suspend fun interpret(input: String, context: Map<String, String>, config: ProviderConfig): ProviderResponse = request(input, context, config)
    override suspend fun testConnection(config: ProviderConfig): ProviderResponse = request("Return a valid empty TeachTrack command envelope.", emptyMap(), config)

    private suspend fun request(input: String, context: Map<String, String>, config: ProviderConfig): ProviderResponse = withContext(Dispatchers.IO) {
        val token = oauth.accessToken()
            ?: return@withContext ProviderResponse.Failure("OAUTH_NOT_AUTHENTICATED", "Kimi Code OAuth 尚未登录，请先在 OAuth 登录模块完成登录")
        val safe = config.normalized()
        val endpoint = "https://api.kimi.com/coding/v1/chat/completions"
        val model = safe.model.ifBlank { "kimi-for-coding" }
        runCatching {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = safe.timeoutSeconds * 1000
                readTimeout = safe.timeoutSeconds * 1000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "TeachTrack/3.0")
            }
            val body = JSONObject()
                .put("model", model)
                // Kimi Code managed models currently require temperature=1.
                .put("temperature", 1)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", OpenAiCompatibleProviderGateway.SYSTEM_PROMPT + OpenAiCompatibleProviderGateway.EXTENDED_COMMAND_POLICY + OpenAiCompatibleProviderGateway.FULL_COVERAGE_COMMAND_POLICY))
                    .put(JSONObject().put("role", "user").put("content", "用户请求：$input\n当前上下文：${JSONObject(context)}\n只返回符合 schema 的 JSON，不要 Markdown，不要解释。")))
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.use { BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText() }.orEmpty()
            connection.disconnect()
            if (status !in 200..299) {
                val detail = runCatching {
                    val json = JSONObject(responseBody)
                    json.optJSONObject("error")?.optString("message")
                        ?.ifBlank { json.optString("message") }
                        ?.ifBlank { null }
                        ?: json.optString("message").ifBlank { null }
                }.getOrNull().orEmpty().take(180)
                ProviderResponse.Failure("HTTP_$status", "Kimi Code OAuth 请求失败（HTTP $status）${if (detail.isBlank()) "" else "：$detail"}")
            } else {
                val content = JSONObject(responseBody).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                ProviderResponse.Success(CopilotCandidate(CopilotCommandCodec.decode(content), "已由 Kimi Code OAuth 解析为可确认命令"))
            }
        }.getOrElse { error -> ProviderResponse.Failure("NETWORK_ERROR", "Kimi Code OAuth 暂时不可用：${error.javaClass.simpleName}") }
    }
}

class ConfigurableCopilotProvider(context: Context) : CopilotProvider {
    private val preferences = ProviderPreferences(context.applicationContext)
    private val oauthPreferences = OAuthPreferences(context.applicationContext)
    private val offline = OfflineProviderGateway()
    private val cloud = OpenAiCompatibleProviderGateway(preferences)
    private val kimiOauth = KimiCodeOAuthProviderGateway(oauthPreferences)
    override suspend fun interpret(input: String, context: Map<String, String>): CopilotCandidate {
        val config = preferences.load()
        if (!config.enabled) return (offline.interpret(input, context, config) as ProviderResponse.Success).candidate
        val oauthConfig = oauthPreferences.load()
        val useKimiOAuth = oauthPreferences.hasSession() && oauthConfig.provider == "kimi_code" && config.authMode == "OAUTH_DEVICE"
        val response = when {
            useKimiOAuth -> kimiOauth.interpret(input, context, config)
            oauthPreferences.hasSession() && oauthConfig.provider != "kimi_code" -> ProviderResponse.Failure("OAUTH_ADAPTER_UNAVAILABLE", "当前已登录 ${oauthConfig.provider} 账号，但该账号的 TeachTrack 操作适配器尚未启用；请选择已支持的 OAuth Provider，或切换到 API Key 模式")
            else -> cloud.interpret(input, context, config)
        }
        return when (response) {
            is ProviderResponse.Success -> response.candidate
            is ProviderResponse.Failure -> (offline.interpret(input, context, config) as ProviderResponse.Success).candidate.copy(explanation = "云端不可用，已切换离线解析：${response.message}", error = response.message)
        }
    }
}
