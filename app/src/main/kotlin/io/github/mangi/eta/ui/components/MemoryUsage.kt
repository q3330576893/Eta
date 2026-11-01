package io.github.mangi.eta.ui.components

import android.app.ActivityManager
import android.content.Context
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 内存用量数据：物理内存已用百分比等。
 * 只读 ActivityManager.MemoryInfo，不需要 Root。
 */
internal data class MemoryUsage(
    val usedPercent: Int,
    val usedBytes: Long,
    val availableBytes: Long,
    val totalBytes: Long,
    val swapTotalBytes: Long = 0L,
    val swapUsedBytes: Long = 0L,
)

internal fun readMemoryUsage(context: Context): MemoryUsage {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val info = ActivityManager.MemoryInfo()
    runCatching { manager?.getMemoryInfo(info) }
    val total = info.totalMem.takeIf { it > 0 } ?: 1L
    val available = info.availMem.coerceAtLeast(0L)
    val used = (total - available).coerceAtLeast(0L)
    val percent = ((used.toDouble() / total.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
    val (swapTotal, swapUsed) = readSwapBytes()
    return MemoryUsage(percent, used, available, total, swapTotal, swapUsed)
}

/**
 * 内存拓展（ZRAM）：只做展示，不参与百分比。
 */
private fun readSwapBytes(): Pair<Long, Long> = runCatching {
    var total = 0L
    var free = 0L
    java.io.File("/proc/meminfo").useLines { lines ->
        lines.forEach { line ->
            when {
                line.startsWith("SwapTotal:") -> total = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
                line.startsWith("SwapFree:") -> free = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
            }
        }
    }
    total * 1024L to (total - free).coerceAtLeast(0L) * 1024L
}.getOrDefault(0L to 0L)

internal fun formatGigabytes(bytes: Long): String =
    String.format(Locale.US, "%.1f GB", bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))

internal const val MEMORY_REFRESH_MILLIS = 5000L
