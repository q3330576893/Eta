package io.github.mangi.eta.data.model

import android.content.Context
import org.json.JSONObject

/**
 * Token 单价表（美元 / 1M tokens），用于用量统计页的费用估算。
 *
 * 默认价格为常见模型的公开价格（2026 年初水平，仅供参考）；
 * 用户可在用量统计页覆盖任意模型关键字的单价，覆盖值存于私有 SharedPreferences。
 */
internal object TokenPriceTable {
    private const val PREFS_NAME = "token_price_overrides"
    private const val KEY_JSON = "overrides_json"

    /** 默认单价：关键字（小写，子串匹配）-> (输入 $/1M, 输出 $/1M) */
    private val defaults: List<Pair<String, Pair<Double, Double>>> = listOf(
        // OpenAI
        "gpt-5.5" to (5.0 to 15.0),
        "gpt-5" to (2.5 to 10.0),
        "gpt-4o" to (2.5 to 10.0),
        "gpt-4o-mini" to (0.15 to 0.6),
        "gpt-4.1" to (2.0 to 8.0),
        "gpt-4.1-mini" to (0.4 to 1.6),
        "o3" to (2.0 to 8.0),
        "o4-mini" to (1.1 to 4.4),
        // Anthropic
        "claude-opus-4" to (15.0 to 75.0),
        "claude-sonnet-4" to (3.0 to 15.0),
        "claude-haiku" to (0.25 to 1.25),
        "claude-3-7-sonnet" to (3.0 to 15.0),
        // Google
        "gemini-2.5-pro" to (1.25 to 10.0),
        "gemini-2.5-flash" to (0.3 to 2.5),
        "gemini-2.0-flash" to (0.1 to 0.4),
        // DeepSeek
        "deepseek-reasoner" to (0.55 to 2.19),
        "deepseek-chat" to (0.27 to 1.1),
        // Moonshot
        "kimi-k2" to (0.6 to 2.5),
        "moonshot-v1" to (1.0 to 1.0),
        // Alibaba
        "qwen-max" to (1.2 to 4.8),
        "qwen-plus" to (0.4 to 1.2),
        "qwen-turbo" to (0.05 to 0.2),
        // MiniMax
        "minimax-m2" to (0.3 to 1.2),
        // xAI
        "grok-4" to (3.0 to 15.0),
        "grok-3" to (3.0 to 15.0),
        // Zhipu
        "glm-4" to (0.7 to 0.7),
        // StepFun
        "step-2" to (1.0 to 4.0),
    )

    /** 估算单次用量的费用（美元）。未命中任何关键字时返回 null。 */
    fun estimateUsd(model: String, inputTokens: Long, outputTokens: Long, context: Context? = null): Double? {
        val (inputPrice, outputPrice) = findPrice(model, context) ?: return null
        return inputTokens / 1_000_000.0 * inputPrice + outputTokens / 1_000_000.0 * outputPrice
    }

    /** 查找某模型关键字的单价（用户覆盖优先）。 */
    fun findPrice(model: String, context: Context? = null): Pair<Double, Double>? {
        val key = model.lowercase()
        context?.let {
            overrides(it)[key]?.let { return it }
        }
        // 最长关键字优先，避免 "gpt-4o" 被 "gpt-4" 之类误匹配（按长度降序）
        return defaults.sortedByDescending { it.first.length }.firstOrNull { (keyword, _) ->
            key.contains(keyword)
        }?.second
    }

    /** 当前生效的全部条目（默认 + 覆盖），供设置页展示。 */
    fun allEntries(context: Context): List<PriceEntry> {
        val custom = overrides(context)
        val merged = LinkedHashMap<String, Pair<Double, Double>>()
        defaults.forEach { (k, v) -> merged[k] = v }
        custom.forEach { (k, v) -> merged[k] = v }
        return merged.map { (keyword, price) ->
            PriceEntry(keyword, price.first, price.second, custom.containsKey(keyword))
        }.sortedBy { it.keyword }
    }

    fun setCustomPrice(context: Context, keyword: String, inputPrice: Double, outputPrice: Double) {
        val map = overrides(context).toMutableMap()
        map[keyword.lowercase().trim()] = inputPrice to outputPrice
        saveOverrides(context, map)
    }

    fun removeCustomPrice(context: Context, keyword: String) {
        val map = overrides(context).toMutableMap()
        map.remove(keyword.lowercase().trim())
        saveOverrides(context, map)
    }

    data class PriceEntry(
        val keyword: String,
        val inputPricePer1M: Double,
        val outputPricePer1M: Double,
        val isCustom: Boolean,
    )

    private fun overrides(context: Context): Map<String, Pair<Double, Double>> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { k ->
                    val v = obj.getJSONObject(k)
                    put(k, v.getDouble("input") to v.getDouble("output"))
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun saveOverrides(context: Context, map: Map<String, Pair<Double, Double>>) {
        val obj = JSONObject()
        map.forEach { (k, v) ->
            obj.put(k, JSONObject().put("input", v.first).put("output", v.second))
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_JSON, obj.toString()).apply()
    }
}
