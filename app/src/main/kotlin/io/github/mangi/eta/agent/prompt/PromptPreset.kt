package io.github.mangi.eta.agent.prompt

import org.json.JSONArray
import org.json.JSONObject

/**
 * 预设模式：自定义身份如何与内置身份组合。
 */
internal enum class PromptPresetMode {
    /** 作为附加约束追加，内置身份段保持原样。 */
    APPEND,

    /** 作为主身份设定前置，并声明优先级高于内置身份描述。 */
    REPLACE,
}

/**
 * 一份可导入导出的提示词预设。
 *
 * 包格式：zip（推荐）或裸 JSON 文件，里面只需要一个 `preset.json`；
 * 字段与自定义人格一一对应，不带任何运行期状态。
 */
internal data class PromptPreset(
    val name: String,
    val description: String = "",
    val mode: PromptPresetMode = PromptPresetMode.APPEND,
    val baseSystemPrompt: String = "",
    val personaName: String = "",
    val personaStyle: String = "",
    val appendInstructions: String = "",
    val author: String = "",
    val createdAt: Long = 0L,
    val builtin: Boolean = false,
    /** schema 2 预设可携带工具范围；当前版本导入时保留、应用时忽略。 */
    val toolMode: String = "ALLOW_ALL",
    val allowedTools: Set<String> = emptySet(),
) {
    val contentLength: Int
        get() = baseSystemPrompt.length + personaName.length + personaStyle.length + appendInstructions.length

    val hasContent: Boolean
        get() = baseSystemPrompt.isNotBlank() || personaName.isNotBlank() ||
            personaStyle.isNotBlank() || appendInstructions.isNotBlank()

    fun toJson(): JSONObject = JSONObject()
        .put("schema", SCHEMA)
        .put("name", name)
        .put("description", description)
        .put("mode", mode.name)
        .put("base_system_prompt", baseSystemPrompt)
        .put("persona_name", personaName)
        .put("persona_style", personaStyle)
        .put("append_instructions", appendInstructions)
        .put("author", author)
        .put("created_at", createdAt)
        .put("tool_mode", toolMode)
        .put("allowed_tools", JSONArray().also { array -> allowedTools.sorted().forEach(array::put) })

    fun sameContentAs(other: PromptPreset): Boolean =
        mode == other.mode && baseSystemPrompt == other.baseSystemPrompt &&
            personaName == other.personaName && personaStyle == other.personaStyle &&
            appendInstructions == other.appendInstructions

    companion object {
        const val SCHEMA = 2
        const val MAX_FIELD_CHARS = 20_000
        const val MAX_BUNDLE_BYTES = 256 * 1024

        /** 解析失败返回 null，由调用方给出明确错误，不静默套用半份配置。 */
        fun fromJson(json: JSONObject): PromptPreset? {
            val schema = json.optInt("schema", SCHEMA)
            if (schema > SCHEMA) return null
            val name = json.optString("name").trim()
            if (name.isEmpty()) return null
            fun field(key: String): String? = json.optString(key).takeIf { it.length <= MAX_FIELD_CHARS }
            val base = field("base_system_prompt") ?: return null
            val personaName = field("persona_name") ?: return null
            val personaStyle = field("persona_style") ?: return null
            val append = field("append_instructions") ?: return null
            return PromptPreset(
                name = name.take(60),
                description = json.optString("description").take(280),
                mode = runCatching { PromptPresetMode.valueOf(json.optString("mode")) }
                    .getOrDefault(PromptPresetMode.APPEND),
                baseSystemPrompt = base,
                personaName = personaName,
                personaStyle = personaStyle,
                appendInstructions = append,
                author = json.optString("author").take(60),
                createdAt = json.optLong("created_at", 0L),
                toolMode = json.optString("tool_mode").takeIf { it.isNotBlank() } ?: "ALLOW_ALL",
                allowedTools = json.optJSONArray("allowed_tools")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        array.optString(index).takeIf(String::isNotBlank)
                    }.toSet()
                }.orEmpty(),
            )
        }
    }
}
