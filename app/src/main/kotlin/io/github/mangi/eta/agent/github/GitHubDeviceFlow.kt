package io.github.mangi.eta.agent.github

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * GitHub OAuth Device Flow。
 *
 * 选设备码而不是回调 OAuth：手机上不需要注册回调地址、也不需要 client secret，
 * 用户只要在浏览器里输一次 8 位码。client_id 用的是 GitHub CLI 的公开应用，
 * 授权记录会出现在 GitHub「Settings → Applications」里，可随时撤销。
 */
internal object GitHubDeviceFlow {
    const val CLIENT_ID = "178c6fc778ccc68e1d6a"
    const val SCOPES = "repo read:org workflow gist"

    private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
    private const val TOKEN_URL = "https://github.com/login/oauth/access_token"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val intervalSeconds: Int,
        val expiresAt: Long,
    )

    class AuthorizationPending : Exception("等待授权中")

    suspend fun request(): DeviceCode = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("scope", SCOPES)
            .build()
        val request = Request.Builder()
            .url(DEVICE_CODE_URL)
            .header("Accept", "application/json")
            .post(body)
            .build()
        val payload = execute(request)
        val deviceCode = payload.optString("device_code")
        val userCode = payload.optString("user_code")
        if (deviceCode.isBlank() || userCode.isBlank()) {
            throw IOException(payload.optString("error_description", "申请设备码失败"))
        }
        val expiresIn = payload.optInt("expires_in", 900)
        DeviceCode(
            deviceCode = deviceCode,
            userCode = userCode,
            verificationUri = payload.optString("verification_uri", "https://github.com/login/device"),
            intervalSeconds = payload.optInt("interval", 5).coerceAtLeast(5),
            expiresAt = System.currentTimeMillis() + expiresIn * 1000L,
        )
    }

    /** 轮询换取 token；网络抖动只重试不放弃，设备码过期或用户拒绝才抛错。 */
    suspend fun awaitToken(
        code: DeviceCode,
        onWaiting: (elapsedSeconds: Long) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        var interval = code.intervalSeconds
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() < code.expiresAt) {
            delay(interval * 1000L)
            onWaiting((System.currentTimeMillis() - startedAt) / 1000)
            val payload = try {
                val body = FormBody.Builder()
                    .add("client_id", CLIENT_ID)
                    .add("device_code", code.deviceCode)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .build()
                execute(
                    Request.Builder()
                        .url(TOKEN_URL)
                        .header("Accept", "application/json")
                        .post(body)
                        .build(),
                )
            } catch (error: IOException) {
                // 弱网下这里会周期性失败，等下一轮即可。
                continue
            }
            payload.optString("access_token").takeIf { it.isNotBlank() }?.let { return@withContext it }
            when (payload.optString("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "expired_token" -> throw IOException("设备码已过期，请重新发起连接")
                "access_denied" -> throw IOException("已取消授权")
                else -> throw IOException(payload.optString("error_description", "授权失败"))
            }
        }
        throw IOException("设备码已过期，请重新发起连接")
    }

    /** 用刚拿到的 token 读一次账号信息，确认凭据可用并取出登录名。 */
    suspend fun fetchLogin(token: String): String = withContext(Dispatchers.IO) {
        val payload = execute(
            Request.Builder()
                .url("https://api.github.com/user")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build(),
        )
        payload.optString("login").ifBlank { throw IOException("读取 GitHub 账号失败") }
    }

    private fun execute(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("GitHub 返回 ${response.code}：${text.take(200)}")
            }
            return JSONObject(text)
        }
    }
}
