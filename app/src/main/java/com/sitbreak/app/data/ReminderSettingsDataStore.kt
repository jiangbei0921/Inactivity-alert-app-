package com.sitbreak.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "reminder_settings")

class ReminderSettingsDataStore(private val context: Context) {

    companion object {
        private val KEY_IS_WATER_REMINDER_ENABLED = booleanPreferencesKey("is_water_reminder_enabled")
        private val KEY_IS_EYE_REMINDER_ENABLED = booleanPreferencesKey("is_eye_reminder_enabled")
        private val KEY_FULLSCREEN_BLACKLIST = stringPreferencesKey("fullscreen_blacklist")

        const val DEFAULT_FULLSCREEN_BLACKLIST = "com.tencent.tmgp.sgame,com.tencent.tmgp.pubgmhd,com.tencent.tmgp.cf,com.tencent.tmgp.cod,com.netease.hyxd,com.miHoYo.Yuanshen,com.miHoYo.enterprise.NGHuichao,com.tencent.qqlive,com.youku.phone,com.qiyi.video,com.bilibili.app.in,com.ss.android.ugc.aweme,com.smile.gifmaker,com.kugou.android,com.netease.cloudmusic,com.tencent.qqmusic"
    }

    val isWaterReminderEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_WATER_REMINDER_ENABLED] ?: true
    }

    val isEyeReminderEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_EYE_REMINDER_ENABLED] ?: true
    }

    val fullscreenBlacklist: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_FULLSCREEN_BLACKLIST] ?: DEFAULT_FULLSCREEN_BLACKLIST
        raw.split(",").filter { it.isNotBlank() }.toSet()
    }

    suspend fun setWaterReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_IS_WATER_REMINDER_ENABLED] = enabled }
    }

    suspend fun setEyeReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_IS_EYE_REMINDER_ENABLED] = enabled }
    }

    suspend fun setFullscreenBlacklist(packages: Set<String>) {
        context.dataStore.edit { it[KEY_FULLSCREEN_BLACKLIST] = packages.joinToString(",") }
    }
}