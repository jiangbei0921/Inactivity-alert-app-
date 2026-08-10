package com.sitbreak.app.ui.achievements

import com.sitbreak.app.data.CheckInRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val repository: CheckInRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow<List<AchievementUi>>(emptyList())
    val ui: StateFlow<List<AchievementUi>> = _ui.asStateFlow()

    private val _unlockedCount = MutableStateFlow(0)
    val unlockedCount: StateFlow<Int> = _unlockedCount.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            val total = repository.getTotalCount()
            val (startOfDay, endOfDay) = todayRange()
            val todayCount = repository.getTodayStandCount(startOfDay, endOfDay)
            val streak = computeStreak(repository.getAllDistinctDays())
            val list = computeAchievements(total, streak, todayCount)
            _ui.value = list
            _unlockedCount.value = list.count { it.unlocked }
        }
    }

    private fun todayRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return startOfDay to calendar.timeInMillis
    }

    /** 连续天数：以今天（若无记录则昨天）为终点向前数连续有记录的日数。 */
    private fun computeStreak(days: List<Long>): Int {
        if (days.isEmpty()) return 0
        val set = days.toSet()
        val today = todayRange().first
        val end = if (set.contains(today)) today
        else if (set.contains(today - 86400000L)) today - 86400000L
        else return 0
        var streak = 0
        var d = end
        while (set.contains(d)) {
            streak++
            d -= 86400000L
        }
        return streak
    }
}
