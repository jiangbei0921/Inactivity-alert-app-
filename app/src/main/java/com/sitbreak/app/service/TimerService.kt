package com.sitbreak.app.service

import android.app.ActivityManager
import android.app.PendingIntent
import android.app.Service
import com.sitbreak.app.detector.SmartDetector
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sitbreak.app.MainActivity
import com.sitbreak.app.TimerState
import com.sitbreak.app.TimerStateHolder
import com.sitbreak.app.data.CheckInRepository
import com.sitbreak.app.data.SettingsDataStore
import com.sitbreak.app.data.db.AppDatabase
import com.sitbreak.app.notification.NotificationHelper
import com.sitbreak.app.ui.reminder.ReminderActivity
import com.sitbreak.app.widget.SitBreakWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null
    private var sittingStartTime: Long = System.currentTimeMillis()
    private var microBreakStartTime: Long = System.currentTimeMillis()
    private var sittingIntervalMinutes: Int = 45
    private var microBreakIntervalMinutes: Int = 20
    private var isMicroBreakEnabled: Boolean = true
    private var isSoundEnabled: Boolean = true
    private var isVibrationEnabled: Boolean = true
    private var workStartHour: Int = 9
    private var workEndHour: Int = 18
    private var isWeekendEnabled: Boolean = false
    private var enabledDays: Set<String> = setOf("MON", "TUE", "WED", "THU", "FRI")
    private var sittingReminderSent: Boolean = false
    private var microBreakReminderSent: Boolean = false
    private var waterReminderSent: Boolean = false
    private var eyeReminderSent: Boolean = false
    private var waterReminderStartTime: Long = 0L
    private var eyeReminderStartTime: Long = 0L
    private var isWaterReminderEnabled: Boolean = true
    private var isEyeReminderEnabled: Boolean = true
    private var pauseStartTime: Long = 0L

    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var repository: CheckInRepository

    override fun onCreate() {
        super.onCreate()
        settingsDataStore = SettingsDataStore(this)
        repository = CheckInRepository(AppDatabase.getInstance(this).checkInDao())
        NotificationHelper.createChannels(this)
        observeActions()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimer()
            ACTION_STOP -> stopSelf()
            else -> startTimer()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    private fun startTimer() {
        startForeground()
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
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("站一站 计时中")
            .setContentText("正在监测久坐时间")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
    }

    private fun loadSettingsAndStartTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            sittingIntervalMinutes = settingsDataStore.sittingIntervalMinutes.first()
            microBreakIntervalMinutes = settingsDataStore.microBreakIntervalMinutes.first()
            isMicroBreakEnabled = settingsDataStore.isMicroBreakEnabled.first()
            isSoundEnabled = settingsDataStore.isSoundEnabled.first()
            isVibrationEnabled = settingsDataStore.isVibrationEnabled.first()
            workStartHour = settingsDataStore.workStartHour.first()
            workEndHour = settingsDataStore.workEndHour.first()
            isWeekendEnabled = settingsDataStore.isWeekendEnabled.first()
            enabledDays = settingsDataStore.enabledDays.first()
            sittingStartTime = settingsDataStore.sittingStartTime.first()
            microBreakStartTime = settingsDataStore.microBreakStartTime.first()
            isWaterReminderEnabled = settingsDataStore.isWaterReminderEnabled.first()
            isEyeReminderEnabled = settingsDataStore.isEyeReminderEnabled.first()

                 if (sittingStartTime <= 0L) {
                waterReminderStartTime = 0L
                eyeReminderStartTime = 0L
                return@launch
            }

            waterReminderStartTime = sittingStartTime
            eyeReminderStartTime = sittingStartTime

            sittingReminderSent = false
            microBreakReminderSent = false
            waterReminderSent = false
            eyeReminderSent = false

            TimerStateHolder.setState(TimerState.Running)

            while (isActive) {
                tick()
                delay(15_000L)
            }
        }
    }

    private fun observeActions() {
        scope.launch {
            actionFlow.collect { action ->
                when (action) {
                    ServiceAction.STAND_UP -> handleStandUp()
                    ServiceAction.SNOOZE -> handleSnooze()
                    ServiceAction.PAUSE_TIMER -> handlePause()
                    ServiceAction.RESUME_TIMER -> handleResume()
                    ServiceAction.STOP_TIMER -> handleStop()
                }
            }
        }
    }

    private suspend fun tick() {
        if (TimerStateHolder.getState() == TimerState.Paused) {
            val pausedElapsed = ((System.currentTimeMillis() - sittingStartTime) / 60_000).toInt()
            updateServiceNotification(pausedElapsed)
            return
        }

        if (!TimeUtils.isInWorkingHours(workStartHour, workEndHour, enabledDays, isWeekendEnabled)) {
            if (TimerStateHolder.getState() == TimerState.Running) {
                pauseTimer()
            }
            updateServiceNotification(0)
            return
        }

        if (TimerStateHolder.getState() != TimerState.Running) {
            resumeTimer()
        }

        val now = System.currentTimeMillis()
        val sittingElapsed = (now - sittingStartTime) / 60_000
        val microBreakElapsed = (now - microBreakStartTime) / 60_000

        updateServiceNotification(sittingElapsed.toInt())

        if (!sittingReminderSent && sittingElapsed >= sittingIntervalMinutes) {
            val delayMinutes = SmartDetector.checkShouldDelay(this)
            if (delayMinutes > 0) {
                sittingStartTime = sittingStartTime + delayMinutes * 60_000L
                settingsDataStore.setSittingStartTime(sittingStartTime)
            } else {
                sittingReminderSent = true
                TimerStateHolder.setState(TimerState.Reminder)
                if (isAppInForeground()) {
                    NotificationHelper.sendSittingReminder(this, sittingElapsed.toInt(), isSoundEnabled, isVibrationEnabled)
                } else {
                    NotificationHelper.sendSittingReminder(this, sittingElapsed.toInt(), isSoundEnabled, isVibrationEnabled)
                    launchReminderActivity(sittingElapsed.toInt())
                }
            }
        }

        if (isMicroBreakEnabled && !microBreakReminderSent && microBreakElapsed >= microBreakIntervalMinutes) {
            microBreakReminderSent = true
            NotificationHelper.sendMicroBreakNotification(this, microBreakElapsed.toInt(), isSoundEnabled, isVibrationEnabled)
        }

        val waterElapsed = (now - waterReminderStartTime) / 60_000
        if (isWaterReminderEnabled && !waterReminderSent && waterElapsed >= 90) {
            waterReminderSent = true
            NotificationHelper.sendWaterReminder(this, isSoundEnabled, isVibrationEnabled)
        }

        val eyeElapsed = (now - eyeReminderStartTime) / 60_000
        if (isEyeReminderEnabled && !eyeReminderSent && eyeElapsed >= 20) {
            eyeReminderSent = true
            NotificationHelper.sendEyeReminder(this, isSoundEnabled, isVibrationEnabled)
        }
    }

    private suspend fun handleStandUp() {
        val now = System.currentTimeMillis()

        val record = CheckInRecord(
            timestamp = now,
            type = "stand_up"
        )
        repository.insert(record)

        TimerStateHolder.setState(TimerState.Completed)

        sittingStartTime = 0L
        microBreakStartTime = 0L
        sittingReminderSent = false
        microBreakReminderSent = false
        settingsDataStore.setSittingStartTime(0L)
        settingsDataStore.setMicroBreakStartTime(0L)

        tickJob?.cancel()
        stopSelf()
    }

    private suspend fun handleSnooze() {
        val snoozeMillis = 5 * 60 * 1000L
        sittingStartTime = sittingStartTime + snoozeMillis
        sittingReminderSent = false
        TimerStateHolder.setState(TimerState.Running)
        settingsDataStore.setSittingStartTime(sittingStartTime)
    }

    private suspend fun handlePause() {
        pauseStartTime = System.currentTimeMillis()
        TimerStateHolder.setState(TimerState.Paused)
    }

    private suspend fun handleResume() {
        if (pauseStartTime > 0L) {
            val pausedDuration = System.currentTimeMillis() - pauseStartTime
            sittingStartTime += pausedDuration
            microBreakStartTime += pausedDuration
            waterReminderStartTime += pausedDuration
            eyeReminderStartTime += pausedDuration
            settingsDataStore.setSittingStartTime(sittingStartTime)
            settingsDataStore.setMicroBreakStartTime(microBreakStartTime)
            pauseStartTime = 0L
        }
        TimerStateHolder.setState(TimerState.Running)
    }

    private suspend fun handleStop() {
        TimerStateHolder.setState(TimerState.Idle)
        tickJob?.cancel()
        sittingStartTime = 0L
        microBreakStartTime = 0L
        pauseStartTime = 0L
        settingsDataStore.setSittingStartTime(0L)
        settingsDataStore.setMicroBreakStartTime(0L)
        stopSelf()
    }

    private suspend fun pauseTimer() {
        sittingStartTime = 0L
        microBreakStartTime = 0L
        settingsDataStore.setSittingStartTime(0L)
        settingsDataStore.setMicroBreakStartTime(0L)
        TimerStateHolder.setState(TimerState.Paused)
    }

    private suspend fun resumeTimer() {
        val now = System.currentTimeMillis()
        sittingStartTime = now
        microBreakStartTime = now
        sittingReminderSent = false
        microBreakReminderSent = false
        settingsDataStore.setSittingStartTime(now)
        settingsDataStore.setMicroBreakStartTime(now)
        TimerStateHolder.setState(TimerState.Running)
    }

    private fun isAppInForeground(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = am.runningAppProcesses ?: return false
        for (process in processes) {
            if (process.processName == packageName &&
                process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            ) {
                return true
            }
        }
        return false
    }

    private fun launchReminderActivity(sittingMinutes: Int) {
        val intent = Intent(this, ReminderActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(ReminderActivity.EXTRA_SITTING_MINUTES, sittingMinutes)
        }
        startActivity(intent)
    }

    private suspend fun updateServiceNotification(elapsedMinutes: Int) {
        val todayStart = TimeUtils.getTodayStartMillis()
        val todayEnd = todayStart + 24 * 60 * 60 * 1000L
        val todayStandCount = try {
            repository.getTodayCount(todayStart, todayEnd)
        } catch (_: Exception) {
            0
        }
        val nextReminder = if (sittingIntervalMinutes > elapsedMinutes) {
            sittingIntervalMinutes - elapsedMinutes
        } else {
            0
        }
        val notification = NotificationHelper.buildServiceNotification(
            context = this,
            elapsedMinutes = elapsedMinutes,
            todayStandCount = todayStandCount,
            nextReminderMinutes = nextReminder,
            timerState = TimerStateHolder.getState(),
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)

        // TODO: 桌面小组件数据更新 - 待实现正确的 Glance 更新 API
        // SitBreakWidget().updateAll(this)
    }

    override fun onDestroy() {
        tickJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.sitbreak.app.ACTION_START"
        const val ACTION_STOP = "com.sitbreak.app.ACTION_STOP"

        private val actionFlow = MutableSharedFlow<ServiceAction>(extraBufferCapacity = 10)

        fun start(context: android.content.Context) {
            val intent = android.content.Intent(context, TimerService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun onStandUp() {
            actionFlow.tryEmit(ServiceAction.STAND_UP)
        }

        fun onSnooze() {
            actionFlow.tryEmit(ServiceAction.SNOOZE)
        }

        fun onPause() {
            actionFlow.tryEmit(ServiceAction.PAUSE_TIMER)
        }

        fun onResume() {
            actionFlow.tryEmit(ServiceAction.RESUME_TIMER)
        }

        fun onStop() {
            actionFlow.tryEmit(ServiceAction.STOP_TIMER)
        }
    }

    private enum class ServiceAction {
        STAND_UP,
        SNOOZE,
        PAUSE_TIMER,
        RESUME_TIMER,
        STOP_TIMER,
    }
}