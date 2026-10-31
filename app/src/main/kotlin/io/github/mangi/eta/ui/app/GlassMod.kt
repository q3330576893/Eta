package io.github.mangi.eta.ui.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur

/**
 * 玻璃化设计规范（iOS 风格磨砂玻璃）v3。
 *
 * 设计目标：每个按键/卡片本身半透明（半透明底色 + 真实背景模糊），参考 iOS 控制中心质感。
 *
 * 实现分两层（v3 改用 Miuix 自带 blur，不再依赖 Haze）：
 * 1. 透明度（全局）：[AgentAppTheme] 中将 surface 系颜色统一改为半透明，
 *    所有使用 MiuixTheme.colorScheme.surface / surfaceContainer / surfaceContainerHigh /
 *    surfaceContainerHighest 的组件（卡片、按钮、对话框、输入栏等）自动获得半透明底色。
 *    v2 起把 alpha 调透（0.5~0.6），配合模糊让玻璃感肉眼可见。
 * 2. Miuix 背景模糊：`rememberLayerBackdrop` + `layerBackdrop` + `textureBlur`。
 *    用法：
 *      a. 在内容顶层 `val backdrop = rememberGlassBackdrop(baseColor)`；
 *      b. 被模糊的背景内容加 `Modifier.layerBackdrop(backdrop)`；
 *      c. 玻璃组件加 `Modifier.glassTextureBlur(backdrop, shape, blendColor)`。
 *
 * 模糊总开关：AppearanceSettings.blurEnabled（默认开），通过 LocalBlurEnabled 下发。
 * backdrop 为 null（开关关闭或设备不支持）时，所有 glassTextureBlur 自动回退为原样，不会崩。
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

// region Miuix blur 集成（v3）

/**
 * iOS 风格毛玻璃：Miuix textureBlur 背景模糊 + 染色。
 *
 * @param backdrop 模糊源，由 rememberLayerBackdrop 创建；为 null 时返回原 Modifier（无操作）
 * @param shape 玻璃形状，默认 20.dp 圆角（iMessage 悬浮栏风格）
 * @param blurRadius 模糊半径，默认 GlassBlurRadius
 * @param blendColor 染色（通常取对应表面的主题色半透明版）
 */
fun Modifier.glassTextureBlur(
    backdrop: LayerBackdrop?,
    shape: Shape = GlassInputBarShape,
    blurRadius: Float = GlassBlurRadius,
    blendColor: Color,
): Modifier {
    if (backdrop == null) return this
    return this.textureBlur(
        backdrop = backdrop,
        shape = shape,
        blurRadius = blurRadius,
        colors = BlurColors(
            blendColors = listOf(BlendColorEntry(blendColor)),
        ),
    )
}

/** 聊天悬浮输入栏的圆角形状。 */
val GlassInputBarShape = RoundedCornerShape(20.dp)

/** 聊天悬浮输入栏的外边距：左右 16.dp，底部 12.dp，悬浮不贴边。 */
internal val GlassInputBarHorizontalMargin = 16.dp
internal val GlassInputBarBottomMargin = 12.dp

// endregion

/**
 * 聊天消息列表的 backdrop，通过 CompositionLocal 由 AgentAppShell 在顶层创建并下发。
 *
 * 提升到 Shell 层级的目的：backdrop 对象在页面导航时不重建。之前 backdrop 在
 * AgentChatScaffold 内用 rememberLayerBackdrop 创建，每次进出页面都会重建，
 * 重建的第一帧 backdrop 还没 capture 到背景内容，先显示了 drawRect 的不透明底色，
 * 造成白闪。提升到 Shell 层后只创建一次，导航时复用，白闪消失。
 *
 * 为 null 表示模糊不可用（开关关闭或设备不支持 RuntimeShader），调用方应回退为非模糊 UI。
 */
val LocalChatBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/**
 * 创建玻璃 backdrop（Miuix 机制）。
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
