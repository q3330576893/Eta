package io.github.mangi.eta.ui.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.voice.VoiceConversationController
import io.github.mangi.eta.agent.voice.VoiceConversationPhase
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 实时语音对话面板（底部弹窗）。
 *
 * 显示当前阶段（聆听/思考/说话）、用户说的话、模型回复（流式），
 * 朗读时点按可打断回到聆听，右上角关闭结束整个会话。
 */
@Composable
internal fun VoiceConversationPanel(
    controller: VoiceConversationController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()

    val phaseText = when (state.phase) {
        VoiceConversationPhase.Idle -> stringResource(R.string.voice_conv_idle)
        VoiceConversationPhase.Listening -> stringResource(R.string.voice_conv_listening)
        VoiceConversationPhase.Thinking -> stringResource(R.string.voice_conv_thinking)
        VoiceConversationPhase.Speaking -> stringResource(R.string.voice_conv_speaking)
        VoiceConversationPhase.Error -> state.error ?: stringResource(R.string.voice_conv_error)
    }
    val phaseColor = when (state.phase) {
        VoiceConversationPhase.Listening -> Color(0xFF4CAF50)
        VoiceConversationPhase.Thinking -> Color(0xFFFF9800)
        VoiceConversationPhase.Speaking -> MiuixTheme.colorScheme.primary
        VoiceConversationPhase.Error -> Color(0xFFF44336)
        VoiceConversationPhase.Idle -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MiuixTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoicePhaseIndicator(phase = state.phase, color = phaseColor)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = phaseText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.voice_conv_close),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 用户说的话
        if (state.transcript.isNotBlank()) {
            Text(
                text = stringResource(R.string.voice_conv_you_said, state.transcript),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 模型回复（流式）
        if (state.reply.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                    .padding(14.dp),
            ) {
                Text(
                    text = state.reply,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 底部操作：朗读中点按打断，其他时候是停止按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.phase == VoiceConversationPhase.Speaking) {
                TapToInterruptButton(onClick = { controller.interruptToListen() })
            } else {
                StopConversationButton(onClick = onClose)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun VoicePhaseIndicator(phase: VoiceConversationPhase, color: Color) {
    if (phase == VoiceConversationPhase.Listening) {
        val transition = rememberInfiniteTransition(label = "voice-pulse")
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(700),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse",
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp),
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (phase) {
                    VoiceConversationPhase.Speaking -> Icons.Rounded.GraphicEq
                    VoiceConversationPhase.Error -> Icons.Rounded.Close
                    else -> Icons.Rounded.Mic
                },
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun TapToInterruptButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Stop,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.voice_conv_tap_to_interrupt),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun StopConversationButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.voice_conv_stop),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
