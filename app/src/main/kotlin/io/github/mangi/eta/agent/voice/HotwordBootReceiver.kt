package io.github.mangi.eta.agent.voice

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.data.datastore.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 热词唤醒开机启动接收器
 *
 * 开机完成后，如果用户开启了热词唤醒且已授予录音权限，则启动 HotwordService。
 * 同时处理看门狗重启（任务被移除后）。
 */
class HotwordBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HotwordBootReceiver"
        private const val RESTART_REQUEST_CODE = 3001
        private const val RESTART_ACTION = "io.github.mangi.eta.hotword.RESTART"

        /**
         * 安排服务重启（任务被移除时调用）
         */
        fun scheduleRestart(context: Context) {
            try {
                val intent = Intent(context, HotwordBootReceiver::class.java).apply {
                    action = RESTART_ACTION
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    RESTART_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val alarmManager =
                    context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                // 1分钟后重启
                val triggerTime = SystemClock.elapsedRealtime() + 60_000
                if (Build.VERSION.SDK_INT >= 23) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    @Suppress("DEPRECATION")
                    alarmManager.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } catch (e: Exception) {
                AndroidAgentLogger.warnThrottled("hotword_restart_schedule_failed") {
                    "热词服务重启计划失败: ${e.message}"
                }
            }
        }

        /**
         * 取消重启计划
         */
        fun cancelRestart(context: Context) {
            try {
                val intent = Intent(context, HotwordBootReceiver::class.java).apply {
                    action = RESTART_ACTION
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    RESTART_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
                )
                pendingIntent?.let {
                    val alarmManager =
                        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    alarmManager.cancel(it)
                    it.cancel()
                }
            } catch (_: Exception) {
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            RESTART_ACTION -> {
                // 异步检查设置，避免阻塞广播
                scope.launch {
                    try {
                        checkAndStart(context)
                    } finally {
                        scope.cancel()
                    }
                }
            }
        }
    }

    private suspend fun checkAndStart(context: Context) {
        try {
            // 检查用户是否开启了热词唤醒
            val speechSettings = SettingsDataStore.speechSettings()
            if (!speechSettings.hotwordEnabled) {
                return
            }

            // 检查录音权限
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                AndroidAgentLogger.warnThrottled("hotword_boot_no_permission") {
                    "开机启动跳过：未授予录音权限"
                }
                return
            }

            // 检查模型是否就绪
            if (!HotwordModelManager.isModelReady(context)) {
                AndroidAgentLogger.warnThrottled("hotword_boot_no_model") {
                    "开机启动跳过：模型未下载"
                }
                return
            }

            AndroidAgentLogger.debug {
                "开机启动热词服务"
            }
            HotwordService.start(context)
        } catch (e: Exception) {
            AndroidAgentLogger.warnThrottled("hotword_boot_failed") {
                "开机启动热词服务失败: ${e.message}"
            }
        }
    }
}
