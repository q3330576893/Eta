package io.github.mangi.eta.agent.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 热词唤醒控制器：接线 UI 开关 -> 权限 -> 模型 -> 服务
 */
object HotwordController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 启用热词唤醒：检查权限，下载模型，启动服务
     * 注意：权限请求需要 Activity 上下文，这里只做检查，UI 层负责申请
     */
    fun enable(context: Context) {
        scope.launch {
            val appContext = context.applicationContext
            // 1. 检查录音权限
            if (ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // 权限未授予，UI 层应已处理申请，这里直接返回
                return@launch
            }
            // 2. 确保模型已下载
            val modelDir = HotwordModelManager.ensureModel(appContext)
            if (modelDir == null) {
                // 模型下载失败
                return@launch
            }
            // 3. 启动服务
            val intent = Intent(appContext, HotwordService::class.java).apply {
                action = "io.github.mangi.eta.hotword.START"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }
    }

    /**
     * 禁用热词唤醒：停止服务
     */
    fun disable(context: Context) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, HotwordService::class.java).apply {
            action = "io.github.mangi.eta.hotword.STOP"
        }
        appContext.startService(intent)
    }

    /**
     * 检查是否有录音权限
     */
    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context.applicationContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
