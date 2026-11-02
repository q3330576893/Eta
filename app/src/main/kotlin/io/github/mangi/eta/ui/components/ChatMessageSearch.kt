package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.repository.ConversationSearchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 会话内消息搜索的状态持有者。
 *
 * 由 [io.github.mangi.eta.ui.app.AgentAppRoot] 在路由壳层级 remember，
 * 通过 [LocalChatMessageSearch] 同时供给顶栏搜索入口与聊天屏的搜索面板。
 */
internal class ChatMessageSearchState {
    var isOpen by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<ConversationSearchRepository.InConversationHit>>(emptyList())
        private set
    var isSearching by mutableStateOf(false)
        private set

    /**
     * 用户点选搜索结果后待滚动定位的消息 id；
     * 聊天列表消费后应调 [consumeScrollTarget] 清空。
     */
    var scrollTargetMessageId by mutableStateOf<String?>(null)

    private var searchJob: Job? = null

    fun open() {
        isOpen = true
    }

    fun close() {
        isOpen = false
        query = ""
        results = emptyList()
        isSearching = false
        scrollTargetMessageId = null
        searchJob?.cancel()
        searchJob = null
    }

    fun consumeScrollTarget() {
        scrollTargetMessageId = null
    }

    /**
     * 输入防抖 300ms 后执行搜索；调用方需在协程作用域内传入 context。
     */
    fun updateQuery(
        newQuery: String,
        scope: CoroutineScope,
        search: suspend (String) -> List<ConversationSearchRepository.InConversationHit>,
    ) {
        query = newQuery
        searchJob?.cancel()
        if (newQuery.isBlank()) {
            results = emptyList()
            isSearching = false
            return
        }
        isSearching = true
        searchJob = scope.launch {
            delay(300)
            val hits = try {
                search(newQuery)
            } catch (_: Exception) {
                emptyList()
            }
            // 防抖期间用户又改了关键词就丢弃本轮结果。
            if (query == newQuery) {
                results = hits
                isSearching = false
            }
        }
    }

    fun selectResult(messageId: String) {
        scrollTargetMessageId = messageId
        isOpen = false
    }
}

internal val LocalChatMessageSearch = staticCompositionLocalOf<ChatMessageSearchState?> { null }

/**
 * 会话内消息搜索面板：覆盖在聊天界面顶部，含搜索框与结果列表。
 *
 * @param conversationId 当前会话 id；为 null 时不执行搜索。
 */
@Composable
internal fun ChatMessageSearchOverlay(
    state: ChatMessageSearchState,
    conversationId: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 遮罩：点空白处关闭。
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { state.close() },
            ),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
            insideMargin = PaddingValues(12.dp),
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SearchBar(
                        modifier = Modifier.weight(1f),
                        insideMargin = DpSize.Zero,
                        expanded = false,
                        onExpandedChange = {},
                        inputField = {
                            InputField(
                                query = state.query,
                                onQueryChange = { newQuery ->
                                    state.updateQuery(newQuery, scope) { q ->
                                        if (conversationId == null) emptyList()
                                        else ConversationSearchRepository.searchInConversation(
                                            context = context,
                                            conversationId = conversationId,
                                            query = q,
                                        )
                                    }
                                },
                                onSearch = {},
                                expanded = false,
                                onExpandedChange = {},
                                label = stringResource(R.string.message_search_hint),
                                color = MiuixTheme.colorScheme.surfaceContainerHighest,
                            )
                        },
                        content = {},
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { state.close() }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.message_search_close),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                when {
                    state.isSearching -> {
                        Text(
                            text = stringResource(R.string.message_search_searching),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                    state.query.isNotBlank() && state.results.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.message_search_no_result),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                    state.results.isNotEmpty() -> {
                        Text(
                            text = stringResource(
                                R.string.message_search_result_count,
                                state.results.size,
                            ),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(
                            modifier = Modifier.height(320.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(
                                items = state.results,
                                key = { it.messageId },
                            ) { hit ->
                                MessageSearchResultItem(
                                    hit = hit,
                                    query = state.query,
                                    onClick = { state.selectResult(hit.messageId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageSearchResultItem(
    hit: ConversationSearchRepository.InConversationHit,
    query: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(MiuixTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (hit.type == "user") Icons.Outlined.Person else Icons.Outlined.SmartToy,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MiuixTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (hit.type == "user") {
                    stringResource(R.string.message_search_sender_user)
                } else {
                    stringResource(R.string.message_search_sender_assistant)
                },
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = highlightKeyword(hit.snippet, query),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 关键词高亮：匹配部分用主题色加粗底色标出。 */
@Composable
private fun highlightKeyword(
    text: String,
    query: String,
): androidx.compose.ui.text.AnnotatedString {
    val highlightColor = MiuixTheme.colorScheme.primary
    return remember(text, query) {
        buildAnnotatedString {
            if (query.isBlank()) {
                append(text)
                return@buildAnnotatedString
            }
            var cursor = 0
            while (true) {
                val index = text.indexOf(query, cursor, ignoreCase = true)
                if (index < 0) {
                    append(text.substring(cursor))
                    break
                }
                append(text.substring(cursor, index))
                withStyle(
                    SpanStyle(
                        color = highlightColor,
                        fontWeight = FontWeight.Bold,
                        background = highlightColor.copy(alpha = 0.16f),
                    ),
                ) {
                    append(text.substring(index, index + query.length))
                }
                cursor = index + query.length
            }
        }
    }
}
