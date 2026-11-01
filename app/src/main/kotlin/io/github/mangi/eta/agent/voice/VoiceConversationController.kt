package io.github.mangi.eta.agent.voice

import android.content.Context
import android.speech.SpeechRecognizer
import io.github.mangi.eta.core.AndroidAgentLogger
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/** 实时语音对话的阶段 */
enum class VoiceConversationPhase { Idle, Listening, Thinking, Speaking, Error }

data class VoiceConversationState(
    val phase: VoiceConversationPhase = VoiceConversationPhase.Idle,
    /** 用户说的话（识别中为 partial，最终为完整） */
    val transcript: String = "",
    /** 模型当前回复的全文（流式增长） */
    val reply: String = "",
    val error: String? = null,
) {
    val active: Boolean get() = phase != VoiceConversationPhase.Idle && phase != VoiceConversationPhase.Error
}

private data class ChatSnapshot(
    val isStreaming: Boolean = false,
    val lastAgentId: String? = null,
    val lastAgentText: String = "",
)

/**
 * 实时语音对话控制器（级联方案：ASR → 模型 → TTS）。
 *
 * 循环：聆听 → 识别成文字 → 提交给模型 → 按完整句子边生成边朗读 → 回到聆听。
 * 用户可在朗读时打断，回到聆听。
 *
 * 复用现有组件：EtaSpeechInput（系统语音识别）、SpeechPlaybackController（TTS 朗读）。
 */
class VoiceConversationController(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val playback: SpeechPlaybackController,
    private val submit: (String) -> Unit,
    private val interruptGeneration: () -> Unit,
) {
    private val mutableState = MutableStateFlow(VoiceConversationState())
    val state: StateFlow<VoiceConversationState> = mutableState.asStateFlow()

    private val chatSnapshot = MutableStateFlow(ChatSnapshot())
    private var job: Job? = null
    /** 朗读打断代际：用户点打断时 +1，TTS 循环检测到变化即回到聆听 */
    private var ttsGeneration = 0L

    /** UI 层每帧调用，把最新聊天快照喂进来 */
    fun updateChat(isStreaming: Boolean, lastAgentId: String?, lastAgentText: String) {
        chatSnapshot.value = ChatSnapshot(isStreaming, lastAgentId, lastAgentText)
    }

    fun start() {
        if (job?.isActive == true) return
        ttsGeneration = 0L
        job = scope.launch {
            try {
                conversationLoop()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AndroidAgentLogger.warn("Voice conversation failed: ${e.message}")
                mutableState.value = VoiceConversationState(
                    phase = VoiceConversationPhase.Error,
                    error = e.message ?: "语音对话失败",
                )
            } finally {
                playback.stop()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        playback.stop()
        mutableState.value = VoiceConversationState()
    }

    /** 朗读中打断：停掉 TTS，回到聆听（不结束整个会话） */
    fun interruptToListen() {
        ttsGeneration++
        playback.stop()
    }

    private suspend fun conversationLoop() {
        while (scope.isActive) {
            // ---- 1. 聆听 ----
            mutableState.value = VoiceConversationState(phase = VoiceConversationPhase.Listening)
            val text = recognizeOnce()
            if (text.isNullOrBlank()) {
                // 识别失败/空结果：短暂等待后继续聆听（避免无声紧循环）
                delay(400)
                continue
            }

            // ---- 2. 提交给模型 ----
            mutableState.value = VoiceConversationState(
                phase = VoiceConversationPhase.Thinking,
                transcript = text,
            )
            val baselineId = chatSnapshot.value.lastAgentId
            submit(text)

            // ---- 3. 等待新回复开始 ----
            try {
                withTimeout(30_000) {
                    chatSnapshot.first { it.lastAgentId != null && it.lastAgentId != baselineId }
                }
            } catch (e: Exception) {
                AndroidAgentLogger.warn("Voice conversation: no response started")
                continue
            }

            // ---- 4. 边生成边按句子朗读 ----
            speakStreaming()
            delay(300)
        }
    }

    private suspend fun speakStreaming() {
        val gen = ttsGeneration
        var speakingId: String? = null
        var spokenCount = 0
        while (scope.isActive) {
            if (ttsGeneration != gen) return  // 用户打断，回聆听
            val snap = chatSnapshot.value
            if (snap.lastAgentId == null) {
                chatSnapshot.first { it != snap }
                continue
            }
            if (speakingId != snap.lastAgentId) {
                speakingId = snap.lastAgentId
                spokenCount = 0
            }
            val finalized = !snap.isStreaming
            val sentences = completeSentences(snap.lastAgentText, finalized)
            val fresh = sentences.drop(spokenCount)
            if (fresh.isNotEmpty()) {
                mutableState.value = mutableState.value.copy(
                    phase = VoiceConversationPhase.Speaking,
                    reply = snap.lastAgentText,
                )
                for (sentence in fresh) {
                    if (ttsGeneration != gen) return
                    val sid = "voice-conv-${UUID.randomUUID()}"
                    playback.speak(sid, sentence)
                    // 等待本句播完（播完/出错/被打断时 messageId 都会离开 sid）
                    try {
                        withTimeout(120_000) {
                            playback.state.first { it.messageId != sid }
                        }
                    } catch (e: Exception) {
                        AndroidAgentLogger.warn("Voice conversation: TTS wait timeout")
                    }
                    playback.state.value.error?.let {
                        AndroidAgentLogger.warn("Voice conversation: TTS error $it")
                        return
                    }
                }
                spokenCount = sentences.size
            } else if (snap.lastAgentText.isNotBlank()) {
                mutableState.value = mutableState.value.copy(reply = snap.lastAgentText)
            }
            if (finalized) break
            chatSnapshot.first {
                it.lastAgentText != snap.lastAgentText ||
                    it.isStreaming != snap.isStreaming ||
                    it.lastAgentId != snap.lastAgentId
            }
        }
    }

    /** 系统语音识别单次识别，挂起直到出结果或出错 */
    private suspend fun recognizeOnce(): String? = suspendCancellableCoroutine { cont ->
        var input: EtaSpeechInput? = null
        input = EtaSpeechInput(
            context = appContext,
            onListening = {
                mutableState.value = mutableState.value.copy(phase = VoiceConversationPhase.Listening)
            },
            onRecognizing = { },
            onLevel = { },
            onPartial = { partial ->
                mutableState.value = mutableState.value.copy(transcript = partial)
            },
            onResult = { text ->
                if (cont.isActive) cont.resume(text)
            },
            onError = { issue ->
                AndroidAgentLogger.warn("Voice conversation: ASR error ${issue.errorCode} kind=${issue.kind}")
                if (cont.isActive) {
                    // 权限/无服务是致命错误，直接抛给外层进 Error 状态
                    if (issue.errorCode == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                        issue.kind == EtaSpeechIssueKind.NO_SERVICE
                    ) {
                        cont.resume(null)
                        job?.cancel(CancellationException("ASR unavailable"))
                    } else {
                        cont.resume(null)
                    }
                }
            },
            onDownloadStatus = { },
        )
        val handle = input
        cont.invokeOnCancellation { handle.cancel() }
        handle.start()
    }

    companion object {
        /**
         * 从文本中提取已完整的句子。未 finalized 时，最后一个不完整的句子先扣下，
         * 等它变完整再播，避免把半句话读出来。
         */
        fun completeSentences(text: String, finalized: Boolean): List<String> {
            val readable = try {
                SpeechText.readable(text)
            } catch (_: Exception) {
                text
            }.trim()
            if (readable.isEmpty()) return emptyList()
            val result = mutableListOf<String>()
            var start = 0
            var i = 0
            while (i < readable.length) {
                val c = readable[i]
                val isBoundary = c == '。' || c == '！' || c == '？' || c == '；' ||
                    c == '!' || c == '?' || c == '\n' ||
                    (c == '.' && (i == readable.length - 1 || readable[i + 1].isWhitespace()))
                if (isBoundary) {
                    val sentence = readable.substring(start, i + 1).trim()
                    if (sentence.isNotEmpty()) result.add(sentence)
                    start = i + 1
                }
                i++
            }
            val tail = readable.substring(start).trim()
            if (finalized) {
                if (tail.isNotEmpty()) result.add(tail)
            }
            // 过滤掉太短的碎片（比如单独的"你好"开头半句先不播）
            return result.filter { it.length >= 2 }
        }
    }
}
