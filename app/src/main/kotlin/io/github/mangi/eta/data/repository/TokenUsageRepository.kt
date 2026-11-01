package io.github.mangi.eta.data.repository

import android.content.Context
import io.github.mangi.eta.agent.runtime.AgentTokenUsage
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.data.db.TokenUsageDao
import io.github.mangi.eta.data.db.TokenUsageDayAggregate
import io.github.mangi.eta.data.db.TokenUsageEntity
import io.github.mangi.eta.data.db.TokenUsageModelAggregate
import io.github.mangi.eta.data.db.TokenUsageProviderAggregate
import io.github.mangi.eta.data.db.TokenUsageTotal
import io.github.mangi.eta.data.model.TokenPriceTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Token 用量统计仓库。
 *
 * - [record] 由 AgentLoop 在每次收到 ProviderEvent.Usage 时调用；
 *   fire-and-forget，任何异常都不向上传，绝不影响 Agent 运行。
 * - 保留最近 180 天数据，写入时顺手清理过期记录。
 */
internal object TokenUsageRepository {
    private const val RETENTION_DAYS = 180L

    @Volatile
    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun dao(): TokenUsageDao? =
        appContext?.let { EtaDatabase.get(it).tokenUsageDao() }

    /**
     * 记录一次模型调用的用量。空用量（各 token 均为 null/0）不记录。
     * @param purpose chat / compaction / reply_rewrite / transcript_refine
     */
    fun record(
        providerId: String,
        providerName: String,
        model: String,
        usage: AgentTokenUsage,
        purpose: String,
    ) {
        val dao = dao() ?: return
        val input = usage.inputTokens ?: 0
        val output = usage.outputTokens ?: 0
        val reasoning = usage.reasoningTokens ?: 0
        val cached = usage.cachedTokens ?: 0
        if (input <= 0 && output <= 0 && reasoning <= 0) return
        val now = System.currentTimeMillis()
        val entity = TokenUsageEntity(
            timestamp = now,
            day = dayFormat.format(Date(now)),
            providerId = providerId.ifBlank { "unknown" },
            providerName = providerName.ifBlank { providerId.ifBlank { "未知" } },
            model = model.ifBlank { "unknown" },
            inputTokens = input,
            outputTokens = output,
            reasoningTokens = reasoning,
            cachedTokens = cached,
            purpose = purpose.ifBlank { "chat" },
        )
        scope.launch {
            runCatching {
                dao.insert(entity)
                dao.pruneBefore(now - RETENTION_DAYS * 24 * 60 * 60 * 1000L)
            }
        }
    }

    suspend fun dayAggregates(days: Int = 30): List<TokenUsageDayAggregate> {
        val dao = dao() ?: return emptyList()
        val since = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000L
        return runCatching { dao.aggregateByDay(since, days) }.getOrDefault(emptyList())
    }

    suspend fun providerAggregates(days: Int = 30): List<TokenUsageProviderAggregate> {
        val dao = dao() ?: return emptyList()
        val since = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000L
        return runCatching { dao.aggregateByProvider(since) }.getOrDefault(emptyList())
    }

    suspend fun modelAggregates(days: Int = 30, limit: Int = 20): List<TokenUsageModelAggregate> {
        val dao = dao() ?: return emptyList()
        val since = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000L
        return runCatching { dao.aggregateByModel(since, limit) }.getOrDefault(emptyList())
    }

    suspend fun totalToday(): TokenUsageTotal? {
        val dao = dao() ?: return null
        val startOfDay = startOfTodayMillis()
        return runCatching { dao.totalSince(startOfDay) }.getOrNull()
    }

    suspend fun totalLast30Days(): TokenUsageTotal? {
        val dao = dao() ?: return null
        val since = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000L
        return runCatching { dao.totalSince(since) }.getOrNull()
    }

    suspend fun clearAll() {
        val dao = dao() ?: return
        runCatching { dao.clearAll() }
    }

    /** 估算费用（美元）。按模型逐条估算后求和，未命中单价表的模型不计入。 */
    suspend fun estimateCostUsd(days: Int = 30): Double? {
        val dao = dao() ?: return null
        val ctx = appContext
        val since = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000L
        return runCatching {
            var total = 0.0
            var matched = false
            dao.aggregateByModel(since, 200).forEach { row ->
                TokenPriceTable.estimateUsd(row.model, row.inputTokens, row.outputTokens, ctx)?.let {
                    total += it
                    matched = true
                }
            }
            if (matched) total else null
        }.getOrNull()
    }

    private fun startOfTodayMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
