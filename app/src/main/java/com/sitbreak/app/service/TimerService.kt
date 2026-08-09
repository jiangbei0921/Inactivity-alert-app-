package com.sitbreak.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sitbreak.app.MainActivity
import com.sitbreak.app.R
import com.sitbreak.app.TimerState
import com.sitbreak.app.TimerStateHolder
import com.sitbreak.app.data.CheckInRepository
import com.sitbreak.app.data.SettingsDataStore
import com.sitbreak.app.data.db.AppDatabase
import com.sitbreak.app.data.db.CheckInRecord
import com.sitbreak.app.health.StandingValidator
import com.sitbreak.app.ui.reminder.ReminderActivity
import com.sitbreak.app.notification.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TimerService : Service() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tickJob: Job? = null
    private var foregroundStarted = false
    private var sittingStartTime: Long = System.currentTimeMillis()
    private var microBreakStartTime: Long = System.currentTimeMillis()
    private var sittingIntervalMinutes: Int = 45
    private var microBreakIntervalMinutes: Int = 20
    private var isMicroBreakEnabled: Boolean = true
    private var isVibrationEnabled: Boolean = true
    private var isSoundEnabled: Boolean = true
    private var workStartHour: Int = 9
    private var workEndHour: Int = 18
    private var isWeekendEnabled: Boolean = false
    private var enabledDays: Set<String> = setOf("MON", "TUE", "WED", "THU", "FRI")
    private var sittingReminderSent: Boolean = false
    private var sittingReminderSentTime: Long = 0L
    private var microBreakReminderSent: Boolean = false
    private var waterReminderStartTime: Long = 0L
    private var eyeReminderStartTime: Long = 0L
    private var isWaterReminderEnabled: Boolean = true
    private var isEyeReminderEnabled: Boolean = true
    private var pauseStartTime: Long = 0L
    private var pausedElapsedMinutes: Int = 0
    private var cachedStandCount: Int = 0
    private var tickCount: Int = 0

    // 循环响铃：久坐提醒触发后由 Ringtone 持续播放，直到用户操作或到达上限/退出 app 才停止。
    private var alertRingtone: Ringtone? = null
    private var alertStartTime: Long = 0L

    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var repository: CheckInRepository

    override fun onCreate() {
        super.onCreate()
        settingsDataStore = SettingsDataStore(this)
        repository = CheckInRepository(AppDatabase.getInstance(this).checkInDao())
        // 同步创建前台服务渠道：startForeground 要求渠道必须已存在，否则 Android 12+
        // 会直接抛 Bad notification for startForeground。createChannels 是挂起函数，
        // 在协程里异步执行，可能在服务启动时尚未完成。
        notificationHelper.ensureServiceChannel(this)
        scope.launch { notificationHelper.createChannels(this@TimerService) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimer()
            NotificationHelper.ACTION_STAND_UP -> dispatch { handleStandUp() }
            NotificationHelper.ACTION_SNOOZE -> dispatch { handleSnooze() }
            NotificationHelper.ACTION_PAUSE_TIMER -> dispatch { handlePause() }
            NotificationHelper.ACTION_RESUME_TIMER -> dispatch { resumeTimer() }
            NotificationHelper.ACTION_STOP_TIMER -> dispatch { handleStop() }
            else -> { /* 未知或空 action 不应重置/重启计时器，避免误触发暂停或恢复 */ }
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 前台计时服务：用户划掉任务卡片后应保持运行（START_REDELIVER_INTENT 会在被系统回收后自动重启），
        // 但循环响铃属于「需要用户介入才停」的强提醒，退出 app 时应立即停止响铃，避免后台持续吵闹。
        stopAlertSound()
        super.onTaskRemoved(rootIntent)
    }

    private fun startTimer() {
        startForeground()
        TimerStateHolder.lastStandVerified = null
        StandingValidator.start(this)
        loadSettingsAndStartTicking()
    }

    private fun startForeground() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("站一站 计时中")
            .setContentText("正在监测久坐时间")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
        foregroundStarted = true
    }

    private fun ensureForegroundStarted() {
        if (!foregroundStarted) {
            startForeground()
        }
    }

    private fun dispatch(block: suspend () -> Unit) {
        ensureForegroundStarted()
        scope.launch { block() }
    }

    private fun loadSettingsAndStartTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            refreshSettings()
            sittingStartTime = settingsDataStore.sittingStartTime.first()
            microBreakStartTime = settingsDataStore.microBreakStartTime.first()
            isWaterReminderEnabled = settingsDataStore.isWaterReminderEnabled.first()
            isEyeReminderEnabled = settingsDataStore.isEyeReminderEnabled.first()

            val now = System.currentTimeMillis()
            if (sittingStartTime <= 0L || sittingStartTime > now || !TimeUtils.isToday(sittingStartTime)) {
                // 避免旧时间戳导致“天文数字”久坐分钟数，或跨天继续计时
                sittingStartTime = now
                microBreakStartTime = now
                settingsDataStore.setSittingStartTime(sittingStartTime)
                settingsDataStore.setMicroBreakStartTime(microBreakStartTime)
            }

            waterReminderStartTime = sittingStartTime
            eyeReminderStartTime = sittingStartTime

            sittingReminderSent = false
            microBreakReminderSent = false

            // 计时开始前先确保提醒渠道已就绪（await 完成），避免首次提醒时
            // 通知因目标渠道尚未创建而被系统静默丢弃。
            notificationHelper.ensureReminderChannels(this@TimerService)

            TimerStateHolder.setState(TimerState.Running)
            refreshStandCount()

            while (isActive) {
                tick()
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    private suspend fun refreshStandCount() {
        val todayStart = TimeUtils.getTodayStartMillis()
        val todayEnd = todayStart + 24 * 60 * 60 * 1000L
        cachedStandCount = try {
            repository.getTodayStandCount(todayStart, todayEnd)
        } catch (e: Exception) {
            Log.e(TAG, "refreshStandCount failed", e)
            0
        }
    }

    private suspend fun refreshSettings() {
        sittingIntervalMinutes = settingsDataStore.sittingIntervalMinutes.first()
        microBreakIntervalMinutes = settingsDataStore.microBreakIntervalMinutes.first()
        isMicroBreakEnabled = settingsDataStore.isMicroBreakEnabled.first()
        isVibrationEnabled = settingsDataStore.isVibrationEnabled.first()
        isSoundEnabled = settingsDataStore.isSoundEnabled.first()
        workStartHour = settingsDataStore.workStartHour.first()
        workEndHour = settingsDataStore.workEndHour.first()
        isWeekendEnabled = settingsDataStore.isWeekendEnabled.first()
        enabledDays = settingsDataStore.enabledDays.first()
        isWaterReminderEnabled = settingsDataStore.isWaterReminderEnabled.first()
        isEyeReminderEnabled = settingsDataStore.isEyeReminderEnabled.first()
    }

    private suspend fun tick() {
        refreshSettings()
        // 循环响铃上限/静音开关：达到 30s 上限或用户临时关闭声音时自动停铃（通知与计时保留）。
        if (alertRingtone?.isPlaying == true &&
            (!isSoundEnabled || System.currentTimeMillis() - alertStartTime >= ALERT_MAX_DURATION_MS)
        ) {
            stopAlertSound()
        }
        // 每约 60 秒（4 个 15 秒 tick）刷新一次当日站立次数，避免通知里的「今日站立」长期失真。
        if (tickCount++ % 4 == 0) refreshStandCount()

        // 计时器仅在「用户主动暂停」时停止计时；Running / Reminder 下持续按 sittingStartTime
        // 累计久坐时长并触发提醒。不再因「非工作时间」强制暂停，也不在任意非运行态自动恢复，
        // 以避免点击/状态切换引发的非预期暂停或恢复，使计时功能稳定持续运行。
        when (val state = TimerStateHolder.state.value) {
            TimerState.Paused -> {
                updateServiceNotification(pausedElapsedMinutes)
                return
            }
            TimerState.Idle, TimerState.Completed -> {
                updateServiceNotification(0)
                return
            }
            else -> { /* Running 或 Reminder：继续计时与提醒 */ }
        }

        val now = System.currentTimeMillis()
        val sittingElapsed = (now - sittingStartTime) / 60_000
        val microBreakElapsed = (now - microBreakStartTime) / 60_000

        updateServiceNotification(sittingElapsed.toInt())

        // 到点必提醒：久坐时长达到设定间隔且本次尚未提醒，立即触发弹窗 + 铃声，不再做任何
        // 「智能延迟」。此前的智能延迟会在前台视频 / 勿扰时把提醒长时间顺延，表现为
        // 「到点不提示、计时继续走」，与「到点必提醒」需求冲突，故移除。
        if (!sittingReminderSent && sittingElapsed >= sittingIntervalMinutes) {
            sittingReminderSent = true
            sittingReminderSentTime = now
            TimerStateHolder.setState(TimerState.Reminder)
            notificationHelper.sendSittingReminder(this, sittingElapsed.toInt(), isVibrationEnabled)
            startAlertSound()
            launchReminderActivity(sittingElapsed.toInt())
        }

        // C9：周期提醒——若已发送久坐提醒但用户未处理，每 5 分钟重复提醒一次
        if (sittingReminderSent && now - sittingReminderSentTime >= SITTING_REMINDER_REPEAT_MS) {
            sittingReminderSentTime = now
            notificationHelper.sendSittingReminder(this, sittingElapsed.toInt(), isVibrationEnabled)
            startAlertSound()
        }

        if (isMicroBreakEnabled && !microBreakReminderSent && microBreakElapsed >= microBreakIntervalMinutes) {
            microBreakReminderSent = true
            notificationHelper.sendMicroBreakNotification(this, microBreakElapsed.toInt(), isVibrationEnabled)
        }

        val waterElapsed = (now - waterReminderStartTime) / 60_000
        if (isWaterReminderEnabled && waterElapsed >= WATER_REMINDER_INTERVAL_MIN) {
            notificationHelper.sendWaterReminder(this, isVibrationEnabled)
            waterReminderStartTime = now
        }

        val eyeElapsed = (now - eyeReminderStartTime) / 60_000
        if (isEyeReminderEnabled && eyeElapsed >= EYE_REMINDER_INTERVAL_MIN) {
            notificationHelper.sendEyeReminder(this, isVibrationEnabled)
            eyeReminderStartTime = now
        }
    }

    private suspend fun handleStandUp() {
        val now = System.currentTimeMillis()

        stopAlertSound()
        val verified = StandingValidator.standingLikely()
        val record = CheckInRecord(
            timestamp = now,
            type = CheckInRecord.TYPE_STAND_UP,
            verified = verified
        )
        repository.insert(record)

        TimerStateHolder.lastStandVerified = verified
        StandingValidator.stop()

        TimerStateHolder.setState(TimerState.Completed)

        sittingStartTime = 0L
        microBreakStartTime = 0L
        sittingReminderSent = false
        sittingReminderSentTime = 0L
        microBreakReminderSent = false
        pauseStartTime = 0L
        settingsDataStore.setSittingStartTime(0L)
        settingsDataStore.setMicroBreakStartTime(0L)

        cancelReminderNotifications()
        refreshStandCount()

        tickJob?.cancel()
        stopSelf()
    }

    private suspend fun handleSnooze() {
        stopAlertSound()
        sittingStartTime = sittingStartTime + SNOOZE_DURATION_MS
        sittingReminderSent = false
        sittingReminderSentTime = 0L
        TimerStateHolder.setState(TimerState.Running)
        settingsDataStore.setSittingStartTime(sittingStartTime)
        cancelReminderNotifications()
    }

    private suspend fun handlePause() {
        stopAlertSound()
        pauseTimer()
        cancelReminderNotifications()
    }

    private suspend fun handleStop() {
        stopAlertSound()
        TimerStateHolder.setState(TimerState.Idle)
        tickJob?.cancel()
        sittingStartTime = 0L
        microBreakStartTime = 0L
        pauseStartTime = 0L
        settingsDataStore.setSittingStartTime(0L)
        settingsDataStore.setMicroBreakStartTime(0L)
        cancelReminderNotifications()
        StandingValidator.stop()
        stopSelf()
    }

    private suspend fun pauseTimer() {
        pausedElapsedMinutes = ((System.currentTimeMillis() - sittingStartTime) / 60_000).toInt().coerceAtLeast(0)
        pauseStartTime = System.currentTimeMillis()
        TimerStateHolder.setState(TimerState.Paused)
    }

    private suspend fun resumeTimer() {
        val now = System.currentTimeMillis()
        // 手动/通知「继续」：把暂停期间消耗的时长补偿回各计时起点，使久坐时长连续。
        if (pauseStartTime > 0L) {
            val pausedDuration = now - pauseStartTime
            sittingStartTime += pausedDuration
            microBreakStartTime += pausedDuration
            waterReminderStartTime += pausedDuration
            eyeReminderStartTime += pausedDuration
        }
        settingsDataStore.setSittingStartTime(sittingStartTime)
        settingsDataStore.setMicroBreakStartTime(microBreakStartTime)
        pauseStartTime = 0L
        TimerStateHolder.setState(TimerState.Running)
    }

    private fun cancelReminderNotifications() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NotificationHelper.NOTIFICATION_ID_SITTING)
        manager.cancel(NotificationHelper.NOTIFICATION_ID_MICRO_BREAK)
        manager.cancel(NotificationHelper.NOTIFICATION_ID_WATER)
        manager.cancel(NotificationHelper.NOTIFICATION_ID_EYE)
    }

    /**
     * 启动循环响铃（久坐提醒用）。已在播放则直接返回，避免 5 分钟重复提醒时重复创建。
     * 声音关闭（isSoundEnabled=false）时不响。声音 URI 复用通知设置里解析出的同一音源。
     */
    private suspend fun startAlertSound() {
        if (alertRingtone?.isPlaying == true) return
        if (!isSoundEnabled) return
        val uri = notificationHelper.alertRingtoneUri(this)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        alertRingtone = RingtoneManager.getRingtone(this, uri)?.apply {
            // 用闹钟流（USAGE_ALARM）播放提醒铃声：独立于「通知音量」，且后台（前台服务）
            // 场景不会被系统静音，确保「到点必响」。默认 Ringtone 走 STREAM_NOTIFICATION，
            // 华为 / Android 12+ 下后台通知流播放常被静音，表现为「有弹窗、没声音」。
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            isLooping = true
            play()
        }
        alertStartTime = System.currentTimeMillis()
    }

    /** 停止循环响铃。 */
    private fun stopAlertSound() {
        alertRingtone?.stop()
        alertRingtone = null
    }

    /**
     * 前台服务内直接拉起全屏提醒页，作为通知「全屏意图」的兜底，确保提示页面必定弹出
     * （即便系统因后台限制拦截了通知的全屏意图）。前台服务允许在后台启动 Activity。
     */
    private fun launchReminderActivity(sittingMinutes: Int) {
        val intent = Intent(this, ReminderActivity::class.java).apply {
            putExtra(ReminderActivity.EXTRA_SITTING_MINUTES, sittingMinutes)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "launchReminderActivity failed", e)
        }
    }

    private suspend fun updateServiceNotification(elapsedMinutes: Int) {
        val todayStandCount = cachedStandCount
        val nextReminder = if (sittingIntervalMinutes > elapsedMinutes) {
            sittingIntervalMinutes - elapsedMinutes
        } else {
            0
        }
        val notification = notificationHelper.buildServiceNotification(
            context = this,
            elapsedMinutes = elapsedMinutes,
            todayStandCount = todayStandCount,
            nextReminderMinutes = nextReminder,
            timerState = TimerStateHolder.state.value,
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)

    }

    override fun onDestroy() {
        stopAlertSound()
        StandingValidator.stop()
        tickJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TimerService"
        private const val TICK_INTERVAL_MS = 15_000L
        private const val WATER_REMINDER_INTERVAL_MIN = 90
        private const val EYE_REMINDER_INTERVAL_MIN = 20
        private const val SNOOZE_DURATION_MS = 5 * 60 * 1000L
        private const val SITTING_REMINDER_REPEAT_MS = 5 * 60 * 1000L
        private const val ALERT_MAX_DURATION_MS = 30_000L

        const val ACTION_START = "com.sitbreak.app.ACTION_START"

        fun start(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun onStandUp(context: Context) {
            context.startForegroundService(
                Intent(context, TimerService::class.java).setAction(NotificationHelper.ACTION_STAND_UP)
            )
        }

        fun onSnooze(context: Context) {
            context.startForegroundService(
                Intent(context, TimerService::class.java).setAction(NotificationHelper.ACTION_SNOOZE)
            )
        }

        fun onPause(context: Context) {
            context.startForegroundService(
                Intent(context, TimerService::class.java).setAction(NotificationHelper.ACTION_PAUSE_TIMER)
            )
        }

        fun onResume(context: Context) {
            context.startForegroundService(
                Intent(context, TimerService::class.java).setAction(NotificationHelper.ACTION_RESUME_TIMER)
            )
        }

        fun onStop(context: Context) {
            context.startForegroundService(
                Intent(context, TimerService::class.java).setAction(NotificationHelper.ACTION_STOP_TIMER)
            )
        }
    }
}
