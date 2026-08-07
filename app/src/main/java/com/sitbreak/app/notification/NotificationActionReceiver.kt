package com.sitbreak.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sitbreak.app.service.TimerService

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val serviceIntent = Intent(context, TimerService::class.java).apply {
            this.action = action
        }
        context.startForegroundService(serviceIntent)
    }
}
