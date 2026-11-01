package io.github.mangi.eta.agent.voice

import android.content.Context
import io.github.mangi.eta.core.AndroidAgentLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * 热词模型管理器
 *
 * 负责 Vosk 中文小模型的下载、解压和管理。
 * 模型不打包进 APK，首次开启热词功能时从网络下载（约 20MB）。
 */
object HotwordModelManager {

    // Vosk 中文小模型下载地址（alphacephei 官方）
    // 使用小模型以控制体积：vosk-model-small-cn-0.22 约 42MB，解压后约 100MB
    // 如需更小，可考虑自定义裁剪版
    private const val MODEL_URL =
        "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
    private const val MODEL_DIR_NAME = "vosk-model-small-cn"
    private const val MODEL_ZIP_NAME = "vosk-model-small-cn.zip"

    // 下载状态
    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Downloading(val progress: Float) : DownloadState
        data object Extracting : DownloadState
        data object Ready : DownloadState
        data class Failed(val reason: String) : DownloadState
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    /**
     * 获取模型目录
     */
    fun getModelDir(context: Context): File {
        return File(context.filesDir, "hotword/$MODEL_DIR_NAME")
    }

    /**
     * 检查模型是否就绪
     */
    fun isModelReady(modelDir: File): Boolean {
        // 检查关键文件是否存在
        val amFinal = File(modelDir, "am/final.mdl")
        val graphDir = File(modelDir, "graph")
        return amFinal.exists() && graphDir.exists() && graphDir.list()?.isNotEmpty() == true
    }

    fun isModelReady(context: Context): Boolean {
        return isModelReady(getModelDir(context))
    }

    /**
     * 下载并解压模型
     *
     * @param context 上下文
     * @param onProgress 进度回调 (0.0 ~ 1.0)
     * @return 是否成功
     */
    suspend fun downloadModel(
        context: Context,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            _downloadState.value = DownloadState.Downloading(0f)

            val modelDir = getModelDir(context)
            val zipFile = File(context.filesDir, "hotword/$MODEL_ZIP_NAME")

            // 如果模型已存在，直接返回成功
            if (isModelReady(modelDir)) {
                _downloadState.value = DownloadState.Ready
                return@withContext true
            }

            // 创建目录
            zipFile.parentFile?.mkdirs()

            // 下载
            AndroidAgentLogger.debug { "开始下载热词模型: $MODEL_URL" }
            val success = downloadFile(MODEL_URL, zipFile) { progress ->
                _downloadState.value = DownloadState.Downloading(progress)
                onProgress(progress)
            }

            if (!success) {
                _downloadState.value = DownloadState.Failed("下载失败")
                return@withContext false
            }

            // 解压
            _downloadState.value = DownloadState.Extracting
            onProgress(1f)
            AndroidAgentLogger.debug { "开始解压热词模型" }

            val extractDir = File(context.filesDir, "hotword")
            unzip(zipFile, extractDir)

            // 删除 zip 文件，节省空间
            zipFile.delete()

            // 验证
            if (isModelReady(modelDir)) {
                _downloadState.value = DownloadState.Ready
                AndroidAgentLogger.debug { "热词模型就绪" }
                return@withContext true
            } else {
                _downloadState.value = DownloadState.Failed("模型文件不完整")
                return@withContext false
            }
        } catch (e: Exception) {
            AndroidAgentLogger.warnThrottled("hotword_model_download_failed") {
                "热词模型下载失败: ${e.message}"
            }
            _downloadState.value = DownloadState.Failed(e.message ?: "未知错误")
            return@withContext false
        }
    }

    /**
     * 删除模型（释放空间）
     */
    suspend fun deleteModel(context: Context) = withContext(Dispatchers.IO) {
        try {
            val hotwordDir = File(context.filesDir, "hotword")
            if (hotwordDir.exists()) {
                hotwordDir.deleteRecursively()
            }
            _downloadState.value = DownloadState.Idle
        } catch (e: Exception) {
            AndroidAgentLogger.warnThrottled("hotword_model_delete_failed") {
                "热词模型删除失败: ${e.message}"
            }
        }
    }

    /**
     * 获取模型大小（用于显示）
     */
    fun getModelSize(context: Context): Long {
        val modelDir = getModelDir(context)
        if (!modelDir.exists()) return 0
        return modelDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    // ── 内部方法 ──────────────────────────────────────────────────────────

    private fun downloadFile(
        url: String,
        dest: File,
        onProgress: (Float) -> Unit
    ): Boolean {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30000
                readTimeout = 30000
                requestMethod = "GET"
                // 支持断点续传
                if (dest.exists() && dest.length() > 0) {
                    setRequestProperty("Range", "bytes=${dest.length()}-")
                }
            }

            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK &&
                responseCode != HttpURLConnection.HTTP_PARTIAL
            ) {
                return false
            }

            val totalBytes = if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                // 断点续传时，从 Content-Range 解析总大小
                val contentRange = connection.getHeaderField("Content-Range")
                contentRange?.substringAfter("/")?.toLongOrNull() ?: -1
            } else {
                connection.contentLengthLong
            }

            val append = responseCode == HttpURLConnection.HTTP_PARTIAL

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(dest, append).use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = if (append) dest.length() else 0L
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        if (totalBytes > 0) {
                            onProgress(downloaded.toFloat() / totalBytes)
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            return false
        } finally {
            connection?.disconnect()
        }
    }

    private fun unzip(zipFile: File, destDir: File) {
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(destDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
