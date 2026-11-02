package io.github.mangi.eta.data.model

import kotlinx.serialization.Serializable

/**
 * 图片/视频生成设置（对接 Agnes AI 这类 OpenAI 兼容的图片/视频生成网关）。
 *
 * 中国站默认 https://api.agnes-ai.cn/v1；apiKey 走加密存储，不进 DataStore。
 */
@Serializable
data class MediaGenSettings(
    val baseUrl: String = "https://api.agnes-ai.cn/v1",
    val imageModel: String = "agnes-image-2.5-flash",
    val videoModel: String = "agnes-video-2.5-flash",
    val imageSize: String = "1K",
    val videoDuration: Int = 5,
) {
    fun endpoint(): String = baseUrl.trim().trimEnd('/').ifEmpty { "https://api.agnes-ai.cn/v1" }
    fun isConfigured(): Boolean = true
}
