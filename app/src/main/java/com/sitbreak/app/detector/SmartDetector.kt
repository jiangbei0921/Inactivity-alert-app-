package com.sitbreak.app.detector

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.telephony.TelephonyManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.sitbreak.app.data.ReminderSettingsDataStore
import java.time.Instant
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
        if (hasRecentSteps(context)) {
            return 0
        }
        return 0
    }

    private fun isDoNotDisturbOn(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE
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

    private fun hasRecentSteps(context: Context): Boolean {
        return try {
            val healthConnectClient = HealthConnectClient.getOrCreate(context)
            val now = Instant.now()
            val fiveMinutesAgo = now.minus(5, java.time.temporal.ChronoUnit.MINUTES)

            val response = healthConnectClient.readRecords(
                ReadRecordsRequest.Builder(
                    StepsRecord::class.java,
                    TimeRangeFilter.between(fiveMinutesAgo, now)
                ).build()
            )

            val records = response.records as List<StepsRecord>
            val totalSteps = records.sumOf { it.count }
            totalSteps >= 50
        } catch (e: Exception) {
            false
        }
    }
}