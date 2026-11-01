package io.github.mangi.eta.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
internal interface TokenUsageDao {
    @Insert
    suspend fun insert(entity: TokenUsageEntity)

    @Query("DELETE FROM token_usage WHERE timestamp < :before")
    suspend fun pruneBefore(before: Long)

    @Query("DELETE FROM token_usage")
    suspend fun clearAll()

    @Query(
        "SELECT day, SUM(input_tokens) AS input_tokens, SUM(output_tokens) AS output_tokens, " +
            "COUNT(*) AS calls FROM token_usage " +
            "WHERE timestamp >= :since GROUP BY day ORDER BY day DESC LIMIT :limit"
    )
    suspend fun aggregateByDay(since: Long, limit: Int): List<TokenUsageDayAggregate>

    @Query(
        "SELECT provider_id, provider_name, SUM(input_tokens) AS input_tokens, " +
            "SUM(output_tokens) AS output_tokens, COUNT(*) AS calls FROM token_usage " +
            "WHERE timestamp >= :since GROUP BY provider_id, provider_name " +
            "ORDER BY input_tokens + output_tokens DESC"
    )
    suspend fun aggregateByProvider(since: Long): List<TokenUsageProviderAggregate>

    @Query(
        "SELECT provider_name, model, SUM(input_tokens) AS input_tokens, " +
            "SUM(output_tokens) AS output_tokens, COUNT(*) AS calls FROM token_usage " +
            "WHERE timestamp >= :since GROUP BY provider_name, model " +
            "ORDER BY input_tokens + output_tokens DESC LIMIT :limit"
    )
    suspend fun aggregateByModel(since: Long, limit: Int): List<TokenUsageModelAggregate>

    @Query(
        "SELECT SUM(input_tokens) AS input_tokens, SUM(output_tokens) AS output_tokens, " +
            "COUNT(*) AS calls FROM token_usage WHERE timestamp >= :since"
    )
    suspend fun totalSince(since: Long): TokenUsageTotal?

    @Query("SELECT COUNT(*) FROM token_usage")
    suspend fun count(): Long
}

/** 总量（SUM 在空表时返回 null 行）。 */
internal data class TokenUsageTotal(
    @ColumnInfo(name = "input_tokens") val inputTokens: Long?,
    @ColumnInfo(name = "output_tokens") val outputTokens: Long?,
    @ColumnInfo(name = "calls") val calls: Long?,
)
