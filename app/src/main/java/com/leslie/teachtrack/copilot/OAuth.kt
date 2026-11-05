package com.leslie.teachtrack.copilot

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class OAuthConfig(
    val enabled: Boolean = false,
    val provider: String = "openai_codex",
    val authorizationEndpoint: String = "",
    val tokenEndpoint: String = "",
    val clientId: String = "",
    val scopes: String = "openid profile email offline_access",
    val redirectUri: String = OAuthManager.REDIRECT_URI
) {
    fun normalized() = copy(provider = provider.trim().ifBlank { "openai_codex" }, authorizationEndpoint = authorizationEndpoint.trim(), tokenEndpoint = tokenEndpoint.trim(), clientId = clientId.trim(), scopes = scopes.trim().ifBlank { "openid profile" }, redirectUri = OAuthManager.REDIRECT_URI)
}

data class OAuthProviderPreset(
    val id: String,
    val displayName: String,
    val authMode: String,
    val consoleUrl: String = "",
    val authorizationEndpoint: String = "",
    val tokenEndpoint: String = "",
    val clientId: String = "",
    val scopes: String = "openid profile",
    val note: String
)

object OAuthProviderCatalog {
    /** Account OAuth providers. API-console links must never appear here. */
    val oauthPresets = listOf(
        OAuthProviderPreset("openai_codex", "OpenAI / ChatGPT Codex", "OAUTH_PKCE", authorizationEndpoint = "https://auth.openai.com/oauth/authorize", tokenEndpoint = "https://auth.openai.com/oauth/token", clientId = "app_EMoamEEZ73f0CkXaXp7hrann", scopes = "openid profile email offline_access", note = "账号 OAuth：浏览器登录 ChatGPT/Codex，回调后交换账号令牌。"),
        OAuthProviderPreset("claude_account", "Claude 账号", "OAUTH_PKCE", authorizationEndpoint = "https://claude.ai/oauth/authorize", tokenEndpoint = "https://console.anthropic.com/v1/oauth/token", clientId = "9d1c250a-e61b-44d9-88ed-5944d1962f5e", scopes = "org:create_api_key user:profile user:inference", note = "账号 OAuth：浏览器登录 Claude，回调后交换账号令牌。"),
        OAuthProviderPreset("kimi_code", "Kimi Code", "OAUTH_DEVICE", note = "账号 OAuth：设备授权码流程，在 auth.kimi.com 确认后由 App 轮询授权结果。")
    )
    /** API-key providers. These are intentionally separate from OAuth. */
    val apiPresets = listOf(
        OAuthProviderPreset("openai", "OpenAI / GPT API", "API_KEY", "https://platform.openai.com/api-keys", note = "API Key 模式：输入 OpenAI Platform API Key。"),
        OAuthProviderPreset("kimi", "Kimi / Moonshot API", "API_KEY", "https://platform.moonshot.cn/console/api-keys", note = "API Key 模式：输入 Moonshot API Key。"),
        OAuthProviderPreset("kimi_code_api", "Kimi Code API", "API_KEY", "https://www.kimi.com/code/en", note = "API Key 模式：Kimi Code API 与 Moonshot API 分开管理。"),
        OAuthProviderPreset("claude_api", "Claude / Anthropic API", "API_KEY", "https://console.anthropic.com/", note = "API Key 模式：输入 Anthropic Console API Key。"),
        OAuthProviderPreset("deepseek", "DeepSeek API", "API_KEY", "https://platform.deepseek.com/api_keys", note = "API Key 模式。"),
        OAuthProviderPreset("zhipu", "智谱 GLM API", "API_KEY", "https://open.bigmodel.cn/usercenter/apikeys", note = "API Key 模式。")
    )
    val presets: List<OAuthProviderPreset> = oauthPresets + apiPresets
    fun find(provider: String): OAuthProviderPreset = presets.firstOrNull { it.displayName == provider || it.id == provider } ?: oauthPresets.first()
    fun findOAuth(provider: String): OAuthProviderPreset = oauthPresets.firstOrNull { it.displayName == provider || it.id == provider } ?: oauthPresets.first()
}

class OAuthPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("teachtrack_oauth", Context.MODE_PRIVATE)
    private val tokens = OAuthTokenStore(context.applicationContext)
    fun load(): OAuthConfig = OAuthConfig(prefs.getBoolean("enabled", false), prefs.getString("provider", "openai_codex").orEmpty(), prefs.getString("authorizationEndpoint", "").orEmpty(), prefs.getString("tokenEndpoint", "").orEmpty(), prefs.getString("clientId", "").orEmpty(), prefs.getString("scopes", "openid profile email offline_access").orEmpty()).normalized()
    fun save(config: OAuthConfig) { val safe = config.normalized(); prefs.edit().putBoolean("enabled", safe.enabled).putString("provider", safe.provider).putString("authorizationEndpoint", safe.authorizationEndpoint).putString("tokenEndpoint", safe.tokenEndpoint).putString("clientId", safe.clientId).putString("scopes", safe.scopes).apply() }
    fun hasSession(): Boolean = tokens.hasSession()
    /** Returns only the access token from the encrypted OAuth token bundle. */
    internal fun accessToken(): String? = tokens.loadAccessToken()
    fun clearSession() { tokens.clear(); prefs.edit().remove("status").apply() }
    fun status(): String = prefs.getString("status", "尚未登录") ?: "尚未登录"
    internal fun pendingState(): String? = prefs.getString("pending_state", null)
    internal fun pendingVerifier(): String? = prefs.getString("pending_verifier", null)
    internal fun savePending(state: String, verifier: String) { prefs.edit().putString("pending_state", state).putString("pending_verifier", verifier).putString("status", "等待浏览器完成账号授权").apply() }
    internal fun clearPending() { prefs.edit().remove("pending_state").remove("pending_verifier").apply() }
    internal fun saveTokens(body: String) { tokens.save(body); prefs.edit().putString("status", "账号 OAuth 登录成功").apply() }
    internal fun fail(message: String) { prefs.edit().putString("status", message).apply() }
}

internal class OAuthTokenStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("teachtrack_oauth_secret", Context.MODE_PRIVATE)
    private val alias = "TeachTrack.OAuth.Token"
    fun hasSession(): Boolean = !prefs.getString("ciphertext", null).isNullOrBlank()
    fun save(value: String) { val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key()); prefs.edit().putString("ciphertext", Base64.encodeToString(cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)).putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).apply() }
    fun clear() { prefs.edit().clear().apply() }
    fun loadAccessToken(): String? = runCatching {
        val ciphertext = Base64.decode(prefs.getString("ciphertext", null).orEmpty(), Base64.NO_WRAP)
        val iv = Base64.decode(prefs.getString("iv", null).orEmpty(), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        JSONObject(String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)).optString("access_token").ifBlank { null }
    }.getOrNull()
    private fun key(): SecretKey { val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }; (store.getKey(alias, null) as? SecretKey)?.let { return it }; val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"); generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); return generator.generateKey() }
}

object OAuthManager {
    const val REDIRECT_URI = "teachtrack://oauth/callback"
    private fun openAuthorizationPage(context: Context, uri: Uri): Boolean {
        val browserIntent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            if (browserIntent.resolveActivity(context.packageManager) != null) context.startActivity(browserIntent)
            else context.startActivity(Intent().setComponent(ComponentName(context, "${context.packageName}.ExternalWebActivity")).putExtra("external_url", uri.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }
    fun startAuthorization(context: Context, config: OAuthConfig): Boolean {
        val safe = config.normalized(); if (safe.authorizationEndpoint.isBlank() || safe.clientId.isBlank()) return false
        val preferences = OAuthPreferences(context); val state = randomToken(24); val verifier = randomToken(48); preferences.savePending(state, verifier)
        val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val uri = Uri.parse(safe.authorizationEndpoint).buildUpon().appendQueryParameter("response_type", "code").appendQueryParameter("client_id", safe.clientId).appendQueryParameter("redirect_uri", REDIRECT_URI).appendQueryParameter("scope", safe.scopes).appendQueryParameter("state", state).appendQueryParameter("code_challenge", challenge).appendQueryParameter("code_challenge_method", "S256").build()
        return openAuthorizationPage(context, uri)
    }
    fun handleCallback(activity: ComponentActivity, intent: Intent?) {
        val uri = intent?.data ?: return; if (uri.scheme != "teachtrack" || uri.host != "oauth" || uri.path != "/callback") return
        val preferences = OAuthPreferences(activity.applicationContext); val error = uri.getQueryParameter("error")
        if (error != null) { preferences.clearPending(); preferences.fail("OAuth 登录取消或失败：$error"); return }
        val code = uri.getQueryParameter("code"); val state = uri.getQueryParameter("state")
        if (code.isNullOrBlank() || state.isNullOrBlank() || state != preferences.pendingState()) { preferences.clearPending(); preferences.fail("OAuth 回调校验失败，已拒绝登录"); return }
        val verifier = preferences.pendingVerifier(); val config = preferences.load(); if (verifier.isNullOrBlank() || config.tokenEndpoint.isBlank()) { preferences.clearPending(); preferences.fail("OAuth token endpoint 未配置"); return }
        activity.lifecycleScope.launch { exchangeCode(preferences, config, code, verifier) }
    }
    private suspend fun exchangeCode(preferences: OAuthPreferences, config: OAuthConfig, code: String, verifier: String) = withContext(Dispatchers.IO) { runCatching { val connection = (URL(config.tokenEndpoint).openConnection() as HttpURLConnection).apply { requestMethod = "POST"; connectTimeout = 30_000; readTimeout = 30_000; doOutput = true; setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); setRequestProperty("Accept", "application/json") }; val body = listOf("grant_type" to "authorization_code", "code" to code, "client_id" to config.clientId, "redirect_uri" to REDIRECT_URI, "code_verifier" to verifier).joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }; connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }; val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty(); if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}"); if (JSONObject(response).optString("access_token").isBlank()) error("missing access_token"); preferences.saveTokens(response); preferences.clearPending() }.onFailure { preferences.clearPending(); preferences.fail("OAuth 登录失败：${it.javaClass.simpleName}") } }

    /** Provider-specific Kimi Code OAuth: RFC 8628 device authorization + polling. */
    suspend fun startKimiDeviceLogin(context: Context): String = withContext(Dispatchers.IO) {
        val clientId = "17e5f671-d194-4dfb-9706-5516cb48c098"; val auth = postForm("https://auth.kimi.com/api/oauth/device_authorization", mapOf("client_id" to clientId)); if (auth.code !in 200..299) error("Kimi device authorization HTTP ${auth.code}")
        val deviceCode = auth.json.optString("device_code").ifBlank { error("Kimi device_code missing") }; val userCode = auth.json.optString("user_code").ifBlank { error("Kimi user_code missing") }; val verifyUrl = auth.json.optString("verification_uri_complete").ifBlank { auth.json.optString("verification_uri", "https://auth.kimi.com") }
        withContext(Dispatchers.Main) { if (!openAuthorizationPage(context, Uri.parse(verifyUrl))) error("无法打开 Kimi 授权页面") }; OAuthPreferences(context).fail("Kimi Code 授权码 $userCode：请在浏览器确认，App 正在轮询")
        val deadline = System.currentTimeMillis() + auth.json.optLong("expires_in", 900) * 1000; var interval = auth.json.optLong("interval", 5)
        while (System.currentTimeMillis() < deadline) { delay(interval * 1000); val poll = postForm("https://auth.kimi.com/api/oauth/token", mapOf("grant_type" to "urn:ietf:params:oauth:grant-type:device_code", "device_code" to deviceCode, "client_id" to clientId)); val access = poll.json.optString("access_token"); if (poll.code in 200..299 && access.isNotBlank()) { OAuthPreferences(context).saveTokens(poll.json.toString()); activateKimiProvider(context); return@withContext "success" }; when (poll.json.optString("error")) { "authorization_pending" -> Unit; "slow_down" -> interval += 5; "access_denied", "expired_token" -> error("Kimi OAuth ${poll.json.optString("error")}") } }
        error("Kimi OAuth timed out")
    }
    private fun activateKimiProvider(context: Context) {
        val preferences = ProviderPreferences(context.applicationContext)
        val current = preferences.load()
        preferences.save(current.copy(enabled = true, vendor = "Kimi Code", providerId = "kimi_code", authMode = "OAUTH_DEVICE", model = current.model.ifBlank { "kimi-for-coding" }))
    }
    private data class FormResponse(val code: Int, val json: JSONObject)
    private fun postForm(url: String, params: Map<String, String>): FormResponse { val body = params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }; val connection = (URL(url).openConnection() as HttpURLConnection).apply { requestMethod = "POST"; connectTimeout = 15_000; readTimeout = 15_000; doOutput = true; setRequestProperty("Content-Type", "application/x-www-form-urlencoded") }; connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }; val text = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty(); return FormResponse(connection.responseCode, runCatching { JSONObject(text) }.getOrDefault(JSONObject())) }
    private fun randomToken(bytes: Int): String { val data = ByteArray(bytes); SecureRandom().nextBytes(data); return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
}
