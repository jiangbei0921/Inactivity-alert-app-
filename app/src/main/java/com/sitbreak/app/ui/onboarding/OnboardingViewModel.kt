package com.sitbreak.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitbreak.app.data.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 首启引导的三种状态：加载中 / 需要展示 / 已完成可直接跳过。 */
enum class OnboardingUiState {
    Loading,
    Show,
    Skip,
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState.Loading)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val completed = settingsDataStore.onboardingCompleted.first()
            _uiState.value = if (completed) OnboardingUiState.Skip else OnboardingUiState.Show
        }
    }

    /** 用户看完或跳过引导，落盘标记后回调，保证下次冷启动不再展示。 */
    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsDataStore.setOnboardingCompleted(true)
            onDone()
        }
    }
}
