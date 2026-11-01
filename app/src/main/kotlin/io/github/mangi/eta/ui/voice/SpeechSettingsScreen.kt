package io.github.mangi.eta.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.voice.HotwordModelManager
import io.github.mangi.eta.agent.voice.HotwordService
import io.github.mangi.eta.data.model.TtsProvider
import io.github.mangi.eta.data.repository.SpeechSettingsRepository
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaPreferenceColors
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.EtaPreferenceIcon
import io.github.mangi.eta.ui.components.EtaSwitchPreference
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.navigation.AppRoute
import kotlinx.coroutines.launch

/**
 * 语音设置总览：识别与播报各有独立子页面（预测性返回与滑动返回由 miuix-nav 提供），
 * 这里的开关直接读写持久化配置，不涉及凭据草稿。
 */
@Composable
internal fun SpeechSettingsScreen(onBack: () -> Unit, onNavigate: (AppRoute) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by SpeechSettingsRepository.settingsFlow().collectAsState(initial = null)
    val modelState by HotwordModelManager.downloadState.collectAsState()
    val current = settings
    val enableHotword = remember(context, scope, current) {
        {
            scope.launch {
                val modelReady = HotwordModelManager.downloadModel(context)
                if (modelReady) {
                    current?.let { SpeechSettingsRepository.save(it.copy(hotwordEnabled = true)) }
                    HotwordService.start(context)
                }
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) enableHotword()
    }

    MiuixScaffoldPage(
        title = stringResource(R.string.speech_settings_title),
        onBack = onBack,
    ) {
        if (current == null) {
            item(key = "loading") { SpeechLoadingState(failure = null) }
            return@MiuixScaffoldPage
        }
        item(key = "service_title") {
            EtaPreferenceGroupTitle(stringResource(R.string.speech_group_service))
        }
        item(key = "service") {
            EtaPreferenceGroup {
                EtaArrowPreference(
                    title = stringResource(R.string.speech_recognition_title),
                    summary = if (current.recognitionEnabled) current.asr.label()
                        else stringResource(R.string.speech_recognition_disabled),
                    startAction = {
                        EtaPreferenceIcon(icon = Icons.Rounded.Mic, tint = EtaPreferenceColors.Blue)
                    },
                    onClick = { onNavigate(AppRoute.SpeechRecognition) },
                )
                EtaPreferenceDivider()
                EtaArrowPreference(
                    title = stringResource(R.string.speech_synthesis_title),
                    summary = current.tts.label(),
                    startAction = {
                        EtaPreferenceIcon(
                            icon = Icons.AutoMirrored.Rounded.VolumeUp,
                            tint = EtaPreferenceColors.Orange,
                        )
                    },
                    onClick = { onNavigate(AppRoute.SpeechSynthesis) },
                )
            }
        }
        item(key = "assistant_title") {
            EtaPreferenceGroupTitle(stringResource(R.string.speech_group_assistant))
        }
        item(key = "assistant") {
            Column {
                EtaPreferenceGroup {
                    EtaSwitchPreference(
                        title = stringResource(R.string.speech_auto_speak),
                        summary = stringResource(R.string.speech_auto_speak_summary),
                        checked = current.autoSpeak,
                        enabled = current.tts != TtsProvider.NONE,
                        startAction = {
                            EtaPreferenceIcon(
                                icon = Icons.Rounded.RecordVoiceOver,
                                tint = EtaPreferenceColors.Green,
                            )
                        },
                        onCheckedChange = { enabled ->
                            scope.launch {
                                SpeechSettingsRepository.save(current.copy(autoSpeak = enabled))
                            }
                        },
                    )
                    EtaPreferenceDivider()
                    EtaSwitchPreference(
                        title = stringResource(R.string.settings_hotword_title),
                        summary = when {
                            current.hotwordEnabled -> stringResource(R.string.settings_hotword_ready)
                            modelState is HotwordModelManager.DownloadState.Downloading ->
                                stringResource(R.string.settings_hotword_downloading)
                            modelState is HotwordModelManager.DownloadState.Extracting ->
                                stringResource(R.string.settings_hotword_extracting)
                            modelState is HotwordModelManager.DownloadState.Failed ->
                                stringResource(R.string.settings_hotword_failed)
                            else -> stringResource(R.string.settings_hotword_summary)
                        },
                        checked = current.hotwordEnabled,
                        enabled = modelState !is HotwordModelManager.DownloadState.Downloading &&
                            modelState !is HotwordModelManager.DownloadState.Extracting,
                        startAction = {
                            EtaPreferenceIcon(icon = Icons.Rounded.Hearing, tint = EtaPreferenceColors.Blue)
                        },
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                scope.launch {
                                    SpeechSettingsRepository.save(current.copy(hotwordEnabled = false))
                                    HotwordService.stop(context)
                                }
                            } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                enableHotword()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    )
                }
                SpeechNote(stringResource(R.string.speech_overview_note))
            }
        }
    }
}
