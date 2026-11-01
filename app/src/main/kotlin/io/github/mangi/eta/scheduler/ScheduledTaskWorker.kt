package io.github.mangi.eta.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.runtime.AgentRuntimeClient
import io.github.mangi.eta.agent.runtime.AgentRuntimeWire
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.core.safeLogType
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * 定时任务执行器（WorkManager）。
 *
 * 流程：读任务 → 条件检查（不满足则静默跳过）→ 取当前模型配置 →
 * 经 [AgentRuntimeClient] 提交一次 Agent run（阻塞等待，服务侧完成后
 * 自动经 TaskCompletionNotification 推送结果）→ 记录执行结果 → 排下一次。
 *
 * 注意：不改动 AgentRuntimeService 核心逻辑，只在外层调度。
 */
internal class ScheduledTaskWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(ScheduledTaskScheduler.EXTRA_TASK_ID)
            ?: return Result.failure()
        val dao = EtaDatabase.get(applicationContext).scheduledTaskDao()
        val entity = dao.getById(taskId) ?: return Result.failure()
        if (!entity.enabled) return Result.success()
        val task = entity.toDomain()

        // 1. 条件检查
        val condition = taskConditionFor(task.conditionType)
        if (condition != null) {
            val checkResult = runCatching {
                condition.check(applicationContext, task.conditionParams)
            }.getOrElse { ConditionResult.Pass } // 检查异常时 fail-open
            if (checkResult is ConditionResult.Skip) {
                dao.upsert(
                    entity.copy(
                        lastRunAt = System.currentTimeMillis(),
                        lastResult = "已跳过：${checkResult.reason}",
                    )
                )
                afterRun(task)
                return Result.success()
            }
        }

        // 2. 模型配置（未配置则跳过，不打扰）
        val config: AgentModelClient.ModelConfig = runCatching {
            RuntimeConfigRepository.currentRuntimeConfig()
        }.getOrNull() ?: run {
            dao.upsert(
                entity.copy(
                    lastRunAt = System.currentTimeMillis(),
                    lastResult = "已跳过：未配置模型",
                )
            )
            afterRun(task)
            return Result.success()
        }

        // 3. 提交 Agent run。阻塞等待；服务侧 enterFinalState 会自动推送任务完成通知。
        val runId = "sched_" + UUID.randomUUID().toString().replace("-", "")
        val result = withTimeoutOrNull(RUN_TIMEOUT_MILLIS) {
            withContext(Dispatchers.IO) {
                runCatching {
                    AgentRuntimeClient(applicationContext, AndroidAgentLogger).run(
                        request = AgentRuntimeWire.RunRequest(
                            runId = runId,
                            prompt = task.prompt,
                            config = config,
                            images = emptyList(),
                        ),
                        onEvent = { /* 定时任务不订阅中间事件 */ },
                    )
                }.getOrElse { throwable ->
                    AndroidAgentLogger.warn(
                        "Scheduled task run threw: type=${throwable.safeLogType()}"
                    )
                    null
                }
            }
        }
        if (result == null) {
            // 超时：尝试取消，避免僵尸运行
            runCatching {
                AgentRuntimeClient(applicationContext, AndroidAgentLogger).cancelRun(runId)
            }
        }
        val summary = when {
            result == null -> "执行超时，已取消"
            result.ok -> "已完成"
            else -> "失败：${result.error?.ifBlank { "未知错误" } ?: "未知错误"}"
        }
        dao.upsert(
            entity.copy(
                lastRunAt = System.currentTimeMillis(),
                lastResult = summary,
            )
        )
        afterRun(task)
        return Result.success()
    }

    private suspend fun afterRun(task: ScheduledTask) {
        val dao = EtaDatabase.get(applicationContext).scheduledTaskDao()
        when (task.scheduleType) {
            ScheduleType.ONCE -> {
                // 一次性任务：跑完即禁用
                dao.getById(task.id)?.let { entity ->
                    dao.upsert(entity.copy(enabled = false, updatedAt = System.currentTimeMillis()))
                }
                ScheduledTaskScheduler.cancel(applicationContext, task.id)
            }
            ScheduleType.HOURLY -> {
                // WorkManager 周期任务自动重复，无需处理
            }
            else -> {
                // Alarm 触发的周期任务：排下一次
                ScheduledTaskScheduler.schedule(applicationContext, task)
            }
        }
    }

    companion object {
        /** 单次执行超时（WorkManager 前台限制约 10 分钟，留余量） */
        private const val RUN_TIMEOUT_MILLIS = 9 * 60 * 1000L
    }
}
