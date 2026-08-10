package com.sitbreak.app.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.sitbreak.app.TimerState
import com.sitbreak.app.health.StandingValidator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.sitbreak.app.R
import com.sitbreak.app.navigation.Routes
import com.sitbreak.app.data.db.CheckInRecord
import com.sitbreak.app.ui.components.ActivityIcon
import com.sitbreak.app.ui.components.ActivityType
import com.sitbreak.app.ui.components.EmptyState
import com.sitbreak.app.ui.components.SectionHeader
import com.sitbreak.app.ui.components.StatCard
import com.sitbreak.app.ui.components.hapticClickable
import com.sitbreak.app.ui.components.rememberHapticClick
import com.sitbreak.app.share.ShareCardData
import com.sitbreak.app.share.ShareCardGenerator
import com.sitbreak.app.ui.theme.AccentRed
import com.sitbreak.app.ui.theme.AccentOrange
import com.sitbreak.app.ui.theme.BlueLight
import com.sitbreak.app.ui.theme.BluePrimary
import com.sitbreak.app.ui.theme.BorderGray
import com.sitbreak.app.ui.theme.CardBackground
import com.sitbreak.app.ui.theme.PageBackground
import com.sitbreak.app.ui.theme.TextPrimary
import com.sitbreak.app.ui.theme.TextSecondary
import com.sitbreak.app.ui.components.AppCard
import com.sitbreak.app.ui.theme.TextTertiary
import com.sitbreak.app.ui.theme.RadiusButton
import com.sitbreak.app.ui.theme.SkyDark
import com.sitbreak.app.ui.theme.SuccessGreen
import com.sitbreak.app.ui.theme.TintSky
import com.sitbreak.app.ui.theme.WarningBg
import com.sitbreak.app.ui.theme.WarningDark
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val timerState by viewModel.timerState.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    val targetSeconds by viewModel.targetSeconds.collectAsState()
    val sittingInterval by viewModel.sittingIntervalMinutes.collectAsState()
    val todayStandCount by viewModel.todayStandCount.collectAsState()
    val todayCompletionRate by viewModel.todayCompletionRate.collectAsState()
    val todayActiveHours by viewModel.todayActiveHours.collectAsState()
    val todayRecords by viewModel.todayRecords.collectAsState()
    val lastStandVerified by viewModel.lastStandVerified.collectAsState()
    val currentStreak by viewModel.currentStreak.collectAsState()
    val celebrationStreak by viewModel.celebrationStreak.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun onShare() {
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                ShareCardGenerator.generate(
                    context,
                    ShareCardData(
                        streak = currentStreak,
                        todayCount = todayStandCount,
                        completionRate = todayCompletionRate,
                        activeHours = todayActiveHours,
                        dateText = SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(Date()),
                    ),
                )
            }
            uri?.let { ShareCardGenerator.share(context, it) }
        }
    }

    val progress = if (targetSeconds > 0) {
        (elapsedSeconds.toFloat() / targetSeconds).coerceIn(0f, 1f)
    } else 0f

    var isRecordExpanded by remember { mutableStateOf(todayStandCount <= 1) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(PageBackground)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { NotificationPermissionBanner() }
            item { StepVerificationBanner() }
            item { TopBar(navController) }
            item { Spacer(modifier = Modifier.height(20.dp)) }

            if (timerState == TimerState.Idle) {
                item { WelcomeCard(onStart = { viewModel.startTimer() }, currentStreak = currentStreak, navController = navController, onShare = { onShare() }) }
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item {
                    TodayInfoCard(
                        intervalMinutes = sittingInterval,
                        todayStandCount = todayStandCount,
                    )
                }
            } else {
            item {
                CircularTimer(
                    progress = progress,
                    elapsedSeconds = elapsedSeconds,
                    targetSeconds = targetSeconds,
                    modifier = Modifier.size(150.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            when (timerState) {
                TimerState.Running -> {
                    item {
                        Button(
                            onClick = rememberHapticClick { viewModel.onPause() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RadiusButton,
                            colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
                        ) {
                            Text(
                                text = stringResource(R.string.home_pause),
                                color = TextSecondary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.W500,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    item {
                        OutlinedButton(
                            onClick = rememberHapticClick { viewModel.onStop() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RadiusButton,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CardBackground,
                                contentColor = TextSecondary,
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray),
                        ) {
                            Text(stringResource(R.string.home_stop), fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }

                TimerState.Paused -> {
                    item {
                        Button(
                            onClick = rememberHapticClick { viewModel.onResume() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RadiusButton,
                            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                        ) {
                            Text(
                                text = stringResource(R.string.home_resume),
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.W500,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    item {
                        OutlinedButton(
                            onClick = rememberHapticClick { viewModel.onStop() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RadiusButton,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CardBackground,
                                contentColor = TextSecondary,
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray),
                        ) {
                            Text(stringResource(R.string.home_stop), fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }

                TimerState.Reminder -> {
                    item {
                        Button(
                            onClick = rememberHapticClick { viewModel.onStandUp() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RadiusButton,
                            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                        ) {
                            Text(stringResource(R.string.home_stand_up), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.W500)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    item {
                        OutlinedButton(
                            onClick = rememberHapticClick { viewModel.onSnooze() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RadiusButton,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CardBackground,
                                contentColor = TextSecondary,
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray),
                        ) {
                            Text(stringResource(R.string.home_snooze), fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }

                TimerState.Completed -> {
                    if (lastStandVerified == true) {
                        item {
                            Text(
                                text = stringResource(R.string.home_step_verified),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W500,
                                color = SuccessGreen,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                    item {
                        Button(
                            onClick = rememberHapticClick { viewModel.startTimer() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RadiusButton,
                            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                        ) {
                            Text(stringResource(R.string.home_complete_continue), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.W500)
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }

                else -> {}
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.home_today_stand),
                        value = stringResource(R.string.home_stand_times, todayStandCount),
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.home_completion_rate),
                        value = "${(todayCompletionRate * 100).toInt()}%",
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.home_active_hours),
                        value = "%.1fh".format(todayActiveHours),
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (todayRecords.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.home_no_record_title),
                        subtitle = stringResource(R.string.home_no_record_subtitle),
                        icon = Icons.Outlined.EventSeat,
                    )
                }
            } else {
                item {
                    SectionHeader(
                        title = stringResource(R.string.home_today_record),
                        expanded = isRecordExpanded,
                        onClick = { isRecordExpanded = !isRecordExpanded }
                    )
                }
                item {
                    AnimatedVisibility(
                        visible = isRecordExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Column {
                            todayRecords.forEach { record ->
                                SwipeToDismissRecordItem(
                                    record = record,
                                    onDelete = { viewModel.deleteRecord(record) },
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
        }
        if (celebrationStreak > 0) {
            StreakCelebrationOverlay(
                milestone = celebrationStreak,
                onDismiss = { viewModel.dismissCelebration() },
            )
        }
    }
}

@Composable
private fun TopBar(navController: NavHostController) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopBarIconButton(
            icon = Icons.Outlined.Person,
            contentDescription = stringResource(R.string.a11y_open_settings),
            onClick = { navController.navigate(Routes.SETTINGS) },
        )
        Text(
            text = greetingFromHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)),
            fontSize = 17.sp,
            fontWeight = FontWeight.W600,
            color = TextPrimary,
        )
        TopBarIconButton(
            icon = Icons.Outlined.Notifications,
            contentDescription = stringResource(R.string.a11y_open_notification_settings),
            onClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                }
                ctx.startActivity(intent)
            },
        )
    }
}

@Composable
private fun TopBarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(CardBackground)
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.06f),
            )
            .then(if (onClick != null) Modifier.hapticClickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = TextSecondary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun WelcomeCard(
    onStart: () -> Unit,
    currentStreak: Int,
    navController: NavHostController,
    onShare: () -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(BlueLight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.EventSeat,
                    contentDescription = null,
                    tint = BluePrimary,
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.home_ready),
                fontSize = 14.sp,
                fontWeight = FontWeight.W500,
                color = TextPrimary,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.home_subtitle),
                fontSize = 12.sp,
                fontWeight = FontWeight.W400,
                color = TextSecondary,
            )

            if (currentStreak > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocalFireDepartment,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.home_streak, currentStreak),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W600,
                        color = AccentOrange,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 快捷入口：一键直达活动、成就与统计，强化首屏价值
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuickEntryChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.FitnessCenter,
                    label = stringResource(R.string.home_quick_activity),
                    onClick = { navController.navigate(Routes.ACTIVITY) },
                )
                QuickEntryChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.EmojiEvents,
                    label = stringResource(R.string.home_quick_achievements),
                    onClick = { navController.navigate(Routes.ACHIEVEMENTS) },
                )
                QuickEntryChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.LocalFireDepartment,
                    label = stringResource(R.string.home_quick_stats),
                    onClick = { navController.navigate(Routes.STATS) },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 本地生成打卡分享图（离线），一键分享今日战绩
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hapticClickable { onShare() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    tint = BluePrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.home_share),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W500,
                    color = BluePrimary,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = rememberHapticClick { onStart() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RadiusButton,
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
            ) {
                Text(stringResource(R.string.home_start_timer), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.W500)
            }
        }
    }
}

@Composable
private fun QuickEntryChip(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RadiusButton)
            .background(CardBackground)
            .border(1.dp, BorderGray, RadiusButton)
            .hapticClickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BluePrimary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.W500,
            color = TextPrimary,
        )
    }
}

private fun greetingFromHour(hour: Int): String {
    return when {
        hour < 6 -> "凌晨好"
        hour < 12 -> "上午好"
        hour < 14 -> "中午好"
        hour < 18 -> "下午好"
        else -> "晚上好"
    }
}

@Composable
private fun TodayInfoCard(
    intervalMinutes: Int,
    todayStandCount: Int,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_today_goal),
                fontSize = 14.sp,
                fontWeight = FontWeight.W500,
                color = TextPrimary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_stand_count, todayStandCount),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = BluePrimary,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.home_interval_minutes, intervalMinutes),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W400,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun CircularTimer(
    progress: Float,
    elapsedSeconds: Int,
    targetSeconds: Int,
    modifier: Modifier = Modifier,
) {
    val ringColor = BluePrimary
    val ringBg = BlueLight
    val ringWidth = 9.dp

    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timeText = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    val targetText = stringResource(R.string.home_target_time, targetSeconds / 60, targetSeconds % 60)

    // 圆环由 Canvas 绘制、时间文本被拆成多个 Text，TalkBack 逐条朗读会非常割裂。
    // 这里把整个圆环合并成一个语义节点，并暴露进度条区间信息。
    val timerDescription = stringResource(
        R.string.a11y_timer_progress,
        minutes,
        seconds,
        targetSeconds / 60,
        (progress.coerceIn(0f, 1f) * 100).toInt(),
    )

    Box(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = timerDescription
            progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = ringWidth.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

            drawArc(
                color = ringBg,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = timeText,
                fontSize = 28.sp,
                fontWeight = FontWeight.W600,
                color = TextPrimary,
            )
            Text(
                text = stringResource(R.string.home_elapsed_label),
                fontSize = 10.sp,
                fontWeight = FontWeight.W400,
                color = TextSecondary,
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = targetText,
                fontSize = 11.sp,
                fontWeight = FontWeight.W400,
                color = TextTertiary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissRecordItem(
    record: CheckInRecord,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AccentRed.copy(alpha = 0.12f))
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.home_delete_record),
                    tint = AccentRed,
                )
            }
        },
        content = { ActivityRecordItem(record = record) },
    )
}

@Composable
private fun ActivityRecordItem(record: CheckInRecord) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val (label, activityType) = when (record.type) {
        "stand_up" -> stringResource(R.string.home_record_stand_up) to ActivityType.STAND_UP
        "micro_break" -> stringResource(R.string.home_record_micro_break) to ActivityType.MICRO_BREAK
        else -> stringResource(R.string.home_record_snoozed) to ActivityType.SNOOZED
    }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActivityIcon(type = activityType, size = 28)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.W400,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = timeFormatter.format(Date(record.timestamp)),
                fontSize = 12.sp,
                color = TextSecondary,
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun NotificationPermissionBanner() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val permissionState = rememberPermissionState(
        android.Manifest.permission.POST_NOTIFICATIONS
    )
    if (permissionState.status.isGranted) return

    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val openLabel = stringResource(R.string.a11y_open_notification_settings)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(WarningBg)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clickable(onClickLabel = openLabel, role = Role.Button) {
                val shouldShowRationale = activity?.shouldShowRequestPermissionRationale(
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) ?: true
                if (shouldShowRationale) {
                    permissionState.launchPermissionRequest()
                } else {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }
            }
    ) {
        Text(
            text = stringResource(R.string.home_notification_permission_banner),
            fontSize = 13.sp,
            color = WarningDark
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun StepVerificationBanner() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

    val permissionState = rememberPermissionState(
        android.Manifest.permission.ACTIVITY_RECOGNITION
    )
    val context = LocalContext.current
    // 授权成功或已授权时，若前台计时仍在运行则立即启用步数传感器验证，
    // 修复「计时过程中才授予权限却未能生效」的边界缺陷
    LaunchedEffect(permissionState.status) {
        if (permissionState.status.isGranted) {
            StandingValidator.start(context)
        }
    }
    if (permissionState.status.isGranted) return

    val activity = context as? ComponentActivity
    val openLabel = stringResource(R.string.a11y_open_activity_permission)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TintSky)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clickable(onClickLabel = openLabel, role = Role.Button) {
                val shouldShowRationale = activity?.shouldShowRequestPermissionRationale(
                    android.Manifest.permission.ACTIVITY_RECOGNITION
                ) ?: true
                if (shouldShowRationale) {
                    permissionState.launchPermissionRequest()
                } else {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            }
    ) {
        Text(
            text = stringResource(R.string.home_step_verify_banner),
            fontSize = 13.sp,
            color = SkyDark
        )
    }
}