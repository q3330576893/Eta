package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 对话内引用：长按消息气泡点「引用」，或在消息搜索结果里点引用，
 * 被引用的消息进输入框上方以引用条展示，用户输入追问后一起发给模型。
 */
@Immutable
data class QuotedMessageUi(
    val messageId: String,
    /** true=用户消息，false=助手消息 */
    val isUser: Boolean,
    /** 引用原文（发送时拼进 prompt） */
    val content: String,
)

/** 引用状态持有者：消息列表、输入栏、搜索面板通过 CompositionLocal 共享。 */
class ChatQuoteState {
    var quoted by mutableStateOf<QuotedMessageUi?>(null)
        private set

    fun quote(message: QuotedMessageUi) {
        if (message.content.isNotBlank()) {
            quoted = message
        }
    }

    fun clear() {
        quoted = null
    }

    /**
     * 发送时把引用拼进 prompt。返回拼好的文本（无引用时原样返回）。
     * 格式：`> 引用原文\n\n用户追问`，模型能看到被引用的原文。
     */
    fun applyToPrompt(text: String): String {
        val q = quoted ?: return text
        if (text.isBlank()) return text
        return "> ${q.content.trim()}\n\n${text.trim()}"
    }
}

internal val LocalChatQuote = staticCompositionLocalOf<ChatQuoteState?> { null }

/**
 * 输入框上方的引用条：微信/QQ 风格，左侧色条 + 发送者 + 内容摘要，右侧关闭按钮。
 */
@Composable
internal fun QuoteStrip(
    quoted: QuotedMessageUi,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧引用色条。
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MiuixTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Rounded.FormatQuote,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MiuixTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (quoted.isUser) R.string.quote_sender_user else R.string.quote_sender_assistant,
                ),
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                text = quoted.content.trim().replace(Regex("\\s+"), " "),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .clickable(onClick = onClear)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.quote_cancel),
                modifier = Modifier.size(16.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
