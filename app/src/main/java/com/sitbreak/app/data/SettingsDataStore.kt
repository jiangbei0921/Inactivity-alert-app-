package com.sitbreak.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class SettingsDataStore(private val context: Context) {

    val timer = TimerSettingsDataStore(context)
    val notification = NotificationSettingsDataStore(context)
    val reminder = ReminderSettingsDataStore(context)
    val appState = AppStateDataStore(context)
    val achievements = AchievementDataStore(context)

    val sittingIntervalMinutes: Flow<Int> get() = timer.sittingIntervalMinutes
    val microBreakIntervalMinutes: Flow<Int> get() = timer.microBreakIntervalMinutes
    val workStartHour: Flow<Int> get() = timer.workStartHour
    val workEndHour: Flow<Int> get() = timer.workEndHour
    val isWeekendEnabled: Flow<Boolean> get() = timer.isWeekendEnabled
    val isMicroBreakEnabled: Flow<Boolean> get() = timer.isMicroBreakEnabled
    val enabledDays: Flow<Set<String>> get() = timer.enabledDays
    val sittingStartTime: Flow<Long> get() = timer.sittingStartTime
    val microBreakStartTime: Flow<Long> get() = timer.microBreakStartTime

    val isSoundEnabled: Flow<Boolean> get() = notification.isSoundEnabled
    val isVibrationEnabled: Flow<Boolean> get() = notification.isVibrationEnabled
    val notificationSoundIndex: Flow<Int> get() = notification.notificationSoundIndex
    val notificationSoundUri: Flow<String> get() = notification.notificationSoundUri
    val reminderStyle: Flow<String> get() = notification.reminderStyle

    val isWaterReminderEnabled: Flow<Boolean> get() = reminder.isWaterReminderEnabled
    val isEyeReminderEnabled: Flow<Boolean> get() = reminder.isEyeReminderEnabled
    val fullscreenBlacklist: Flow<Set<String>> get() = reminder.fullscreenBlacklist

    val onboardingCompleted: Flow<Boolean> get() = appState.onboardingCompleted

    val lastCelebratedStreak: Flow<Int> get() = achievements.lastCelebratedStreak

    suspend fun setSittingInterval(minutes: Int) = timer.setSittingInterval(minutes)
    suspend fun setMicroBreakInterval(minutes: Int) = timer.setMicroBreakInterval(minutes)
    suspend fun setWorkStartHour(hour: Int) = timer.setWorkStartHour(hour)
    suspend fun setWorkEndHour(hour: Int) = timer.setWorkEndHour(hour)
    suspend fun setWeekendEnabled(enabled: Boolean) = timer.setWeekendEnabled(enabled)
    suspend fun setMicroBreakEnabled(enabled: Boolean) = timer.setMicroBreakEnabled(enabled)
    suspend fun setEnabledDays(days: Set<String>) = timer.setEnabledDays(days)
    suspend fun setSittingStartTime(time: Long) = timer.setSittingStartTime(time)
    suspend fun setMicroBreakStartTime(time: Long) = timer.setMicroBreakStartTime(time)

    suspend fun setSoundEnabled(enabled: Boolean) = notification.setSoundEnabled(enabled)
    suspend fun setVibrationEnabled(enabled: Boolean) = notification.setVibrationEnabled(enabled)
    suspend fun setNotificationSoundIndex(index: Int) = notification.setNotificationSoundIndex(index)
    suspend fun setNotificationSoundUri(uri: String) = notification.setNotificationSoundUri(uri)
    suspend fun setReminderStyle(style: String) = notification.setReminderStyle(style)

    suspend fun setWaterReminderEnabled(enabled: Boolean) = reminder.setWaterReminderEnabled(enabled)
    suspend fun setEyeReminderEnabled(enabled: Boolean) = reminder.setEyeReminderEnabled(enabled)
    suspend fun setFullscreenBlacklist(packages: Set<String>) = reminder.setFullscreenBlacklist(packages)

    suspend fun setOnboardingCompleted(completed: Boolean) = appState.setOnboardingCompleted(completed)
    suspend fun setLastCelebratedStreak(value: Int) = achievements.setLastCelebratedStreak(value)
}