package com.sitbreak.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appStateDataStore by preferencesDataStore(name = "app_state")

/**
 * 应用级一次性状态（与用户可配置的「设置项」区分开，避免污染设置备份/重置语义）。
 */
class AppStateDataStore(private val context: Context) {

    companion object {
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    /** 是否已完成首启引导。默认 false，即全新安装用户需要看引导。 */
    val onboardingCompleted: Flow<Boolean> = context.appStateDataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.appStateDataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }
}
