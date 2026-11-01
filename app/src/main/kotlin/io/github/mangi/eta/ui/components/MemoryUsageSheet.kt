package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 内存详情面板：显示内存总量/已用/可用/百分比。
 * 简化版：不含进程排行（无需 MemoryProcessProbe）。
 */
@Composable
internal fun MemoryUsageSheet(show: Boolean, onDismiss: () -> Unit) {
    if (!show) return
    val context = LocalContext.current
    var usage by remember { mutableStateOf(readMemoryUsage(context)) }
    LaunchedEffect(show) {
        while (show) {
            usage = readMemoryUsage(context)
            delay(MEMORY_REFRESH_MILLIS)
        }
    }
    EtaWindowDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text(
                text = stringResource(R.string.memory_sheet_title),
                style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            MemoryInfoRow(
                label = stringResource(R.string.memory_sheet_used),
                value = "${formatGigabytes(usage.usedBytes)} / ${formatGigabytes(usage.totalBytes)}",
            )
            MemoryInfoRow(
                label = stringResource(R.string.memory_sheet_available),
                value = formatGigabytes(usage.availableBytes),
            )
            MemoryInfoRow(
                label = stringResource(R.string.memory_sheet_percent_label),
                value = "${usage.usedPercent}%",
            )
            if (usage.swapTotalBytes > 0) {
                MemoryInfoRow(
                    label = stringResource(R.string.memory_sheet_swap),
                    value = "${formatGigabytes(usage.swapUsedBytes)} / ${formatGigabytes(usage.swapTotalBytes)}",
                )
            }
            Spacer(Modifier.height(16.dp))
            MiuixDialogActions(
                onConfirm = onDismiss,
                confirmText = stringResource(R.string.memory_sheet_confirm),
            )
        }
    }
}

@Composable
private fun MemoryInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}
