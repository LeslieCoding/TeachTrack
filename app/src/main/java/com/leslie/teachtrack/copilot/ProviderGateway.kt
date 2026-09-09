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
    val timeoutSeconds: Int = 30
) {
    fun normalized() = copy(vendor = vendor.trim().ifBlank { "OpenAI-compatible" }, endpoint = endpoint.trim(), model = model.trim(), timeoutSeconds = timeoutSeconds.coerceIn(5, 120))
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
    fun load(): ProviderConfig = ProviderConfig(prefs.getBoolean("enabled", false), prefs.getString("vendor", "Fake / Offline").orEmpty(), prefs.getString("endpoint", "").orEmpty(), prefs.getString("model", "").orEmpty(), prefs.getInt("timeoutSeconds", 30)).normalized()
    fun save(config: ProviderConfig) { val safe = config.normalized(); prefs.edit().putBoolean("enabled", safe.enabled).putString("vendor", safe.vendor).putString("endpoint", safe.endpoint).putString("model", safe.model).putInt("timeoutSeconds", safe.timeoutSeconds).apply() }
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
        if (safe.endpoint.isBlank() || safe.model.isBlank() || apiKey.isNullOrBlank()) return@withContext ProviderResponse.Failure("NOT_CONFIGURED", "请先配置 endpoint、model 和 API Key")
        runCatching {
            val connection = (URL(safe.endpoint).openConnection() as HttpURLConnection).apply { requestMethod = "POST"; connectTimeout = safe.timeoutSeconds * 1000; readTimeout = safe.timeoutSeconds * 1000; doOutput = true; setRequestProperty("Authorization", "Bearer $apiKey"); setRequestProperty("Content-Type", "application/json"); setRequestProperty("Accept", "application/json") }
            val body = JSONObject().put("model", safe.model).put("temperature", 0).put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT)).put(JSONObject().put("role", "user").put("content", "用户请求：$input\n当前上下文：${JSONObject(context)}\n只返回符合 schema 的 JSON，不要 Markdown，不要解释。"))).toString()
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
    companion object { const val SYSTEM_PROMPT = "You are TeachTrack command planner. Convert the user's request into exactly one JSON object with schema=teachtrack.command.v1, action, confirmationRequired=true, clientRequestId, and string parameters. Use only requested application actions. If information is missing use action=navigation.open with clarification. Never return Markdown or explanations. All state-changing actions require confirmation." }
}

class ConfigurableCopilotProvider(context: Context) : CopilotProvider {
    private val preferences = ProviderPreferences(context.applicationContext)
    private val offline = OfflineProviderGateway()
    private val cloud = OpenAiCompatibleProviderGateway(preferences)
    override suspend fun interpret(input: String, context: Map<String, String>): CopilotCandidate {
        val config = preferences.load()
        if (!config.enabled) return (offline.interpret(input, context, config) as ProviderResponse.Success).candidate
        return when (val response = cloud.interpret(input, context, config)) {
            is ProviderResponse.Success -> response.candidate
            is ProviderResponse.Failure -> (offline.interpret(input, context, config) as ProviderResponse.Success).candidate.copy(explanation = "云端不可用，已切换离线解析：${response.message}", error = response.message)
        }
    }
}
