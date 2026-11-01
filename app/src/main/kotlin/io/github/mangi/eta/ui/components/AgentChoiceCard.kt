package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.agent.overlay.AgentChoiceCardBridge
import top.yukonga.miuix.kmp.basic.Text

private val CardTitleColor = Color(0xFF111114)
private val CardSubColor = Color(0xFF6B6B73)
private val CardFootColor = Color(0xFFA0A0AA)
private val OptionBgNormal = Color(0xFFF4F4F7)
private val OptionBgRecommend = Color(0xFFEEFAF2)
private val OptionBorderRecommend = Color(0xFF3FC978)

/**
 * 悬浮选择卡片：底部白色圆角卡片，选项纵向排列，推荐项绿边高亮。
 * 用户点选后通过 [onSelect] 回传选项序号（从 0 开始）。
 */
@Composable
internal fun AgentChoiceCard(
    request: AgentChoiceCardBridge.ChoiceRequest,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp)
                .shadow(14.dp, RoundedCornerShape(26.dp))
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White)
                .padding(horizontal = 24.dp, vertical = 22.dp),
        ) {
            Text(
                text = request.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = CardTitleColor,
                modifier = Modifier.fillMaxWidth(),
            )
            if (request.sub.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = request.sub,
                    fontSize = 14.sp,
                    color = CardSubColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            request.options.forEachIndexed { index, option ->
                Spacer(Modifier.height(if (index == 0) 16.dp else 10.dp))
                val bg = if (option.recommend) OptionBgRecommend else OptionBgNormal
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(bg)
                        .then(
                            if (option.recommend) {
                                Modifier.border(1.2.dp, OptionBorderRecommend, RoundedCornerShape(16.dp))
                            } else Modifier
                        )
                        .clickable { onSelect(index) }
                        .padding(16.dp),
                ) {
                    Text(
                        text = option.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = CardTitleColor,
                    )
                    if (option.desc.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = option.desc,
                            fontSize = 14.sp,
                            color = CardSubColor,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "点选后自动返回对话",
                fontSize = 11.sp,
                color = CardFootColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 悬浮复制卡片：展示可一键复制的文本。
 * 用户点"复制"后 [onDone] 回传 true，直接关闭回传 false。
 */
@Composable
internal fun AgentCopyCard(
    request: AgentChoiceCardBridge.CopyRequest,
    onDone: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp)
                .shadow(14.dp, RoundedCornerShape(26.dp))
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White)
                .padding(horizontal = 24.dp, vertical = 22.dp),
        ) {
            Text(
                text = request.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = CardTitleColor,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(OptionBgNormal)
                    .padding(16.dp),
            ) {
                Text(
                    text = request.text,
                    fontSize = 15.sp,
                    color = CardTitleColor,
                    modifier = Modifier.verticalScroll(scrollState),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "关闭",
                    fontSize = 16.sp,
                    color = CardSubColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(OptionBgNormal)
                        .clickable { onDone(false) }
                        .padding(vertical = 12.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "复制",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(OptionBorderRecommend)
                        .clickable {
                            clipboard.setText(AnnotatedString(request.text))
                            onDone(true)
                        }
                        .padding(vertical = 12.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "点选后自动返回对话",
                fontSize = 11.sp,
                color = CardFootColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
