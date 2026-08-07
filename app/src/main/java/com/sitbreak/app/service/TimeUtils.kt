package com.sitbreak.app.service

import java.util.Calendar

object TimeUtils {

    fun getTodayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun isDayEnabled(enabledDays: Set<String>, isWeekendEnabled: Boolean): Boolean {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            if (!isWeekendEnabled) return false
        }
        val dayKey = when (dayOfWeek) {
            Calendar.MONDAY -> "MON"
            Calendar.TUESDAY -> "TUE"
            Calendar.WEDNESDAY -> "WED"
            Calendar.THURSDAY -> "THU"
            Calendar.FRIDAY -> "FRI"
            Calendar.SATURDAY -> "SAT"
            Calendar.SUNDAY -> "SUN"
            else -> "MON"
        }
        return dayKey in enabledDays
    }

    fun isInWorkingHours(
        workStartHour: Int,
        workEndHour: Int,
        enabledDays: Set<String>,
        isWeekendEnabled: Boolean
    ): Boolean {
        if (!isDayEnabled(enabledDays, isWeekendEnabled)) {
            return false
        }
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        return currentHour in workStartHour until workEndHour
    }
}