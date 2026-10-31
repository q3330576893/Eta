package io.github.mangi.eta.agent.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.mangi.eta.R
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.core.safeLogType
import io.github.mangi.eta.ui.MainActivity

/**
 * 任务完成通知：Agent 任务结束（成功/失败）时弹一条高优先级通知，
 * 点击跳回 Eta 主界面。移植自魔改包的 VirtualScreenNotification.showCompletion。
 */
internal object TaskCompletionNotification {
    private const val CHANNEL = "eta_task_completion"
    private const val ID = 1111

    fun show(context: Context, ok: Boolean, detail: String) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.task_completion_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                )
            )
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            manager.notify(
                ID, Notification.Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(
                        if (ok) context.getString(R.string.task_completion_ok_title)
                        else context.getString(R.string.task_completion_fail_title)
                    )
                    .setContentText(detail.ifBlank { context.getString(R.string.task_completion_ok_body) })
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setAutoCancel(true)
                    .setContentIntent(open).build()
            )
        }.onFailure { AndroidAgentLogger.warn("Task completion notification failed: type=${it.safeLogType()}") }
    }
}
