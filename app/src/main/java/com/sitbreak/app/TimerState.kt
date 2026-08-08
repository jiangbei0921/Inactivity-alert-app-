package com.sitbreak.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TimerState {
    Idle,
    Running,
    Paused,
    Reminder,
    Completed
}

object TimerStateHolder {
    private val _state = MutableStateFlow(TimerState.Idle)
    val state: StateFlow<TimerState> = _state.asStateFlow()

    /** 最近一次「我站起来了」是否经传感器验证（true=已验证 / false 或 null=未验证/不可用）。 */
    @Volatile
    var lastStandVerified: Boolean? = null

    fun setState(newState: TimerState) {
        _state.value = newState
    }
}