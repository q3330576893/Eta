package io.github.mangi.eta.agent.prompt

import io.github.mangi.eta.config.Prefs

/**
 * 当前生效的自定义人格（轻量版）。
 *
 * 只存 5 个字段 + 当前预设名，不做版本历史。由预设管理页写入，
 * [io.github.mangi.eta.agent.model.AgentPromptBuilder] 在拼 system prompt 时读取并注入。
 */
internal object ActivePersonaStore {
    private const val KEY_ACTIVE = "persona_active"
    private const val KEY_NAME = "persona_active_name"
    private const val KEY_BASE = "persona_base_system_prompt"
    private const val KEY_PERSONA_NAME = "persona_persona_name"
    private const val KEY_STYLE = "persona_persona_style"
    private const val KEY_APPEND = "persona_append_instructions"
    private const val KEY_MODE = "persona_mode"

    data class ActivePersona(
        val baseSystemPrompt: String,
        val personaName: String,
        val personaStyle: String,
        val appendInstructions: String,
        val mode: PromptPresetMode,
        val activeName: String,
    ) {
        val hasContent: Boolean
            get() = baseSystemPrompt.isNotBlank() || personaName.isNotBlank() ||
                personaStyle.isNotBlank() || appendInstructions.isNotBlank()
    }

    /** 有自定义人格且内容非空时返回，否则 null。 */
    fun get(): ActivePersona? {
        val prefs = Prefs.localAgentPreferences() ?: return null
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return null
        val persona = ActivePersona(
            baseSystemPrompt = prefs.getString(KEY_BASE, "").orEmpty(),
            personaName = prefs.getString(KEY_PERSONA_NAME, "").orEmpty(),
            personaStyle = prefs.getString(KEY_STYLE, "").orEmpty(),
            appendInstructions = prefs.getString(KEY_APPEND, "").orEmpty(),
            mode = runCatching { PromptPresetMode.valueOf(prefs.getString(KEY_MODE, "APPEND").orEmpty()) }
                .getOrDefault(PromptPresetMode.APPEND),
            activeName = prefs.getString(KEY_NAME, "").orEmpty(),
        )
        return persona.takeIf { it.hasContent }
    }

    fun set(
        baseSystemPrompt: String,
        personaName: String,
        personaStyle: String,
        appendInstructions: String,
        mode: PromptPresetMode,
        activeName: String,
    ) {
        val prefs = Prefs.localAgentPreferences() ?: return
        prefs.edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_NAME, activeName)
            .putString(KEY_BASE, baseSystemPrompt)
            .putString(KEY_PERSONA_NAME, personaName)
            .putString(KEY_STYLE, personaStyle)
            .putString(KEY_APPEND, appendInstructions)
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    /** 恢复系统默认：清掉自定义人格。 */
    fun clear() {
        Prefs.localAgentPreferences()?.edit()?.putBoolean(KEY_ACTIVE, false)?.apply()
    }

    fun activeName(): String? {
        val prefs = Prefs.localAgentPreferences() ?: return null
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return null
        return prefs.getString(KEY_NAME, null)
    }
}
