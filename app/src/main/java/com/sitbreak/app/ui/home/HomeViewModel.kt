package com.sitbreak.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.sitbreak.app.TimerState
import com.sitbreak.app.TimerStateHolder
import com.sitbreak.app.data.CheckInRepository
import com.sitbreak.app.data.SettingsDataStore
import com.sitbreak.app.data.db.CheckInRecord
import com.sitbreak.app.service.TimerService
import com.sitbreak.app.ui.achievements.STREAK_MILESTONES
import com.sitbreak.app.health.StandingValidator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsDataStore: SettingsDataStore,
    private val repository: CheckInRepository,
    private val standingValidator: StandingValidator,
) : ViewModel() {

    val timerState: StateFlow<TimerState> = TimerStateHolder.state

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _sittingIntervalMinutes = MutableStateFlow(45)
    val sittingIntervalMinutes: StateFlow<Int> = _sittingIntervalMinutes.asStateFlow()

    private val _targetSeconds = MutableStateFlow(60)
    val targetSeconds: StateFlow<Int> = _targetSeconds.asStateFlow()

    private val _todayStandCount = MutableStateFlow(0)
    val todayStandCount: StateFlow<Int> = _todayStandCount.asStateFlow()

    private val _todayCompletionRate = MutableStateFlow(0f)
    val todayCompletionRate: StateFlow<Float> = _todayCompletionRate.asStateFlow()

    private val _todayActiveHours = MutableStateFlow(0f)
    val todayActiveHours: StateFlow<Float> = _todayActiveHours.asStateFlow()

    private val _todayRecords = MutableStateFlow<List<CheckInRecord>>(emptyList())
    val todayRecords: StateFlow<List<CheckInRecord>> = _todayRecords.asStateFlow()

    private val _currentStreak = MutableStateFlow(0)
    val currentStreak: StateFlow<Int> = _currentStreak.asStateFlow()

    /** 当前需要弹出的「连续天数庆祝」里程碑值；0 表示无需庆祝。 */
    private val _celebrationStreak = MutableStateFlow(0)
    val celebrationStreak: StateFlow<Int> = _celebrationStreak.asStateFlow()

    private val _lastStandVerified = MutableStateFlow<Boolean?>(null)
    val lastStandVerified: StateFlow<Boolean?> = _lastStandVerified.asStateFlow()

    init {
        observeSettingsAndRefresh()
        startElapsedTimer()
        restoreTimerState()
    }

    private fun restoreTimerState() {
        viewModelScope.launch {
            val startTime = settingsDataStore.sittingStartTime.first()
            if (startTime > 0L) {
                TimerStateHolder.setState(TimerState.Running)
                TimerService.start(appContext)
            }
        }
    }

    private fun startElapsedTimer() {
        viewModelScope.launch {
            var cachedStartTime = 0L
            while (true) {
                if (TimerStateHolder.state.value == TimerState.Running) {
                    if (cachedStartTime == 0L) {
                        cachedStartTime = settingsDataStore.sittingStartTime.first()
                    }
                    if (cachedStartTime > 0L) {
                        val elapsed = maxOf(0L, System.currentTimeMillis() - cachedStartTime) / 1000
                        _elapsedSeconds.value = elapsed.toInt()
                    }
                } else {
                    cachedStartTime = 0L
                }
                delay(1000L)
            }
        }
    }

    private fun observeSettingsAndRefresh() {
        viewModelScope.launch {
            combine(
                settingsDataStore.sittingIntervalMinutes,
                settingsDataStore.workStartHour,
                settingsDataStore.workEndHour
            ) { interval, startHour, endHour ->
                Triple(interval, startHour, endHour)
            }.collect { (interval, startHour, endHour) ->
                _sittingIntervalMinutes.value = interval
                _targetSeconds.value = interval * 60
                refreshTodayStats(interval, startHour, endHour)
            }
        }
    }

    fun startTimer() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            settingsDataStore.setSittingStartTime(now)
            settingsDataStore.setMicroBreakStartTime(now)
            _elapsedSeconds.value = 0
            TimerStateHolder.setState(TimerState.Running)

            val interval = settingsDataStore.sittingIntervalMinutes.first()
            _targetSeconds.value = interval * 60

            TimerService.start(appContext)
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            val interval = settingsDataStore.sittingIntervalMinutes.first()
            val startHour = settingsDataStore.workStartHour.first()
            val endHour = settingsDataStore.workEndHour.first()
            refreshTodayStats(interval, startHour, endHour)
        }
    }

    fun deleteRecord(record: CheckInRecord) {
        viewModelScope.launch {
            repository.delete(record)
            refreshStats()
        }
    }

    fun onStandUp() {
        // 本地直接依据传感器状态判断本次计时是否检测到起身活动，
        // 避免跨进程延迟读取 TimerStateHolder 导致的竞态（此前依赖 delay 轮询，不可靠）
        val verified = standingValidator.standingLikely()
        _lastStandVerified.value = verified
        TimerService.onStandUp(appContext)
        _elapsedSeconds.value = 0
        TimerStateHolder.setState(TimerState.Completed)
        viewModelScope.launch {
            delay(2000L)
            TimerStateHolder.setState(TimerState.Idle)
        }
        viewModelScope.launch {
            val interval = settingsDataStore.sittingIntervalMinutes.first()
            _targetSeconds.value = interval * 60
        }
        // 等待服务侧异步写入打卡记录完成后再刷新统计，避免站立次数延迟一次
        viewModelScope.launch {
            delay(600L)
            refreshStats()
        }
    }

    fun onSnooze() {
        TimerService.onSnooze(appContext)
        _elapsedSeconds.value = 0
        TimerStateHolder.setState(TimerState.Running)
        viewModelScope.launch {
            val interval = settingsDataStore.sittingIntervalMinutes.first()
            _targetSeconds.value = interval * 60 + 300
        }
    }

    fun onPause() {
        TimerService.onPause(appContext)
        TimerStateHolder.setState(TimerState.Paused)
    }

    fun onResume() {
        TimerService.onResume(appContext)
        TimerStateHolder.setState(TimerState.Running)
    }

    fun onStop() {
        TimerService.onStop(appContext)
        _elapsedSeconds.value = 0
        TimerStateHolder.setState(TimerState.Idle)
        refreshStats()
    }

    private suspend fun refreshTodayStats(intervalMinutes: Int, workStartHour: Int, workEndHour: Int) {
        val (startOfDay, endOfDay) = getTodayRange()
        val standCount = repository.getTodayStandCount(startOfDay, endOfDay)
        _todayStandCount.value = standCount

        _todayCompletionRate.value = HomeStatsCalculator.completionRate(
            standCount = standCount,
            workStartHour = workStartHour,
            workEndHour = workEndHour,
            intervalMinutes = intervalMinutes,
            fallback = _todayCompletionRate.value,
        )

        _todayActiveHours.value = HomeStatsCalculator.activeHours(standCount)

        val records = repository.getTodayRecords(startOfDay, endOfDay)
        _todayRecords.value = records
        _currentStreak.value = computeCurrentStreak()
        checkStreakCelebration(_currentStreak.value)
    }

    /**
     * 当连续天数达到里程碑且高于上次已庆祝的里程碑时，触发庆祝浮层并记录该里程碑，
     * 避免重复弹窗。数据全部来自本地，不依赖网络。
     */
    private suspend fun checkStreakCelebration(streak: Int) {
        if (streak <= 0 || !STREAK_MILESTONES.contains(streak)) return
        val last = settingsDataStore.lastCelebratedStreak.first()
        if (streak > last) {
            settingsDataStore.setLastCelebratedStreak(streak)
            _celebrationStreak.value = streak
        }
    }

    /** 用户关闭庆祝浮层后复位，避免重复展示。 */
    fun dismissCelebration() {
        _celebrationStreak.value = 0
    }

    /** 连续打卡天数：以今天（若无记录则昨天）为终点向前数连续有记录的日数。 */
    private suspend fun computeCurrentStreak(): Int {
        val days = repository.getAllDistinctDays()
        if (days.isEmpty()) return 0
        val set = days.toSet()
        val today = getTodayRange().first
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

    private fun getTodayRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis
        return Pair(startOfDay, endOfDay)
    }
}