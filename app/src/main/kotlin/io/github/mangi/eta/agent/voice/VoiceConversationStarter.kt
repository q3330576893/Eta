package io.github.mangi.eta.agent.voice

import android.content.Context
import android.content.Intent

/**
 * 实时语音对话的外部启动入口。
 *
 * 系统助手唤醒后，调用 [start] 即可让当前聊天页
 * 打开实时语音对话面板。聊天页通过广播接收，处于前台时响应。
 */
object VoiceConversationStarter {
    const val ACTION_START_VOICE_CONVERSATION = "io.github.mangi.eta.voice.START_CONVERSATION"

    fun start(context: Context) {
        context.sendBroadcast(
            Intent(ACTION_START_VOICE_CONVERSATION).setPackage(context.packageName),
        )
    }
}
