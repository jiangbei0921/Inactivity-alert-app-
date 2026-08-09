package com.sitbreak.app.update

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.updateDataStore by preferencesDataStore(name = "app_update")

/**
 * 更新功能自身的偏好。刻意与用户「设置」分开存放，
 * 避免设置重置/备份时把「我不想升到 42 版」这类临时决定一起带走。
 */
@Singleton
class UpdatePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_AUTO_CHECK = booleanPreferencesKey("auto_check_enabled")
        private val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only")
        private val KEY_LAST_CHECKED_AT = longPreferencesKey("last_checked_at")
        private val KEY_IGNORED_VERSION = intPreferencesKey("ignored_version_code")
    }

    /** 是否允许后台每日静默检查。默认开——用户装了才有更新可拿。 */
    val autoCheckEnabled: Flow<Boolean> =
        context.updateDataStore.data.map { it[KEY_AUTO_CHECK] ?: true }

    /** 仅在 Wi-Fi 下自动下载。默认开，避免偷跑流量；手动点更新时不受此限制。 */
    val wifiOnly: Flow<Boolean> =
        context.updateDataStore.data.map { it[KEY_WIFI_ONLY] ?: true }

    val lastCheckedAt: Flow<Long> =
        context.updateDataStore.data.map { it[KEY_LAST_CHECKED_AT] ?: 0L }

    /** 用户主动跳过的版本号，后台检查到该版本时不再打扰。 */
    val ignoredVersionCode: Flow<Int> =
        context.updateDataStore.data.map { it[KEY_IGNORED_VERSION] ?: 0 }

    suspend fun setAutoCheckEnabled(enabled: Boolean) {
        context.updateDataStore.edit { it[KEY_AUTO_CHECK] = enabled }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.updateDataStore.edit { it[KEY_WIFI_ONLY] = enabled }
    }

    suspend fun markChecked(timestamp: Long = System.currentTimeMillis()) {
        context.updateDataStore.edit { it[KEY_LAST_CHECKED_AT] = timestamp }
    }

    suspend fun ignoreVersion(versionCode: Int) {
        context.updateDataStore.edit { it[KEY_IGNORED_VERSION] = versionCode }
    }

    suspend fun isIgnored(versionCode: Int): Boolean =
        ignoredVersionCode.first() == versionCode
}
