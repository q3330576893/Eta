package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.app.glassHairlineBorder
import io.github.mangi.eta.ui.app.glassTopHighlight
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.utils.PressFeedbackType

internal object EtaCardDefaults {
    val CornerRadius = 24.dp
}

@Composable
internal fun EtaCard(
    modifier: Modifier = Modifier,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    colors: CardColors = CardDefaults.defaultColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        // v4 玻璃质感：发丝边框 + 顶部高光（所有设置卡片统一生效）
        modifier = modifier
            .glassHairlineBorder(RoundedCornerShape(EtaCardDefaults.CornerRadius))
            .glassTopHighlight(RoundedCornerShape(EtaCardDefaults.CornerRadius)),
        cornerRadius = EtaCardDefaults.CornerRadius,
        insideMargin = insideMargin,
        colors = colors,
        content = content,
    )
}

@Composable
internal fun EtaCard(
    modifier: Modifier = Modifier,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    colors: CardColors = CardDefaults.defaultColors(),
    pressFeedbackType: PressFeedbackType = PressFeedbackType.None,
    showIndication: Boolean = false,
    holdDownState: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        // v4 玻璃质感：发丝边框 + 顶部高光（所有设置卡片统一生效）
        modifier = modifier
            .glassHairlineBorder(RoundedCornerShape(EtaCardDefaults.CornerRadius))
            .glassTopHighlight(RoundedCornerShape(EtaCardDefaults.CornerRadius)),
        cornerRadius = EtaCardDefaults.CornerRadius,
        insideMargin = insideMargin,
        colors = colors,
        pressFeedbackType = pressFeedbackType,
        showIndication = showIndication,
        holdDownState = holdDownState,
        onClick = onClick,
        onLongPress = onLongPress,
        content = content,
    )
}
