package com.sitbreak.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sitbreak.app.service.TimerService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_START
            }
            context.startForegroundService(serviceIntent)
        }
    }
}