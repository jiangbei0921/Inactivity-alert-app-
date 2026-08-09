package com.sitbreak.app.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 更新界面的薄壳。
 *
 * 这里刻意**不持有**任何更新进度状态：真正的状态机活在应用级的 [UpdateRepository] 里。
 * ViewModel 只做两件事——把 Flow 转给 Compose、把点击转成仓库调用。
 * 这样用户在下载途中退出设置页、甚至退出界面，进度都不会丢，回来还能接着看。
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: UpdateRepository,
    private val preferences: UpdatePreferences,
) : ViewModel() {

    val state: StateFlow<UpdateState> = repository.state

    val autoCheckEnabled: StateFlow<Boolean> = preferences.autoCheckEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val wifiOnly: StateFlow<Boolean> = preferences.wifiOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val lastCheckedAt: StateFlow<Long> = preferences.lastCheckedAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 一次性提示（安装器拉不起来之类），UI 消费后调 [consumeMessage] 清空。 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val currentVersionName: String get() = repository.currentVersionName

    fun check() = repository.check(silent = false)

    /** 用户主动点的更新，允许走移动网络——他知道自己在做什么。 */
    fun startUpdate() = repository.startUpdate(allowMetered = true)

    fun cancel() = repository.cancel()

    fun ignoreCurrentVersion() = repository.ignoreCurrentVersion()

    fun dismiss() = repository.dismiss()

    /** 未授权「安装未知应用」时先把用户送去授权页，避免点了没反应。 */
    fun install() {
        if (!repository.canInstall()) {
            val opened = repository.requestInstallPermission()
            _message.value = if (opened) {
                "请先允许「安装未知应用」，返回后再点安装"
            } else {
                "请在系统设置中允许本应用安装未知应用"
            }
            return
        }
        repository.install()?.let { _message.value = it }
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoCheckEnabled(enabled) }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { preferences.setWifiOnly(enabled) }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
