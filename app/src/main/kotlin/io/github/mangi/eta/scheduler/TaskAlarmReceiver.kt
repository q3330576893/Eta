package io.github.mangi.eta.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.core.safeLogType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 定时任务闹钟接收器：
 * - ACTION_FIRE：精确闹钟触发 → 转交 WorkManager 执行（避免在 Receiver 里做耗时工作）
 * - BOOT_COMPLETED：开机后重排所有启用任务（闹钟在重启后会丢失）
 */
internal class TaskAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> {
                val taskId = intent.getStringExtra(ScheduledTaskScheduler.EXTRA_TASK_ID)
                    ?: return
                runCatching {
                    val req = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
                        .setInputData(workDataOf(ScheduledTaskScheduler.EXTRA_TASK_ID to taskId))
                        .addTag("eta_scheduled_task")
                        .build()
                    WorkManager.getInstance(context.applicationContext).enqueue(req)
                }.onFailure {
                    AndroidAgentLogger.warn(
                        "Enqueue scheduled task failed: type=${it.safeLogType()}"
                    )
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                // 开机广播时间有限，只做重排（IO 线程）
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    runCatching {
                        ScheduledTaskScheduler.rescheduleAll(context.applicationContext)
                    }.onFailure {
                        AndroidAgentLogger.warn(
                            "Reschedule after boot failed: type=${it.safeLogType()}"
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "io.github.mangi.eta.scheduler.TASK_ALARM_FIRE"
    }
}
