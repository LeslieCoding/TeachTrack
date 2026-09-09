package com.leslie.teachtrack.copilot

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class OAuthConfig(
    val enabled: Boolean = false,
    val provider: String = "OAuth / PKCE",
    val authorizationEndpoint: String = "",
    val tokenEndpoint: String = "",
    val clientId: String = "",
    val scopes: String = "openid profile",
    val redirectUri: String = OAuthManager.REDIRECT_URI
) {
    fun normalized() = copy(provider = provider.trim().ifBlank { "OAuth / PKCE" }, authorizationEndpoint = authorizationEndpoint.trim(), tokenEndpoint = tokenEndpoint.trim(), clientId = clientId.trim(), scopes = scopes.trim().ifBlank { "openid profile" }, redirectUri = OAuthManager.REDIRECT_URI)
}

data class OAuthProviderPreset(
    val id: String,
    val displayName: String,
    val authMode: String,
    val consoleUrl: String,
    val authorizationEndpoint: String = "",
    val tokenEndpoint: String = "",
    val scopes: String = "openid profile",
    val note: String
)

object OAuthProviderCatalog {
    val presets = listOf(
        OAuthProviderPreset("openai", "OpenAI / GPT", "API_KEY", "https://platform.openai.com/api-keys", note = "OpenAI API 官方使用 API Key；网页 ChatGPT 登录不会返回可用于 API 的 Token。"),
        OAuthProviderPreset("kimi", "Kimi / Moonshot", "API_KEY", "https://platform.moonshot.cn/console/api-keys", note = "Kimi 开放平台使用 API Key；请在控制台创建后保存到 Provider 配置。"),
        OAuthProviderPreset("kimi_code", "Kimi Code", "API_KEY", "https://www.kimi.com/code/en", note = "Kimi Code 是独立的编程服务入口；官网登录与 Kimi 开放平台 API Key 不共用入口。"),
        OAuthProviderPreset("claude", "Claude / Anthropic", "API_KEY", "https://console.anthropic.com/", note = "Claude API 在 Anthropic Console 管理 API Key。"),
        OAuthProviderPreset("deepseek", "DeepSeek", "API_KEY", "https://platform.deepseek.com/api_keys", note = "DeepSeek 开放平台使用 API Key。"),
        OAuthProviderPreset("zhipu", "智谱 GLM", "API_KEY", "https://open.bigmodel.cn/usercenter/apikeys", note = "智谱开放平台使用 API Key。"),
        OAuthProviderPreset("custom", "自定义 OAuth 2.0 / PKCE", "OAUTH_PKCE", "", note = "填写服务商提供的 Authorization Endpoint、Token Endpoint 和移动端 Client ID。")
    )
    fun find(provider: String): OAuthProviderPreset = presets.firstOrNull { it.displayName == provider } ?: presets.first()
}

class OAuthPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("teachtrack_oauth", Context.MODE_PRIVATE)
    private val tokens = OAuthTokenStore(context.applicationContext)
    fun load(): OAuthConfig = OAuthConfig(prefs.getBoolean("enabled", false), prefs.getString("provider", "OAuth / PKCE").orEmpty(), prefs.getString("authorizationEndpoint", "").orEmpty(), prefs.getString("tokenEndpoint", "").orEmpty(), prefs.getString("clientId", "").orEmpty(), prefs.getString("scopes", "openid profile").orEmpty()).normalized()
    fun save(config: OAuthConfig) { val safe = config.normalized(); prefs.edit().putBoolean("enabled", safe.enabled).putString("provider", safe.provider).putString("authorizationEndpoint", safe.authorizationEndpoint).putString("tokenEndpoint", safe.tokenEndpoint).putString("clientId", safe.clientId).putString("scopes", safe.scopes).apply() }
    fun hasSession(): Boolean = tokens.hasSession()
    fun clearSession() { tokens.clear(); prefs.edit().remove("status").apply() }
    fun status(): String = prefs.getString("status", "尚未登录") ?: "尚未登录"
    internal fun pendingState(): String? = prefs.getString("pending_state", null)
    internal fun pendingVerifier(): String? = prefs.getString("pending_verifier", null)
    internal fun savePending(state: String, verifier: String) { prefs.edit().putString("pending_state", state).putString("pending_verifier", verifier).putString("status", "等待浏览器完成登录").apply() }
    internal fun clearPending() { prefs.edit().remove("pending_state").remove("pending_verifier").apply() }
    internal fun saveTokens(body: String) { tokens.save(body); prefs.edit().putString("status", "OAuth 登录成功").apply() }
    internal fun fail(message: String) { prefs.edit().putString("status", message).apply() }
}

private class OAuthTokenStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("teachtrack_oauth_secret", Context.MODE_PRIVATE)
    private val alias = "TeachTrack.OAuth.Token"
    fun hasSession(): Boolean = !prefs.getString("ciphertext", null).isNullOrBlank()
    fun save(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
        prefs.edit().putString("ciphertext", Base64.encodeToString(cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)).putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).apply()
    }
    fun clear() { prefs.edit().clear().apply() }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
}

object OAuthManager {
    const val REDIRECT_URI = "teachtrack://oauth/callback"

    fun startAuthorization(context: Context, config: OAuthConfig): Boolean {
        val safe = config.normalized()
        if (safe.authorizationEndpoint.isBlank() || safe.clientId.isBlank()) return false
        val preferences = OAuthPreferences(context)
        val state = randomToken(24)
        val verifier = randomToken(48)
        preferences.savePending(state, verifier)
        val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val uri = Uri.parse(safe.authorizationEndpoint).buildUpon().appendQueryParameter("response_type", "code").appendQueryParameter("client_id", safe.clientId).appendQueryParameter("redirect_uri", REDIRECT_URI).appendQueryParameter("scope", safe.scopes).appendQueryParameter("state", state).appendQueryParameter("code_challenge", challenge).appendQueryParameter("code_challenge_method", "S256").build()
        return runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    }

    fun handleCallback(activity: ComponentActivity, intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "teachtrack" || uri.host != "oauth" || uri.path != "/callback") return
        val preferences = OAuthPreferences(activity.applicationContext)
        val error = uri.getQueryParameter("error")
        if (error != null) { preferences.clearPending(); preferences.fail("OAuth 登录取消或失败：$error"); return }
        val code = uri.getQueryParameter("code"); val state = uri.getQueryParameter("state")
        if (code.isNullOrBlank() || state.isNullOrBlank() || state != preferences.pendingState()) { preferences.clearPending(); preferences.fail("OAuth 回调校验失败，已拒绝登录"); return }
        val verifier = preferences.pendingVerifier(); val config = preferences.load()
        if (verifier.isNullOrBlank() || config.tokenEndpoint.isBlank()) { preferences.clearPending(); preferences.fail("OAuth token endpoint 未配置"); return }
        activity.lifecycleScope.launch { exchangeCode(preferences, config, code, verifier) }
    }

    private suspend fun exchangeCode(preferences: OAuthPreferences, config: OAuthConfig, code: String, verifier: String) = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(config.tokenEndpoint).openConnection() as HttpURLConnection).apply { requestMethod = "POST"; connectTimeout = 30_000; readTimeout = 30_000; doOutput = true; setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); setRequestProperty("Accept", "application/json") }
            val body = listOf("grant_type" to "authorization_code", "code" to code, "client_id" to config.clientId, "redirect_uri" to REDIRECT_URI, "code_verifier" to verifier).joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            JSONObject(response).let { if (it.optString("access_token").isBlank()) error("missing access_token") }
            preferences.saveTokens(response); preferences.clearPending()
        }.onFailure { preferences.clearPending(); preferences.fail("OAuth 登录失败：${it.javaClass.simpleName}") }
    }

    private fun randomToken(bytes: Int): String { val data = ByteArray(bytes); SecureRandom().nextBytes(data); return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
}
