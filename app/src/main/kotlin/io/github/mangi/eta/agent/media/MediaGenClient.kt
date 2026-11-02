package io.github.mangi.eta.agent.media

import io.github.mangi.eta.agent.model.AgentHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 图片/视频生成网关客户端（对接 Agnes AI，中国站 https://api.agnes-ai.cn/v1）。
 *
 * - 图片：POST {base}/v1/images/generations
 *   注意：response_format 必须放在 extra_body 里，不能放顶层；size 用 1K/2K 档位。
 * - 视频：POST {base}/v1/videos（返回 video_id）→ 轮询 GET {base}/agnesapi?video_id=..&model_name=..
 *   注意：seconds 是字符串 "4"~"12"；flash 模型 size 固定 720P；mode 必填。
 * - 模型：GET {base}/v1/models
 */
internal object MediaGenClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Serializable
    data class ModelInfo(val id: String, val owned_by: String = "")

    @Serializable
    private data class ModelsResponse(val data: List<ModelInfo> = emptyList())

    @Serializable
    private data class ExtraBody(val response_format: String = "url")

    @Serializable
    private data class ImageGenRequest(
        val model: String,
        val prompt: String,
        val size: String = "1K",
        val extra_body: ExtraBody = ExtraBody("url"),
    )

    @Serializable
    private data class ImageData(val url: String = "", val b64_json: String = "")

    @Serializable
    private data class ImageGenResponse(val data: List<ImageData> = emptyList())

    @Serializable
    private data class VideoGenRequest(
        val model: String,
        val prompt: String,
        val mode: String = "text",
        val seconds: String = "5",
        val size: String = "720P",
        val aspect_ratio: String = "16:9",
    )

    @Serializable
    private data class VideoSubmitResponse(
        val id: String = "",
        val task_id: String = "",
        val video_id: String = "",
        val status: String = "",
    )

    @Serializable
    data class VideoStatus(
        val video_id: String = "",
        val status: String = "",
        val progress: Int = 0,
        val url: String = "",
        val error: VideoError? = null,
    ) {
        /** 完成时取视频地址（兼容 metadata.url 回退） */
        fun videoUrl(): String = url
    }

    @Serializable
    data class VideoError(val message: String = "")

    sealed interface ImageResult {
        data class Url(val url: String) : ImageResult
        data class Base64(val data: String) : ImageResult
    }

    suspend fun fetchModels(baseUrl: String, apiKey: String): Result<List<ModelInfo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/v1/models")
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                AgentHttpClient.client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    check(resp.isSuccessful) { "拉取模型列表失败(${resp.code})" }
                    json.decodeFromString<ModelsResponse>(body).data
                }
            }
        }

    suspend fun generateImage(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        size: String,
    ): Result<ImageResult> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = json.encodeToString(
                ImageGenRequest.serializer(),
                ImageGenRequest(model = model, prompt = prompt, size = size),
            )
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/v1/images/generations")
                .header("Authorization", "Bearer $apiKey")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            AgentHttpClient.modelClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                check(resp.isSuccessful) { "生图失败(${resp.code}): ${body.take(200)}" }
                val parsed = json.decodeFromString<ImageGenResponse>(body)
                val first = parsed.data.firstOrNull()
                    ?: error("生图返回为空")
                when {
                    first.url.isNotEmpty() -> ImageResult.Url(first.url)
                    first.b64_json.isNotEmpty() -> ImageResult.Base64(first.b64_json)
                    else -> error("生图返回无可用数据")
                }
            }
        }
    }

    suspend fun submitVideo(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        duration: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val seconds = duration.coerceIn(4, 12).toString()
            val payload = json.encodeToString(
                VideoGenRequest.serializer(),
                VideoGenRequest(model = model, prompt = prompt, seconds = seconds),
            )
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/v1/videos")
                .header("Authorization", "Bearer $apiKey")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            AgentHttpClient.client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                check(resp.isSuccessful) { "视频任务提交失败(${resp.code}): ${body.take(200)}" }
                val parsed = json.decodeFromString<VideoSubmitResponse>(body)
                val videoId = parsed.video_id.ifEmpty { parsed.task_id.ifEmpty { parsed.id } }
                check(videoId.isNotEmpty()) { "视频任务提交无返回 id" }
                videoId
            }
        }
    }

    suspend fun pollVideo(
        baseUrl: String,
        apiKey: String,
        videoId: String,
        model: String,
    ): Result<VideoStatus> = withContext(Dispatchers.IO) {
        runCatching {
            // Agnes 视频轮询走 /agnesapi?video_id=..&model_name=..（非 OpenAI 标准路径）
            val url = "${baseUrl.trimEnd('/')}/agnesapi?video_id=$videoId&model_name=$model"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            AgentHttpClient.client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                check(resp.isSuccessful) { "查询视频进度失败(${resp.code})" }
                json.decodeFromString<VideoStatus>(body)
            }
        }
    }
}
