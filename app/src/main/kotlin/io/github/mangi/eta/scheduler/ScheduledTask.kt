package io.github.mangi.eta.scheduler

import io.github.mangi.eta.data.db.ScheduledTaskEntity
import java.util.Calendar
import java.util.UUID

/** 周期类型 */
enum class ScheduleType {
    ONCE, DAILY, WEEKLY, HOURLY;

    companion object {
        fun fromName(name: String): ScheduleType =
            entries.firstOrNull { it.name == name } ?: DAILY
    }
}

/** 条件类型（可扩展） */
enum class TaskConditionType {
    NONE, WEATHER_RAIN;

    companion object {
        fun fromName(name: String): TaskConditionType =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}

/** 定时任务领域模型 */
data class ScheduledTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val prompt: String,
    val scheduleType: ScheduleType = ScheduleType.DAILY,
    val hour: Int = 8,
    val minute: Int = 0,
    /** Calendar.MONDAY(2) .. Calendar.SUNDAY(1)，仅 WEEKLY 使用 */
    val dayOfWeek: Int? = null,
    val enabled: Boolean = true,
    val conditionType: TaskConditionType = TaskConditionType.NONE,
    val conditionParams: String = "",
    val lastRunAt: Long? = null,
    val lastResult: String = "",
) {
    /** 计算下一次触发时间（毫秒时间戳） */
    fun nextTriggerMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return when (scheduleType) {
            ScheduleType.HOURLY -> {
                // 下一个整点
                cal.apply {
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.HOUR_OF_DAY, 1)
                }.timeInMillis
            }
            ScheduleType.DAILY -> {
                cal.apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= nowMillis) add(Calendar.DAY_OF_YEAR, 1)
                }.timeInMillis
            }
            ScheduleType.WEEKLY -> {
                val target = dayOfWeek ?: Calendar.MONDAY
                cal.apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    var daysAhead = (target - get(Calendar.DAY_OF_WEEK) + 7) % 7
                    if (daysAhead == 0 && timeInMillis <= nowMillis) daysAhead = 7
                    add(Calendar.DAY_OF_YEAR, daysAhead)
                }.timeInMillis
            }
            ScheduleType.ONCE -> {
                cal.apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= nowMillis) add(Calendar.DAY_OF_YEAR, 1)
                }.timeInMillis
            }
        }
    }

    fun scheduleSummary(): String = buildString {
        when (scheduleType) {
            ScheduleType.ONCE -> append("一次 ")
            ScheduleType.DAILY -> append("每天 ")
            ScheduleType.WEEKLY -> {
                append("每")
                append(
                    when (dayOfWeek) {
                        Calendar.MONDAY -> "周一"
                        Calendar.TUESDAY -> "周二"
                        Calendar.WEDNESDAY -> "周三"
                        Calendar.THURSDAY -> "周四"
                        Calendar.FRIDAY -> "周五"
                        Calendar.SATURDAY -> "周六"
                        Calendar.SUNDAY -> "周日"
                        else -> "周"
                    }
                )
                append(" ")
            }
            ScheduleType.HOURLY -> append("每小时 ")
        }
        if (scheduleType != ScheduleType.HOURLY) {
            append(String.format("%02d:%02d", hour, minute))
        }
        if (conditionType == TaskConditionType.WEATHER_RAIN) append(" · 下雨才执行")
    }
}

internal fun ScheduledTaskEntity.toDomain(): ScheduledTask = ScheduledTask(
    id = id,
    title = title,
    prompt = prompt,
    scheduleType = ScheduleType.fromName(scheduleType),
    hour = hour,
    minute = minute,
    dayOfWeek = dayOfWeek,
    enabled = enabled,
    conditionType = TaskConditionType.fromName(conditionType),
    conditionParams = conditionParams,
    lastRunAt = lastRunAt,
    lastResult = lastResult,
)

internal fun ScheduledTask.toEntity(nowMillis: Long = System.currentTimeMillis()): ScheduledTaskEntity =
    ScheduledTaskEntity(
        id = id,
        title = title,
        prompt = prompt,
        scheduleType = scheduleType.name,
        hour = hour,
        minute = minute,
        dayOfWeek = dayOfWeek,
        enabled = enabled,
        conditionType = conditionType.name,
        conditionParams = conditionParams,
        updatedAt = nowMillis,
        lastRunAt = lastRunAt,
        lastResult = lastResult,
    )

/** 自然语言解析结果 */
data class ParsedSchedule(
    val title: String,
    val prompt: String,
    val scheduleType: ScheduleType,
    val hour: Int,
    val minute: Int,
    val dayOfWeek: Int?,
    val conditionType: TaskConditionType,
)

/**
 * 极简中文时间解析（无 NLP 模型，纯正则/关键词）。
 * 支持：
 * - "每天早上8点查天气" → DAILY 8:00
 * - "每天晚上9点半提醒我喝水" → DAILY 21:30
 * - "每周一9点开会提醒" → WEEKLY MONDAY 9:00
 * - "每小时同步一次" → HOURLY
 * - "明早8点叫我起床" → ONCE 明天 8:00
 * - "下雨才告诉我/下雨才执行" → WEATHER_RAIN
 */
object NaturalLanguageScheduleParser {
    private val timePattern = Regex("(\\d{1,2})\\s*[点:：]\\s*(\\d{1,2})?\\s*分?")
    private val weekdayMap = mapOf(
        "周一" to Calendar.MONDAY, "星期一" to Calendar.MONDAY,
        "周二" to Calendar.TUESDAY, "星期二" to Calendar.TUESDAY,
        "周三" to Calendar.WEDNESDAY, "星期三" to Calendar.WEDNESDAY,
        "周四" to Calendar.THURSDAY, "星期四" to Calendar.THURSDAY,
        "周五" to Calendar.FRIDAY, "星期五" to Calendar.FRIDAY,
        "周六" to Calendar.SATURDAY, "星期六" to Calendar.SATURDAY,
        "周日" to Calendar.SUNDAY, "星期日" to Calendar.SUNDAY, "周天" to Calendar.SUNDAY,
    )

    fun parse(input: String): ParsedSchedule? {
        val text = input.trim()
        if (text.isEmpty()) return null

        // 周期
        var scheduleType = ScheduleType.DAILY
        var dayOfWeek: Int? = null
        for ((key, value) in weekdayMap) {
            if (text.contains(key)) {
                scheduleType = ScheduleType.WEEKLY
                dayOfWeek = value
                break
            }
        }
        if (scheduleType != ScheduleType.WEEKLY) {
            scheduleType = when {
                text.contains("每小时") || text.contains("每小時") -> ScheduleType.HOURLY
                text.contains("明天") || text.contains("明早") || text.contains("明晚") -> ScheduleType.ONCE
                text.contains("每天") || text.contains("每日") -> ScheduleType.DAILY
                // 默认：提到具体时间但没说周期 → 每天
                else -> ScheduleType.DAILY
            }
        }

        // 时间
        var hour = 8
        var minute = 0
        timePattern.find(text)?.let { match ->
            hour = match.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: 8
            val minGroup = match.groupValues[2]
            minute = if (minGroup.isNotEmpty()) {
                minGroup.toIntOrNull()?.coerceIn(0, 59) ?: 0
            } else if (text.contains("半", ignoreCase = false) &&
                text.indexOf("半") > match.range.first
            ) {
                30
            } else 0
        }
        // 上下午修正
        val beforeTime = text.substringBefore(timePattern.find(text)?.value ?: "")
        if (beforeTime.contains("晚上") || beforeTime.contains("夜里") || beforeTime.contains("傍晚")) {
            if (hour < 12) hour += 12
        } else if (beforeTime.contains("下午") || beforeTime.contains("中午")) {
            if (hour < 12) hour += 12
        } else if (beforeTime.contains("凌晨")) {
            if (hour == 12) hour = 0
        }

        // 条件
        val conditionType = if (text.contains("下雨")) TaskConditionType.WEATHER_RAIN else TaskConditionType.NONE

        // 标题/执行内容：去掉时间与周期关键词，剩余部分当标题
        var title = text
            .replace(Regex("每天|每日|每周|每小時|每小时|明天|明早|明晚"), "")
            .replace(Regex("早上|上午|中午|下午|晚上|夜里|凌晨|傍晚"), "")
            .replace(timePattern, "")
            .replace("半", "")
            .replace(Regex("下雨才[^，,。]*"), "")
            .replace(Regex("^[，,。.\\s]+"), "")
            .trim()
        if (title.isEmpty()) title = "定时任务"
        if (title.length > 20) title = title.take(20)

        val prompt = buildString {
            append(title)
            if (conditionType == TaskConditionType.WEATHER_RAIN) {
                append("（注意：只有在下雨或预报将下雨时才需要通知我，否则保持安静）")
            }
        }

        return ParsedSchedule(
            title = title,
            prompt = prompt,
            scheduleType = scheduleType,
            hour = hour,
            minute = minute,
            dayOfWeek = dayOfWeek,
            conditionType = conditionType,
        )
    }
}
