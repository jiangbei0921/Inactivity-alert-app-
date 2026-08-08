package com.sitbreak.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.sitbreak.app.data.SettingsDataStore
import com.sitbreak.app.notification.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsDataStore: SettingsDataStore,
    private val notificationHelper: NotificationHelper,
) : ViewModel() {

    private val _sittingIntervalMinutes = MutableStateFlow(1)
    val sittingIntervalMinutes: StateFlow<Int> = _sittingIntervalMinutes.asStateFlow()

    private val _microBreakIntervalMinutes = MutableStateFlow(20)
    val microBreakIntervalMinutes: StateFlow<Int> = _microBreakIntervalMinutes.asStateFlow()

    private val _isMicroBreakEnabled = MutableStateFlow(true)
    val isMicroBreakEnabled: StateFlow<Boolean> = _isMicroBreakEnabled.asStateFlow()

    private val _workStartHour = MutableStateFlow(9)
    val workStartHour: StateFlow<Int> = _workStartHour.asStateFlow()

    private val _workEndHour = MutableStateFlow(18)
    val workEndHour: StateFlow<Int> = _workEndHour.asStateFlow()

    private val _isWeekendEnabled = MutableStateFlow(false)
    val isWeekendEnabled: StateFlow<Boolean> = _isWeekendEnabled.asStateFlow()

    private val _enabledDays = MutableStateFlow(setOf("MON", "TUE", "WED", "THU", "FRI"))
    val enabledDays: StateFlow<Set<String>> = _enabledDays.asStateFlow()

    private val _isVibrationEnabled = MutableStateFlow(true)
    val isVibrationEnabled: StateFlow<Boolean> = _isVibrationEnabled.asStateFlow()

    private val _isSoundEnabled = MutableStateFlow(true)
    val isSoundEnabled: StateFlow<Boolean> = _isSoundEnabled.asStateFlow()

    private val _notificationSoundIndex = MutableStateFlow(0)
    val notificationSoundIndex: StateFlow<Int> = _notificationSoundIndex.asStateFlow()

    private val _notificationSoundUri = MutableStateFlow("")
    val notificationSoundUri: StateFlow<String> = _notificationSoundUri.asStateFlow()

    private val _isWaterReminderEnabled = MutableStateFlow(true)
    val isWaterReminderEnabled: StateFlow<Boolean> = _isWaterReminderEnabled.asStateFlow()

    private val _isEyeReminderEnabled = MutableStateFlow(true)
    val isEyeReminderEnabled: StateFlow<Boolean> = _isEyeReminderEnabled.asStateFlow()

    private val _reminderStyle = MutableStateFlow("health_care")
    val reminderStyle: StateFlow<String> = _reminderStyle.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            settingsDataStore.sittingIntervalMinutes.onEach { _sittingIntervalMinutes.value = it }.catch {}.launchIn(viewModelScope)
        }
        viewModelScope.launch {
            settingsDataStore.microBreakIntervalMinutes.onEach { _microBreakIntervalMinutes.value = it }.catch {}.launchIn(viewModelScope)
        }
        viewModelScope.launch {
            settingsDataStore.isMicroBreakEnabled.onEach { _isMicroBreakEnabled.value = it }.catch {}.launchIn(viewModelScope)
        }
        viewModelScope.launch {
            settingsDataStore.workStartHour.onEach { _workStartHour.value = it }.catch {}.launchIn(viewModelScope)
        }
        viewModelScope.launch {
            settingsDataStore.workEndHour.onEach { _workEndHour.value = it }.catch {}.launchIn(viewModelScope)
        }
        viewModelScope.launch {
            settingsDataStore.isWeekendEnabled.onEach { _isWeekendEnabled.value = it }.catch {}.launchIn(viewModelScope)
        }
        viewModelScope.launch {
            settingsDataStore.enabledDays.onEach { _enabledDays.value = it }.catch {}.launchIn(viewModelScope)
        }
        viewModelScope.launch {
            settingsDataStore.isVibrationEnabled.onEach { _isVibrationEnabled.value = it }.catch {}.launchIn(viewModelScope)
        }
        viewModelScope.launch {
            settingsDataStore.isSoundEnabled.onEach { _isSoundEnabled.value = it }.catch {}.launchIn(viewModelScope)
        }
        viewModelScope.launch {
            settingsDataStore.notificationSoundIndex.onEach { _notificationSoundIndex.value = it }.catch {}.launchIn(viewModelScope)
        }
        viewModelScope.launch {
            settingsDataStore.notificationSoundUri.onEach { _notificationSoundUri.value = it }.catch {}.launchIn(viewModelScope)
        }
        viewModelScope.launch {
            settingsDataStore.isWaterReminderEnabled.onEach { _isWaterReminderEnabled.value = it }.catch {}.launchIn(viewModelScope)
        }
        viewModelScope.launch {
            settingsDataStore.isEyeReminderEnabled.onEach { _isEyeReminderEnabled.value = it }.catch {}.launchIn(viewModelScope)
        }
        viewModelScope.launch {
            settingsDataStore.reminderStyle.onEach { _reminderStyle.value = it }.catch {}.launchIn(viewModelScope)
        }
    }

    fun setSittingInterval(minutes: Int) {
        val clamped = minutes.coerceIn(1, 180)
        _sittingIntervalMinutes.value = clamped
        viewModelScope.launch { settingsDataStore.setSittingInterval(clamped) }
    }

    fun setMicroBreakInterval(minutes: Int) {
        val clamped = minutes.coerceIn(1, 180)
        _microBreakIntervalMinutes.value = clamped
        viewModelScope.launch { settingsDataStore.setMicroBreakInterval(clamped) }
    }

    fun setMicroBreakEnabled(enabled: Boolean) {
        _isMicroBreakEnabled.value = enabled
        viewModelScope.launch { settingsDataStore.setMicroBreakEnabled(enabled) }
    }

    fun setWorkStartHour(hour: Int) {
        val clamped = hour.coerceIn(0, 23)
        if (clamped >= _workEndHour.value) return
        _workStartHour.value = clamped
        viewModelScope.launch { settingsDataStore.setWorkStartHour(clamped) }
    }

    fun setWorkEndHour(hour: Int) {
        val clamped = hour.coerceIn(0, 23)
        if (clamped <= _workStartHour.value) return
        _workEndHour.value = clamped
        viewModelScope.launch { settingsDataStore.setWorkEndHour(clamped) }
    }

    fun setWeekendEnabled(enabled: Boolean) {
        _isWeekendEnabled.value = enabled
        viewModelScope.launch { settingsDataStore.setWeekendEnabled(enabled) }
    }

    fun setEnabledDays(days: Set<String>) {
        _enabledDays.value = days
        viewModelScope.launch { settingsDataStore.setEnabledDays(days) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        _isVibrationEnabled.value = enabled
        viewModelScope.launch {
            settingsDataStore.setVibrationEnabled(enabled)
            recreateChannels()
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        _isSoundEnabled.value = enabled
        viewModelScope.launch {
            settingsDataStore.setSoundEnabled(enabled)
            recreateChannels()
        }
    }

    fun setNotificationSoundIndex(index: Int) {
        _notificationSoundIndex.value = index
        viewModelScope.launch {
            settingsDataStore.setNotificationSoundIndex(index)
            recreateChannels()
        }
    }

    fun setNotificationSoundUri(uri: String) {
        _notificationSoundUri.value = uri
        viewModelScope.launch {
            settingsDataStore.setNotificationSoundUri(uri)
            recreateChannels()
        }
    }

    fun setWaterReminderEnabled(enabled: Boolean) {
        _isWaterReminderEnabled.value = enabled
        viewModelScope.launch { settingsDataStore.setWaterReminderEnabled(enabled) }
    }

    fun setEyeReminderEnabled(enabled: Boolean) {
        _isEyeReminderEnabled.value = enabled
        viewModelScope.launch { settingsDataStore.setEyeReminderEnabled(enabled) }
    }

    fun setReminderStyle(style: String) {
        _reminderStyle.value = style
        viewModelScope.launch { settingsDataStore.setReminderStyle(style) }
    }

    private suspend fun recreateChannels() {
        notificationHelper.createChannels(appContext)
    }
}