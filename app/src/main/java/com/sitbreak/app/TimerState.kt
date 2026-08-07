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

    fun setState(newState: TimerState) {
        _state.value = newState
    }

    fun getState(): TimerState = _state.value
}