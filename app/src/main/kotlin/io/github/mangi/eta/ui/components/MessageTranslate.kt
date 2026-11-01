package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.voice.SpeechText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 消息长按翻译：中英互译。
 *
 * 含 CJK 字符的消息译为英文，否则译为中文。走 MyMemory 免费接口，无需 key；
 * 失败只在弹窗内提示重试，不抛异常、不影响主流程。
 */
internal data class MessageTranslation(
    val sourceLangLabel: String,
    val targetLangLabel: String,
    val text: String,
)

private val translateHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

private fun String.containsCjk(): Boolean = any { c ->
    c in '一'..'鿿' || c in '぀'..'ヿ' || c in '가'..'힯' || c in '㐀'..'䶿'
}

internal suspend fun translateMessageText(rawMarkdown: String): Result<MessageTranslation> =
    withContext(Dispatchers.IO) {
        runCatching {
            // 去掉 markdown 标记再翻译，译文更干净。
            val plain = SpeechText.readable(rawMarkdown).trim().take(450)
            require(plain.isNotBlank()) { "empty" }
            val (from, to, fromLabel, toLabel) = if (plain.containsCjk()) {
                arrayOf("zh-CN", "en", "中文", "English")
            } else {
                arrayOf("en", "zh-CN", "English", "中文")
            }
            val url = "https://api.mymemory.translated.net/get" +
                "?q=${URLEncoder.encode(plain, "UTF-8")}" +
                "&langpair=$from|$to"
            val request = Request.Builder().url(url).get().build()
            translateHttpClient.newCall(request).execute().use { resp ->
                require(resp.isSuccessful) { "http ${resp.code}" }
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                require(json.optInt("responseStatus") == 200) { "bad status" }
                val translated = json.getJSONObject("responseData")
                    .optString("translatedText").trim()
                require(
                    translated.isNotEmpty() &&
                        !translated.startsWith("MYMEMORY WARNING") &&
                        !translated.startsWith("QUERY LENGTH LIMIT") &&
                        !translated.equals(plain, ignoreCase = true)
                ) { "bad result" }
                MessageTranslation(fromLabel, toLabel, translated)
            }
        }
    }

private sealed interface TranslateUiState {
    data object Loading : TranslateUiState
    data class Done(val result: MessageTranslation) : TranslateUiState
    data object Failed : TranslateUiState
}

/**
 * 翻译结果弹窗（Miuix 风格）：方向标注 + 译文（可选中）+ 复制 / 重试 / 关闭。
 */
@Composable
internal fun MessageTranslateDialog(
    text: String,
    onDismiss: () -> Unit,
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var state by remember(text) { mutableStateOf<TranslateUiState>(TranslateUiState.Loading) }

    fun load() {
        state = TranslateUiState.Loading
        scope.launch {
            translateMessageText(text).fold(
                onSuccess = { state = TranslateUiState.Done(it) },
                onFailure = { state = TranslateUiState.Failed },
            )
        }
    }
    LaunchedEffect(text) { load() }

    EtaWindowDialog(
        show = true,
        title = stringResource(R.string.msg_translate_title),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val s = state) {
                is TranslateUiState.Loading -> {
                    InfiniteProgressIndicator(size = 28.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.msg_translate_loading),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                is TranslateUiState.Failed -> {
                    Text(
                        text = stringResource(R.string.msg_translate_failed),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
                is TranslateUiState.Done -> {
                    Text(
                        text = "${s.result.sourceLangLabel} → ${s.result.targetLangLabel}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = s.result.text,
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (state) {
                    is TranslateUiState.Failed -> {
                        EtaTextButton(
                            text = stringResource(R.string.msg_translate_retry),
                            onClick = { load() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    is TranslateUiState.Done -> {
                        EtaTextButton(
                            text = stringResource(R.string.msg_translate_copy),
                            onClick = {
                                @Suppress("DEPRECATION")
                                clipboardManager.setText(
                                    AnnotatedString((state as TranslateUiState.Done).result.text)
                                )
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    else -> Unit
                }
                EtaTextButton(
                    text = stringResource(R.string.action_close),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
