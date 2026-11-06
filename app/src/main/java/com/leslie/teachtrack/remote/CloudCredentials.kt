package com.leslie.teachtrack.remote

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/**
 * Cloud backend address and bearer token. The URL is not sensitive; the token is
 * encrypted with an AndroidKeyStore key and never stored in Room, logs or backups.
 */
class CloudCredentials(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("teachtrack_cloud", Context.MODE_PRIVATE)
    private val secretPrefs = context.applicationContext.getSharedPreferences("teachtrack_cloud_secret", Context.MODE_PRIVATE)

    fun baseUrl(): String = prefs.getString("baseUrl", "").orEmpty()
    fun saveBaseUrl(value: String) {
        prefs.edit().putString("baseUrl", value.trim().trimEnd('/')).apply()
    }

    fun token(): String? = runCatching {
        val ciphertext = secretPrefs.getString("ciphertext", null) ?: return null
        val iv = Base64.decode(secretPrefs.getString("iv", ""), Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(Base64.decode(ciphertext, Base64.DEFAULT)), Charsets.UTF_8)
    }.getOrNull()

    fun saveToken(value: String) {
        if (value.isBlank()) return
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        secretPrefs.edit()
            .putString("ciphertext", Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun hasToken(): Boolean = !secretPrefs.getString("ciphertext", null).isNullOrBlank()

    fun clear() {
        secretPrefs.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "TeachTrack.Cloud.Token"
    }
}
