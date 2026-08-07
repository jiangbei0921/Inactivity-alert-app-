package com.sitbreak.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "notification_settings")

class NotificationSettingsDataStore(private val context: Context) {

    companion object {
        private val KEY_IS_VIBRATION_ENABLED = booleanPreferencesKey("is_vibration_enabled")
        private val KEY_IS_SOUND_ENABLED = booleanPreferencesKey("is_sound_enabled")
        private val KEY_NOTIFICATION_SOUND_INDEX = intPreferencesKey("notification_sound_index")
        private val KEY_NOTIFICATION_SOUND_URI = stringPreferencesKey("notification_sound_uri")
        private val KEY_REMINDER_STYLE = stringPreferencesKey("reminder_style")
    }

    val isSoundEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_SOUND_ENABLED] ?: true
    }

    val isVibrationEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_VIBRATION_ENABLED] ?: true
    }

    val notificationSoundIndex: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIFICATION_SOUND_INDEX] ?: 0
    }

    val notificationSoundUri: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIFICATION_SOUND_URI] ?: ""
    }

    val reminderStyle: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_REMINDER_STYLE] ?: "health_care"
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_IS_SOUND_ENABLED] = enabled }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_IS_VIBRATION_ENABLED] = enabled }
    }

    suspend fun setNotificationSoundIndex(index: Int) {
        context.dataStore.edit { it[KEY_NOTIFICATION_SOUND_INDEX] = index }
    }

    suspend fun setNotificationSoundUri(uri: String) {
        context.dataStore.edit { it[KEY_NOTIFICATION_SOUND_URI] = uri }
    }

    suspend fun setReminderStyle(style: String) {
        context.dataStore.edit { it[KEY_REMINDER_STYLE] = style }
    }
}