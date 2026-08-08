package com.sitbreak.app.ui.activity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sitbreak.app.data.CheckInRepository
import com.sitbreak.app.data.db.AppDatabase
import com.sitbreak.app.data.db.CheckInRecord
import kotlinx.coroutines.launch

class ActivityDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CheckInRepository(AppDatabase.getInstance(application).checkInDao())

    fun recordExerciseCompletion() {
        viewModelScope.launch {
            try {
                repository.insert(
                    CheckInRecord(
                        timestamp = System.currentTimeMillis(),
                        type = CheckInRecord.TYPE_EXERCISE,
                    )
                )
            } catch (_: Exception) {
                // 本地 DB 写入失败不影响 UI 计时流程，静默降级。
            }
        }
    }
}
