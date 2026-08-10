package com.sitbreak.app.ui.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.sitbreak.app.data.CheckInRepository
import com.sitbreak.app.data.SettingsDataStore
import com.sitbreak.app.data.db.CheckInDao
import com.sitbreak.app.data.db.CheckInRecord
import com.sitbreak.app.service.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

@HiltViewModel
class StatsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: CheckInRepository,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _weeklyAverage = MutableStateFlow(0f)
    val weeklyAverage: StateFlow<Float> = _weeklyAverage.asStateFlow()

    private val _dailyCounts = MutableStateFlow<List<DailyBarData>>(emptyList())
    val dailyCounts: StateFlow<List<DailyBarData>> = _dailyCounts.asStateFlow()

    private val _totalCheckIns = MutableStateFlow(0)
    val totalCheckIns: StateFlow<Int> = _totalCheckIns.asStateFlow()

    private val _longestStreak = MutableStateFlow(0)
    val longestStreak: StateFlow<Int> = _longestStreak.asStateFlow()

    private val _dailyTarget = MutableStateFlow(0)
    val dailyTarget: StateFlow<Int> = _dailyTarget.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _yearlyCompletionRate = MutableStateFlow(0f)
    val yearlyCompletionRate: StateFlow<Float> = _yearlyCompletionRate.asStateFlow()

    private val _monthlyStandCounts = MutableStateFlow<List<MonthlyBarData>>(emptyList())
    val monthlyStandCounts: StateFlow<List<MonthlyBarData>> = _monthlyStandCounts.asStateFlow()

    private val _bestMonth = MutableStateFlow("")
    val bestMonth: StateFlow<String> = _bestMonth.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadWeeklyStats()
    }

    private suspend fun calculateLongestStreak(target: Int): Int {
        if (target <= 0) return 0
        val tzOffset = TimeUtils.getLocalTimezoneOffset()
        val allDayCounts = repository.getAllDayCountsByType(CheckInRecord.TYPE_STAND_UP, tzOffset)
        if (allDayCounts.isEmpty()) return 0

        var maxStreak = 0
        var currentStreak = 0
        var previousDayStart = 0L

        for (dayCount in allDayCounts) {
            val completed = dayCount.count.toFloat() / target >= 0.8f

            if (previousDayStart == 0L) {
                currentStreak = if (completed) 1 else 0
            } else {
                val dayDiff = (dayCount.dayStart - previousDayStart) / 86400000L
                if (dayDiff == 1L && completed) {
                    currentStreak++
                } else if (completed) {
                    currentStreak = 1
                } else {
                    currentStreak = 0
                }
            }
            previousDayStart = dayCount.dayStart
            if (currentStreak > maxStreak) maxStreak = currentStreak
        }
        return maxStreak
    }

    private fun buildBarDataList(
        counts: List<CheckInDao.DailyCount>,
        target: Int
    ): List<DailyBarData> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val countMap = counts.associate { it.dayStart to it.count }
        val result = mutableListOf<DailyBarData>()

        for (i in 6 downTo 0) {
            val dayCalendar = calendar.clone() as Calendar
            dayCalendar.add(Calendar.DAY_OF_MONTH, -i)
            val dayStart = dayCalendar.timeInMillis
            val count = countMap[dayStart] ?: 0
            val dayOfWeek = when (dayCalendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "一"
                Calendar.TUESDAY -> "二"
                Calendar.WEDNESDAY -> "三"
                Calendar.THURSDAY -> "四"
                Calendar.FRIDAY -> "五"
                Calendar.SATURDAY -> "六"
                Calendar.SUNDAY -> "日"
                else -> ""
            }
            result.add(DailyBarData(dayOfWeek, count, target))
        }
        return result
    }

    private fun getSevenDaysAgo(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.DAY_OF_MONTH, -6)
        return calendar.timeInMillis
    }

    data class DailyBarData(
        val dayLabel: String,
        val count: Int,
        val target: Int
    )

    data class MonthlyBarData(
        val monthLabel: String,
        val count: Int,
        val target: Int,
    )

    fun selectTab(index: Int) {
        _selectedTab.value = index
        when (index) {
            0 -> loadWeeklyStats()
            1 -> loadMonthlyStats()
            2 -> loadYearlyStats()
        }
    }

    private fun loadWeeklyStats() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val interval = settingsDataStore.sittingIntervalMinutes.first()
                val startHour = settingsDataStore.workStartHour.first()
                val endHour = settingsDataStore.workEndHour.first()
                val workingHours = endHour - startHour
                val target = if (workingHours > 0) (workingHours * 60) / interval else 0
                _dailyTarget.value = target

                val tzOffset = TimeUtils.getLocalTimezoneOffset()
                val sevenDaysAgo = getSevenDaysAgo()
                val counts = repository.getDailyCountsForLast7Days(sevenDaysAgo, tzOffset)

                val barDataList = buildBarDataList(counts, target)
                _dailyCounts.value = barDataList

                val sum = barDataList.sumOf { it.count }
                _weeklyAverage.value = if (barDataList.isNotEmpty()) {
                    sum.toFloat() / barDataList.size / target.coerceAtLeast(1)
                } else 0f

                _totalCheckIns.value = repository.getTotalCount()

                _longestStreak.value = calculateLongestStreak(target)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadMonthlyStats() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _totalCheckIns.value = repository.getTotalCount()
                // 「本月」页与「本年」页共用 12 个月柱状数据，避免该页只显示两张统计卡而无图表。
                _monthlyStandCounts.value = computeMonthlyStandCounts(dailyTarget())
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadYearlyStats() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val barDataList = computeMonthlyStandCounts(dailyTarget())

                var totalCompleted = 0
                var maxCount = 0
                var bestMonth = 1
                barDataList.forEach { item ->
                    if (item.target > 0 && item.count >= item.target * 0.8f) totalCompleted++
                    if (item.count > maxCount) {
                        maxCount = item.count
                        bestMonth = item.monthLabel.toIntOrNull() ?: 1
                    }
                }

                _monthlyStandCounts.value = barDataList
                _yearlyCompletionRate.value = if (barDataList.isNotEmpty()) totalCompleted.toFloat() / barDataList.size else 0f
                _bestMonth.value = "$bestMonth 月，共站立 $maxCount 次"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 计算本年 1–12 月每月站立次数（月度统计图表数据），「本月」「本年」两页共用。 */
    private suspend fun computeMonthlyStandCounts(dailyTarget: Int): List<MonthlyBarData> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val yearStart = calendar.timeInMillis
        val tzOffset = TimeUtils.getLocalTimezoneOffset()
        val counts = repository.getMonthlyCountsForYear(yearStart, tzOffset)

        val workingDays = 22
        return (1..12).map { month ->
            val count = counts.find { it.month == month }?.count ?: 0
            MonthlyBarData("$month", count, workingDays * dailyTarget)
        }
    }

    /** 由工作时段与提醒间隔推算「每日目标站立次数」。 */
    private suspend fun dailyTarget(): Int {
        val interval = settingsDataStore.sittingIntervalMinutes.first()
        val startHour = settingsDataStore.workStartHour.first()
        val endHour = settingsDataStore.workEndHour.first()
        val workingHours = endHour - startHour
        return if (workingHours > 0) (workingHours * 60) / interval else 0
    }
}