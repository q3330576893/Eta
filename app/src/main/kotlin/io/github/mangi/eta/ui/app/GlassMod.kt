package io.github.mangi.eta.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/**
 * 玻璃化设计规范（iOS 风格磨砂玻璃）。
 *
 * 设计目标：每个按键/卡片本身半透明（半透明底色 + 磨砂模糊），参考 iOS 控制中心质感。
 *
 * 实现分为两层：
 * 1. 透明度（全局）：[AgentAppTheme] 中将 surface 系颜色统一改为半透明，
 *    所有使用 MiuixTheme.colorScheme.surface / surfaceContainer / surfaceContainerHigh /
 *    surfaceContainerHighest 的组件（卡片、按钮、对话框、输入栏等）自动获得半透明底色。
 * 2. 磨砂模糊（按需）：使用 Miuix 的 LayerBackdrop + textureBlur 机制。
 *    已有位置：顶栏（TopBarBackdrop）、聊天底部磨砂（AgentChatBody）。
 *    新增玻璃表面时，遵循以下模式：
 *      a. 在被遮挡内容的父级调用 [rememberGlassBackdrop] 创建 backdrop；
 *      b. 被遮挡内容加 Modifier.layerBackdrop(backdrop)；
 *      c. 玻璃表面加 Modifier.textureBlur(backdrop, shape, GlassBlurRadius, colors)。
 *
 * 模糊总开关：AppearanceSettings.blurEnabled（默认开），通过 LocalBlurEnabled 下发。
 * 主题透明度与模糊共用该开关：关闭时恢复原有不透明外观。
 */

// region 玻璃透明度（surface 系颜色的 alpha）

internal const val GlassSurfaceAlpha = 0.72f
internal const val GlassSurfaceContainerAlpha = 0.62f
internal const val GlassSurfaceContainerHighAlpha = 0.68f
internal const val GlassSurfaceContainerHighestAlpha = 0.72f

// endregion

// region 磨砂模糊参数

/** 磨砂模糊半径，与顶栏保持一致。 */
internal const val GlassBlurRadius = 25f

// endregion

/**
 * 创建玻璃 backdrop，复用全局模糊开关与运行时着色器支持检查。
 *
 * @param baseColor backdrop 底色（通常取对应表面的主题色，textureBlur 会再叠加 blend 颜色）
 * @return 可用时返回 LayerBackdrop；模糊关闭或设备不支持时返回 null，调用方应回退为纯色背景
 */
@Composable
internal fun rememberGlassBackdrop(baseColor: Color): LayerBackdrop? {
    if (!LocalBlurEnabled.current || !isRuntimeShaderSupported()) return null
    return rememberLayerBackdrop {
        drawRect(baseColor)
        drawContent()
    }
}
