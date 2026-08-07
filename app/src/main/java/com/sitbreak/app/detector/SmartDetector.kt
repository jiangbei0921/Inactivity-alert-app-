package com.sitbreak.app.detector

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.telephony.TelephonyManager
import com.sitbreak.app.data.ReminderSettingsDataStore
import kotlinx.coroutines.flow.first

object SmartDetector {

    suspend fun checkShouldDelay(context: Context): Int {
        if (isDoNotDisturbOn(context)) {
            return 10
        }
        if (isInCall(context)) {
            return 10
        }
        if (isFullScreenApp(context)) {
            return 5
        }
        return 0
    }

    private fun isDoNotDisturbOn(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }

    private fun isInCall(context: Context): Boolean {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.callState != TelephonyManager.CALL_STATE_IDLE
        } catch (e: SecurityException) {
            false
        }
    }

    private suspend fun isFullScreenApp(context: Context): Boolean {
        val blacklist = ReminderSettingsDataStore(context).fullscreenBlacklist.first()
        if (blacklist.isEmpty()) return false

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val topProcess = am.runningAppProcesses?.firstOrNull {
            it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } ?: return false

        val topPackageName = topProcess.processName.split(":").first()
        return topPackageName in blacklist
    }
}