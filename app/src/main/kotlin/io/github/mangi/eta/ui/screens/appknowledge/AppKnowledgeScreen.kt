package io.github.mangi.eta.ui.screens.appknowledge

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.mangi.eta.data.db.AppKnowledgeEntity
import io.github.mangi.eta.data.repository.AppKnowledgeRepository
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaPreferenceColors
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.EtaPreferenceIcon
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App 知识库页面：查看 / 删除已记住的 App 操作流程。
 * 新增记录由 Agent 通过 remember_app_usage 工具完成，这里只做管理。
 */
@Composable
internal fun AppKnowledgeScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val records by AppKnowledgeRepository.observeAll(context).collectAsState(initial = emptyList())
    var deleting by remember { mutableStateOf<AppKnowledgeEntity?>(null) }

    MiuixScaffoldPage(
        title = "App 知识库",
        onBack = onBack,
    ) {
        item(key = "header") {
            EtaPreferenceGroupTitle("记住的操作流程，下次可自动执行")
        }
        if (records.isEmpty()) {
            item(key = "empty") {
                EtaPreferenceGroup {
                    EtaArrowPreference(
                        title = "还没有记住任何操作",
                        summary = "让 Eta 在 App 里帮你做完一件事后，说“记住这个操作”，下次直接说“帮我在 XX 里做 YY”即可。",
                        startAction = {
                            EtaPreferenceIcon(Icons.Rounded.Apps, tint = EtaPreferenceColors.Blue)
                        },
                        onClick = null,
                    )
                }
            }
        } else {
            item(key = "list") {
                EtaPreferenceGroup {
                    records.forEachIndexed { index, record ->
                        if (index > 0) EtaPreferenceDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                EtaArrowPreference(
                                    title = record.actionDescription,
                                    summary = "${record.appName}（${record.packageName}）\n" +
                                        "已用 ${record.useCount} 次 · ${formatTime(record.lastUsed)}",
                                    startAction = {
                                        EtaPreferenceIcon(Icons.Rounded.Apps, tint = EtaPreferenceColors.Green)
                                    },
                                    onClick = null,
                                )
                            }
                            IconButton(onClick = { deleting = record }) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "删除",
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    deleting?.let { record ->
        EtaWindowDialog(
            show = true,
            title = "删除记录",
            summary = "确定删除「${record.actionDescription}」吗？",
            onDismissRequest = { deleting = null },
        ) {
            MiuixDialogActions(
                confirmText = "删除",
                destructive = true,
                onCancel = { deleting = null },
                onConfirm = {
                    scope.launch {
                        AppKnowledgeRepository.delete(context, record.id)
                        deleting = null
                    }
                },
            )
        }
    }
}

private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

private fun formatTime(millis: Long): String =
    try {
        dateFormat.format(Date(millis))
    } catch (_: Exception) {
        ""
    }
