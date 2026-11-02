package io.github.mangi.eta.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.media.MediaGenClient
import io.github.mangi.eta.data.model.MediaGenSettings
import io.github.mangi.eta.data.repository.MediaGenSettingsRepository
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaPreferenceColors
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.EtaPreferenceIcon
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.voice.SpeechField
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text

/**
 * 图片/视频生成设置页：配置 muse-bridge 网关地址、API Key、选择生图/生视频模型。
 */
@Composable
internal fun MediaGenSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by MediaGenSettingsRepository.settingsFlow().collectAsState(initial = null)
    val current = settings

    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var apiKeyLoaded by remember { mutableStateOf(false) }
    var imageModel by remember { mutableStateOf("") }
    var videoModel by remember { mutableStateOf("") }
    var dirty by remember { mutableStateOf(false) }

    var models by remember { mutableStateOf<List<MediaGenClient.ModelInfo>>(emptyList()) }
    var loadingModels by remember { mutableStateOf(false) }
    var modelsError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(current) {
        current?.let {
            if (!dirty) {
                baseUrl = it.baseUrl
                imageModel = it.imageModel
                videoModel = it.videoModel
            }
        }
    }
    LaunchedEffect(Unit) {
        apiKey = MediaGenSettingsRepository.apiKey(context)
        apiKeyLoaded = true
    }

    fun save() {
        scope.launch {
            MediaGenSettingsRepository.save(
                MediaGenSettings(
                    baseUrl = baseUrl.trim(),
                    imageModel = imageModel.trim(),
                    videoModel = videoModel.trim(),
                ),
            )
            MediaGenSettingsRepository.saveApiKey(context, apiKey.trim())
            dirty = false
            Toast.makeText(context, R.string.media_gen_settings_saved, Toast.LENGTH_SHORT).show()
        }
    }

    fun refreshModels() {
        val url = baseUrl.trim().trimEnd('/')
        val key = apiKey.trim()
        if (url.isEmpty() || key.isEmpty()) {
            Toast.makeText(context, R.string.media_gen_need_config, Toast.LENGTH_SHORT).show()
            return
        }
        loadingModels = true
        modelsError = null
        scope.launch {
            MediaGenClient.fetchModels(url, key)
                .onSuccess {
                    models = it
                    modelsError = null
                    Toast.makeText(
                        context,
                        context.getString(R.string.media_gen_models_loaded, it.size),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                .onFailure {
                    modelsError = it.message
                    Toast.makeText(
                        context,
                        context.getString(R.string.media_gen_models_failed, it.message ?: ""),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            loadingModels = false
        }
    }

    MiuixScaffoldPage(
        title = stringResource(R.string.media_gen_settings_title),
        onBack = onBack,
    ) {
        if (current == null || !apiKeyLoaded) {
            item(key = "loading") {
                Text(
                    text = stringResource(R.string.media_gen_refreshing),
                    modifier = Modifier.padding(16.dp),
                )
            }
            return@MiuixScaffoldPage
        }

        item(key = "service_title") {
            EtaPreferenceGroupTitle(stringResource(R.string.media_gen_group_service))
        }
        item(key = "service") {
            EtaPreferenceGroup {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SpeechField(
                        label = stringResource(R.string.media_gen_base_url),
                        value = baseUrl,
                        onChange = { baseUrl = it; dirty = true },
                    )
                    SpeechField(
                        label = stringResource(R.string.media_gen_api_key),
                        value = apiKey,
                        secret = true,
                        onChange = { apiKey = it; dirty = true },
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = ::refreshModels,
                            enabled = !loadingModels,
                        ) {
                            Text(
                                if (loadingModels) stringResource(R.string.media_gen_refreshing)
                                else stringResource(R.string.media_gen_refresh_models),
                            )
                        }
                    }
                    // 刷新失败仅 Toast 提示（见 refreshModels），不阻塞；模型名可手动填写
                }
            }
        }

        item(key = "models_title") {
            EtaPreferenceGroupTitle(stringResource(R.string.media_gen_group_models))
        }
        item(key = "models") {
            EtaPreferenceGroup {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SpeechField(
                        label = stringResource(R.string.media_gen_image_model),
                        value = imageModel,
                        onChange = { imageModel = it; dirty = true },
                    )
                    SpeechField(
                        label = stringResource(R.string.media_gen_video_model),
                        value = videoModel,
                        onChange = { videoModel = it; dirty = true },
                    )
                    if (models.isNotEmpty()) {
                        Text(text = stringResource(R.string.media_gen_models_loaded, models.size))
                        models.take(20).forEach { m ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(text = m.id, modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    // 简单启发式：id 含 video/movie 的归为视频模型，其余归生图
                                    if (m.id.contains("video", true) || m.id.contains("movie", true)) {
                                        videoModel = m.id
                                    } else {
                                        imageModel = m.id
                                    }
                                    dirty = true
                                }) {
                                    Text(text = "✓")
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "save") {
            Button(
                onClick = ::save,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text(text = stringResource(R.string.page_save_fadf24))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
