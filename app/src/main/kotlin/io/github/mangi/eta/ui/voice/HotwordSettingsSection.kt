package io.github.mangi.eta.ui.voice

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.voice.HotwordModelManager
import io.github.mangi.eta.agent.voice.HotwordService
import io.github.mangi.eta.data.model.SpeechSettings
import io.github.mangi.eta.data.repository.SpeechSettingsRepository
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaPreferenceColors
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.EtaPreferenceIcon
import io.github.mangi.eta.ui.components.EtaSwitchPreference
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.MiuixDialogActions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 语音唤醒设置区：热词唤醒开关 + 自定义唤醒词 + 模型管理 + 默认助手指引。
 *
 * 放在语音设置页的"语音助手"分组之后。
 */
internal fun LazyListScope.hotwordSettingsSection(current: SpeechSettings) {
    item(key = "hotword_title") {
        EtaPreferenceGroupTitle(stringResource(R.string.settings_hotword_title))
    }
    item(key = "hotword") {
        HotwordSettingsContent(current = current)
    }
}

@Composable
private fun HotwordSettingsContent(current: SpeechSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            // 授权后如果开关开着，启动服务
            scope.launch {
                val settings = SpeechSettingsRepository.settingsFlow()
                    .let { kotlinx.coroutines.flow.first(it) }
                if (settings.hotwordEnabled && HotwordModelManager.isModelReady(context)) {
                    HotwordService.start(context)
                }
            }
        }
    }

    var showPhraseDialog by remember { mutableStateOf(false) }
    val downloadState by HotwordModelManager.downloadState.collectAsState()
    var serviceRunning by remember { mutableStateOf(isHotwordServiceRunning(context)) }
    var modelReadyTick by remember { mutableStateOf(0) }

    // 定期刷新服务状态
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            serviceRunning = isHotwordServiceRunning(context)
            modelReadyTick++
        }
    }
    @Suppress("UNUSED_EXPRESSION")
    modelReadyTick // 触发重组以刷新模型状态
    val modelReady = HotwordModelManager.isModelReady(context)

    Column {
        EtaPreferenceGroup {
            // ── 开关 ──
            EtaSwitchPreference(
                title = stringResource(R.string.hotword_wakeup_title),
                summary = stringResource(R.string.hotword_wakeup_summary),
                checked = current.hotwordEnabled,
                startAction = {
                    EtaPreferenceIcon(
                        icon = Icons.Rounded.RecordVoiceOver,
                        tint = EtaPreferenceColors.Green,
                    )
                },
                onCheckedChange = { enabled ->
                    if (enabled) {
                        if (!hasMicPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@EtaSwitchPreference
                        }
                        if (!HotwordModelManager.isModelReady(context)) {
                            // 模型未下载：保持关闭，提示先下载
                            return@EtaSwitchPreference
                        }
                    }
                    scope.launch {
                        SpeechSettingsRepository.save(
                            current.copy(hotwordEnabled = enabled)
                        )
                        if (enabled) {
                            HotwordService.start(context)
                        } else {
                            HotwordService.stop(context)
                        }
                    }
                },
            )
            EtaPreferenceDivider()
            // ── 唤醒词 ──
            EtaArrowPreference(
                title = stringResource(R.string.hotword_phrase_title),
                summary = stringResource(
                    R.string.hotword_phrase_summary,
                    current.hotwordPhrase.ifEmpty { "艾塔助手" }
                ),
                startAction = {
                    EtaPreferenceIcon(
                        icon = Icons.Rounded.Psychology,
                        tint = EtaPreferenceColors.Blue,
                    )
                },
                onClick = { showPhraseDialog = true },
            )
            EtaPreferenceDivider()
            // ── 模型状态 ──
            val modelSummary = when (val s = downloadState) {
                is HotwordModelManager.DownloadState.Downloading ->
                    context.getString(
                        R.string.hotword_model_downloading,
                        (s.progress * 100).toInt()
                    )
                is HotwordModelManager.DownloadState.Extracting ->
                    context.getString(R.string.hotword_model_extracting)
                is HotwordModelManager.DownloadState.Failed ->
                    context.getString(R.string.hotword_model_failed)
                is HotwordModelManager.DownloadState.Ready ->
                    context.getString(
                        R.string.hotword_model_ready,
                        formatBytes(HotwordModelManager.getModelSize(context))
                    )
                else ->
                    if (modelReady) context.getString(
                        R.string.hotword_model_ready,
                        formatBytes(HotwordModelManager.getModelSize(context))
                    )
                    else context.getString(R.string.hotword_model_not_downloaded)
            }
            val isDownloading = downloadState is HotwordModelManager.DownloadState.Downloading ||
                downloadState is HotwordModelManager.DownloadState.Extracting
            EtaArrowPreference(
                title = stringResource(R.string.hotword_model_title),
                summary = modelSummary,
                startAction = {
                    EtaPreferenceIcon(
                        icon = Icons.Rounded.Mic,
                        tint = EtaPreferenceColors.Orange,
                    )
                },
                onClick = {
                    if (isDownloading) return@EtaArrowPreference
                    scope.launch {
                        if (HotwordModelManager.isModelReady(context)) {
                            HotwordModelManager.deleteModel(context)
                        } else {
                            HotwordModelManager.downloadModel(context)
                        }
                    }
                },
            )
            EtaPreferenceDivider()
            // ── 服务状态（仅展示）──
            EtaArrowPreference(
                title = if (serviceRunning) stringResource(R.string.hotword_service_running)
                else stringResource(R.string.hotword_service_stopped),
                summary = null,
                startAction = {
                    EtaPreferenceIcon(
                        icon = Icons.Rounded.RecordVoiceOver,
                        tint = EtaPreferenceColors.Green,
                    )
                },
                onClick = { /* 仅展示状态 */ },
            )
        }
        SpeechNote(
            if (modelReady) stringResource(R.string.hotword_model_delete)
            else stringResource(R.string.hotword_need_model_first)
        )
        // ── 设为默认助手 ──
        EtaPreferenceGroup {
            EtaArrowPreference(
                title = stringResource(R.string.hotword_default_assistant_title),
                summary = stringResource(R.string.hotword_default_assistant_summary),
                startAction = {
                    EtaPreferenceIcon(
                        icon = Icons.Rounded.Psychology,
                        tint = EtaPreferenceColors.Blue,
                    )
                },
                onClick = {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                        context.startActivity(
                            Intent(Settings.ACTION_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
            )
        }
    }

    // ── 自定义唤醒词对话框 ──
    if (showPhraseDialog) {
        HotwordPhraseDialog(
            initial = current.hotwordPhrase,
            onDismiss = { showPhraseDialog = false },
            onConfirm = { phrase ->
                scope.launch {
                    val latest = kotlinx.coroutines.flow.first(
                        SpeechSettingsRepository.settingsFlow()
                    )
                    SpeechSettingsRepository.save(
                        latest.copy(hotwordPhrase = phrase)
                    )
                    HotwordService.reload(context)
                    showPhraseDialog = false
                }
            },
        )
    }
}

@Composable
private fun HotwordPhraseDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }

    EtaWindowDialog(
        show = true,
        title = stringResource(R.string.hotword_phrase_dialog_title),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        ) {
            TextField(
                value = input,
                onValueChange = {
                    input = it
                    error = null
                },
                placeholder = {
                    Text(stringResource(R.string.hotword_phrase_dialog_hint))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
            )
            Spacer(Modifier.height(8.dp))
            MiuixDialogActions(
                confirmText = stringResource(R.string.hotword_phrase_dialog_confirm),
                onCancel = onDismiss,
                onConfirm = {
                    val trimmed = input.trim()
                    val err = when {
                        trimmed.length < 2 ->
                            context.getString(R.string.hotword_phrase_too_short)
                        trimmed.length > 6 ->
                            context.getString(R.string.hotword_phrase_too_long)
                        else -> null
                    }
                    if (err != null) {
                        error = err
                        return@MiuixDialogActions
                    }
                    onConfirm(trimmed)
                },
            )
        }
    }
}

/**
 * 检查热词服务是否在运行
 */
private fun isHotwordServiceRunning(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        ?: return false
    @Suppress("DEPRECATION")
    return manager.getRunningServices(Int.MAX_VALUE).any {
        it.service.className == HotwordService::class.java.name
    }
}

/**
 * 格式化字节数
 */
private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
        else -> "%.1f MB".format(bytes / (1024f * 1024f))
    }
}
