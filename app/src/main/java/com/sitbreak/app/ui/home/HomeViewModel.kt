package com.sitbreak.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sitbreak.app.TimerState
import com.sitbreak.app.TimerStateHolder
import com.sitbreak.app.data.CheckInRepository
import com.sitbreak.app.data.SettingsDataStore
import com.sitbreak.app.data.db.AppDatabase
import com.sitbreak.app.data.db.CheckInRecord
import com.sitbreak.app.service.TimerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)
    private val repository = CheckInRepository(AppDatabase.getInstance(application).checkInDao())

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
                TimerService.start(getApplication())
            }
        }
    }

    private fun startElapsedTimer() {
        viewModelScope.launch {
            var cachedStartTime = 0L
            while (true) {
                if (TimerStateHolder.getState() == TimerState.Running) {
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

            TimerService.start(getApplication())
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

    fun onStandUp() {
        TimerService.onStandUp(getApplication())
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
        // 等服务侧异步写入打卡记录完成后再刷新，避免站立次数延迟一次
        viewModelScope.launch {
            delay(600L)
            refreshStats()
        }
    }

    fun onSnooze() {
        TimerService.onSnooze(getApplication())
        _elapsedSeconds.value = 0
        TimerStateHolder.setState(TimerState.Running)
        viewModelScope.launch {
            val interval = settingsDataStore.sittingIntervalMinutes.first()
            _targetSeconds.value = interval * 60 + 300
        }
    }

    fun onPause() {
        TimerService.onPause(getApplication())
        TimerStateHolder.setState(TimerState.Paused)
    }

    fun onResume() {
        TimerService.onResume(getApplication())
        TimerStateHolder.setState(TimerState.Running)
    }

    fun onStop() {
        TimerService.onStop(getApplication())
        _elapsedSeconds.value = 0
        TimerStateHolder.setState(TimerState.Idle)
        refreshStats()
    }

    private suspend fun refreshTodayStats(intervalMinutes: Int, workStartHour: Int, workEndHour: Int) {
        val (startOfDay, endOfDay) = getTodayRange()
        val standCount = repository.getTodayStandCount(startOfDay, endOfDay)
        _todayStandCount.value = standCount

        val workingHours = workEndHour - workStartHour
        if (workingHours > 0) {
            val targetStands = (workingHours * 60) / intervalMinutes
            if (targetStands > 0) {
                _todayCompletionRate.value = (standCount.toFloat() / targetStands).coerceAtMost(1f)
            }
        }

        _todayActiveHours.value = standCount * 3f / 60f

        val records = repository.getTodayRecords(startOfDay, endOfDay)
        _todayRecords.value = records
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