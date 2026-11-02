package io.github.mangi.eta.data.repository

import android.content.Context
import androidx.room.withTransaction
import io.github.mangi.eta.data.db.ConversationDao
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.data.db.openTextStream
import java.io.Reader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException

/** 只返回命中的会话标识，搜索不把未打开会话的正文带回界面状态。 */
internal object ConversationSearchRepository {
    /** 会话内消息搜索命中。 */
    data class InConversationHit(
        val messageId: String,
        /** "user" 或 "assistant" */
        val type: String,
        /** 关键词前后截取的正文片段（已去除 markdown 符号干扰）。 */
        val snippet: String,
    )

    /**
     * 搜索指定会话内的用户/助手消息，按时间倒序返回。
     * 正文经 restoreText 还原（含分块大消息），大小写不敏感匹配。
     */
    suspend fun searchInConversation(
        context: Context,
        conversationId: String,
        query: String,
        maxResults: Int = 50,
    ): List<InConversationHit> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val database = EtaDatabase.get(context.applicationContext)
        val dao = database.conversationDao()
        val hits = mutableListOf<InConversationHit>()
        val cancellation = currentCoroutineContext()
        for (candidate in dao.messageSearchCandidates(conversationId)) {
            cancellation.ensureActive()
            if (hits.size >= maxResults) break
            val stored = dao.messageContents(conversationId, listOf(candidate.id))
                .firstOrNull()?.content ?: continue
            val fullText = try {
                dao.restoreText("conversation_messages", candidate.id, "content", stored)
            } catch (_: Exception) {
                stored
            }
            val matchIndex = fullText.indexOf(query, ignoreCase = true)
            if (matchIndex < 0) continue
            hits += InConversationHit(
                messageId = candidate.id,
                type = candidate.type,
                snippet = buildSnippet(fullText, matchIndex, query.length),
            )
        }
        hits
    }

    private fun buildSnippet(text: String, matchIndex: Int, matchLength: Int): String {
        val radius = 40
        val start = maxOf(0, matchIndex - radius)
        val end = minOf(text.length, matchIndex + matchLength + radius)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return prefix + text.substring(start, end).replace(Regex("\\s+"), " ") + suffix
    }

    suspend fun search(context: Context, query: String, matchingNoticeCodes: List<String>): Set<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptySet()
        val database = EtaDatabase.get(context.applicationContext)
        database.withTransaction {
            val dao = database.conversationDao()
            // 引用与分块共享读快照；内存仅保留当前块和跨块匹配所需的后缀。
            searchMessageStreams(dao, query) + dao.searchSystemNotices(matchingNoticeCodes)
        }
    }

    private suspend fun searchMessageStreams(dao: ConversationDao, query: String): Set<String> {
        val matches = mutableSetOf<String>()
        val cancellation = currentCoroutineContext()
        dao.messageSearchCursor().use { cursor ->
            while (cursor.moveToNext()) {
                cancellation.ensureActive()
                val conversationId = cursor.getString(1)
                if (conversationId in matches) continue
                val type = cursor.getString(2)
                val contentMatches = type in textMessageTypes &&
                    dao.openTextStream("conversation_messages", cursor.getString(0), "content", cursor.getString(3))
                        .reader(Charsets.UTF_8).use { it.containsText(query, cancellation::ensureActive) }
                val detailMatches = when (type) {
                    "tool" -> (4..6).any { column -> cursor.getString(column)?.contains(query, ignoreCase = true) == true }
                    "system_notice" -> cursor.getString(6)?.contains(query, ignoreCase = true) == true
                    "tool_summary" -> toolNamesMatch(cursor.getString(7), query)
                    else -> false
                }
                if (contentMatches || detailMatches) {
                    matches += conversationId
                }
            }
        }
        return matches
    }

    private fun toolNamesMatch(stored: String, query: String): Boolean {
        val names = try { JSONArray(stored) } catch (_: JSONException) { return false }
        return (0 until names.length()).any { names.optString(it).contains(query, ignoreCase = true) }
    }

    private fun Reader.containsText(query: String, checkCancelled: () -> Unit): Boolean {
        val buffer = CharArray(DEFAULT_BUFFER_SIZE)
        var overlap = ""
        while (true) {
            checkCancelled()
            val count = read(buffer)
            if (count < 0) return false
            val candidate = overlap + String(buffer, 0, count)
            if (candidate.contains(query, ignoreCase = true)) return true
            overlap = candidate.takeLast(query.length - 1)
        }
    }

    private val textMessageTypes = setOf("user", "assistant", "thinking", "tool")
}
