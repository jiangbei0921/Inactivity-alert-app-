package com.sitbreak.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.sitbreak.app.TimerState
import com.sitbreak.app.data.NotificationSettingsDataStore
import com.sitbreak.app.data.SettingsDataStore
import kotlinx.coroutines.flow.first

object NotificationHelper {

    const val CHANNEL_SITTING_REMINDER = "sitting_reminder"
    const val CHANNEL_MICRO_BREAK = "micro_break"
    const val CHANNEL_SERVICE = "timer_service"

    const val NOTIFICATION_ID_SITTING = 1001
    const val NOTIFICATION_ID_MICRO_BREAK = 1002
    const val NOTIFICATION_ID_SERVICE = 1003
    const val NOTIFICATION_ID_WATER = 1004
    const val NOTIFICATION_ID_EYE = 1005

    const val ACTION_STAND_UP = "com.sitbreak.app.ACTION_STAND_UP"
    const val ACTION_SNOOZE = "com.sitbreak.app.ACTION_SNOOZE"
    const val ACTION_PAUSE_TIMER = "com.sitbreak.app.ACTION_PAUSE_TIMER"
    const val ACTION_RESUME_TIMER = "com.sitbreak.app.ACTION_RESUME_TIMER"
    const val ACTION_STOP_TIMER = "com.sitbreak.app.ACTION_STOP_TIMER"

    private const val GROUP_KEY_REMINDERS = "sitbreak_reminders"

    suspend fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val settings = SettingsDataStore(context)
        val isSoundEnabled = settings.isSoundEnabled.first()
        val isVibrationEnabled = settings.isVibrationEnabled.first()
        val soundUri = if (isSoundEnabled) resolveNotificationSoundUri(context) else null
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val sittingChannel = NotificationChannel(
            CHANNEL_SITTING_REMINDER,
            "久坐提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "提醒您站起来活动一下"
            enableVibration(isVibrationEnabled)
            setSound(if (isSoundEnabled) soundUri else null, audioAttributes)
        }

        val microBreakChannel = NotificationChannel(
            CHANNEL_MICRO_BREAK,
            "微休息",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "提醒您短暂休息片刻"
            enableVibration(isVibrationEnabled)
            setSound(if (isSoundEnabled) soundUri else null, audioAttributes)
        }

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "计时服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "久坐计时器后台运行"
            setShowBadge(false)
        }

        manager.createNotificationChannel(sittingChannel)
        manager.createNotificationChannel(microBreakChannel)
        manager.createNotificationChannel(serviceChannel)
    }

    suspend fun sendSittingReminder(context: Context, sittingMinutes: Int, soundEnabled: Boolean = true, vibrationEnabled: Boolean = true) {
        val standUpIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_STAND_UP
        }
        val standUpPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            standUpIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_SNOOZE
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SITTING_REMINDER)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("你已经连续久坐 ${sittingMinutes} 分钟")
            .setContentText("该站起来活动一下了！")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setGroup(GROUP_KEY_REMINDERS)
            .setVibrate(if (vibrationEnabled) longArrayOf(0, 300, 200, 300) else null)
            .addAction(0, "我站起来了", standUpPendingIntent)
            .addAction(0, "延迟5分钟", snoozePendingIntent)
            .apply {
                if (soundEnabled) {
                    setSound(resolveNotificationSoundUri(context))
                } else {
                    setSound(null)
                }
            }
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_SITTING, notification)
    }

    suspend fun sendMicroBreakNotification(context: Context, sittingMinutes: Int, soundEnabled: Boolean = true, vibrationEnabled: Boolean = true) {
        val notification = NotificationCompat.Builder(context, CHANNEL_MICRO_BREAK)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("微休息一下")
            .setContentText("你已经持续坐了 $sittingMinutes 分钟，休息一下眼睛和身体吧。")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY_REMINDERS)
            .setVibrate(if (vibrationEnabled) longArrayOf(0, 200, 100, 200) else null)
            .apply {
                if (soundEnabled) {
                    setSound(resolveNotificationSoundUri(context))
                } else {
                    setSound(null)
                }
            }
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_MICRO_BREAK, notification)
    }

    fun buildServiceNotification(
        context: Context,
        elapsedMinutes: Int,
        todayStandCount: Int = 0,
        nextReminderMinutes: Int = 0,
        timerState: TimerState = TimerState.Idle,
    ): android.app.Notification {
        val openAppIntent = Intent(context, com.sitbreak.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
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

                val pauseIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = ACTION_PAUSE_TIMER
                }
                val pausePendingIntent = PendingIntent.getBroadcast(
                    context, 10, pauseIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(0, "暂停", pausePendingIntent)

                val stopIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = ACTION_STOP_TIMER
                }
                val stopPendingIntent = PendingIntent.getBroadcast(
                    context, 11, stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(0, "结束", stopPendingIntent)
            }

            TimerState.Paused -> {
                builder.setContentTitle("\u23F8 已暂停")
                builder.setContentText("计时已暂停 · 已久坐 ${elapsedMinutes} 分钟")

                val resumeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = ACTION_RESUME_TIMER
                }
                val resumePendingIntent = PendingIntent.getBroadcast(
                    context, 10, resumeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(0, "继续", resumePendingIntent)

                val stopIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = ACTION_STOP_TIMER
                }
                val stopPendingIntent = PendingIntent.getBroadcast(
                    context, 11, stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(0, "结束", stopPendingIntent)
            }

            TimerState.Reminder -> {
                builder.setContentTitle("你已经连续久坐 ${elapsedMinutes} 分钟")
                builder.setContentText("该站起来活动一下了！")

                val standUpIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = ACTION_STAND_UP
                }
                val standUpPendingIntent = PendingIntent.getBroadcast(
                    context, 10, standUpIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(0, "我站起来了", standUpPendingIntent)

                val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = ACTION_SNOOZE
                }
                val snoozePendingIntent = PendingIntent.getBroadcast(
                    context, 11, snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(0, "延迟5分钟", snoozePendingIntent)
            }

            else -> {
                builder.setContentTitle("站一站")
                builder.setContentText("计时未开始")
            }
        }

        return builder.build()
    }

    private suspend fun resolveNotificationSoundUri(context: Context): Uri {
        val dataStore = NotificationSettingsDataStore(context)
        val index = dataStore.notificationSoundIndex.first()
        val customUri = dataStore.notificationSoundUri.first()

        if (index == 5 && customUri.isNotEmpty()) {
            return Uri.parse(customUri)
        }

        if (index in 0..4) {
            val ringtoneManager = RingtoneManager(context)
            ringtoneManager.setType(RingtoneManager.TYPE_NOTIFICATION)
            val cursor = ringtoneManager.cursor
            try {
                if (cursor != null && cursor.moveToPosition(index)) {
                    return ringtoneManager.getRingtoneUri(index)
                }
            } finally {
                cursor?.close()
            }
        }

        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    suspend fun sendWaterReminder(context: Context, soundEnabled: Boolean = true, vibrationEnabled: Boolean = true) {
        val notification = NotificationCompat.Builder(context, CHANNEL_MICRO_BREAK)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("喝杯水吧")
            .setContentText("定时喝水，保持身体水分充足。")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY_REMINDERS)
            .setVibrate(if (vibrationEnabled) longArrayOf(0, 200, 100, 200) else null)
            .apply {
                if (soundEnabled) {
                    setSound(resolveNotificationSoundUri(context))
                } else {
                    setSound(null)
                }
            }
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_WATER, notification)
    }

    suspend fun sendEyeReminder(context: Context, soundEnabled: Boolean = true, vibrationEnabled: Boolean = true) {
        val notification = NotificationCompat.Builder(context, CHANNEL_MICRO_BREAK)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("护眼时间到")
            .setContentText("20分钟了，记得看向远处休息一下眼睛。")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY_REMINDERS)
            .setVibrate(if (vibrationEnabled) longArrayOf(0, 200, 100, 200) else null)
            .apply {
                if (soundEnabled) {
                    setSound(resolveNotificationSoundUri(context))
                } else {
                    setSound(null)
                }
            }
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_EYE, notification)
    }
}