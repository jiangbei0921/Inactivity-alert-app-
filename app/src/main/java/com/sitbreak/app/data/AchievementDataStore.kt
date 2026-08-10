package com.sitbreak.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.achievementDataStore by preferencesDataStore(name = "achievement_state")

/**
 * 成就/庆祝相关的轻量持久化状态（与用户可配置「设置项」区分开）。
 * 目前仅记录「最后已庆祝的连续天数里程碑」，避免每次打开应用重复弹庆祝。
 */
class AchievementDataStore(private val context: Context) {

    companion object {
        private val KEY_LAST_CELEBRATED_STREAK = intPreferencesKey("last_celebrated_streak")
    }

    val lastCelebratedStreak: Flow<Int> = context.achievementDataStore.data.map { prefs ->
        prefs[KEY_LAST_CELEBRATED_STREAK] ?: 0
    }

    suspend fun setLastCelebratedStreak(value: Int) {
        context.achievementDataStore.edit { it[KEY_LAST_CELEBRATED_STREAK] = value }
    }
}
