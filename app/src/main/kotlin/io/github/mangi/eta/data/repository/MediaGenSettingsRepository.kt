package io.github.mangi.eta.data.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.model.MediaGenSettings
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 图片/视频生成设置仓库。
 *
 * 普通设置走 DataStore；apiKey 走 Android Keystore 加密存 SharedPreferences，
 * 与语音凭据同一安全级别。
 */
internal object MediaGenSettingsRepository {
    fun settingsFlow(): Flow<MediaGenSettings> = SettingsDataStore.mediaGenSettingsFlow()
    suspend fun settings(): MediaGenSettings = SettingsDataStore.mediaGenSettings()
    suspend fun save(settings: MediaGenSettings) = SettingsDataStore.setMediaGenSettings(settings)

    suspend fun apiKey(context: Context): String = withContext(Dispatchers.IO) {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, null) ?: return@withContext ""
        try {
            val bytes = Base64.getDecoder().decode(encoded)
            require(bytes.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, 12))
            cipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun saveApiKey(context: Context, apiKey: String) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (apiKey.isBlank()) {
            prefs.edit().remove(KEY_API_KEY).apply()
            return@withContext
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KEY_API_KEY, Base64.getEncoder().encodeToString(encrypted)).apply()
    }

    @Synchronized
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private const val PREFS = "eta_media_gen_secrets"
    private const val KEY_API_KEY = "api_key"
    private const val KEYSTORE_ALIAS = "eta_media_gen_api_key_v1"
}
