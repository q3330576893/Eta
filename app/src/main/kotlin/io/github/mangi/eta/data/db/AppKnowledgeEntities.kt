package io.github.mangi.eta.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * App 使用知识库：记住用户在各个 App 中的操作流程。
 *
 * stepsJson: JSON 数组字符串，格式如
 * [{"action":"click","target":"搜索框","text":""},
 *  {"action":"input","target":"搜索框","text":"周杰伦"},
 *  {"action":"click","target":"播放按钮","text":""}]
 * action 取值：click（点击） / input（输入） / swipe（滑动） / back（返回） / wait（等待）
 */
@Entity(tableName = "app_knowledge")
internal data class AppKnowledgeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val appName: String,
    val actionDescription: String,
    val stepsJson: String,
    val useCount: Int = 0,
    val lastUsed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
internal interface AppKnowledgeDao {
    @Query("SELECT * FROM app_knowledge WHERE packageName = :packageName ORDER BY useCount DESC, lastUsed DESC")
    fun observeByPackage(packageName: String): Flow<List<AppKnowledgeEntity>>

    @Query("SELECT * FROM app_knowledge ORDER BY lastUsed DESC")
    fun observeAll(): Flow<List<AppKnowledgeEntity>>

    @Query("SELECT * FROM app_knowledge WHERE packageName = :packageName")
    suspend fun getByPackage(packageName: String): List<AppKnowledgeEntity>

    @Query("SELECT * FROM app_knowledge WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AppKnowledgeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppKnowledgeEntity)

    @Query("DELETE FROM app_knowledge WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE app_knowledge SET useCount = useCount + 1, lastUsed = :now WHERE id = :id")
    suspend fun bumpUsage(id: String, now: Long)
}
