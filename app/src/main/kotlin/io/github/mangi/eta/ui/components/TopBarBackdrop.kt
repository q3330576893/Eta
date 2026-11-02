package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.data.model.AppearanceTopBarBlurStyle
import io.github.mangi.eta.ui.app.LocalBlurEnabled
import io.github.mangi.eta.ui.app.LocalTopBarBlurStyle
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun rememberTopBarBackdrop(): LayerBackdrop? {
    if (!LocalBlurEnabled.current || !isRuntimeShaderSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

@Composable
internal fun TopBarBackdrop(
    backdrop: LayerBackdrop?,
    content: @Composable () -> Unit,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    // v4 玻璃质感：顶栏底部发丝线颜色（@Composable 上下文外不能读 colorScheme，先取出）
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val hairlineColor = if (dark) Color.White.copy(alpha = 0.10f)
    else Color.Black.copy(alpha = 0.08f)
    val modifier = when {
        backdrop == null -> Modifier.background(surfaceColor)
        LocalTopBarBlurStyle.current == AppearanceTopBarBlurStyle.PROGRESSIVE -> {
            Modifier.progressiveTextureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                gradient = ProgressiveBlur.Top.copy(curve = 2.2f),
                blurRadius = ProgressiveTopBarBlurRadius,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(surfaceColor.copy(alpha = ProgressiveTopBarSurfaceAlpha)),
                    ),
                ),
            )
        }
        else -> {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = TopBarBlurRadius,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(surfaceColor.copy(alpha = TopBarSurfaceAlpha)),
                    ),
                ),
            )
        }
    }
    Box(
        modifier = modifier
            // v4 玻璃质感：顶栏底部发丝线（深色白 10% / 浅色黑 8%）
            .drawWithContent {
                drawContent()
                drawLine(
                    color = hairlineColor,
                    start = Offset(0f, size.height - 0.5.dp.toPx()),
                    end = Offset(size.width, size.height - 0.5.dp.toPx()),
                    strokeWidth = 0.5.dp.toPx(),
                )
            }
    ) { content() }
}

internal fun Modifier.captureForTopBar(backdrop: LayerBackdrop?): Modifier =
    if (backdrop == null) this else layerBackdrop(backdrop)

@Composable
internal fun topBarContainerColor(backdrop: LayerBackdrop?): Color =
    if (backdrop == null) MiuixTheme.colorScheme.surface else Color.Transparent

private const val TopBarBlurRadius = 25f
private const val TopBarSurfaceAlpha = 0.8f
private const val ProgressiveTopBarBlurRadius = 10f
private const val ProgressiveTopBarSurfaceAlpha = 0.3f
