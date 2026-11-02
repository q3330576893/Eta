package io.github.mangi.eta.ui.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.voice.VoiceConversationController
import io.github.mangi.eta.agent.voice.VoiceConversationStarter
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.AgentMessageUi

/** 打开实时语音对话面板的动作，输入栏按钮与热词广播都走这里 */
val LocalVoiceConversationOpen = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * 实时语音对话宿主：创建控制器、喂聊天快照、管理面板显隐、接收热词唤醒广播。
 *
 * 放在 SpeechPlaybackHost 内部，确保能拿到 LocalSpeechPlayback。
 */
@Composable
internal fun VoiceConversationHost(
    messages: List<AgentChatMessageUi>,
    isStreaming: Boolean,
    onSubmit: (String) -> Unit,
    onStop: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playback = LocalSpeechPlayback.current
    var showPanel by remember { mutableStateOf(false) }

    val controller = remember(context, scope, playback) {
        playback?.let {
            VoiceConversationController(
                appContext = context.applicationContext,
                scope = scope,
                playback = it,
                submit = onSubmit,
                interruptGeneration = onStop,
            )
        }
    }

    // 热词唤醒 / 系统助手唤醒 → 打开语音对话面板
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == VoiceConversationStarter.ACTION_START_VOICE_CONVERSATION) {
                    if (controller != null) showPanel = true
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(VoiceConversationStarter.ACTION_START_VOICE_CONVERSATION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    // 把最新聊天快照喂给控制器（用于跟踪流式回复做边播边读）
    LaunchedEffect(messages, isStreaming) {
        val lastAgent = messages.lastOrNull { it is AgentMessageUi } as? AgentMessageUi
        controller?.updateChat(isStreaming, lastAgent?.id, lastAgent?.content ?: "")
    }

    // 面板打开 → 启动对话循环；关闭 → 停止
    LaunchedEffect(showPanel) {
        if (showPanel) controller?.start() else controller?.stop()
    }

    CompositionLocalProvider(
        // Bug 3 修复：controller 为 null（TTS 未配置）时弹 Toast 提示，不再静默无反应
        LocalVoiceConversationOpen provides {
            val c = controller
            if (c != null) {
                showPanel = true
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.voice_conversation_need_tts),
                    Toast.LENGTH_LONG
                ).show()
            }
        },
        content = content,
    )

    if (showPanel && controller != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 半透明遮罩，点按不关闭（避免误触），只有关闭按钮能结束
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(enabled = false, onClick = {}),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                VoiceConversationPanel(
                    controller = controller,
                    onClose = { showPanel = false },
                )
            }
        }
    }
}
