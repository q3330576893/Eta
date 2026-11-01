package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.runtime.AgentTokenUsage
import io.github.mangi.eta.data.repository.TokenUsageRepository

/**
 * Token 用量埋点入口。
 *
 * 由 [AgentLoop] 在收到 `ProviderEvent.Usage` 时调用。
 * 只做记录，不抛异常、不阻塞、不改变任何调用逻辑。
 */
internal object TokenUsageRecorder {
    fun record(
        config: AgentModelClient.ModelConfig,
        usage: AgentTokenUsage,
        purpose: ProviderRequestPurpose,
    ) {
        runCatching {
            TokenUsageRepository.record(
                providerId = config.providerId,
                providerName = config.providerName,
                model = config.model,
                usage = usage,
                purpose = when (purpose) {
                    ProviderRequestPurpose.CHAT -> "chat"
                    ProviderRequestPurpose.COMPACTION -> "compaction"
                    ProviderRequestPurpose.REPLY_REWRITE -> "reply_rewrite"
                    ProviderRequestPurpose.TRANSCRIPT_REFINE -> "transcript_refine"
                },
            )
        }
    }
}
