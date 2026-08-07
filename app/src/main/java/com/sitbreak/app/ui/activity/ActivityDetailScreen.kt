package com.sitbreak.app.ui.activity

import android.os.CountDownTimer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitbreak.app.data.CheckInRepository
import com.sitbreak.app.data.db.AppDatabase
import com.sitbreak.app.data.db.CheckInRecord
import com.sitbreak.app.ui.theme.BluePrimary
import com.sitbreak.app.ui.theme.CardBackground
import com.sitbreak.app.ui.theme.PageBackground
import com.sitbreak.app.ui.theme.TextPrimary
import com.sitbreak.app.ui.theme.TextSecondary
import com.sitbreak.app.ui.theme.TextTertiary
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ActivityDetailScreen(
    activityId: String,
    onBack: () -> Unit,
) {
    val activity = activities.find { it.id == activityId } ?: return
    val context = LocalContext.current
    var isRunning by remember { mutableStateOf(false) }
    var isCompleted by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableStateOf(parseDuration(activity.duration)) }
    var timer by remember { mutableStateOf<CountDownTimer?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val totalSeconds = parseDuration(activity.duration)

    DisposableEffect(Unit) {
        onDispose { timer?.cancel() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "活动详情",
                fontSize = 17.sp,
                fontWeight = FontWeight.W600,
                color = TextPrimary,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = activity.bgColor),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = activity.name,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "总时长 ${activity.duration}",
                            fontSize = 14.sp,
                            color = TextSecondary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "动作步骤",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W600,
                    color = TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(activity.steps.size) { index ->
                val step = activity.steps[index]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(BluePrimary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W600,
                                color = BluePrimary,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = step,
                            fontSize = 13.sp,
                            color = TextPrimary,
                            lineHeight = 22.sp,
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))

                if (isCompleted) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "完成",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.W600,
                                color = Color(0xFF16A34A),
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = formatTime(remainingSeconds),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRunning) BluePrimary else TextTertiary,
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (isRunning) {
                                        timer?.cancel()
                                        isRunning = false
                                    } else {
                                        isRunning = true
                                        timer = object : CountDownTimer(
                                            (remainingSeconds * 1000L),
                                            1000L,
                                        ) {
                                            override fun onTick(millisUntilFinished: Long) {
                                                remainingSeconds = (millisUntilFinished / 1000).toInt()
                                            }

                                            override fun onFinish() {
                                                remainingSeconds = 0
                                                isRunning = false
                                                isCompleted = true
                                                coroutineScope.launch {
                                                    try {
                                                        val repository = CheckInRepository(
                                                            AppDatabase.getInstance(context).checkInDao()
                                                        )
                                                        repository.insert(
                                                            CheckInRecord(
                                                                timestamp = System.currentTimeMillis(),
                                                                type = CheckInRecord.TYPE_EXERCISE,
                                                            )
                                                        )
                                                    } catch (_: Exception) {
                                                    }
                                                }
                                            }
                                        }.start()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRunning) Color(0xFFEF4444) else BluePrimary,
                                ),
                            ) {
                                Icon(
                                    imageVector = if (isRunning) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isRunning) "暂停" else "开始计时",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.W500,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private fun parseDuration(duration: String): Int {
    return duration.replace("秒", "").toIntOrNull() ?: 30
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}