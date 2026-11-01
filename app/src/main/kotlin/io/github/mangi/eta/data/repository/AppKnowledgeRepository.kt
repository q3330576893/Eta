package io.github.mangi.eta.data.repository

import android.content.Context
import io.github.mangi.eta.data.db.AppKnowledgeEntity
import io.github.mangi.eta.data.db.EtaDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

/**
 * App 使用知识库仓库：记住 / 回忆用户在各 App 中的操作流程。
 *
 * MVP 说明：
 * - remember：同一 App + 相同操作描述视为同一条，直接更新步骤（不产生重复）。
 * - recall：按包名过滤，再按用户意图做关键词/子串匹配打分，useCount 与 lastUsed 参与排序。
 * - 数据库操作通过 runBlocking 同步执行，调用方是 Agent 工具后台线程。
 */
internal object AppKnowledgeRepository {

    private fun dao(context: Context) =
        EtaDatabase.get(context.applicationContext).appKnowledgeDao()

    fun observeAll(context: Context): Flow<List<AppKnowledgeEntity>> =
        dao(context).observeAll()

    fun observeByPackage(context: Context, packageName: String): Flow<List<AppKnowledgeEntity>> =
        dao(context).observeByPackage(packageName)

    /** 记住一条操作流程；同包名+同描述则更新步骤。返回最终实体。 */
    fun remember(
        context: Context,
        packageName: String,
        appName: String,
        actionDescription: String,
        stepsJson: String,
    ): AppKnowledgeEntity = runBlocking {
        val d = dao(context)
        val now = System.currentTimeMillis()
        val existing = d.getByPackage(packageName)
            .firstOrNull { it.actionDescription.trim() == actionDescription.trim() }
        val entity = if (existing != null) {
            existing.copy(
                appName = appName.ifBlank { existing.appName },
                stepsJson = stepsJson,
                lastUsed = now,
            )
        } else {
            AppKnowledgeEntity(
                packageName = packageName,
                appName = appName.ifBlank { packageName },
                actionDescription = actionDescription,
                stepsJson = stepsJson,
                lastUsed = now,
                createdAt = now,
            )
        }
        d.upsert(entity)
        entity
    }

    /**
     * 回忆操作流程：按包名过滤后打分排序。
     * 中文无分词：整句包含直接 +100；空白分词后的 token 命中每个 +1；
     * 再按 useCount、lastUsed 排序。
     */
    fun recall(
        context: Context,
        packageName: String,
        query: String,
    ): List<AppKnowledgeEntity> = runBlocking {
        val all = dao(context).getByPackage(packageName)
        if (all.isEmpty()) return@runBlocking emptyList()
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            return@runBlocking all.sortedWith(
                compareByDescending<AppKnowledgeEntity> { it.useCount }
                    .thenByDescending { it.lastUsed },
            )
        }
        val tokens = q.split(Regex("\\s+")).filter { it.length >= 2 }
        all.map { e ->
            val desc = e.actionDescription.lowercase()
            var score = 0
            if (q.length >= 2 && desc.contains(q)) score += 100
            score += tokens.count { desc.contains(it) }
            e to score
        }
            .sortedWith(
                compareByDescending<Pair<AppKnowledgeEntity, Int>> { it.second }
                    .thenByDescending { it.first.useCount }
                    .thenByDescending { it.first.lastUsed },
            )
            .map { it.first }
    }

    /** 使用一条记录后调用：次数 +1 并刷新最后使用时间。 */
    fun bumpUsage(context: Context, id: String) = runBlocking {
        dao(context).bumpUsage(id, System.currentTimeMillis())
    }

    fun delete(context: Context, id: String) = runBlocking {
        dao(context).deleteById(id)
    }
}
