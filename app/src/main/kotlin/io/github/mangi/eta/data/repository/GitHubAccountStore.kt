package io.github.mangi.eta.data.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** GitHub 账号凭据：只以 Android Keystore 密文保存在 App 私有偏好中。 */
internal data class GitHubAccount(
    val login: String,
    val token: String,
    val scopes: String,
    val connectedAt: Long,
)

/** GitHub 账号与「顺手把这些凭据同步给终端 git」的开关。 */
internal class GitHubAccountStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): GitHubAccount? {
        val encoded = preferences.getString(ACCOUNT_KEY, null) ?: return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > GCM_IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(GCM_TAG_BITS, payload, 0, GCM_IV_BYTES),
            )
            val json = JSONObject(
                cipher.doFinal(payload, GCM_IV_BYTES, payload.size - GCM_IV_BYTES)
                    .toString(Charsets.UTF_8),
            )
            GitHubAccount(
                login = json.optString("login"),
                token = json.optString("token"),
                scopes = json.optString("scopes"),
                connectedAt = json.optLong("connectedAt"),
            )
        }.getOrNull()?.takeIf { it.token.isNotBlank() }
    }

    fun save(account: GitHubAccount) {
        val json = JSONObject().apply {
            put("login", account.login)
            put("token", account.token)
            put("scopes", account.scopes)
            put("connectedAt", account.connectedAt)
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        check(
            preferences.edit()
                .putString(ACCOUNT_KEY, Base64.encodeToString(payload, Base64.NO_WRAP))
                .commit(),
        ) { "GitHub 凭据保存失败" }
    }

    fun clear() {
        check(preferences.edit().remove(ACCOUNT_KEY).commit()) { "GitHub 凭据删除失败" }
    }

    /** 连接成功后自动注册的 MCP 服务器 id，断开时据此清理。 */
    fun synchronizedServerId(): String? = preferences.getString(SERVER_KEY, null)

    fun setSynchronizedServerId(serverId: String?) {
        preferences.edit().apply {
            if (serverId == null) remove(SERVER_KEY) else putString(SERVER_KEY, serverId)
        }.commit()
    }

    fun syncToTerminal(): Boolean = preferences.getBoolean(SYNC_TERMINAL_KEY, true)

    fun setSyncToTerminal(enabled: Boolean) {
        preferences.edit().putBoolean(SYNC_TERMINAL_KEY, enabled).commit()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "eta_github_account"
        const val ACCOUNT_KEY = "account"
        const val SERVER_KEY = "server"
        const val SYNC_TERMINAL_KEY = "sync_terminal"
        const val KEY_ALIAS = "eta_github_account_key"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
