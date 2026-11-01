package io.github.mangi.eta.agent.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.mangi.eta.R
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.repository.SpeechSettingsRepository
import io.github.mangi.eta.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

/**
 * 热词唤醒前台服务（Vosk 离线识别）
 *
 * 持续监听麦克风，检测到用户自定义唤醒词（默认"艾塔助手"）时触发语音会话。
 * 参考 openclaw-assistant 的 HotwordService 实现。
 */
class HotwordService : Service(), RecognitionListener {

    companion object {
        private const val TAG = "HotwordService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "hotword_wakeup"

        // 唤醒词：用户自定义（默认"艾塔助手"），英文"etta"为辅
        // 注意：Vosk 中文模型对短词更敏感，建议 2-4 个字
        private const val FALLBACK_HOTWORD_EN = "etta"

        // Vosk 采样率
        private const val SAMPLE_RATE = 16000f

        // 看门狗检查间隔（5分钟）
        private const val WATCHDOG_INTERVAL_MS = 5 * 60 * 1000L

        const val ACTION_START = "io.github.mangi.eta.hotword.START"
        const val ACTION_STOP = "io.github.mangi.eta.hotword.STOP"
        const val ACTION_PAUSE = "io.github.mangi.eta.hotword.PAUSE"
        const val ACTION_RESUME = "io.github.mangi.eta.hotword.RESUME"
        const val ACTION_RELOAD = "io.github.mangi.eta.hotword.RELOAD"

        fun start(context: Context) {
            val intent = Intent(context, HotwordService::class.java).setAction(ACTION_START)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                AndroidAgentLogger.warnThrottled("hotword_start_failed") {
                    "HotwordService 启动失败: ${e.message}"
                }
            }
        }

        fun stop(context: Context) {
            // 取消看门狗重启计划（用户手动关闭）
            HotwordBootReceiver.cancelRestart(context)
            val intent = Intent(context, HotwordService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var watchdogJob: Job? = null

    @Volatile
    private var isPaused = false

    @Volatile
    private var isModelReady = false

    /** 当前生效的唤醒词列表（用户自定义 + 英文兜底） */
    @Volatile
    private var activeHotwords: List<String> = listOf("艾塔助手", FALLBACK_HOTWORD_EN)

    /** 通知外部唤醒词已变更，重建识别器 */
    fun reload(context: Context) {
        val intent = Intent(context, HotwordService::class.java).setAction(ACTION_RELOAD)
        context.startService(intent)
    }

    private val pauseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PAUSE -> pauseListening()
                ACTION_RESUME -> resumeListening()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // 注册暂停/恢复广播（语音会话占用麦克风时暂停热词）
        val filter = IntentFilter().apply {
            addAction(ACTION_PAUSE)
            addAction(ACTION_RESUME)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(pauseReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pauseReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                pauseListening()
                return START_STICKY
            }
            ACTION_RESUME -> {
                resumeListening()
                return START_STICKY
            }
            ACTION_RELOAD -> {
                // 唤醒词变更：重建识别器
                scope.launch { reloadHotwords() }
                return START_STICKY
            }
        }

        // 启动前台服务
        startForegroundWithNotification()

        // 获取 WakeLock（息屏保活）
        acquireWakeLock()

        // 初始化 Vosk
        scope.launch {
            initVosk()
        }

        // 启动看门狗
        startWatchdog()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(pauseReceiver)
        } catch (_: Exception) {
        }
        stopListening()
        releaseWakeLock()
        watchdogJob?.cancel()
        scope.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 任务被移除时，用 AlarmManager 重启服务（防杀）
        HotwordBootReceiver.scheduleRestart(this)
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.hotword_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.hotword_notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 启动前台通知
     */
    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.hotword_notification_title))
            .setContentText(getString(R.string.hotword_notification_text))
            .setSmallIcon(R.drawable.ic_assistant_voice)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /**
     * 获取 Partial WakeLock，保持 CPU 运行（息屏时）
     */
    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Eta:HotwordWakeLock"
            ).apply {
                acquire(12 * 60 * 60 * 1000L) // 12小时超时
            }
        } catch (e: Exception) {
            AndroidAgentLogger.warnThrottled("hotword_wakelock_failed") {
                "WakeLock 获取失败: ${e.message}"
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null
        } catch (_: Exception) {
        }
    }

    /**
     * 初始化 Vosk 模型
     */
    private suspend fun initVosk() = withContext(Dispatchers.IO) {
        try {
            // 先读取用户自定义唤醒词
            loadHotwordsFromSettings()

            // 检查模型是否已下载
            val modelDir = HotwordModelManager.getModelDir(this@HotwordService)
            if (!HotwordModelManager.isModelReady(modelDir)) {
                AndroidAgentLogger.warnThrottled("hotword_model_missing") {
                    "热词模型未下载，请在设置中下载"
                }
                // 模型未就绪，停止服务
                withContext(Dispatchers.Main) {
                    stopSelf()
                }
                return@withContext
            }

            // 加载模型
            model = Model(modelDir.absolutePath)
            isModelReady = true

            withContext(Dispatchers.Main) {
                if (!isPaused) {
                    startListening()
                }
            }
        } catch (e: IOException) {
            AndroidAgentLogger.warnThrottled("hotword_init_failed") {
                "Vosk 模型加载失败: ${e.message}"
            }
        } catch (e: Exception) {
            AndroidAgentLogger.warnThrottled("hotword_init_error") {
                "Vosk 初始化异常: ${e.message}"
            }
        }
    }

    /**
     * 从设置读取用户自定义唤醒词
     */
    private suspend fun loadHotwordsFromSettings() {
        try {
            val settings = SpeechSettingsRepository.settingsFlow()
                .let { kotlinx.coroutines.flow.first(it) }
            val phrase = settings.hotwordPhrase.trim().ifEmpty { "艾塔助手" }
            activeHotwords = listOf(phrase, FALLBACK_HOTWORD_EN).distinct()
            AndroidAgentLogger.debug { "热词已加载: ${activeHotwords.joinToString()}" }
        } catch (e: Exception) {
            AndroidAgentLogger.warnThrottled("hotword_phrase_load_failed") {
                "唤醒词读取失败，使用默认: ${e.message}"
            }
        }
    }

    /**
     * 唤醒词变更后重建识别器
     */
    private suspend fun reloadHotwords() {
        loadHotwordsFromSettings()
        withContext(Dispatchers.Main) {
            if (isModelReady && !isPaused) {
                // 停止旧监听，用新唤醒词重建
                try {
                    speechService?.stop()
                    speechService?.shutdown()
                } catch (_: Exception) {
                }
                speechService = null
                startListening()
            }
        }
    }

    /**
     * 开始监听
     */
    private fun startListening() {
        if (!isModelReady || isPaused) return

        try {
            val currentModel = model ?: return

            // 构建热词语法（JSON 数组格式），用用户自定义唤醒词
            val hotwords = activeHotwords
            val hotwordsJson = (hotwords + "[unk]").joinToString("\", \"", "[\"", "\"]")

            val recognizer = Recognizer(currentModel, SAMPLE_RATE, hotwordsJson)
            // 启用词语时间戳（可选，用于置信度）
            recognizer.setWords(true)

            speechService = SpeechService(recognizer, SAMPLE_RATE).apply {
                startListening(this@HotwordService)
            }

            AndroidAgentLogger.debug {
                "热词监听已启动: ${hotwords.joinToString()}"
            }
        } catch (e: IOException) {
            AndroidAgentLogger.warnThrottled("hotword_listen_failed") {
                "热词监听启动失败: ${e.message}"
            }
            // 延迟重试
            scope.launch {
                delay(5000)
                if (isActive && !isPaused) {
                    startListening()
                }
            }
        }
    }

    /**
     * 停止监听
     */
    private fun stopListening() {
        try {
            speechService?.apply {
                stop()
                shutdown()
            }
            speechService = null
        } catch (_: Exception) {
        }
        try {
            model?.close()
        } catch (_: Exception) {
        }
        model = null
        isModelReady = false
    }

    /**
     * 暂停监听（语音会话占用麦克风时）
     */
    private fun pauseListening() {
        isPaused = true
        try {
            speechService?.stop()
        } catch (_: Exception) {
        }
    }

    /**
     * 恢复监听
     */
    private fun resumeListening() {
        isPaused = false
        if (isModelReady) {
            startListening()
        }
    }

    /**
     * 启动看门狗（定期检查服务存活）
     */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                // 检查 Vosk 是否还在运行，如果停止了则重启
                if (!isPaused && isModelReady && speechService == null) {
                    AndroidAgentLogger.debug {
                        "看门狗检测到监听停止，正在重启"
                    }
                    startListening()
                }
            }
        }
    }

    // ── RecognitionListener 回调 ──────────────────────────────────────────

    override fun onPartialResult(hypothesis: String?) {
        // 部分结果不处理，只看最终结果（降低误唤醒）
    }

    override fun onResult(hypothesis: String?) {
        if (hypothesis.isNullOrEmpty()) return

        try {
            val json = JSONObject(hypothesis)
            val text = json.optString("text", "").trim()

            if (text.isEmpty()) return

            // 检查是否包含唤醒词
            val matched = activeHotwords.any { hotword ->
                text.contains(hotword, ignoreCase = true)
            }

            if (matched) {
                AndroidAgentLogger.debug {
                    "检测到唤醒词: $text"
                }
                onHotwordDetected()
            }
        } catch (e: Exception) {
            // JSON 解析失败，忽略
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        // 最终结果也检查（与 onResult 逻辑相同）
        onResult(hypothesis)
    }

    override fun onError(exception: Exception?) {
        AndroidAgentLogger.warnThrottled("hotword_recognition_error") {
            "Vosk 识别错误: ${exception?.message}"
        }
        // 出错后延迟重启监听
        scope.launch {
            delay(3000)
            if (isActive && !isPaused && isModelReady) {
                stopListening()
                // 重新初始化
                initVosk()
            }
        }
    }

    override fun onTimeout() {
        // Vosk 超时，重启监听
        scope.launch {
            if (isActive && !isPaused) {
                startListening()
            }
        }
    }

    /**
     * 检测到唤醒词时的处理
     */
    private fun onHotwordDetected() {
        // 先暂停热词监听，释放麦克风
        pauseListening()

        // 发送暂停广播（通知其他组件）
        sendBroadcast(Intent(ACTION_PAUSE).setPackage(packageName))

        scope.launch {
            try {
                // 尝试通过 VoiceInteractionService 拉起会话
                val success = EtaVoiceInteractionService.requestSession()

                if (!success) {
                    // 如果 VoiceInteractionService 不可用，尝试启动 Activity
                    val intent = Intent(
                        this@HotwordService,
                        EtaVoiceAssistActivity::class.java
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }

                // 延迟后恢复监听（给语音会话留出时间）
                delay(3000)
                if (isActive) {
                    sendBroadcast(Intent(ACTION_RESUME).setPackage(packageName))
                    resumeListening()
                }
            } catch (e: Exception) {
                AndroidAgentLogger.warnThrottled("hotword_trigger_failed") {
                    "唤醒触发失败: ${e.message}"
                }
                // 失败也要恢复监听
                resumeListening()
            }
        }
    }
}
