package com.sitbreak.app.ui.reminder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.sitbreak.app.ui.components.AppCard
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.sitbreak.app.TimerState
import com.sitbreak.app.TimerStateHolder
import com.sitbreak.app.notification.NotificationHelper
import com.sitbreak.app.service.TimerService
import com.sitbreak.app.ui.theme.BluePrimary
import com.sitbreak.app.ui.theme.CardBackground
import com.sitbreak.app.ui.theme.TextPrimary
import com.sitbreak.app.ui.theme.TextSecondary

class ReminderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sittingMinutes = intent.getIntExtra(EXTRA_SITTING_MINUTES, 45)

        setShowWhenLocked(true)
        setTurnScreenOn(true)

        lifecycleScope.launch {
            delay(60_000L)
            if (!isFinishing) finish()
        }

        lifecycleScope.launch {
            TimerStateHolder.state.collectLatest { state ->
                if (state != TimerState.Reminder && !isFinishing) {
                    finish()
                }
            }
        }

        setContent {
            ReminderScreen(
                sittingMinutes = sittingMinutes,
                onStandUp = {
                    startForegroundService(
                        Intent(this, TimerService::class.java).apply {
                            action = NotificationHelper.ACTION_STAND_UP
                        }
                    )
                    finish()
                },
                onSnooze = {
                    startForegroundService(
                        Intent(this, TimerService::class.java).apply {
                            action = NotificationHelper.ACTION_SNOOZE
                        }
                    )
                    finish()
                },
            )
        }
    }

    companion object {
        const val EXTRA_SITTING_MINUTES = "sitting_minutes"
    }
}

@Composable
private fun ReminderScreen(
    sittingMinutes: Int,
    onStandUp: () -> Unit,
    onSnooze: () -> Unit,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        AppCard(
            modifier = Modifier
                .width(screenWidth - 48.dp),
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDBEAFE)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = BluePrimary,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "你已经连续久坐 ${sittingMinutes} 分钟",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onStandUp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                ) {
                    Text(
                        text = "我站起来了",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W500,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CardBackground,
                        contentColor = TextSecondary,
                    ),
                ) {
                    Text(
                        text = "延迟5分钟",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W500,
                    )
                }
            }
        }
    }
}