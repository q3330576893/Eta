package io.github.mangi.eta.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

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

// region 玻璃质感三件套（v4）：发丝边框 + 顶部高光 + 主题染色
//
// 调研结论：v1 被吐槽"有点玻璃但不多"的真正原因是只有模糊、没有边框/高光。
// 玻璃配方 = 模糊 + 1px 发丝边框（深色模式白 10%，浅色模式黑 8%）+ 顶部高光（白 8%→透明）。
// 这三个 Modifier 与模糊引擎无关，模糊关闭时依然生效（纯视觉层）。

/** 当前是否为深色模式（用背景亮度判断）。 */
@Composable
private fun isGlassDarkMode(): Boolean =
    MiuixTheme.colorScheme.background.luminance() < 0.5f

/**
 * 发丝边框：1px 玻璃描边。深色模式用白 10%，浅色模式用黑 8%。
 * 这是"玻璃感"最关键的一笔——没有它，半透明就只是半透明。
 */
@Composable
fun Modifier.glassHairlineBorder(
    shape: Shape = RoundedCornerShape(12.dp),
    width: Dp = 1.dp,
): Modifier {
    val dark = isGlassDarkMode()
    val borderColor = if (dark) Color.White.copy(alpha = 0.10f)
    else Color.Black.copy(alpha = 0.08f)
    return this.border(width, borderColor, shape)
}

/**
 * 顶部高光：在组件上半部分压一层白 8%→透明的渐变，模拟玻璃的反光。
 * 用 drawWithContent 实现，不占布局，不影响点击。
 */
fun Modifier.glassTopHighlight(
    shape: Shape = RoundedCornerShape(12.dp),
    highlightAlpha: Float = 0.08f,
): Modifier = this
    .clip(shape)
    .drawWithContent {
        drawContent()
        val h = size.height
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = highlightAlpha),
                    Color.White.copy(alpha = highlightAlpha * 0.4f),
                    Color.Transparent,
                ),
                start = Offset(0f, 0f),
                end = Offset(0f, h * 0.45f),
            ),
            size = androidx.compose.ui.geometry.Size(size.width, h * 0.45f),
        )
    }

/**
 * 玻璃卡片：一站式玻璃面（底色 + 发丝边框 + 顶部高光）。
 * 用于设置页分组卡片、列表卡片等不需要真实模糊的"层"。
 * 性能：零离屏缓冲，纯绘制，列表里随便用。
 *
 * @param color 卡片底色（通常取 surface 系半透明色）
 * @param shape 形状
 */
@Composable
fun Modifier.glassCard(
    color: Color,
    shape: Shape = RoundedCornerShape(12.dp),
): Modifier = this
    .background(color, shape)
    .glassHairlineBorder(shape)
    .glassTopHighlight(shape)

/**
 * 玻璃弹窗：一站式弹窗玻璃面（底色 + 发丝边框 + 顶部高光 + 可选真实模糊）。
 * 有 backdrop 时叠加 Miuix 真实模糊，没有时回退为纯玻璃卡片。
 *
 * @param backdrop 模糊源；为 null 时只用底色+边框+高光
 * @param color 弹窗底色
 * @param shape 形状
 */
@Composable
fun Modifier.glassDialog(
    backdrop: LayerBackdrop?,
    color: Color,
    shape: Shape = RoundedCornerShape(16.dp),
): Modifier {
    var m = Modifier
        .background(color, shape)
        .glassHairlineBorder(shape)
        .glassTopHighlight(shape, highlightAlpha = 0.10f)
    if (backdrop != null && LocalBlurEnabled.current && isRuntimeShaderSupported()) {
        m = m.textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = GlassBlurRadius,
            colors = BlurColors(
                blendColors = listOf(BlendColorEntry(color)),
            ),
        )
    }
    return this.then(m)
}

/**
 * 玻璃气泡：消息气泡专用（底色 + 发丝边框 + 顶部高光）。
 * 按性能红线：气泡不用真实模糊（LazyColumn 里全量模糊开销大），
 * 用半透明 + 边框 + 高光模拟，视觉差极小。
 *
 * @param color 气泡底色
 * @param shape 气泡形状（注意用户气泡是不对称圆角，调用方传入）
 */
@Composable
fun Modifier.glassBubble(
    color: Color,
    shape: Shape,
): Modifier = this
    .background(color, shape)
    .glassHairlineBorder(shape, width = 0.5.dp)
    .glassTopHighlight(shape, highlightAlpha = 0.06f)

// endregion
