package io.github.mangi.eta.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.core.safeLogType
import io.github.mangi.eta.data.db.EtaDatabase
import java.util.concurrent.TimeUnit

/**
 * 定时任务调度器。
 *
 * - ONCE / DAILY / WEEKLY：AlarmManager 精确闹钟（setExactAndAllowWhileIdle），
 *   触发后由 [TaskAlarmReceiver] 转交 WorkManager 执行，执行完再排下一次。
 * - HOURLY：WorkManager 周期任务（1 小时），系统自动重复。
 * - 开机：[TaskAlarmReceiver] 收到 BOOT_COMPLETED 后重排所有启用任务。
 */
internal object ScheduledTaskScheduler {
    const val EXTRA_TASK_ID = "scheduled_task_id"
    private const val HOURLY_WORK_NAME_PREFIX = "eta_hourly_task_"

    private fun requestCode(taskId: String): Int = taskId.hashCode()

    fun schedule(context: Context, task: ScheduledTask) {
        cancel(context, task.id)
        if (!task.enabled) return
        when (task.scheduleType) {
            ScheduleType.HOURLY -> scheduleHourlyWork(context, task)
            else -> scheduleExactAlarm(context, task)
        }
    }

    fun cancel(context: Context, taskId: String) {
        runCatching {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            pendingIntent(context, taskId, PendingIntent.FLAG_NO_CREATE)?.let { pi ->
                am.cancel(pi)
                pi.cancel()
            }
        }
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(HOURLY_WORK_NAME_PREFIX + taskId)
        }
    }

    /** 精确闹钟是否可用（Android 12+ 需要用户授予 SCHEDULE_EXACT_ALARM） */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    suspend fun rescheduleAll(context: Context) {
        val tasks = runCatching {
            EtaDatabase.get(context).scheduledTaskDao().getEnabled()
        }.getOrElse { emptyList() }
        tasks.forEach { entity ->
            runCatching { schedule(context, entity.toDomain()) }
                .onFailure {
                    AndroidAgentLogger.warn(
                        "Reschedule task failed: type=${it.safeLogType()}"
                    )
                }
        }
        AndroidAgentLogger.debug { "Rescheduled ${tasks.size} enabled tasks" }
    }

    private fun scheduleExactAlarm(context: Context, task: ScheduledTask) {
        val triggerAt = task.nextTriggerMillis()
        // 防止触发时间已过（极端时钟偏移）：至少 1 分钟后
        val safeTrigger = maxOf(triggerAt, System.currentTimeMillis() + 60_000L)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, task.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        runCatching {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeTrigger, pi)
            } else {
                // 无精确闹钟权限时降级为非精确（仍会触发，时间可能偏移）
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeTrigger, pi)
            }
            AndroidAgentLogger.debug {
                "Scheduled task '${task.title}' at ${java.util.Date(safeTrigger)}"
            }
        }.onFailure {
            AndroidAgentLogger.warn("Schedule exact alarm failed: type=${it.safeLogType()}")
        }
    }

    private fun scheduleHourlyWork(context: Context, task: ScheduledTask) {
        runCatching {
            val req = PeriodicWorkRequestBuilder<ScheduledTaskWorker>(1, TimeUnit.HOURS)
                .setInputData(workDataOf(EXTRA_TASK_ID to task.id))
                .addTag("eta_scheduled_task")
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                HOURLY_WORK_NAME_PREFIX + task.id,
                ExistingPeriodicWorkPolicy.UPDATE,
                req,
            )
        }.onFailure {
            AndroidAgentLogger.warn("Schedule hourly work failed: type=${it.safeLogType()}")
        }
    }

    private fun pendingIntent(context: Context, taskId: String, flags: Int): PendingIntent? {
        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = TaskAlarmReceiver.ACTION_FIRE
            putExtra(EXTRA_TASK_ID, taskId)
        }
        // Android 12+ 要求显式指定 mutability
        val mutableFlags = flags or PendingIntent.FLAG_IMMUTABLE
        return runCatching {
            PendingIntent.getBroadcast(context, requestCode(taskId), intent, mutableFlags)
        }.getOrNull()
    }
}
