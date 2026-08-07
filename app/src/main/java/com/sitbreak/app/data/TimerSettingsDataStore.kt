package com.sitbreak.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "timer_settings")

class TimerSettingsDataStore(private val context: Context) {

    companion object {
        private val KEY_SITTING_INTERVAL = intPreferencesKey("sitting_interval_minutes")
        private val KEY_MICRO_BREAK_INTERVAL = intPreferencesKey("micro_break_interval_minutes")
        private val KEY_WORK_START_HOUR = intPreferencesKey("work_start_hour")
        private val KEY_WORK_END_HOUR = intPreferencesKey("work_end_hour")
        private val KEY_IS_WEEKEND_ENABLED = booleanPreferencesKey("is_weekend_enabled")
        private val KEY_IS_MICRO_BREAK_ENABLED = booleanPreferencesKey("is_micro_break_enabled")
        private val KEY_ENABLED_DAYS = stringPreferencesKey("enabled_days")
        private val KEY_SITTING_START_TIME = longPreferencesKey("sitting_start_time")
        private val KEY_MICRO_BREAK_START_TIME = longPreferencesKey("micro_break_start_time")

        const val DEFAULT_SITTING_INTERVAL = 45
        const val DEFAULT_MICRO_BREAK_INTERVAL = 20
        const val DEFAULT_WORK_START_HOUR = 9
        const val DEFAULT_WORK_END_HOUR = 18
    }

    val sittingIntervalMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_SITTING_INTERVAL] ?: DEFAULT_SITTING_INTERVAL
    }

    val microBreakIntervalMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_MICRO_BREAK_INTERVAL] ?: DEFAULT_MICRO_BREAK_INTERVAL
    }

    val workStartHour: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_WORK_START_HOUR] ?: DEFAULT_WORK_START_HOUR
    }

    val workEndHour: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_WORK_END_HOUR] ?: DEFAULT_WORK_END_HOUR
    }

    val isWeekendEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_WEEKEND_ENABLED] ?: false
    }

    val isMicroBreakEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_MICRO_BREAK_ENABLED] ?: true
    }

    val enabledDays: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_ENABLED_DAYS] ?: "MON,TUE,WED,THU,FRI"
        raw.split(",").filter { it.isNotBlank() }.toSet()
    }

    val sittingStartTime: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_SITTING_START_TIME] ?: 0L
    }

    val microBreakStartTime: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_MICRO_BREAK_START_TIME] ?: 0L
    }

    suspend fun setSittingInterval(minutes: Int) {
        context.dataStore.edit { it[KEY_SITTING_INTERVAL] = minutes }
    }

    suspend fun setMicroBreakInterval(minutes: Int) {
        context.dataStore.edit { it[KEY_MICRO_BREAK_INTERVAL] = minutes }
    }

    suspend fun setWorkStartHour(hour: Int) {
        context.dataStore.edit { it[KEY_WORK_START_HOUR] = hour }
    }

    suspend fun setWorkEndHour(hour: Int) {
        context.dataStore.edit { it[KEY_WORK_END_HOUR] = hour }
    }

    suspend fun setWeekendEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_IS_WEEKEND_ENABLED] = enabled }
    }

    suspend fun setMicroBreakEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_IS_MICRO_BREAK_ENABLED] = enabled }
    }

    suspend fun setEnabledDays(days: Set<String>) {
        context.dataStore.edit { it[KEY_ENABLED_DAYS] = days.joinToString(",") }
    }

    suspend fun setSittingStartTime(time: Long) {
        context.dataStore.edit { it[KEY_SITTING_START_TIME] = time }
    }

    suspend fun setMicroBreakStartTime(time: Long) {
        context.dataStore.edit { it[KEY_MICRO_BREAK_START_TIME] = time }
    }
}