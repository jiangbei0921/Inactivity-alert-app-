package com.sitbreak.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sitbreak.app.MainActivity
import com.sitbreak.app.TimerState
import com.sitbreak.app.TimerStateHolder
import com.sitbreak.app.data.CheckInRepository
import com.sitbreak.app.data.SettingsDataStore
import com.sitbreak.app.data.db.AppDatabase
import com.sitbreak.app.data.db.CheckInRecord
import com.sitbreak.app.detector.SmartDetector
import com.sitbreak.app.health.StandingValidator
import com.sitbreak.app.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tickJob: Job? = null
    private var foregroundStarted = false
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
    private var waterReminderStartTime: Long = 0L
    private var eyeReminderStartTime: Long = 0L
    private var isWaterReminderEnabled: Boolean = true
    private var isEyeReminderEnabled: Boolean = true
    private var pauseStartTime: Long = 0L
    private var pausedElapsedMinutes: Int = 0
    private var autoPaused: Boolean = false
    private var cachedStandCount: Int = 0

    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var repository: CheckInRepository

    override fun onCreate() {
        super.onCreate()
        settingsDataStore = SettingsDataStore(this)
        repository = CheckInRepository(AppDatabase.getInstance(this).checkInDao())
        scope.launch { NotificationHelper.createChannels(this@TimerService) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimer()
            NotificationHelper.ACTION_STAND_UP -> dispatch { handleStandUp() }
            NotificationHelper.ACTION_SNOOZE -> dispatch { handleSnooze() }
            NotificationHelper.ACTION_PAUSE_TIMER -> dispatch { handlePause() }
            NotificationHelper.ACTION_RESUME_TIMER -> dispatch { handleResume() }
            NotificationHelper.ACTION_STOP_TIMER -> dispatch { handleStop() }
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
            .setSmallIcon(android.R.drawable.ic_dialog_info)
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
        isSoundEnabled = settingsDataStore.isSoundEnabled.first()
        isVibrationEnabled = settingsDataStore.isVibrationEnabled.first()
        workStartHour = settingsDataStore.workStartHour.first()
        workEndHour = settingsDataStore.workEndHour.first()
        isWeekendEnabled = settingsDataStore.isWeekendEnabled.first()
        enabledDays = settingsDataStore.enabledDays.first()
        isWaterReminderEnabled = settingsDataStore.isWaterReminderEnabled.first()
        isEyeReminderEnabled = settingsDataStore.isEyeReminderEnabled.first()
    }

    private suspend fun tick() {
        refreshSettings()

        if (TimerStateHolder.getState() == TimerState.Paused) {
            updateServiceNotification(pausedElapsedMinutes)
            return
        }

        if (!TimeUtils.isInWorkingHours(workStartHour, workEndHour, enabledDays, isWeekendEnabled)) {
            if (TimerStateHolder.getState() == TimerState.Running) {
                autoPaused = true
                pauseTimer()
            }
            updateServiceNotification(pausedElapsedMinutes)
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
                NotificationHelper.sendSittingReminder(this, sittingElapsed.toInt(), isSoundEnabled, isVibrationEnabled)
            }
        }

        if (isMicroBreakEnabled && !microBreakReminderSent && microBreakElapsed >= microBreakIntervalMinutes) {
            microBreakReminderSent = true
            NotificationHelper.sendMicroBreakNotification(this, microBreakElapsed.toInt(), isSoundEnabled, isVibrationEnabled)
        }

        val waterElapsed = (now - waterReminderStartTime) / 60_000
        if (isWaterReminderEnabled && waterElapsed >= WATER_REMINDER_INTERVAL_MIN) {
            NotificationHelper.sendWaterReminder(this, isSoundEnabled, isVibrationEnabled)
            waterReminderStartTime = now
        }

        val eyeElapsed = (now - eyeReminderStartTime) / 60_000
        if (isEyeReminderEnabled && eyeElapsed >= EYE_REMINDER_INTERVAL_MIN) {
            NotificationHelper.sendEyeReminder(this, isSoundEnabled, isVibrationEnabled)
            eyeReminderStartTime = now
        }
    }

    private suspend fun handleStandUp() {
        val now = System.currentTimeMillis()

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
        microBreakReminderSent = false
        autoPaused = false
        pauseStartTime = 0L
        settingsDataStore.setSittingStartTime(0L)
        settingsDataStore.setMicroBreakStartTime(0L)

        cancelReminderNotifications()
        refreshStandCount()

        tickJob?.cancel()
        stopSelf()
    }

    private suspend fun handleSnooze() {
        sittingStartTime = sittingStartTime + SNOOZE_DURATION_MS
        sittingReminderSent = false
        TimerStateHolder.setState(TimerState.Running)
        settingsDataStore.setSittingStartTime(sittingStartTime)
    }

    private suspend fun handlePause() {
        pausedElapsedMinutes = ((System.currentTimeMillis() - sittingStartTime) / 60_000).toInt().coerceAtLeast(0)
        pauseStartTime = System.currentTimeMillis()
        autoPaused = false
        TimerStateHolder.setState(TimerState.Paused)
    }

    private suspend fun handleResume() {
        val now = System.currentTimeMillis()
        if (autoPaused) {
            sittingStartTime = now
            microBreakStartTime = now
            waterReminderStartTime = now
            eyeReminderStartTime = now
        } else if (pauseStartTime > 0L) {
            val pausedDuration = now - pauseStartTime
            sittingStartTime += pausedDuration
            microBreakStartTime += pausedDuration
            waterReminderStartTime += pausedDuration
            eyeReminderStartTime += pausedDuration
        }
        settingsDataStore.setSittingStartTime(sittingStartTime)
        settingsDataStore.setMicroBreakStartTime(microBreakStartTime)
        autoPaused = false
        pauseStartTime = 0L
        TimerStateHolder.setState(TimerState.Running)
    }

    private suspend fun handleStop() {
        TimerStateHolder.setState(TimerState.Idle)
        tickJob?.cancel()
        sittingStartTime = 0L
        microBreakStartTime = 0L
        pauseStartTime = 0L
        autoPaused = false
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
        if (autoPaused) {
            sittingStartTime = now
            microBreakStartTime = now
            waterReminderStartTime = now
            eyeReminderStartTime = now
        } else if (pauseStartTime > 0L) {
            val pausedDuration = now - pauseStartTime
            sittingStartTime += pausedDuration
            microBreakStartTime += pausedDuration
            waterReminderStartTime += pausedDuration
            eyeReminderStartTime += pausedDuration
        }
        settingsDataStore.setSittingStartTime(sittingStartTime)
        settingsDataStore.setMicroBreakStartTime(microBreakStartTime)
        autoPaused = false
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

    private suspend fun updateServiceNotification(elapsedMinutes: Int) {
        val todayStandCount = cachedStandCount
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
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)

    }

    override fun onDestroy() {
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
