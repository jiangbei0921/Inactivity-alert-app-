package com.sitbreak.app.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sitbreak.app.MainActivity
import com.sitbreak.app.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 更新过程的通知反馈。
 *
 * 刻意用 IMPORTANCE_LOW 的独立渠道：更新是后台事务，不该发出声音、不该弹横幅，
 * 更不该和「久坐提醒」抢用户注意力——那才是这个应用的核心功能。
 * 用户想看进度时下拉通知栏就有，不想看就完全无感。
 */
@Singleton
class UpdateNotifier @Inject constructor() {

    companion object {
        const val CHANNEL_UPDATE = "app_update"
        private const val NOTIFICATION_ID_UPDATE = 1007
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_UPDATE) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_UPDATE,
                "应用更新",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "新版本检测与增量更新进度"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun post(context: Context, builder: NotificationCompat.Builder) {
        if (!canPost(context)) return
        ensureChannel(context)
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID_UPDATE, builder.build())
        }
    }

    private fun base(context: Context) = NotificationCompat.Builder(context, CHANNEL_UPDATE)
        .setSmallIcon(R.drawable.ic_notification)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(contentIntent(context))
        .setOnlyAlertOnce(true)

    /** 后台检查发现新版本。只提示，不自作主张下载。 */
    fun notifyAvailable(context: Context, info: UpdateInfo) {
        val size = info.patchSize ?: info.fullSize
        val text = if (info.patchSize != null) {
            "增量更新仅需 ${formatSize(size)}，点击查看"
        } else {
            "安装包 ${formatSize(size)}，点击查看"
        }
        post(
            context,
            base(context)
                .setContentTitle("发现新版本 ${info.versionName}")
                .setContentText(text)
                .setAutoCancel(true),
        )
    }

    /** 下载/合成进度。节流由调用方负责，这里只负责画进度条。 */
    fun notifyProgress(context: Context, info: UpdateInfo, progress: Float, strategy: UpdateStrategy) {
        val label = if (strategy == UpdateStrategy.INCREMENTAL) "正在增量更新" else "正在下载更新"
        post(
            context,
            base(context)
                .setContentTitle("$label ${info.versionName}")
                .setContentText("${(progress * 100).toInt()}%")
                .setProgress(100, (progress * 100).toInt(), false)
                .setOngoing(true)
                .setAutoCancel(false),
        )
    }

    fun notifyReady(context: Context, info: UpdateInfo) {
        post(
            context,
            base(context)
                .setContentTitle("${info.versionName} 已准备好")
                .setContentText("点击完成安装")
                .setOngoing(false)
                .setAutoCancel(true),
        )
    }

    fun notifyFailed(context: Context, reason: String) {
        post(
            context,
            base(context)
                .setContentTitle("更新未完成")
                .setContentText(reason)
                .setOngoing(false)
                .setAutoCancel(true),
        )
    }

    fun cancel(context: Context) {
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID_UPDATE)
        }
    }
}

/** 把字节数格式化成「1.2 MB」这种人能一眼看懂的形式。 */
fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "未知大小"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}
