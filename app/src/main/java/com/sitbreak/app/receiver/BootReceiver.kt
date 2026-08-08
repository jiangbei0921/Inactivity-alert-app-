package com.sitbreak.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sitbreak.app.data.TimerSettingsDataStore
import com.sitbreak.app.service.TimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // 使用 goAsync() 避免在 BroadcastReceiver 主线程中 runBlocking 读取 DataStore，
        // 同时保证启动前台服务所需的异步窗口。
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val wasRunning = TimerSettingsDataStore(context).sittingStartTime.first() > 0L
                if (wasRunning) {
                    val serviceIntent = Intent(context, TimerService::class.java).apply {
                        action = TimerService.ACTION_START
                    }
                    context.startForegroundService(serviceIntent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
