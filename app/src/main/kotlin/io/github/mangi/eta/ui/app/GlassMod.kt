package io.github.mangi.eta.ui.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/**
 * 玻璃化设计规范（iOS 风格磨砂玻璃）v2。
 *
 * 设计目标：每个按键/卡片本身半透明（半透明底色 + 真实背景模糊），参考 iOS 控制中心质感。
 *
 * 实现分三层：
 * 1. 透明度（全局）：[AgentAppTheme] 中将 surface 系颜色统一改为半透明，
 *    所有使用 MiuixTheme.colorScheme.surface / surfaceContainer / surfaceContainerHigh /
 *    surfaceContainerHighest 的组件（卡片、按钮、对话框、输入栏等）自动获得半透明底色。
 *    v2 把 alpha 再调透（0.5~0.6），配合模糊让玻璃感肉眼可见。
 * 2. Haze 背景模糊（v2 新增）：chrisbanes/haze v2，真正的背景快照模糊。
 *    用法：
 *      a. 在内容顶层 `val hazeState = rememberGlassHazeState()`；
 *      b. 被模糊的背景内容加 `Modifier.hazeSource(hazeState)`；
 *      c. 玻璃组件加 `Modifier.glassHaze(hazeState)`（= hazeBlur + HazeMaterials.regular() 苹果质感）。
 * 3. Miuix LayerBackdrop（v1 保留）：顶栏、聊天底部渐隐仍用 Miuix 机制，与 Haze 共存。
 *
 * 模糊总开关：AppearanceSettings.blurEnabled（默认开），通过 LocalBlurEnabled 下发。
 * Haze 只在模糊开关打开时启用；关闭时回退为纯半透明底色。
 */

// region 玻璃透明度（surface 系颜色的 alpha）v2：更透

internal const val GlassSurfaceAlpha = 0.60f
internal const val GlassSurfaceContainerAlpha = 0.52f
internal const val GlassSurfaceContainerHighAlpha = 0.55f
internal const val GlassSurfaceContainerHighestAlpha = 0.60f

// endregion

// region 磨砂模糊参数

/** 磨砂模糊半径，与顶栏保持一致。 */
internal const val GlassBlurRadius = 25f

// endregion

// region Haze v2 集成

/**
 * 聊天界面的 HazeState，通过 CompositionLocal 下发，深层级的消息气泡可直接取用，
 * 不用一层层透传参数。
 */
val LocalChatHazeState = compositionLocalOf<HazeState?> { null }

/**
 * 创建 HazeState。调用方应在内容顶层 remember，例如聊天脚手架：
 * ```
 * val hazeState = rememberGlassHazeState()
 * ```
 */
@Composable
fun rememberGlassHazeState(): HazeState = rememberHazeState()

/**
 * 背景内容标记为 Haze 模糊源。加在消息列表等背景内容上。
 * 模糊关闭时返回原 Modifier（无操作）。
 */
fun Modifier.glassHazeSource(hazeState: HazeState?, blurEnabled: Boolean): Modifier =
    if (blurEnabled && hazeState != null) this.hazeSource(hazeState) else this

/**
 * iOS 风格毛玻璃：Haze 背景模糊 + 苹果质感材料。
 *
 * @param hazeState 模糊源状态
 * @param blurEnabled 模糊总开关，关闭时只保留半透明底色
 * @param cornerRadius 圆角，默认 20.dp（iMessage 悬浮栏风格）
 */
fun Modifier.glassHaze(
    hazeState: HazeState?,
    blurEnabled: Boolean,
    cornerRadius: androidx.compose.ui.unit.Dp = 20.dp,
): Modifier {
    if (!blurEnabled || hazeState == null) return this
    return this.hazeBlur(
        input = HazeInput.Sources(hazeState),
        style = HazeMaterials.regular(),
    )
}

/** 聊天悬浮输入栏的圆角形状。 */
val GlassInputBarShape = RoundedCornerShape(20.dp)

/** 聊天悬浮输入栏的外边距：左右 16.dp，底部 12.dp，悬浮不贴边。 */
internal val GlassInputBarHorizontalMargin = 16.dp
internal val GlassInputBarBottomMargin = 12.dp

// endregion

/**
 * 创建玻璃 backdrop（Miuix 机制，v1 保留给顶栏/底部渐隐用）。
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
