package com.sitbreak.app.detector

import android.app.NotificationManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.telephony.TelephonyManager
import com.sitbreak.app.data.ReminderSettingsDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SmartDetector @Inject constructor() {

    /**
     * 判断本次提醒是否应被推迟（返回推迟分钟数，0 表示不推迟）。
     *
     * 设备适配：本方法在计时 tick 循环中被调用，部分国产 ROM（如某些 vivo/OPPO/华为）
     * 对 [UsageStatsManager] 的查询可能抛出 SecurityException / RemoteException 等异常，
     * 此处整体兜底——任何异常都视为「不抑制」，保证提醒照常触发，绝不因检测失败而崩溃。
     */
    suspend fun checkShouldDelay(context: Context): Int {
        return try {
            if (isDoNotDisturbOn(context)) {
                return 10
            }
            if (isInCall(context)) {
                return 10
            }
            if (isFullScreenApp(context)) {
                return 5
            }
            0
        } catch (e: Exception) {
            // 检测失败不应影响正常提醒：安全降级为不抑制。
            0
        }
    }

    private fun isDoNotDisturbOn(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }

    @Suppress("DEPRECATION")
    private fun isInCall(context: Context): Boolean {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.callState != TelephonyManager.CALL_STATE_IDLE
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * 判断是否处于黑名单内的全屏应用前台。
     * API 22+ 上 [ActivityManager.getRunningAppProcesses] 仅返回自身进程，无法获取其他应用，
     * 因此改用 [UsageStatsManager] 查询最近前台应用；若用户未授予「使用情况访问」权限
     * （queryUsageStats 返回空），则安全降级为不抑制提醒。
     */
    private suspend fun isFullScreenApp(context: Context): Boolean {
        val blacklist = ReminderSettingsDataStore(context).fullscreenBlacklist.first()
        if (blacklist.isEmpty()) return false

        val topPackage = getForegroundPackageName(context) ?: return false
        return topPackage in blacklist
    }

    private fun getForegroundPackageName(context: Context): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        return try {
            val now = System.currentTimeMillis()
            val stats: List<UsageStats> = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 10_000L,
                now
            )
            if (stats.isEmpty()) return null
            stats.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            // 部分 ROM 在未授权或受限时会抛异常，安全降级为无法判断前台应用。
            null
        }
    }
}