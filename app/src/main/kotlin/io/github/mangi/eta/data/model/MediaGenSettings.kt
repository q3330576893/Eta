package io.github.mangi.eta.data.model

import kotlinx.serialization.Serializable

/**
 * 图片/视频生成设置（对接 muse-bridge 这类 OpenAI 兼容的图片/视频生成网关）。
 *
 * baseUrl 如 http://服务器IP:18610/v1；apiKey 走加密存储，不进 DataStore。
 */
@Serializable
data class MediaGenSettings(
    val baseUrl: String = "",
    val imageModel: String = "",
    val videoModel: String = "",
    val imageSize: String = "1024x1024",
    val videoDuration: Int = 5,
) {
    fun endpoint(): String = baseUrl.trim().trimEnd('/')
    fun isConfigured(): Boolean = endpoint().isNotEmpty()
}
