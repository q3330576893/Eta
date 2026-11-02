package io.github.mangi.eta.agent.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.github.mangi.eta.core.AndroidAgentLogger
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android 系统 TTS 封装（无需 API Key，开箱即用）。
 *
 * 用于 [TtsProvider.SYSTEM]：直接调用设备自带的语音合成引擎播报中文。
 * 初始化是异步的，[awaitReady] 会挂起直到引擎就绪；中文语音数据缺失时抛
 * [SpeechFailure]，引导用户去系统设置下载。
 */
internal class SystemTtsEngine(context: Context) {
    private val ready = CompletableDeferred<TextToSpeech>()
    private var tts: TextToSpeech? = null
    private var closed = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (closed) return@TextToSpeech
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                val result = engine.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    ready.completeExceptionally(
                        SpeechFailure(
                            SpeechErrorCode.CONFIGURATION,
                            "系统缺少中文语音数据，请到系统设置→语言→语音输出中下载"
                        )
                    )
                } else {
                    ready.complete(engine)
                }
            } else {
                ready.completeExceptionally(
                    SpeechFailure(SpeechErrorCode.AUDIO, "系统语音引擎初始化失败")
                )
            }
        }
    }

    /** 等待引擎就绪；失败时抛 [SpeechFailure]。 */
    suspend fun awaitReady(): TextToSpeech = ready.await()

    /**
     * 播报文本，播完（或出错/被打断）后返回。
     * 用 QUEUE_FLUSH，每次调用都会打断正在播的上一句。
     * 必须在主线程调用（TextToSpeech 要求）。
     */
    suspend fun speakAndAwait(text: String) {
        val engine = try {
            ready.await()
        } catch (e: Exception) {
            throw e.speechFailure()
        }
        val id = UUID.randomUUID().toString()
        suspendCancellableCoroutine { cont ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit
                override fun onDone(utteranceId: String) {
                    if (utteranceId == id && !cont.isCompleted) cont.resume(Unit)
                }

                override fun onError(utteranceId: String) {
                    if (utteranceId == id && !cont.isCompleted) {
                        cont.resumeWithException(SpeechFailure(SpeechErrorCode.AUDIO, "系统语音播报出错"))
                    }
                }

                override fun onStop(utteranceId: String, interrupted: Boolean) {
                    // 被 tts.stop() 打断：按取消处理
                    if (utteranceId == id && !cont.isCompleted) {
                        cont.resumeWithException(CancellationException("system tts interrupted"))
                    }
                }
            })
            cont.invokeOnCancellation { runCatching { engine.stop() } }
            // 主线程调用：speakAndAwait 的调用方已在 Dispatchers.Main.immediate
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
            if (result != TextToSpeech.SUCCESS) {
                AndroidAgentLogger.warn("Eta system tts speak failed: result=$result")
                if (!cont.isCompleted) {
                    cont.resumeWithException(SpeechFailure(SpeechErrorCode.AUDIO, "系统语音播报启动失败"))
                }
            }
        }
    }

    /** 打断当前播报。 */
    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        closed = true
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        if (!ready.isCompleted) {
            ready.completeExceptionally(SpeechFailure(SpeechErrorCode.AUDIO, "系统语音引擎已关闭"))
        }
    }
}
