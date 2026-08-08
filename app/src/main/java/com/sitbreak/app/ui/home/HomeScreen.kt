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
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import com.sitbreak.app.TimerState
import com.sitbreak.app.health.StandingValidator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.sitbreak.app.R
import com.sitbreak.app.data.db.CheckInRecord
import com.sitbreak.app.ui.components.ActivityIcon
import com.sitbreak.app.ui.components.ActivityType
import com.sitbreak.app.ui.components.SectionHeader
import com.sitbreak.app.ui.components.StatCard
import com.sitbreak.app.ui.theme.BlueLight
import com.sitbreak.app.ui.theme.BluePrimary
import com.sitbreak.app.ui.theme.BorderGray
import com.sitbreak.app.ui.theme.CardBackground
import com.sitbreak.app.ui.theme.PageBackground
import com.sitbreak.app.ui.theme.TextPrimary
import com.sitbreak.app.ui.theme.TextSecondary
import com.sitbreak.app.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val timerState by viewModel.timerState.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    val targetSeconds by viewModel.targetSeconds.collectAsState()
    val sittingInterval by viewModel.sittingIntervalMinutes.collectAsState()
    val todayStandCount by viewModel.todayStandCount.collectAsState()
    val todayCompletionRate by viewModel.todayCompletionRate.collectAsState()
    val todayActiveHours by viewModel.todayActiveHours.collectAsState()
    val todayRecords by viewModel.todayRecords.collectAsState()
    val lastStandVerified by viewModel.lastStandVerified.collectAsState()

    val progress = if (targetSeconds > 0) {
        (elapsedSeconds.toFloat() / targetSeconds).coerceIn(0f, 1f)
    } else 0f

    var isRecordExpanded by remember { mutableStateOf(todayStandCount <= 1) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { NotificationPermissionBanner() }
        item { StepVerificationBanner() }
        item { TopBar() }
        item { Spacer(modifier = Modifier.height(20.dp)) }

        if (timerState == TimerState.Idle) {
            item { WelcomeCard(onStart = { viewModel.startTimer() }) }
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
                            onClick = { viewModel.onPause() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
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
                            onClick = { viewModel.onStop() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
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
                            onClick = { viewModel.onResume() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
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
                            onClick = { viewModel.onStop() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
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
                            onClick = { viewModel.onStandUp() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                        ) {
                            Text(stringResource(R.string.home_stand_up), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.W500)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    item {
                        OutlinedButton(
                            onClick = { viewModel.onSnooze() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
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
                                color = Color(0xFF16A34A),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                    item {
                        Button(
                            onClick = { viewModel.startTimer() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
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

            if (todayRecords.isNotEmpty()) {
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
                                ActivityRecordItem(record = record)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopBarIconButton(icon = Icons.Outlined.Person)
        Text(
            text = stringResource(R.string.home_title),
            fontSize = 17.sp,
            fontWeight = FontWeight.W600,
            color = TextPrimary,
        )
        TopBarIconButton(icon = Icons.Outlined.Notifications)
    }
}

@Composable
private fun TopBarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun WelcomeCard(onStart: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.06f),
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
            ) {
                Text(stringResource(R.string.home_start_timer), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.W500)
            }
        }
    }
}

@Composable
private fun TodayInfoCard(
    intervalMinutes: Int,
    todayStandCount: Int,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.06f),
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
    val timeText = String.format("%02d:%02d", minutes, seconds)
    val targetText = stringResource(R.string.home_target_time, targetSeconds / 60, targetSeconds % 60)

    Box(
        modifier = modifier,
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

@Composable
private fun ActivityRecordItem(record: CheckInRecord) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val (label, activityType) = when (record.type) {
        "stand_up" -> stringResource(R.string.home_record_stand_up) to ActivityType.STAND_UP
        "micro_break" -> stringResource(R.string.home_record_micro_break) to ActivityType.MICRO_BREAK
        else -> stringResource(R.string.home_record_snoozed) to ActivityType.SNOOZED
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.06f),
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFEF9C3))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clickable {
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
            color = Color(0xFF854F0B)
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE0F2FE))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clickable {
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
            color = Color(0xFF075985)
        )
    }
}