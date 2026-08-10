package com.sitbreak.app.ui.settings

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sitbreak.app.R
import com.sitbreak.app.ui.components.AppCard
import com.sitbreak.app.ui.components.SettingRow
import com.sitbreak.app.ui.theme.BluePrimary
import com.sitbreak.app.ui.theme.BorderGray
import com.sitbreak.app.ui.theme.DividerGray
import com.sitbreak.app.ui.theme.SuccessGreen
import com.sitbreak.app.ui.theme.TextPrimary
import com.sitbreak.app.ui.theme.TextSecondary
import com.sitbreak.app.ui.theme.TextTertiary
import com.sitbreak.app.ui.theme.AccentRed
import com.sitbreak.app.ui.components.rememberHapticClick
import com.sitbreak.app.update.UpdateState
import com.sitbreak.app.update.UpdateStrategy
import com.sitbreak.app.update.UpdateViewModel
import com.sitbreak.app.update.formatSize
import kotlin.math.roundToInt

/**
 * 设置页里的「应用更新」卡片。
 *
 * 设计取舍：整张卡片只有一处随状态变化的区域，其余（当前版本、两个开关）保持不动。
 * 用户在下载过程中不会看到界面跳来跳去，也不会因为进度刷新丢掉自己正在操作的开关。
 */
@Composable
fun UpdateSettingsCard(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val autoCheck by viewModel.autoCheckEnabled.collectAsState()
    val wifiOnly by viewModel.wifiOnly.collectAsState()
    val lastCheckedAt by viewModel.lastCheckedAt.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shadowElevation = 4.dp,
    ) {
        Column {
            SettingRow(
                title = stringResource(R.string.update_current_version),
                subtitle = viewModel.currentVersionName,
            ) {
                CheckAction(state = state, onCheck = viewModel::check)
            }

            UpdateStatusBlock(
                state = state,
                lastCheckedAt = lastCheckedAt,
                onStart = viewModel::startUpdate,
                onCancel = viewModel::cancel,
                onInstall = viewModel::install,
                onIgnore = viewModel::ignoreCurrentVersion,
                onDismiss = viewModel::dismiss,
            )

            HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = DividerGray)
            SettingRow(
                title = stringResource(R.string.update_auto_check),
                subtitle = stringResource(R.string.update_auto_check_hint),
            ) {
                Switch(
                    checked = autoCheck,
                    onCheckedChange = viewModel::setAutoCheckEnabled,
                    colors = SwitchDefaults.colors(checkedTrackColor = BluePrimary),
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = DividerGray)
            SettingRow(
                title = stringResource(R.string.update_wifi_only),
                subtitle = stringResource(R.string.update_wifi_only_hint),
            ) {
                Switch(
                    checked = wifiOnly,
                    onCheckedChange = viewModel::setWifiOnly,
                    colors = SwitchDefaults.colors(checkedTrackColor = BluePrimary),
                )
            }
        }
    }
}

/** 右上角的「检查」按钮。忙碌时换成转圈，避免用户重复点击堆任务。 */
@Composable
private fun CheckAction(state: UpdateState, onCheck: () -> Unit) {
    val busy = state is UpdateState.Checking ||
        state is UpdateState.Downloading ||
        state is UpdateState.Patching ||
        state is UpdateState.Verifying

    if (busy) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = BluePrimary,
        )
    } else {
        TextButton(onClick = rememberHapticClick(onCheck), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
            Text(
                text = stringResource(R.string.update_check),
                fontSize = 14.sp,
                color = BluePrimary,
                fontWeight = FontWeight.W500,
            )
        }
    }
}

@Composable
private fun UpdateStatusBlock(
    state: UpdateState,
    lastCheckedAt: Long,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        when (state) {
            is UpdateState.Idle -> StatusText(lastCheckedLabel(lastCheckedAt), TextTertiary)

            is UpdateState.Checking -> StatusText(stringResource(R.string.update_checking), TextSecondary)

            is UpdateState.UpToDate -> StatusText(stringResource(R.string.update_up_to_date), SuccessGreen)

            is UpdateState.Available -> {
                StatusText(
                    stringResource(R.string.update_found, state.info.versionName),
                    TextPrimary,
                    FontWeight.W600,
                )
                SizeSummary(
                    patchSize = state.info.patchSize,
                    fullSize = state.info.fullSize,
                    savedRatio = state.info.savedRatio,
                )
                ReleaseNotes(state.info.releaseNotes)
                Spacer(Modifier.height(12.dp))
                ActionRow(
                    primaryText = stringResource(R.string.update_now),
                    onPrimary = onStart,
                    secondaryText = stringResource(R.string.update_ignore),
                    onSecondary = onIgnore,
                )
            }

            is UpdateState.Downloading -> {
                val label = if (state.strategy == UpdateStrategy.INCREMENTAL) {
                    stringResource(R.string.update_downloading_incremental)
                } else {
                    stringResource(R.string.update_downloading_full)
                }
                StatusText(label, TextPrimary, FontWeight.W500)
                Spacer(Modifier.height(8.dp))
                ProgressBar(state.progress)
                Spacer(Modifier.height(6.dp))
                StatusText(
                    stringResource(
                        R.string.update_progress_bytes,
                        formatSize(state.bytesDownloaded),
                        formatSize(state.totalBytes),
                        (state.progress * 100).roundToInt(),
                    ),
                    TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                SecondaryOnlyRow(stringResource(R.string.update_cancel), onCancel)
            }

            is UpdateState.Patching -> {
                StatusText(stringResource(R.string.update_patching), TextPrimary, FontWeight.W500)
                Spacer(Modifier.height(8.dp))
                ProgressBar(state.progress)
                Spacer(Modifier.height(6.dp))
                StatusText(
                    stringResource(R.string.update_patching_hint, (state.progress * 100).roundToInt()),
                    TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                SecondaryOnlyRow(stringResource(R.string.update_cancel), onCancel)
            }

            is UpdateState.Verifying -> {
                StatusText(stringResource(R.string.update_verifying), TextPrimary, FontWeight.W500)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = BluePrimary,
                    trackColor = DividerGray,
                )
            }

            is UpdateState.ReadyToInstall -> {
                StatusText(
                    stringResource(R.string.update_ready, state.info.versionName),
                    TextPrimary,
                    FontWeight.W600,
                )
                if (state.strategy == UpdateStrategy.INCREMENTAL) {
                    StatusText(
                        stringResource(
                            R.string.update_saved_summary,
                            (state.info.savedRatio * 100).roundToInt(),
                        ),
                        SuccessGreen,
                    )
                }
                Spacer(Modifier.height(12.dp))
                ActionRow(
                    primaryText = stringResource(R.string.update_install),
                    onPrimary = onInstall,
                    secondaryText = stringResource(R.string.update_later),
                    onSecondary = onDismiss,
                )
            }

            is UpdateState.Installing ->
                StatusText(stringResource(R.string.update_installing), TextSecondary)

            is UpdateState.Failed -> {
                StatusText(
                    stringResource(R.string.update_failed_prefix, state.message),
                    AccentRed,
                    FontWeight.W500,
                )
                Spacer(Modifier.height(4.dp))
                StatusText(stringResource(R.string.update_rollback_hint), TextTertiary)
                Spacer(Modifier.height(12.dp))
                if (state.retryable) {
                    ActionRow(
                        primaryText = stringResource(R.string.update_retry),
                        onPrimary = onStart,
                        secondaryText = stringResource(R.string.update_dismiss),
                        onSecondary = onDismiss,
                    )
                } else {
                    SecondaryOnlyRow(stringResource(R.string.update_dismiss), onDismiss)
                }
            }
        }
    }
}

@Composable
private fun StatusText(
    text: String,
    color: Color,
    weight: FontWeight = FontWeight.W400,
) {
    Text(text = text, fontSize = 13.sp, color = color, fontWeight = weight)
}

@Composable
private fun SizeSummary(patchSize: Long?, fullSize: Long, savedRatio: Float) {
    Spacer(Modifier.height(4.dp))
    if (patchSize != null) {
        StatusText(
            stringResource(
                R.string.update_size_incremental,
                formatSize(patchSize),
                (savedRatio * 100).roundToInt(),
            ),
            SuccessGreen,
            FontWeight.W500,
        )
    } else {
        StatusText(stringResource(R.string.update_size_full, formatSize(fullSize)), TextSecondary)
    }
}

@Composable
private fun ReleaseNotes(notes: String) {
    if (notes.isBlank()) return
    Spacer(Modifier.height(6.dp))
    Text(text = notes, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
}

@Composable
private fun ProgressBar(progress: Float) {
    LinearProgressIndicator(
        progress = progress.coerceIn(0f, 1f),
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = BluePrimary,
        trackColor = DividerGray,
    )
}

@Composable
private fun ActionRow(
    primaryText: String,
    onPrimary: () -> Unit,
    secondaryText: String,
    onSecondary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = rememberHapticClick(onPrimary),
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
        ) {
            Text(primaryText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.W500)
        }
        androidx.compose.material3.OutlinedButton(
            onClick = rememberHapticClick(onSecondary),
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray),
        ) {
            Text(secondaryText, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SecondaryOnlyRow(text: String, onClick: () -> Unit) {
        androidx.compose.material3.OutlinedButton(
            onClick = rememberHapticClick(onClick),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray),
    ) {
        Text(text, fontSize = 14.sp)
    }
}

@Composable
private fun lastCheckedLabel(timestamp: Long): String {
    if (timestamp <= 0L) return stringResource(R.string.update_never_checked)
    val relative = DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    return stringResource(R.string.update_last_checked, relative)
}
