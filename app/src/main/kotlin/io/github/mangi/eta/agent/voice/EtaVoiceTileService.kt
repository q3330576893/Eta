package io.github.mangi.eta.agent.voice

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/**
 * 快捷设置磁贴：一键唤醒 Eta 语音助手。
 *
 * 点击后拉起语音浮层（需要悬浮窗权限），长按跳转到 Eta 设置。
 * 用户需在通知栏编辑中手动添加此磁贴。
 */
@RequiresApi(Build.VERSION_CODES.N)
class EtaVoiceTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // 点击后收起快捷面板，避免遮挡语音浮层
        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+ 需要用户确认，直接拉起即可
        }
        try {
            // 拉起语音助手浮层
            EtaAssistantOverlayService.show(this, null)
        } catch (_: Exception) {
            // 失败时打开主界面
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(intent)
            return
        }
        // 收起快捷设置面板
        collapseStatusBar()
    }

    /**
     * 收起状态栏（兼容旧版本）
     */
    @Suppress("DEPRECATION")
    private fun collapseStatusBar() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ 推荐用 startActivityAndCollapse，已在 onClick 处理
            } else {
                sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
            }
        } catch (_: Exception) {
        }
    }
}
