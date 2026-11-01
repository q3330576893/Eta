package io.github.mangi.eta.agent.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import android.speech.SpeechRecognizer

internal enum class EtaSpeechPhase { IDLE, STARTING, LISTENING, RECOGNIZING }

internal data class EtaSpeechState(
    val phase: EtaSpeechPhase = EtaSpeechPhase.IDLE,
    val errorRes: Int? = null,
    val message: String? = null,
    val configureAvailable: Boolean = false,
    val feedbackIsError: Boolean = true,
    /** 用户正按住麦克风说话：松手才结束，界面保持输入栏而不切到语音态。 */
    val holdToTalk: Boolean = false,
) {
    val active: Boolean get() = phase != EtaSpeechPhase.IDLE
    val hasFeedback: Boolean get() = !message.isNullOrBlank() || errorRes != null
}

@Composable
internal fun EtaSpeechFeedback(
    speech: EtaSpeechState,
    colors: EtaVoicePanelColors,
    onOpenSpeechSettings: () -> Unit,
) {
    val message = speech.message ?: speech.errorRes?.let { stringResource(it) }
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val dotColor = if (speech.feedbackIsError) {
        if (dark) Color(0xFFFF6B6B) else Color(0xFFE5484D)
    } else {
        MiuixTheme.colorScheme.primary
    }
    val actionTint = MiuixTheme.colorScheme.primary
    val actionBackground = actionTint.copy(alpha = if (dark) 0.20f else 0.10f)

    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn(tween(180)) + expandVertically(
            expandFrom = Alignment.Bottom,
            animationSpec = tween(200, easing = FastOutSlowInEasing),
        ),
        exit = fadeOut(tween(120)) + shrinkVertically(
            shrinkTowards = Alignment.Bottom,
            animationSpec = tween(160, easing = FastOutSlowInEasing),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(CircleShape)
                    .background(colors.chipBackground)
                    .border(0.75.dp, colors.chipStroke, CircleShape)
                    .then(
                        if (speech.configureAvailable) {
                            Modifier.clickable(onClick = onOpenSpeechSettings)
                        } else {
                            Modifier
                        },
                    )
                    .padding(
                        start = 14.dp,
                        end = if (speech.configureAvailable) 6.dp else 14.dp,
                        top = 5.dp,
                        bottom = 5.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Text(
                    text = message.orEmpty(),
                    color = colors.inputPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (speech.configureAvailable) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(actionBackground)
                            .clickable(onClick = onOpenSpeechSettings)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.speech_open_settings),
                            color = actionTint,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** 系统语音识别的错误码 → 提示文案。 */
internal fun speechIssueMessage(issue: EtaSpeechIssue): Int = when (issue.kind) {
    EtaSpeechIssueKind.NO_SERVICE -> R.string.voice_speech_no_service
    EtaSpeechIssueKind.SERVICE_TIMEOUT -> R.string.voice_speech_service_timeout
    EtaSpeechIssueKind.DOWNLOAD_AVAILABLE -> R.string.voice_model_needed
    EtaSpeechIssueKind.DOWNLOAD_PENDING -> R.string.voice_model_pending
    EtaSpeechIssueKind.RECOGNITION -> when (issue.errorCode) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_audio_permission
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.voice_no_speech
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> R.string.voice_speech_network_error
        SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> R.string.voice_speech_service_error
        SpeechRecognizer.ERROR_AUDIO -> R.string.voice_speech_audio_error
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.voice_speech_busy
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> R.string.voice_speech_rate_limited
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> R.string.voice_language_not_supported
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> R.string.voice_language_unavailable
        else -> R.string.voice_speech_unavailable
    }
}
