package io.github.mangi.eta.scheduler

import android.content.Context
import io.github.mangi.eta.data.db.EtaDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 定时任务仓库：CRUD + 调度联动。
 * 新增/启用 → 排闹钟；删除/禁用 → 取消闹钟。
 */
internal class ScheduledTaskRepository(private val appContext: Context) {
    private val context = appContext.applicationContext
    private val dao get() = EtaDatabase.get(context).scheduledTaskDao()

    fun observeTasks(): Flow<List<ScheduledTask>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getTask(id: String): ScheduledTask? =
        dao.getById(id)?.toDomain()

    suspend fun saveTask(task: ScheduledTask) {
        dao.upsert(task.toEntity())
        if (task.enabled) ScheduledTaskScheduler.schedule(context, task)
        else ScheduledTaskScheduler.cancel(context, task.id)
    }

    suspend fun deleteTask(id: String) {
        ScheduledTaskScheduler.cancel(context, id)
        dao.deleteById(id)
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val entity = dao.getById(id) ?: return
        dao.upsert(entity.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
        if (enabled) ScheduledTaskScheduler.schedule(context, entity.toDomain().copy(enabled = true))
        else ScheduledTaskScheduler.cancel(context, id)
    }

    suspend fun rescheduleAll() {
        ScheduledTaskScheduler.rescheduleAll(context)
    }
}
