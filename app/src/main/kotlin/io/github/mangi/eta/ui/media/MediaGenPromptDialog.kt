package io.github.mangi.eta.ui.media

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
        val p = prompt.trim()
        if (p.isEmpty() || generating) return
        generating = true
        progress = 0
        statusText = context.getString(R.string.media_gen_generating)
        scope.launch {
            val settings = MediaGenSettingsRepository.settings()
            val apiKey = MediaGenSettingsRepository.apiKey(context)
            val baseUrl = settings.endpoint()
            if (baseUrl.isEmpty() || apiKey.isEmpty()) {
                Toast.makeText(context, R.string.media_gen_need_config, Toast.LENGTH_LONG).show()
                generating = false
                return@launch
            }
            if (isVideo) {
                val model = settings.videoModel.trim()
                if (model.isEmpty()) {
                    Toast.makeText(context, R.string.media_gen_need_model, Toast.LENGTH_LONG).show()
                    generating = false
                    return@launch
                }
                // 提交视频任务
                val submit = MediaGenClient.submitVideo(baseUrl, apiKey, model, p, settings.videoDuration)
                val taskId = submit.getOrElse {
                    statusText = context.getString(R.string.media_gen_failed, it.message ?: "")
                    generating = false
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
                        statusText = context.getString(R.string.media_gen_failed, poll.exceptionOrNull()?.message ?: "")
                        return@repeat
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
                                statusText = context.getString(R.string.media_gen_failed, "empty result")
                            }
                            done = true
                            return@repeat
                        }
                        s.contains("fail") || s.contains("error") -> {
                            statusText = context.getString(R.string.media_gen_failed, st.error?.message ?: s)
                            done = true
                            return@repeat
                        }
                    }
                }
                if (!done) statusText = context.getString(R.string.media_gen_failed, "timeout")
            } else {
                val model = settings.imageModel.trim()
                if (model.isEmpty()) {
                    Toast.makeText(context, R.string.media_gen_need_model, Toast.LENGTH_LONG).show()
                    generating = false
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
                        statusText = context.getString(R.string.media_gen_failed, it.message ?: "")
                    }
            }
            generating = false
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
