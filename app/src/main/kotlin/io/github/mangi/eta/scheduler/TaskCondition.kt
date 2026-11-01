package io.github.mangi.eta.scheduler

import android.content.Context
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.core.safeLogType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 条件检查结果 */
sealed interface ConditionResult {
    /** 条件满足，继续执行任务 */
    data object Pass : ConditionResult
    /** 条件不满足，跳过本次执行（不打扰用户） */
    data class Skip(val reason: String) : ConditionResult
}

/**
 * 任务前置条件接口（可扩展：后续可加"在公司才执行""电量充足才执行"等）。
 * fail-open：检查本身出错时返回 Pass，避免静默吞掉用户任务。
 */
interface TaskCondition {
    val type: TaskConditionType
    suspend fun check(context: Context, params: String): ConditionResult
}

internal fun taskConditionFor(type: TaskConditionType): TaskCondition? = when (type) {
    TaskConditionType.NONE -> null
    TaskConditionType.WEATHER_RAIN -> WeatherRainCondition()
}

/**
 * 天气条件示例："下雨才执行"。
 *
 * params 格式："lat,lon"（如 "31.23,121.47"）；为空时尝试 IP 定位，失败则放行。
 * 语义：未来 3 小时降雨概率 > 50%（或当前正在下雨）→ Pass，否则 Skip。
 * 数据源：Open-Meteo（免 key）。
 */
internal class WeatherRainCondition : TaskCondition {
    override val type = TaskConditionType.WEATHER_RAIN

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun check(context: Context, params: String): ConditionResult =
        withContext(Dispatchers.IO) {
            try {
                val (lat, lon) = resolveLocation(params) ?: return@withContext ConditionResult.Pass
                val willRain = withTimeoutOrNull(15_000L) { queryRainProbability(lat, lon) }
                    ?: return@withContext ConditionResult.Pass
                if (willRain) ConditionResult.Pass
                else ConditionResult.Skip("未来3小时无雨")
            } catch (t: Throwable) {
                AndroidAgentLogger.warn("Weather condition check failed, fail-open: type=${t.safeLogType()}")
                ConditionResult.Pass
            }
        }

    private fun resolveLocation(params: String): Pair<Double, Double>? {
        // 显式坐标
        params.split(",").map { it.trim() }.takeIf { it.size == 2 }?.let { parts ->
            val lat = parts[0].toDoubleOrNull()
            val lon = parts[1].toDoubleOrNull()
            if (lat != null && lon != null) return lat to lon
        }
        // IP 定位兜底
        return runCatching {
            val req = Request.Builder().url("http://ip-api.com/json/?fields=lat,lon,status").build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val obj = json.parseToJsonElement(body).jsonObject
                if (obj["status"]?.jsonPrimitive?.content != "success") return null
                val lat = obj["lat"]?.jsonPrimitive?.doubleOrNull ?: return null
                val lon = obj["lon"]?.jsonPrimitive?.doubleOrNull ?: return null
                lat to lon
            }
        }.getOrNull()
    }

    private fun queryRainProbability(lat: Double, lon: Double): Boolean {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&hourly=precipitation_probability,precipitation" +
            "&current=precipitation" +
            "&forecast_days=1&timezone=auto"
        val req = Request.Builder().url(url).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return false
            val body = resp.body?.string() ?: return false
            val root = json.parseToJsonElement(body).jsonObject
            // 当前正在下雨 → 满足
            val currentPrecip = root["current"]?.jsonObject
                ?.get("precipitation")?.jsonPrimitive?.doubleOrNull ?: 0.0
            if (currentPrecip > 0.1) return true
            // 未来3小时降雨概率
            val probs = root["hourly"]?.jsonObject
                ?.get("precipitation_probability")?.jsonArray
                ?.take(3)
                ?.mapNotNull { it.jsonPrimitive.doubleOrNull }
                ?: return false
            return probs.any { it >= 50.0 }
        }
    }
}
