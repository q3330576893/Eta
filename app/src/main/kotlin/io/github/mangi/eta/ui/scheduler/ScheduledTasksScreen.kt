package io.github.mangi.eta.ui.scheduler

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.scheduler.NaturalLanguageScheduleParser
import io.github.mangi.eta.scheduler.ScheduleType
import io.github.mangi.eta.scheduler.ScheduledTask
import io.github.mangi.eta.scheduler.ScheduledTaskRepository
import io.github.mangi.eta.scheduler.TaskConditionType
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaPreferenceColors
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.EtaPreferenceIcon
import io.github.mangi.eta.ui.components.EtaSwitchPreference
import io.github.mangi.eta.ui.components.EtaTextButton
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 定时任务列表页：新增 / 编辑 / 删除 / 启用开关。
 * 新增时支持自然语言输入（"每天早上8点查天气，下雨才告诉我"）一键解析。
 */
@Composable
internal fun ScheduledTasksScreen(
    context: Context,
    onBack: () -> Unit,
) {
    val appContext = context.applicationContext
    val repository = remember(appContext) { ScheduledTaskRepository(appContext) }
    val tasks by repository.observeTasks().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var editingTask by remember { mutableStateOf<ScheduledTask?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deletingTask by remember { mutableStateOf<ScheduledTask?>(null) }

    MiuixScaffoldPage(
        title = "定时任务",
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                editingTask = null
                showEditor = true
            }) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = "新增")
            }
        },
    ) {
        item(key = "header") {
            EtaPreferenceGroupTitle("到点自动执行，支持条件跳过")
        }
        if (tasks.isEmpty()) {
            item(key = "empty") {
                EtaPreferenceGroup {
                    EtaArrowPreference(
                        title = "还没有定时任务",
                        summary = "点右上角 + 新建，例如：每天早上8点查天气",
                        startAction = {
                            EtaPreferenceIcon(Icons.Rounded.Schedule, tint = EtaPreferenceColors.Blue)
                        },
                        onClick = {
                            editingTask = null
                            showEditor = true
                        },
                    )
                }
            }
        } else {
            item(key = "list") {
                EtaPreferenceGroup {
                    tasks.forEachIndexed { index, task ->
                        if (index > 0) EtaPreferenceDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                EtaSwitchPreference(
                                    title = task.title,
                                    summary = buildTaskSummary(task),
                                    checked = task.enabled,
                                    onCheckedChange = { checked ->
                                        scope.launch { repository.setEnabled(task.id, checked) }
                                    },
                                )
                            }
                            IconButton(onClick = { deletingTask = task }) {
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
        item(key = "tip") {
            EtaPreferenceGroup {
                EtaArrowPreference(
                    title = "说明",
                    summary = "触发后自动执行并推送结果通知；条件不满足时静默跳过。\n" +
                        "精确时间需要「闹钟与提醒」权限，请在系统设置中允许。",
                    onClick = null,
                )
            }
        }
    }

    if (showEditor) {
        TaskEditorDialog(
            initial = editingTask,
            onDismiss = { showEditor = false },
            onSave = { task ->
                scope.launch {
                    repository.saveTask(task)
                    showEditor = false
                }
            },
        )
    }

    deletingTask?.let { task ->
        EtaWindowDialog(
            show = true,
            title = "删除任务",
            summary = "确定删除「${task.title}」吗？",
            onDismissRequest = { deletingTask = null },
        ) {
            MiuixDialogActions(
                confirmText = "删除",
                destructive = true,
                onCancel = { deletingTask = null },
                onConfirm = {
                    scope.launch {
                        repository.deleteTask(task.id)
                        deletingTask = null
                    }
                },
            )
        }
    }
}

private fun buildTaskSummary(task: ScheduledTask): String = buildString {
    append(task.scheduleSummary())
    if (!task.lastResult.isBlank()) {
        append("\n上次：${task.lastResult}")
        task.lastRunAt?.let {
            val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            append("（${fmt.format(Date(it))}）")
        }
    }
}

@Composable
private fun TaskEditorDialog(
    initial: ScheduledTask?,
    onDismiss: () -> Unit,
    onSave: (ScheduledTask) -> Unit,
) {
    var nlInput by remember { mutableStateOf("") }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var prompt by remember { mutableStateOf(initial?.prompt ?: "") }
    var scheduleType by remember { mutableStateOf(initial?.scheduleType ?: ScheduleType.DAILY) }
    var hour by remember { mutableStateOf(initial?.hour ?: 8) }
    var minute by remember { mutableStateOf(initial?.minute ?: 0) }
    var dayOfWeek by remember { mutableStateOf(initial?.dayOfWeek ?: Calendar.MONDAY) }
    var conditionType by remember { mutableStateOf(initial?.conditionType ?: TaskConditionType.NONE) }
    var parseHint by remember { mutableStateOf<String?>(null) }

    EtaWindowDialog(
        show = true,
        title = if (initial == null) "新建定时任务" else "编辑定时任务",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 自然语言输入 + 解析
            TextField(
                value = nlInput,
                onValueChange = { nlInput = it },
                label = "自然语言，例如：每天早上8点查天气，下雨才告诉我",
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            EtaTextButton(
                text = "智能解析",
                onClick = {
                    val parsed = NaturalLanguageScheduleParser.parse(nlInput)
                    if (parsed == null) {
                        parseHint = "没识别出来，换个说法试试"
                    } else {
                        title = parsed.title
                        prompt = parsed.prompt
                        scheduleType = parsed.scheduleType
                        hour = parsed.hour
                        minute = parsed.minute
                        dayOfWeek = parsed.dayOfWeek ?: Calendar.MONDAY
                        conditionType = parsed.conditionType
                        parseHint = "已解析：${parsed.scheduleType.name} " +
                            String.format("%02d:%02d", parsed.hour, parsed.minute) +
                            if (parsed.conditionType == TaskConditionType.WEATHER_RAIN) " · 下雨才执行" else ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            parseHint?.let {
                Text(
                    text = it,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            TextField(
                value = title,
                onValueChange = { title = it },
                label = "任务标题",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = "执行内容（发给 Agent 的指令）",
                singleLine = false,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            // 周期选择
            Text(text = "周期", style = MiuixTheme.textStyles.body2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleType.entries.forEach { type ->
                    ChoiceChip(
                        text = when (type) {
                            ScheduleType.DAILY -> "每天"
                            ScheduleType.WEEKLY -> "每周"
                            ScheduleType.HOURLY -> "每小时"
                            ScheduleType.ONCE -> "一次"
                        },
                        selected = scheduleType == type,
                        onClick = { scheduleType = type },
                    )
                }
            }

            // 时间选择（非每小时）
            if (scheduleType != ScheduleType.HOURLY) {
                Text(text = "时间", style = MiuixTheme.textStyles.body2)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Stepper(value = hour, range = 0..23, onChange = { hour = it })
                    Text(text = ":", style = MiuixTheme.textStyles.title3)
                    Stepper(value = minute, range = 0..59, onChange = { minute = it })
                }
            }

            // 星期选择（每周）
            if (scheduleType == ScheduleType.WEEKLY) {
                Text(text = "星期", style = MiuixTheme.textStyles.body2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val days = listOf(
                        Calendar.MONDAY to "一", Calendar.TUESDAY to "二",
                        Calendar.WEDNESDAY to "三", Calendar.THURSDAY to "四",
                        Calendar.FRIDAY to "五", Calendar.SATURDAY to "六",
                        Calendar.SUNDAY to "日",
                    )
                    days.forEach { (value, label) ->
                        ChoiceChip(
                            text = label,
                            selected = dayOfWeek == value,
                            onClick = { dayOfWeek = value },
                        )
                    }
                }
            }

            // 条件选择
            Text(text = "执行条件", style = MiuixTheme.textStyles.body2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(
                    text = "无条件",
                    selected = conditionType == TaskConditionType.NONE,
                    onClick = { conditionType = TaskConditionType.NONE },
                )
                ChoiceChip(
                    text = "下雨才执行",
                    selected = conditionType == TaskConditionType.WEATHER_RAIN,
                    onClick = { conditionType = TaskConditionType.WEATHER_RAIN },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            MiuixDialogActions(
                confirmText = "保存",
                onCancel = onDismiss,
                onConfirm = {
                    val finalTitle = title.ifBlank { "定时任务" }
                    val finalPrompt = prompt.ifBlank { finalTitle }
                    onSave(
                        ScheduledTask(
                            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                            title = finalTitle,
                            prompt = finalPrompt,
                            scheduleType = scheduleType,
                            hour = hour,
                            minute = minute,
                            dayOfWeek = if (scheduleType == ScheduleType.WEEKLY) dayOfWeek else null,
                            enabled = initial?.enabled ?: true,
                            conditionType = conditionType,
                            conditionParams = initial?.conditionParams ?: "",
                            lastRunAt = initial?.lastRunAt,
                            lastResult = initial?.lastResult ?: "",
                        )
                    )
                },
            )
        }
    }
}

@Composable
private fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MiuixTheme.colorScheme.primary
    else MiuixTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) MiuixTheme.colorScheme.onPrimary
    else MiuixTheme.colorScheme.onSurface
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun Stepper(
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "−",
            style = MiuixTheme.textStyles.title2,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                .clickable {
                    val newValue = if (value - 1 < range.first) range.last else value - 1
                    onChange(newValue)
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = String.format("%02d", value),
            style = MiuixTheme.textStyles.title3,
            modifier = Modifier.width(40.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "+",
            style = MiuixTheme.textStyles.title2,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                .clickable {
                    val newValue = if (value + 1 > range.last) range.first else value + 1
                    onChange(newValue)
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** 设置页入口用的本地 Context 包装（与项目其它 Screen 一致） */
@Composable
internal fun ScheduledTasksRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    ScheduledTasksScreen(context = context, onBack = onBack)
}
