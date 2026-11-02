package io.github.mangi.eta.ui.media

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.media.MediaGenClient
import io.github.mangi.eta.data.repository.MediaGenSettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.window.WindowDialog
import io.github.mangi.eta.ui.components.MiuixDialogActions

/**
 * 生图/生视频的提示词输入弹窗。
 *
 * @param isVideo true=生视频，false=生图
 */
@Composable
internal fun MediaGenPromptDialog(
    isVideo: Boolean,
    uiState: MediaGenUiState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var statusText by remember { mutableStateOf("") }

    fun doGenerate() {
        Log.d("MediaGen", "doGenerate CLICKED isVideo=$isVideo generating=$generating promptLen=${prompt.trim().length}")
        val p = prompt.trim()
        if (p.isEmpty()) {
            Log.d("MediaGen", "doGenerate: prompt empty, ignored")
            Toast.makeText(context, "请输入提示词", Toast.LENGTH_SHORT).show()
            return
        }
        if (generating) {
            Log.d("MediaGen", "doGenerate: already generating, ignored")
            Toast.makeText(context, context.getString(R.string.media_gen_generating), Toast.LENGTH_SHORT).show()
            return
        }
        Log.d("MediaGen", "doGenerate START isVideo=$isVideo prompt=${p.take(30)}")
        Toast.makeText(context, context.getString(R.string.media_gen_generating), Toast.LENGTH_SHORT).show()
        generating = true
        progress = 0
        statusText = context.getString(R.string.media_gen_generating)
        try {
            scope.launch {
                try {
                val settings = MediaGenSettingsRepository.settings()
                val apiKey = MediaGenSettingsRepository.apiKey(context)
                val baseUrl = settings.endpoint()
                Log.d("MediaGen", "config baseUrl=$baseUrl keyLen=${apiKey.length} imageModel=${settings.imageModel} videoModel=${settings.videoModel}")
                if (baseUrl.isEmpty() || apiKey.isEmpty()) {
                    val msg = context.getString(R.string.media_gen_need_config)
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    statusText = msg
                    return@launch
                }
                if (isVideo) {
                    val model = settings.videoModel.trim()
                    if (model.isEmpty()) {
                        val msg = context.getString(R.string.media_gen_need_model)
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        statusText = msg
                        return@launch
                    }
                    // 提交视频任务
                    val submit = MediaGenClient.submitVideo(baseUrl, apiKey, model, p, settings.videoDuration)
                    val taskId = submit.getOrElse {
                        val msg = context.getString(R.string.media_gen_failed, it.message ?: "")
                        statusText = msg
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    statusText = context.getString(R.string.media_gen_video_queued)
                    // 轮询，最多 10 分钟（Agnes 走 /agnesapi?video_id=..&model_name=..）
                    var done = false
                    repeat(120) {
                        delay(5000)
                        val poll = MediaGenClient.pollVideo(baseUrl, apiKey, taskId, model)
                        val st = poll.getOrNull()
                        if (st == null) {
                            val msg = context.getString(R.string.media_gen_failed, poll.exceptionOrNull()?.message ?: "")
                            statusText = msg
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        val s = st.status.lowercase()
                        if (st.progress > 0) {
                            progress = st.progress
                            statusText = context.getString(R.string.media_gen_video_progress, st.progress)
                        }
                        when {
                            s.contains("complet") || s.contains("success") || s == "done" -> {
                                val url = st.videoUrl()
                                if (url.isNotEmpty()) {
                                    uiState.showVideoResult(p, url)
                                    onDismiss()
                                } else {
                                    val msg = context.getString(R.string.media_gen_failed, "empty result")
                                    statusText = msg
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                                done = true
                                return@launch
                            }
                            s.contains("fail") || s.contains("error") -> {
                                val msg = context.getString(R.string.media_gen_failed, st.error?.message ?: s)
                                statusText = msg
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                done = true
                                return@launch
                            }
                        }
                    }
                    if (!done) {
                        val msg = context.getString(R.string.media_gen_failed, "timeout")
                        statusText = msg
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                } else {
                    val model = settings.imageModel.trim()
                    if (model.isEmpty()) {
                        val msg = context.getString(R.string.media_gen_need_model)
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        statusText = msg
                        return@launch
                    }
                    MediaGenClient.generateImage(baseUrl, apiKey, model, p, settings.imageSize)
                        .onSuccess { result ->
                            when (result) {
                                is MediaGenClient.ImageResult.Url -> uiState.showImageResult(p, result.url, null)
                                is MediaGenClient.ImageResult.Base64 -> uiState.showImageResult(p, null, result.data)
                            }
                            onDismiss()
                        }
                        .onFailure {
                            val msg = context.getString(R.string.media_gen_failed, it.message ?: "")
                            statusText = msg
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                }
            } catch (e: Exception) {
                Log.w("MediaGen", "doGenerate crashed", e)
                val msg = context.getString(R.string.media_gen_failed, e.message ?: "")
                statusText = msg
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            } finally {
                generating = false
            }
        }
        } catch (e: Exception) {
            Log.w("MediaGen", "doGenerate launch failed", e)
            generating = false
            val msg = context.getString(R.string.media_gen_failed, e.message ?: "")
            statusText = msg
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    WindowDialog(
        show = true,
        cornerRadius = DialogDefaults.CornerRadius,
        title = stringResource(if (isVideo) R.string.media_gen_title_video else R.string.media_gen_title_image),
        onDismissRequest = { if (!generating) onDismiss() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = stringResource(
                    if (isVideo) R.string.media_gen_video_prompt_hint else R.string.media_gen_prompt_hint,
                ),
                useLabelAsPlaceholder = true,
                singleLine = false,
                maxLines = 5,
                enabled = !generating,
                modifier = Modifier.fillMaxWidth(),
            )
            if (generating) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(text = statusText)
                }
            }
            MiuixDialogActions(
                confirmText = stringResource(R.string.media_gen_generate),
                confirmEnabled = prompt.trim().isNotEmpty() && !generating,
                onCancel = { if (!generating) onDismiss() },
                onConfirm = ::doGenerate,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
