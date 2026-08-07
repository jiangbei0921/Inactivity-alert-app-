package com.sitbreak.app.service

import java.util.Calendar
import java.util.TimeZone

object TimeUtils {

    /**
     * 设备当前时区相对于 UTC 的偏移量（毫秒，含夏令时）。
     * 用于把 UTC 时间戳按"本地自然日/月"分桶，避免东八区等场景下统计错位。
     */
    fun getLocalTimezoneOffset(): Long {
        return TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
    }

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
        return if (workEndHour <= workStartHour) {
            // 跨夜班次（如 22:00 ~ 06:00）
            currentHour >= workStartHour || currentHour < workEndHour
        } else {
            currentHour in workStartHour until workEndHour
        }
    }
}