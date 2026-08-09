package com.sitbreak.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sitbreak.app.MainActivity
import com.sitbreak.app.R
import com.sitbreak.app.TimerState
import com.sitbreak.app.data.NotificationSettingsDataStore
import com.sitbreak.app.data.SettingsDataStore
import com.sitbreak.app.service.TimerService
import com.sitbreak.app.ui.reminder.ReminderActivity
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class NotificationHelper @Inject constructor() {

    companion object {
        const val CHANNEL_SERVICE = "timer_service"
        const val CHANNEL_SITTING_REMINDER = "sitting_reminder"
        const val CHANNEL_MICRO_BREAK = "micro_break"
        const val CHANNEL_DAILY_SUMMARY = "daily_summary"

        private const val GROUP_KEY_REMINDERS = "sitbreak_reminders"

        const val NOTIFICATION_ID_SITTING = 1001
        const val NOTIFICATION_ID_MICRO_BREAK = 1002
        const val NOTIFICATION_ID_SERVICE = 1003
        const val NOTIFICATION_ID_WATER = 1004
        const val NOTIFICATION_ID_EYE = 1005
        const val NOTIFICATION_ID_DAILY_SUMMARY = 1006

        const val ACTION_STAND_UP = "com.sitbreak.app.ACTION_STAND_UP"
        const val ACTION_SNOOZE = "com.sitbreak.app.ACTION_SNOOZE"
        const val ACTION_PAUSE_TIMER = "com.sitbreak.app.ACTION_PAUSE_TIMER"
        const val ACTION_RESUME_TIMER = "com.sitbreak.app.ACTION_RESUME_TIMER"
        const val ACTION_STOP_TIMER = "com.sitbreak.app.ACTION_STOP_TIMER"
    }

    private val audioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    /**
     * 根据当前设置创建/更新通知渠道。
     * 提醒类渠道使用动态 ID（编码声音/震动/铃声索引），确保 Android O+ 上修改设置后能生效。
     */
    suspend fun createChannels(context: Context) {
        val settings = SettingsDataStore(context)
        val isSoundEnabled = settings.isSoundEnabled.first()
        val isVibrationEnabled = settings.isVibrationEnabled.first()
        val soundIndex = settings.notificationSoundIndex.first()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val sittingId = sittingChannelId(isSoundEnabled, isVibrationEnabled, soundIndex)
        val microId = microChannelId(isSoundEnabled, isVibrationEnabled, soundIndex)

        // 清理旧的提醒渠道（避免用户设置变更后旧渠道仍然生效）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.notificationChannels?.forEach { channel ->
                val id = channel.id
                if ((id.startsWith(CHANNEL_SITTING_REMINDER) && id != sittingId) ||
                    (id.startsWith(CHANNEL_MICRO_BREAK) && id != microId)
                ) {
                    manager.deleteNotificationChannel(id)
                }
            }
        }

        manager.createNotificationChannel(
            buildReminderChannel(
                id = sittingId,
                name = "久坐提醒",
                description = "提醒您站起来活动一下",
                importance = NotificationManager.IMPORTANCE_HIGH,
                isSoundEnabled = isSoundEnabled,
                isVibrationEnabled = isVibrationEnabled,
                soundIndex = soundIndex,
                context = context,
            )
        )

        manager.createNotificationChannel(
            buildReminderChannel(
                id = microId,
                name = "微休息",
                description = "提醒您短暂休息片刻",
                importance = NotificationManager.IMPORTANCE_DEFAULT,
                isSoundEnabled = isSoundEnabled,
                isVibrationEnabled = isVibrationEnabled,
                soundIndex = soundIndex,
                context = context,
            )
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                "计时服务",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "久坐计时器后台运行"
                setShowBadge(false)
            }
        )

        // 每日小结属于「可以静默错过」的信息，用 LOW 重要度，不响铃不震动
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DAILY_SUMMARY,
                "每日小结",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "每天汇总昨天的站立情况"
                setShowBadge(false)
            }
        )
    }

    /**
     * 每日小结通知，由 [com.sitbreak.app.work.DailySummaryWorker] 触发。
     * 与提醒类通知刻意分渠道，用户可以只关掉小结而保留提醒。
     */
    fun sendDailySummary(context: Context, standCount: Int, activeMinutes: Int) {
        val title = if (standCount > 0) "昨天站起来了 $standCount 次" else "昨天一次都没站起来"
        val text = if (standCount > 0) {
            "累计活动约 $activeMinutes 分钟，今天继续保持 👍"
        } else {
            "今天给自己定个小目标：先站 3 次。"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_SUMMARY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    2001,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_DAILY_SUMMARY, notification)
    }

    private suspend fun buildReminderChannel(
        id: String,
        name: String,
        description: String,
        importance: Int,
        isSoundEnabled: Boolean,
        isVibrationEnabled: Boolean,
        soundIndex: Int,
        context: Context,
    ): NotificationChannel {
        return NotificationChannel(id, name, importance).apply {
            this.description = description
            enableVibration(isVibrationEnabled)
            vibrationPattern = if (isVibrationEnabled) longArrayOf(0, 300, 200, 300) else null
            setSound(
                if (isSoundEnabled) resolveNotificationSoundUri(context, soundIndex) else null,
                audioAttributes,
            )
        }
    }

    private fun sittingChannelId(sound: Boolean, vibration: Boolean, soundIndex: Int): String {
        return "${CHANNEL_SITTING_REMINDER}_${bool(sound)}_${bool(vibration)}_$soundIndex"
    }

    private fun microChannelId(sound: Boolean, vibration: Boolean, soundIndex: Int): String {
        return "${CHANNEL_MICRO_BREAK}_${bool(sound)}_${bool(vibration)}_$soundIndex"
    }

    private fun bool(value: Boolean): String = if (value) "1" else "0"

    private suspend fun currentChannelIds(context: Context): Pair<String, String> {
        val settings = SettingsDataStore(context)
        val sound = settings.isSoundEnabled.first()
        val vibration = settings.isVibrationEnabled.first()
        val index = settings.notificationSoundIndex.first()
        return sittingChannelId(sound, vibration, index) to microChannelId(sound, vibration, index)
    }

    suspend fun sendSittingReminder(
        context: Context,
        sittingMinutes: Int,
        vibrationEnabled: Boolean = true,
    ) {
        val (sittingChannelId, _) = currentChannelIds(context)

        val notification = NotificationCompat.Builder(context, sittingChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("你已经连续久坐 ${sittingMinutes} 分钟")
            .setContentText("该站起来活动一下了！")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setGroup(GROUP_KEY_REMINDERS)
            .setVibrate(if (vibrationEnabled) longArrayOf(0, 300, 200, 300) else null)
            .setFullScreenIntent(fullScreenPendingIntent(context, sittingMinutes), true)
            // 持续响铃由 TimerService 内的 Ringtone 循环提供，这里关闭渠道一次性 beep，避免双响。
            .setSound(null)
            .addAction(0, "我站起来了", actionPendingIntent(context, ACTION_STAND_UP, 0))
            .addAction(0, "延迟5分钟", actionPendingIntent(context, ACTION_SNOOZE, 1))
            .setOngoing(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_SITTING, notification)
    }

    private suspend fun sendSimpleReminder(
        context: Context,
        notificationId: Int,
        title: String,
        text: String,
        vibrationEnabled: Boolean,
    ) {
        val (_, microChannelId) = currentChannelIds(context)
        val notification = NotificationCompat.Builder(context, microChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY_REMINDERS)
            .setVibrate(if (vibrationEnabled) longArrayOf(0, 200, 100, 200) else null)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    suspend fun sendMicroBreakNotification(
        context: Context,
        sittingMinutes: Int,
        vibrationEnabled: Boolean = true,
    ) = sendSimpleReminder(
        context,
        NOTIFICATION_ID_MICRO_BREAK,
        "微休息一下",
        "你已经持续坐了 $sittingMinutes 分钟，休息一下眼睛和身体吧。",
        vibrationEnabled,
    )

    suspend fun sendWaterReminder(
        context: Context,
        vibrationEnabled: Boolean = true,
    ) = sendSimpleReminder(context, NOTIFICATION_ID_WATER, "喝杯水吧", "定时喝水，保持身体水分充足。", vibrationEnabled)

    suspend fun sendEyeReminder(
        context: Context,
        vibrationEnabled: Boolean = true,
    ) = sendSimpleReminder(context, NOTIFICATION_ID_EYE, "护眼时间到", "20分钟了，记得看向远处休息一下眼睛。", vibrationEnabled)

    fun buildServiceNotification(
        context: Context,
        elapsedMinutes: Int,
        todayStandCount: Int = 0,
        nextReminderMinutes: Int = 0,
        timerState: TimerState = TimerState.Idle,
    ): android.app.Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setGroup(GROUP_KEY_REMINDERS)
            .setContentIntent(contentPendingIntent)

        when (timerState) {
            TimerState.Running -> {
                val title = "\uD83E\uDD91 已久坐 ${elapsedMinutes} 分钟"
                builder.setContentTitle(title)

                val body = buildString {
                    append("今日站立 ${todayStandCount} 次")
                    if (nextReminderMinutes > 0) {
                        append(" · 下次提醒 ${nextReminderMinutes} 分钟后")
                    }
                }
                builder.setContentText(body)

                builder.addAction(0, "暂停", actionPendingIntent(context, ACTION_PAUSE_TIMER, 10))
                builder.addAction(0, "结束", actionPendingIntent(context, ACTION_STOP_TIMER, 11))
            }

            TimerState.Paused -> {
                builder.setContentTitle("\u23F8 已暂停")
                builder.setContentText("计时已暂停 · 已久坐 ${elapsedMinutes} 分钟")

                builder.addAction(0, "继续", actionPendingIntent(context, ACTION_RESUME_TIMER, 10))
                builder.addAction(0, "结束", actionPendingIntent(context, ACTION_STOP_TIMER, 11))
            }

            TimerState.Reminder -> {
                builder.setContentTitle("你已经连续久坐 ${elapsedMinutes} 分钟")
                builder.setContentText("该站起来活动一下了！")

                builder.addAction(0, "我站起来了", actionPendingIntent(context, ACTION_STAND_UP, 10))
                builder.addAction(0, "延迟5分钟", actionPendingIntent(context, ACTION_SNOOZE, 11))
            }

            else -> {
                builder.setContentTitle("站一站")
                builder.setContentText("计时未开始")
            }
        }

        return builder.build()
    }

    private fun actionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, TimerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getForegroundService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun fullScreenPendingIntent(context: Context, sittingMinutes: Int): PendingIntent {
        val reminderIntent = Intent(context, ReminderActivity::class.java).apply {
            putExtra(ReminderActivity.EXTRA_SITTING_MINUTES, sittingMinutes)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            12,
            reminderIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private suspend fun resolveNotificationSoundUri(context: Context, soundIndex: Int): Uri {
        val dataStore = NotificationSettingsDataStore(context)
        val customUri = dataStore.notificationSoundUri.first()

        if (soundIndex == 5 && customUri.isNotEmpty()) {
            return Uri.parse(customUri)
        }

        if (soundIndex in 0..4) {
            val ringtoneManager = RingtoneManager(context)
            ringtoneManager.setType(RingtoneManager.TYPE_NOTIFICATION)
            val cursor = ringtoneManager.cursor
            try {
                if (cursor != null && cursor.moveToPosition(soundIndex)) {
                    return ringtoneManager.getRingtoneUri(soundIndex)
                }
            } finally {
                cursor?.close()
            }
        }

        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    /**
     * 返回循环响铃应使用的声音 URI；若声音已关闭（isSoundEnabled=false）返回 null。
     * 供 TimerService 用 Ringtone 持续播放，复用与通知渠道相同的音源解析逻辑。
     */
    suspend fun alertRingtoneUri(context: Context): Uri? {
        val dataStore = NotificationSettingsDataStore(context)
        if (!dataStore.isSoundEnabled.first()) return null
        return resolveNotificationSoundUri(context, dataStore.notificationSoundIndex.first())
    }
}
