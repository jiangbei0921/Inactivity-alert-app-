package com.sitbreak.app.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sitbreak.app.data.CheckInRepository
import com.sitbreak.app.data.SettingsDataStore
import com.sitbreak.app.data.db.AppDatabase
import com.sitbreak.app.data.db.CheckInDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CheckInRepository(AppDatabase.getInstance(application).checkInDao())
    private val settingsDataStore = SettingsDataStore(application)

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

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            val interval = settingsDataStore.sittingIntervalMinutes.first()
            val startHour = settingsDataStore.workStartHour.first()
            val endHour = settingsDataStore.workEndHour.first()
            val workingHours = endHour - startHour
            val target = if (workingHours > 0) (workingHours * 60) / interval else 0
            _dailyTarget.value = target

            val sevenDaysAgo = getSevenDaysAgo()
            val counts = checkInDao.getDailyCountsForLast7Days(sevenDaysAgo)

            val barDataList = buildBarDataList(counts, target)
            _dailyCounts.value = barDataList

            val sum = barDataList.sumOf { it.count }
            _weeklyAverage.value = if (barDataList.isNotEmpty()) {
                sum.toFloat() / barDataList.size / target.coerceAtLeast(1)
            } else 0f

            _totalCheckIns.value = checkInDao.getTotalCount()

            _longestStreak.value = calculateLongestStreak(target)
        }
    }

    private suspend fun calculateLongestStreak(target: Int): Int {
        if (target <= 0) return 0
        val allDayCounts = checkInDao.getAllDayCountsByType("stand_up")
        if (allDayCounts.isEmpty()) return 0

        val countMap = allDayCounts.associate { it.dayStart to it.count }

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
            val interval = settingsDataStore.sittingIntervalMinutes.first()
            val startHour = settingsDataStore.workStartHour.first()
            val endHour = settingsDataStore.workEndHour.first()
            val workingHours = endHour - startHour
            val target = if (workingHours > 0) (workingHours * 60) / interval else 0
            _dailyTarget.value = target

            val sevenDaysAgo = getSevenDaysAgo()
            val counts = checkInDao.getDailyCountsForLast7Days(sevenDaysAgo)

            val barDataList = buildBarDataList(counts, target)
            _dailyCounts.value = barDataList

            val sum = barDataList.sumOf { it.count }
            _weeklyAverage.value = if (barDataList.isNotEmpty()) {
                sum.toFloat() / barDataList.size / target.coerceAtLeast(1)
            } else 0f

            _totalCheckIns.value = checkInDao.getTotalCount()

            _longestStreak.value = calculateLongestStreak(target)
        }
    }

    private fun loadMonthlyStats() {
        viewModelScope.launch {
            _totalCheckIns.value = checkInDao.getTotalCount()
        }
    }

    private fun loadYearlyStats() {
        viewModelScope.launch {
            val interval = settingsDataStore.sittingIntervalMinutes.first()
            val startHour = settingsDataStore.workStartHour.first()
            val endHour = settingsDataStore.workEndHour.first()
            val workingHours = endHour - startHour
            val dailyTarget = if (workingHours > 0) (workingHours * 60) / interval else 0

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.MONTH, Calendar.JANUARY)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            val yearStart = calendar.timeInMillis

            val counts = checkInDao.getMonthlyCountsForYear(yearStart)

            val barDataList = mutableListOf<MonthlyBarData>()
            var totalCompleted = 0
            var totalPossible = 0
            var maxCount = 0
            var bestMonth = 1

            for (month in 1..12) {
                val found = counts.find { it.month == month }
                val count = found?.count ?: 0
                val workingDays = if (month in 1..12) 22 else 0
                val target = workingDays * dailyTarget

                barDataList.add(MonthlyBarData("$month", count, target))

                if (count >= target * 0.8f) {
                    totalCompleted++
                }
                totalPossible++

                if (count > maxCount) {
                    maxCount = count
                    bestMonth = month
                }
            }

            _monthlyStandCounts.value = barDataList
            _yearlyCompletionRate.value = if (totalPossible > 0) totalCompleted.toFloat() / totalPossible else 0f
            _bestMonth.value = "$bestMonth 月，共站立 $maxCount 次"
        }
    }

    private fun getYearStart(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.MONTH, Calendar.JANUARY)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}