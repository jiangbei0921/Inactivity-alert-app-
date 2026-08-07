package com.sitbreak.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sitbreak.app.service.TimerService

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationHelper.ACTION_STAND_UP -> {
                TimerService.onStandUp()
            }
            NotificationHelper.ACTION_SNOOZE -> {
                TimerService.onSnooze()
            }
            NotificationHelper.ACTION_PAUSE_TIMER -> {
                TimerService.onPause()
            }
            NotificationHelper.ACTION_RESUME_TIMER -> {
                TimerService.onResume()
            }
            NotificationHelper.ACTION_STOP_TIMER -> {
                TimerService.onStop()
            }
        }
    }
}