package io.github.mangi.eta.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 定时任务表：自然语言定时/条件任务。
 *
 * scheduleType: ONCE（一次） / DAILY（每天） / WEEKLY（每周） / HOURLY（每小时）
 * conditionType: NONE（无条件） / WEATHER_RAIN（下雨才执行）
 */
@Entity(tableName = "scheduled_tasks")
internal data class ScheduledTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val prompt: String,
    val scheduleType: String,
    val hour: Int = 8,
    val minute: Int = 0,
    /** 1=Monday .. 7=Sunday（仅 WEEKLY 使用） */
    val dayOfWeek: Int? = null,
    val enabled: Boolean = true,
    val conditionType: String = "NONE",
    val conditionParams: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long? = null,
    val lastResult: String = "",
)

@Dao
internal interface ScheduledTaskDao {
    @Query("SELECT * FROM scheduled_tasks ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks ORDER BY createdAt ASC")
    suspend fun getAll(): List<ScheduledTaskEntity>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ScheduledTaskEntity?

    @Query("SELECT * FROM scheduled_tasks WHERE enabled = 1")
    suspend fun getEnabled(): List<ScheduledTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: ScheduledTaskEntity)

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}
