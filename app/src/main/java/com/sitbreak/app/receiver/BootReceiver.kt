package com.sitbreak.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sitbreak.app.data.TimerSettingsDataStore
import com.sitbreak.app.service.TimerService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // 仅当用户上次确实在计时（sittingStartTime > 0）时才恢复前台服务，
        // 避免每次开机都挂上不可清除的僵尸通知。
        val wasRunning = runBlocking {
            TimerSettingsDataStore(context).sittingStartTime.first() > 0L
        }
        if (!wasRunning) return

        val serviceIntent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_START
        }
        context.startForegroundService(serviceIntent)
    }
}
