package io.github.mangi.eta.agent.prompt

import android.content.Context
import io.github.mangi.eta.config.Prefs
import org.json.JSONArray

/**
 * 预设存储：内置预设从 assets 读，导入的预设存在 App 私有配置里。
 *
 * 两边都是纯内容；应用一份预设 = 把它的 4 个字段写进 [ActivePersonaStore]。
 */
internal object PromptPresetRepository {
    private const val BUILTIN_DIR = "prompt_presets"
    private const val KEY_IMPORTED = "agent_prompt_presets_json"
    private const val MAX_IMPORTED = 20

    /** 内置清单固定：顺序就是界面顺序。 */
    private val BUILTIN_FILES = listOf(
        "default.json",
        "concise.json",
        "rigorous.json",
        "technical.json",
        "coding.json",
        "research.json",
        "life.json",
    )

    fun builtins(context: Context): List<PromptPreset> =
        BUILTIN_FILES.mapNotNull { file -> PromptPresetCodec.readAsset(context, "$BUILTIN_DIR/$file") }

    fun imported(): List<PromptPreset> {
        val preferences = Prefs.localAgentPreferences() ?: return emptyList()
        val raw = runCatching { preferences.getString(KEY_IMPORTED, null) }.getOrNull() ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(PromptPreset::fromJson)
        }
    }

    fun addImported(preset: PromptPreset): List<PromptPreset> {
        val next = (listOf(preset.copy(builtin = false)) + imported().filterNot { it.name == preset.name })
            .take(MAX_IMPORTED)
        persist(next)
        return next
    }

    fun find(context: Context, name: String): PromptPreset? =
        builtins(context).firstOrNull { it.name == name }
            ?: imported().firstOrNull { it.name == name }

    fun removeImported(name: String): List<PromptPreset> {
        val next = imported().filterNot { it.name == name }
        persist(next)
        return next
    }

    /**
     * 应用预设：把预设的 4 个字段写进当前人格存储。
     * tool_mode / allowed_tools 导入时保留，应用时暂忽略。
     */
    fun applyPreset(preset: PromptPreset) {
        ActivePersonaStore.set(
            baseSystemPrompt = preset.baseSystemPrompt,
            personaName = preset.personaName,
            personaStyle = preset.personaStyle,
            appendInstructions = preset.appendInstructions,
            mode = preset.mode,
            activeName = preset.name,
        )
    }

    private fun persist(presets: List<PromptPreset>) {
        val preferences = Prefs.localAgentPreferences() ?: return
        val array = JSONArray()
        presets.forEach { array.put(it.toJson()) }
        runCatching { preferences.edit().putString(KEY_IMPORTED, array.toString()).commit() }
    }
}
