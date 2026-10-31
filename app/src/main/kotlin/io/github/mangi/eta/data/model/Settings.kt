package io.github.mangi.eta.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val selectedProviderId: String? = null,
    val selectedModelId: String? = null,
    val memoryEnabled: Boolean = true,
    /** 打开 Eta 后悬浮胶囊常驻显示：没有任务时也留在屏幕上，点它看运行中会话。 */
    val overlayCapsulePinned: Boolean = false,
    val appearance: AppearanceSettings = AppearanceSettings(),
)
