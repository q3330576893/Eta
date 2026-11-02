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
 * 图片/视频生成网关客户端（对接 muse-bridge 这类 OpenAI 兼容接口）。
 *
 * - 图片：POST {base}/v1/images/generations（同步返回）
 * - 视频：POST {base}/v1/videos（返回 task_id）→ 轮询 GET {base}/v1/videos/{task_id}
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
    private data class ImageGenRequest(
        val model: String,
        val prompt: String,
        val size: String = "1024x1024",
        val response_format: String = "url",
    )

    @Serializable
    private data class ImageData(val url: String = "", val b64_json: String = "")

    @Serializable
    private data class ImageGenResponse(val data: List<ImageData> = emptyList())

    @Serializable
    private data class VideoGenRequest(
        val model: String,
        val prompt: String,
        val size: String = "1024x1024",
        val duration: Int = 5,
    )

    @Serializable
    private data class VideoSubmitResponse(val id: String = "", val task_id: String = "")

    @Serializable
    data class VideoStatus(
        val id: String = "",
        val status: String = "",
        val progress: Int = 0,
        val result: VideoResult? = null,
        val error: String? = null,
    )

    @Serializable
    data class VideoResult(val url: String = "")

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
                    first.b64_json.isNotEmpty() -> ImageResult.Base64(first.b64_json)
                    first.url.isNotEmpty() -> ImageResult.Url(first.url)
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
            val payload = json.encodeToString(
                VideoGenRequest.serializer(),
                VideoGenRequest(model = model, prompt = prompt, duration = duration),
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
                val taskId = parsed.task_id.ifEmpty { parsed.id }
                check(taskId.isNotEmpty()) { "视频任务提交无返回 id" }
                taskId
            }
        }
    }

    suspend fun pollVideo(
        baseUrl: String,
        apiKey: String,
        taskId: String,
    ): Result<VideoStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/v1/videos/$taskId")
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
