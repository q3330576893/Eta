package io.github.mangi.eta.agent.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.CompletableFuture

/**
 * Agent 悬浮卡片的状态桥：连接 agent-runtime 线程上的工具执行与主线程的 Compose UI。
 *
 * 工具在 agent 线程调用时创建一个 [CompletableFuture] 并写入对应的 StateFlow，
 * UI 层观察到非空请求后弹出卡片；用户点选后 complete future 并清空请求，
 * 工具线程被唤醒后把结果返回给模型。
 */
internal object AgentChoiceCardBridge {
    data class ChoiceOption(
        val title: String,
        val desc: String,
        val recommend: Boolean,
    )

    data class ChoiceRequest(
        val title: String,
        val sub: String,
        val options: List<ChoiceOption>,
        /** 完成时填选项序号（0 起）；-1 表示取消/超时/用户关闭。 */
        val future: CompletableFuture<Int>,
    )

    data class CopyRequest(
        val title: String,
        val text: String,
        /** true=用户点了复制，false=用户直接关闭。 */
        val future: CompletableFuture<Boolean>,
    )

    val choiceRequest = MutableStateFlow<ChoiceRequest?>(null)
    val copyRequest = MutableStateFlow<CopyRequest?>(null)
}
