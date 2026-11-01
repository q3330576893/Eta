package io.github.mangi.eta.ui.screens.presets

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.prompt.ActivePersonaStore
import io.github.mangi.eta.agent.prompt.PromptPreset
import io.github.mangi.eta.agent.prompt.PromptPresetCodec
import io.github.mangi.eta.agent.prompt.PromptPresetMode
import io.github.mangi.eta.agent.prompt.PromptPresetRepository
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaPreferenceColors
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.EtaPreferenceIcon
import io.github.mangi.eta.ui.components.EtaTextButton
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 预设管理页：查看内置/导入的提示词预设，一键套用、导入 zip/json 包、导出、删除。
 * 套用 = 把预设的自定义身份/人格/追加指令写进当前人格存储，Agent 下次请求即生效。
 */
@Composable
internal fun PresetManagerScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshTick by remember { mutableStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<PromptPreset?>(null) }
    var exporting by remember { mutableStateOf<PromptPreset?>(null) }

    val builtins = remember(refreshTick) { PromptPresetRepository.builtins(context) }
    val imported = remember(refreshTick) { PromptPresetRepository.imported() }
    val activeName = remember(refreshTick) { ActivePersonaStore.activeName() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val result = runCatching { PromptPresetCodec.read(context, uri) }
                .getOrElse { PromptPresetCodec.Result.Failure("读取异常：${it.message}") }
            // 磁盘写入放 IO 线程
            val appliedName: String? = when (result) {
                is PromptPresetCodec.Result.Ok -> runCatching {
                    PromptPresetRepository.addImported(result.value)
                    PromptPresetRepository.applyPreset(result.value)
                    result.value.name
                }.getOrNull()
                is PromptPresetCodec.Result.Failure -> null
            }
            withContext(Dispatchers.Main) {
                when (result) {
                    is PromptPresetCodec.Result.Ok -> {
                        refreshTick++
                        notice = if (appliedName != null) "已导入并套用「$appliedName」" else "导入失败：写入预设时出错"
                    }
                    is PromptPresetCodec.Result.Failure -> {
                        notice = "导入失败：${result.reason}"
                    }
                }
            }
        }
    }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        val preset = exporting ?: return@rememberLauncherForActivityResult
        exporting = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val result = PromptPresetCodec.write(context, uri, preset)
            withContext(Dispatchers.Main) {
                notice = when (result) {
                    is PromptPresetCodec.Result.Ok -> "已导出「${preset.name}」"
                    is PromptPresetCodec.Result.Failure -> "导出失败：${result.reason}"
                }
            }
        }
    }

    MiuixScaffoldPage(
        title = "预设管理",
        onBack = onBack,
        actions = {
            IconButton(onClick = { importLauncher.launch("*/*") }) {
                Icon(
                    imageVector = Icons.Rounded.FileUpload,
                    contentDescription = "导入预设",
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        },
    ) {
        item(key = "header") {
            EtaPreferenceGroupTitle(
                "导入 zip / json 预设包，一键套用自定义人格。" +
                    (activeName?.let { "\n当前生效：$it" } ?: "\n当前：系统默认")
            )
        }

        if (activeName != null) {
            item(key = "reset") {
                EtaPreferenceGroup {
                    EtaArrowPreference(
                        title = "恢复系统默认",
                        summary = "清除当前套用的预设，回到内置身份",
                        onClick = {
                            ActivePersonaStore.clear()
                            refreshTick++
                            notice = "已恢复系统默认"
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (imported.isNotEmpty()) {
            item(key = "imported_header") {
                EtaPreferenceGroupTitle("已导入")
            }
            item(key = "imported") {
                EtaPreferenceGroup {
                    imported.forEachIndexed { index, preset ->
                        if (index > 0) EtaPreferenceDivider()
                        PresetRow(
                            preset = preset,
                            isActive = preset.name == activeName,
                            onApply = {
                                PromptPresetRepository.applyPreset(preset)
                                refreshTick++
                                notice = "已套用「${preset.name}」"
                            },
                            onExport = { exporting = preset; exportLauncher.launch(exportFileName(preset)) },
                            onDelete = { deleting = preset },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        item(key = "builtin_header") {
            EtaPreferenceGroupTitle("内置预设")
        }
        item(key = "builtin") {
            EtaPreferenceGroup {
                if (builtins.isEmpty()) {
                    EtaArrowPreference(
                        title = "暂无内置预设",
                        summary = "assets/prompt_presets 目录为空",
                        onClick = null,
                    )
                } else {
                    builtins.forEachIndexed { index, preset ->
                        if (index > 0) EtaPreferenceDivider()
                        PresetRow(
                            preset = preset,
                            isActive = preset.name == activeName,
                            onApply = {
                                PromptPresetRepository.applyPreset(preset)
                                refreshTick++
                                notice = "已套用「${preset.name}」"
                            },
                            onExport = { exporting = preset; exportLauncher.launch(exportFileName(preset)) },
                            onDelete = null,
                        )
                    }
                }
            }
        }

        item(key = "hint") {
            Spacer(Modifier.height(8.dp))
            EtaPreferenceGroup {
                EtaArrowPreference(
                    title = "导入说明",
                    summary = "支持 zip 包（含 preset.json）或裸 JSON 文件，单包上限 256KB。schema 高于 2 的包会被拒绝。",
                    startAction = {
                        EtaPreferenceIcon(Icons.Rounded.Description, tint = EtaPreferenceColors.Blue)
                    },
                    onClick = null,
                )
            }
        }
    }

    deleting?.let { preset ->
        EtaWindowDialog(
            show = true,
            title = "删除预设",
            summary = "确定删除「${preset.name}」吗？",
            onDismissRequest = { deleting = null },
        ) {
            MiuixDialogActions(
                confirmText = "删除",
                destructive = true,
                onCancel = { deleting = null },
                onConfirm = {
                    PromptPresetRepository.removeImported(preset.name)
                    if (ActivePersonaStore.activeName() == preset.name) {
                        ActivePersonaStore.clear()
                    }
                    deleting = null
                    refreshTick++
                },
            )
        }
    }

    notice?.let { text ->
        EtaWindowDialog(
            show = true,
            title = "提示",
            summary = text,
            onDismissRequest = { notice = null },
        ) {
            MiuixDialogActions(
                confirmText = "知道了",
                onCancel = { notice = null },
                onConfirm = { notice = null },
            )
        }
    }
}

@Composable
private fun PresetRow(
    preset: PromptPreset,
    isActive: Boolean,
    onApply: () -> Unit,
    onExport: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            EtaArrowPreference(
                title = preset.name + if (isActive) "（生效中）" else "",
                summary = preset.description.ifBlank { "无描述" } +
                    "\n模式：" + if (preset.mode == PromptPresetMode.REPLACE) "替换" else "追加" +
                    if (preset.toolMode != "ALLOW_ALL") "\n工具范围：${preset.toolMode}（暂不生效）" else "",
                startAction = {
                    EtaPreferenceIcon(
                        if (isActive) Icons.Rounded.Check else Icons.Rounded.Description,
                        tint = if (isActive) EtaPreferenceColors.Green else EtaPreferenceColors.Blue,
                    )
                },
                onClick = null,
            )
        }
        if (!isActive) {
            EtaTextButton(
                text = "套用",
                onClick = onApply,
            )
        }
        IconButton(onClick = onExport) {
            Icon(
                imageVector = Icons.Rounded.FileDownload,
                contentDescription = "导出",
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        onDelete?.let { del ->
            IconButton(onClick = del) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "删除",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}

private fun exportFileName(preset: PromptPreset): String {
    val safe = preset.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40).ifBlank { "preset" }
    return "$safe.zip"
}
