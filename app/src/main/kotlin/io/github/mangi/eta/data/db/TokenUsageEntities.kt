package io.github.mangi.eta.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 单次模型调用的 Token 用量记录。
 *
 * 由 AgentLoop 在收到 ProviderEvent.Usage 时写入；只埋点，不影响调用逻辑。
 * day 使用 yyyy-MM-dd（设备本地时区），方便按天聚合。
 */
@Entity(
    tableName = "token_usage",
    indices = [
        Index("day"),
        Index("provider_id"),
        Index("timestamp"),
    ],
)
internal data class TokenUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "day") val day: String,
    @ColumnInfo(name = "provider_id") val providerId: String,
    @ColumnInfo(name = "provider_name") val providerName: String,
    @ColumnInfo(name = "model") val model: String,
    @ColumnInfo(name = "input_tokens") val inputTokens: Int,
    @ColumnInfo(name = "output_tokens") val outputTokens: Int,
    @ColumnInfo(name = "reasoning_tokens") val reasoningTokens: Int = 0,
    @ColumnInfo(name = "cached_tokens") val cachedTokens: Int = 0,
    /** 调用目的：chat / compaction / reply_rewrite / transcript_refine */
    @ColumnInfo(name = "purpose") val purpose: String = "chat",
)

/** 按天聚合的用量。 */
internal data class TokenUsageDayAggregate(
    val day: String,
    @ColumnInfo(name = "input_tokens") val inputTokens: Long,
    @ColumnInfo(name = "output_tokens") val outputTokens: Long,
    @ColumnInfo(name = "calls") val calls: Long,
)

/** 按服务商聚合的用量。 */
internal data class TokenUsageProviderAggregate(
    @ColumnInfo(name = "provider_id") val providerId: String,
    @ColumnInfo(name = "provider_name") val providerName: String,
    @ColumnInfo(name = "input_tokens") val inputTokens: Long,
    @ColumnInfo(name = "output_tokens") val outputTokens: Long,
    @ColumnInfo(name = "calls") val calls: Long,
)

/** 按模型聚合的用量。 */
internal data class TokenUsageModelAggregate(
    @ColumnInfo(name = "provider_name") val providerName: String,
    val model: String,
    @ColumnInfo(name = "input_tokens") val inputTokens: Long,
    @ColumnInfo(name = "output_tokens") val outputTokens: Long,
    @ColumnInfo(name = "calls") val calls: Long,
)
