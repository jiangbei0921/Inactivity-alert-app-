package com.sitbreak.app.ui.settings

import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitbreak.app.ui.components.SectionHeader
import com.sitbreak.app.notification.ReminderCopywriter
import com.sitbreak.app.ui.components.SettingRow
import com.sitbreak.app.ui.theme.BluePrimary
import com.sitbreak.app.ui.theme.BorderGray
import com.sitbreak.app.ui.theme.CardBackground
import com.sitbreak.app.ui.theme.DividerGray
import com.sitbreak.app.ui.theme.PageBackground
import com.sitbreak.app.ui.theme.TextPrimary
import com.sitbreak.app.ui.theme.TextSecondary
import com.sitbreak.app.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sitbreak.app.R

private val INTERVAL_OPTIONS = listOf(10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 75, 90)
private val MICRO_BREAK_OPTIONS = listOf(5, 10, 15, 20, 25, 30)
private const val OPTION_CUSTOM = -1

@Composable
private fun soundNames(): List<String> = listOf(
    stringResource(R.string.settings_sound_crisp),
    stringResource(R.string.settings_sound_soft),
    stringResource(R.string.settings_sound_short),
    stringResource(R.string.settings_sound_lively),
    stringResource(R.string.settings_sound_classic),
    stringResource(R.string.settings_sound_custom),
)

private val DAY_KEYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

@Composable
private fun dayLabels(): List<String> = listOf(
    stringResource(R.string.settings_day_monday),
    stringResource(R.string.settings_day_tuesday),
    stringResource(R.string.settings_day_wednesday),
    stringResource(R.string.settings_day_thursday),
    stringResource(R.string.settings_day_friday),
    stringResource(R.string.settings_day_saturday),
    stringResource(R.string.settings_day_sunday),
)
private val WORKDAY_KEYS = setOf("MON", "TUE", "WED", "THU", "FRI")
private val ALL_DAYS_KEYS = setOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val sittingInterval by viewModel.sittingIntervalMinutes.collectAsState()
    val microBreakInterval by viewModel.microBreakIntervalMinutes.collectAsState()
    val isMicroBreakEnabled by viewModel.isMicroBreakEnabled.collectAsState()
    val workStartHour by viewModel.workStartHour.collectAsState()
    val workEndHour by viewModel.workEndHour.collectAsState()
    val enabledDays by viewModel.enabledDays.collectAsState()
    val isVibrationEnabled by viewModel.isVibrationEnabled.collectAsState()
    val isSoundEnabled by viewModel.isSoundEnabled.collectAsState()
    val notificationSoundIndex by viewModel.notificationSoundIndex.collectAsState()
    val notificationSoundUri by viewModel.notificationSoundUri.collectAsState()
    val isWaterReminderEnabled by viewModel.isWaterReminderEnabled.collectAsState()
    val isEyeReminderEnabled by viewModel.isEyeReminderEnabled.collectAsState()
    val reminderStyle by viewModel.reminderStyle.collectAsState()

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showIntervalSheet by remember { mutableStateOf(false) }
    var showMicroBreakSheet by remember { mutableStateOf(false) }
    var showSoundSheet by remember { mutableStateOf(false) }
    var showDaySheet by remember { mutableStateOf(false) }
    var showStyleSheet by remember { mutableStateOf(false) }
    var customSoundUri by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            customSoundUri = uri.toString()
        }
    }

    val isCustomInterval = sittingInterval !in INTERVAL_OPTIONS
    val intervalDisplayText = if (isCustomInterval) stringResource(R.string.settings_custom_interval, sittingInterval) else stringResource(R.string.settings_interval_minutes, sittingInterval)
    val isCustomMicroBreak = microBreakInterval !in MICRO_BREAK_OPTIONS
    val microBreakDisplayText = if (isCustomMicroBreak) stringResource(R.string.settings_custom_interval, microBreakInterval) else stringResource(R.string.settings_interval_minutes, microBreakInterval)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.settings_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.W600,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item { SectionHeader(title = stringResource(R.string.settings_reminder)) }
        item {
            ReminderSettingsCard(
                intervalDisplayText = intervalDisplayText,
                microBreakDisplayText = microBreakDisplayText,
                isMicroBreakEnabled = isMicroBreakEnabled,
                notificationSoundIndex = notificationSoundIndex,
                onIntervalClick = { showIntervalSheet = true },
                onMicroBreakClick = { showMicroBreakSheet = true },
                onMicroBreakToggle = { viewModel.setMicroBreakEnabled(it) },
                onSoundClick = { showSoundSheet = true },
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
        item { SectionHeader(title = stringResource(R.string.settings_work_hours)) }
        item {
            WorkTimeSettingsCard(
                workStartHour = workStartHour,
                workEndHour = workEndHour,
                enabledDays = enabledDays,
                onStartTimeClick = { showStartTimePicker = true },
                onEndTimeClick = { showEndTimePicker = true },
                onDayClick = { showDaySheet = true },
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
        item { SectionHeader(title = stringResource(R.string.settings_notification)) }
        item {
            NotificationSettingsCard(
                isVibrationEnabled = isVibrationEnabled,
                isSoundEnabled = isSoundEnabled,
                isWaterReminderEnabled = isWaterReminderEnabled,
                isEyeReminderEnabled = isEyeReminderEnabled,
                reminderStyle = reminderStyle,
                onVibrationToggle = { viewModel.setVibrationEnabled(it) },
                onSoundToggle = { viewModel.setSoundEnabled(it) },
                onWaterToggle = { viewModel.setWaterReminderEnabled(it) },
                onEyeToggle = { viewModel.setEyeReminderEnabled(it) },
                onStyleClick = { showStyleSheet = true },
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.settings_version),
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showIntervalSheet) {
        IntervalPickerSheet(
            title = stringResource(R.string.settings_select_interval),
            options = INTERVAL_OPTIONS,
            currentValue = sittingInterval,
            isCustom = isCustomInterval,
            customMin = 1,
            customMax = 180,
            onDismiss = { showIntervalSheet = false },
            onConfirmPreset = { minutes -> viewModel.setSittingInterval(minutes); showIntervalSheet = false },
            onConfirmCustom = { minutes -> viewModel.setSittingInterval(minutes); showIntervalSheet = false },
        )
    }

    if (showMicroBreakSheet) {
        IntervalPickerSheet(
            title = stringResource(R.string.settings_select_micro_break),
            options = MICRO_BREAK_OPTIONS,
            currentValue = microBreakInterval,
            isCustom = isCustomMicroBreak,
            customMin = 1,
            customMax = 180,
            onDismiss = { showMicroBreakSheet = false },
            onConfirmPreset = { minutes -> viewModel.setMicroBreakInterval(minutes); showMicroBreakSheet = false },
            onConfirmCustom = { minutes -> viewModel.setMicroBreakInterval(minutes); showMicroBreakSheet = false },
        )
    }

    if (showSoundSheet) {
        SoundPickerSheet(
            currentIndex = notificationSoundIndex,
            currentCustomUri = notificationSoundUri,
            onDismiss = { showSoundSheet = false },
            onConfirm = { index, uri ->
                viewModel.setNotificationSoundIndex(index)
                viewModel.setNotificationSoundUri(uri)
                showSoundSheet = false
            },
            onPickCustomFile = { filePickerLauncher.launch(arrayOf("audio/mpeg", "audio/wav", "audio/ogg")) },
            customSoundUri = customSoundUri,
            onCustomSoundUriPicked = { customSoundUri = it },
        )
    }

    if (showDaySheet) {
        DayPickerSheet(
            currentDays = enabledDays,
            onDismiss = { showDaySheet = false },
            onConfirm = { viewModel.setEnabledDays(it); showDaySheet = false },
        )
    }

    if (showStartTimePicker) {
        TimePickerDialog(
            initialHour = workStartHour,
            onConfirm = { hour, _ -> viewModel.setWorkStartHour(hour); showStartTimePicker = false },
            onDismiss = { showStartTimePicker = false },
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            initialHour = workEndHour,
            onConfirm = { hour, _ -> viewModel.setWorkEndHour(hour); showEndTimePicker = false },
            onDismiss = { showEndTimePicker = false },
        )
    }

    if (showStyleSheet) {
        ReminderStyleSheet(
            currentStyle = reminderStyle,
            onStyleSelected = { viewModel.setReminderStyle(it) },
            onDismiss = { showStyleSheet = false },
        )
    }
}

@Composable
private fun ReminderSettingsCard(
    intervalDisplayText: String,
    microBreakDisplayText: String,
    isMicroBreakEnabled: Boolean,
    notificationSoundIndex: Int,
    onIntervalClick: () -> Unit,
    onMicroBreakClick: () -> Unit,
    onMicroBreakToggle: (Boolean) -> Unit,
    onSoundClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column {
            SettingRow(
                title = stringResource(R.string.settings_sitting_interval),
                subtitle = intervalDisplayText,
                onClick = onIntervalClick,
            ) {
                Text(intervalDisplayText, fontSize = 14.sp, color = BluePrimary, fontWeight = FontWeight.W500)
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = DividerGray)
            SettingRow(
                title = stringResource(R.string.settings_micro_break_interval),
                subtitle = microBreakDisplayText,
                onClick = onMicroBreakClick,
            ) {
                Text(microBreakDisplayText, fontSize = 14.sp, color = BluePrimary, fontWeight = FontWeight.W500)
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = DividerGray)
            SettingRow(
                title = stringResource(R.string.settings_micro_break_enabled),
                subtitle = if (isMicroBreakEnabled) stringResource(R.string.settings_enabled) else stringResource(R.string.settings_disabled),
            ) {
                Switch(
                    checked = isMicroBreakEnabled,
                    onCheckedChange = onMicroBreakToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = BluePrimary),
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = DividerGray)
            SettingRow(
                title = stringResource(R.string.settings_notification_sound),
                subtitle = soundNames().getOrElse(notificationSoundIndex) { stringResource(R.string.settings_sound_crisp) },
                onClick = onSoundClick,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        soundNames().getOrElse(notificationSoundIndex) { stringResource(R.string.settings_sound_crisp) },
                        fontSize = 14.sp, color = TextSecondary,
                    )
                    Text(">", fontSize = 14.sp, color = TextTertiary)
                }
            }
        }
    }
}

@Composable
private fun WorkTimeSettingsCard(
    workStartHour: Int,
    workEndHour: Int,
    enabledDays: Set<String>,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit,
    onDayClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column {
            SettingRow(
                title = stringResource(R.string.settings_work_start),
                subtitle = formatHour(workStartHour),
                onClick = onStartTimeClick,
            ) {
                Text(formatHour(workStartHour), fontSize = 14.sp, color = BluePrimary, fontWeight = FontWeight.W500)
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = DividerGray)
            SettingRow(
                title = stringResource(R.string.settings_work_end),
                subtitle = formatHour(workEndHour),
                onClick = onEndTimeClick,
            ) {
                Text(formatHour(workEndHour), fontSize = 14.sp, color = BluePrimary, fontWeight = FontWeight.W500)
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = DividerGray)
            SettingRow(
                title = stringResource(R.string.settings_reminder_date),
                subtitle = formatDaysSummary(enabledDays),
                onClick = onDayClick,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(formatDaysSummary(enabledDays), fontSize = 14.sp, color = TextSecondary)
                    Text(">", fontSize = 14.sp, color = TextTertiary)
                }
            }
        }
    }
}

@Composable
private fun NotificationSettingsCard(
    isVibrationEnabled: Boolean,
    isSoundEnabled: Boolean,
    isWaterReminderEnabled: Boolean,
    isEyeReminderEnabled: Boolean,
    reminderStyle: String,
    onVibrationToggle: (Boolean) -> Unit,
    onSoundToggle: (Boolean) -> Unit,
    onWaterToggle: (Boolean) -> Unit,
    onEyeToggle: (Boolean) -> Unit,
    onStyleClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column {
            SettingRow(
                title = stringResource(R.string.settings_vibration),
                subtitle = if (isVibrationEnabled) stringResource(R.string.settings_enabled) else stringResource(R.string.settings_disabled),
            ) {
                Switch(checked = isVibrationEnabled, onCheckedChange = onVibrationToggle, colors = SwitchDefaults.colors(checkedTrackColor = BluePrimary))
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = DividerGray)
            SettingRow(
                title = stringResource(R.string.settings_sound),
                subtitle = if (isSoundEnabled) stringResource(R.string.settings_enabled) else stringResource(R.string.settings_disabled),
            ) {
                Switch(checked = isSoundEnabled, onCheckedChange = onSoundToggle, colors = SwitchDefaults.colors(checkedTrackColor = BluePrimary))
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = DividerGray)
            SettingRow(
                title = stringResource(R.string.settings_water_reminder),
                subtitle = if (isWaterReminderEnabled) stringResource(R.string.settings_enabled) else stringResource(R.string.settings_disabled),
            ) {
                Switch(checked = isWaterReminderEnabled, onCheckedChange = onWaterToggle, colors = SwitchDefaults.colors(checkedTrackColor = BluePrimary))
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = DividerGray)
            SettingRow(
                title = stringResource(R.string.settings_eye_reminder),
                subtitle = if (isEyeReminderEnabled) stringResource(R.string.settings_enabled) else stringResource(R.string.settings_disabled),
            ) {
                Switch(checked = isEyeReminderEnabled, onCheckedChange = onEyeToggle, colors = SwitchDefaults.colors(checkedTrackColor = BluePrimary))
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = DividerGray)
            SettingRow(
                title = stringResource(R.string.settings_reminder_style),
                subtitle = ReminderCopywriter.styleNames[reminderStyle] ?: stringResource(R.string.settings_style_health_care),
                onClick = onStyleClick,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(ReminderCopywriter.styleNames[reminderStyle] ?: stringResource(R.string.settings_style_health_care), fontSize = 14.sp, color = TextSecondary)
                    Text(">", fontSize = 14.sp, color = TextTertiary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderStyleSheet(
    currentStyle: String,
    onStyleSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                stringResource(R.string.settings_select_style), fontSize = 18.sp, fontWeight = FontWeight.W600,
                color = TextPrimary, modifier = Modifier.padding(bottom = 16.dp),
            )
            ReminderCopywriter.styleNames.forEach { (key, name) ->
                Row(
                    Modifier.fillMaxWidth().clickable { onStyleSelected(key) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = currentStyle == key,
                        onClick = { onStyleSelected(key) },
                        colors = RadioButtonDefaults.colors(selectedColor = BluePrimary),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        name, fontSize = 15.sp,
                        color = if (currentStyle == key) BluePrimary else TextPrimary,
                        fontWeight = if (currentStyle == key) FontWeight.W500 else FontWeight.W400,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
            ) {
                Text(stringResource(R.string.settings_confirm), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.W500)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntervalPickerSheet(
    title: String,
    options: List<Int>,
    currentValue: Int,
    isCustom: Boolean,
    customMin: Int,
    customMax: Int,
    onDismiss: () -> Unit,
    onConfirmPreset: (Int) -> Unit,
    onConfirmCustom: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initialIndex = if (isCustom) options.size else options.indexOf(currentValue).coerceAtLeast(0)
    var selectedIndex by remember { mutableStateOf(initialIndex) }
    var isCustomSelected by remember { mutableStateOf(isCustom) }
    var customInput by remember { mutableStateOf(if (isCustom) currentValue.toString() else "") }
    var customError by remember { mutableStateOf(false) }
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardBackground,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.W600,
                color = TextPrimary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            WheelPicker(
                items = options + listOf(OPTION_CUSTOM),
                initialIndex = initialIndex,
                itemHeight = 48,
                visibleItems = 5,
                onIndexChanged = { index ->
                    if (index < options.size) {
                        selectedIndex = index
                        isCustomSelected = false
                    } else {
                        isCustomSelected = true
                    }
                },
                itemLabel = { value ->
                    if (value == OPTION_CUSTOM) context.getString(R.string.settings_custom) else context.getString(R.string.settings_interval_minutes, value)
                },
            )

            if (isCustomSelected) {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = customInput,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() }
                        if (filtered.length <= 3) {
                            customInput = filtered
                            val num = filtered.toIntOrNull()
                            customError = num == null || num < customMin || num > customMax
                        }
                    },
                    label = { Text(stringResource(R.string.settings_custom_minutes)) },
                    placeholder = { Text("${customMin}~${customMax}") },
                    isError = customError && customInput.isNotEmpty(),
                    supportingText = {
                        Text(
                            text = stringResource(R.string.settings_custom_range_hint, customMin, customMax),
                            fontSize = 12.sp,
                            color = if (customError && customInput.isNotEmpty()) Color(0xFFDC2626) else TextTertiary,
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BluePrimary,
                        cursorColor = BluePrimary,
                        focusedLabelColor = BluePrimary,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CardBackground,
                        contentColor = TextSecondary,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray),
                ) {
                    Text(stringResource(R.string.settings_cancel), fontSize = 14.sp)
                }
                Button(
                    onClick = {
                        if (isCustomSelected) {
                            val num = customInput.toIntOrNull()
                            if (num != null && num in customMin..customMax) {
                                onConfirmCustom(num)
                            }
                        } else {
                            onConfirmPreset(options[selectedIndex])
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                    enabled = !isCustomSelected || (customInput.isNotEmpty() && !customError),
                ) {
                    Text(stringResource(R.string.settings_confirm), color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun WheelPicker(
    items: List<Int>,
    initialIndex: Int,
    itemHeight: Int,
    visibleItems: Int,
    onIndexChanged: (Int) -> Unit,
    itemLabel: (Int) -> String,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val halfVisible = visibleItems / 2
    val totalHeight = itemHeight * visibleItems

    val centerIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty()) {
                initialIndex
            } else {
                val centerOffset = layoutInfo.viewportStartOffset + layoutInfo.viewportSize.height / 2
                layoutInfo.visibleItemsInfo
                    .minByOrNull { item ->
                        val itemCenter = item.offset + item.size / 2
                        kotlin.math.abs(itemCenter - centerOffset)
                    }
                    ?.index ?: initialIndex
            }
        }
    }

    LaunchedEffect(centerIndex) {
        onIndexChanged(centerIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight.dp),
        contentAlignment = Alignment.Center,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            userScrollEnabled = true,
        ) {
            item { Spacer(modifier = Modifier.height((itemHeight * halfVisible).dp)) }

            items(items.size) { index ->
                val value = items[index]
                val isSelected = index == centerIndex
                val alpha = if (isSelected) 1f else 0.4f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight.dp)
                        .clickable {
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = itemLabel(value),
                        fontSize = if (isSelected) 20.sp else 14.sp,
                        fontWeight = if (isSelected) FontWeight.W600 else FontWeight.W400,
                        color = if (isSelected) TextPrimary else TextTertiary,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height((itemHeight * halfVisible).dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = 0,
        is24Hour = true,
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TimePicker(state = state)
            TextButton(onClick = {
                onConfirm(state.hour, state.minute)
            }) {
                Text(stringResource(R.string.settings_ok), color = BluePrimary)
            }
        }
    }
}

private fun formatHour(hour: Int): String {
    return "%02d:00".format(hour)
}

@Composable
private fun formatDaysSummary(days: Set<String>): String {
    if (days.isEmpty()) return stringResource(R.string.settings_none)
    if (days == ALL_DAYS_KEYS) return stringResource(R.string.settings_every_day)
    if (days == WORKDAY_KEYS) return stringResource(R.string.settings_weekdays_range)
    if (days.size == 1) {
        val index = DAY_KEYS.indexOf(days.first())
        return if (index >= 0) dayLabels()[index] else days.first()
    }
    val sorted = DAY_KEYS.filter { it in days }
    val labels = sorted.map { key ->
        val idx = DAY_KEYS.indexOf(key)
        if (idx >= 0) dayLabels()[idx] else key
    }
    return labels.joinToString("、")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPickerSheet(
    currentDays: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedDays by remember { mutableStateOf(currentDays) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardBackground,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.settings_select_days),
                fontSize = 16.sp,
                fontWeight = FontWeight.W600,
                color = TextPrimary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DAY_KEYS.forEachIndexed { index, key ->
                    val isSelected = key in selectedDays
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 3.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) BluePrimary else DividerGray)
                            .clickable {
                                selectedDays = if (isSelected) {
                                    selectedDays - key
                                } else {
                                    selectedDays + key
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = dayLabels()[index],
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.W500 else FontWeight.W400,
                            color = if (isSelected) Color.White else TextSecondary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { selectedDays = WORKDAY_KEYS },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CardBackground,
                        contentColor = TextSecondary,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray),
                ) {
                    Text(stringResource(R.string.settings_workdays), fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { selectedDays = ALL_DAYS_KEYS },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CardBackground,
                        contentColor = TextSecondary,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray),
                ) {
                    Text(stringResource(R.string.settings_every_day), fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { selectedDays = emptySet() },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CardBackground,
                        contentColor = TextSecondary,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray),
                ) {
                    Text(stringResource(R.string.settings_clear), fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { onConfirm(selectedDays) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
            ) {
                Text(stringResource(R.string.settings_confirm), color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundPickerSheet(
    currentIndex: Int,
    currentCustomUri: String,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit,
    onPickCustomFile: () -> Unit,
    customSoundUri: String,
    onCustomSoundUriPicked: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedIndex by remember { mutableStateOf(currentIndex) }
    var playingIndex by remember { mutableStateOf(-1) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val resolvedUri = if (customSoundUri.isNotEmpty()) customSoundUri else currentCustomUri
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val soundUris = remember {
        val rm = android.media.RingtoneManager(context)
        rm.setType(android.media.RingtoneManager.TYPE_NOTIFICATION)
        val cursor = rm.cursor
        val uris = mutableListOf<Uri>()
        if (cursor != null) {
            for (i in 0 until minOf(5, cursor.count)) {
                uris.add(rm.getRingtoneUri(i))
            }
            cursor.close()
        }
        while (uris.size < 5) {
            uris.add(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION))
        }
        uris
    }

    fun stopAndRelease() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            reset()
            release()
        }
        mediaPlayer = null
        playingIndex = -1
    }

    fun playPreview(index: Int) {
        stopAndRelease()
        val uri = if (index < 5) soundUris[index] else Uri.parse(resolvedUri)
        if (uri == null || uri.toString().isEmpty()) return
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                prepare()
                start()
                playingIndex = index
                setOnCompletionListener {
                    stopAndRelease()
                }
            }
            scope.launch {
                delay(2000)
                if (playingIndex == index) {
                    stopAndRelease()
                }
            }
        } catch (_: Exception) {
            stopAndRelease()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            stopAndRelease()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = CardBackground,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.settings_select_sound),
                fontSize = 16.sp,
                fontWeight = FontWeight.W600,
                color = TextPrimary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            soundNames().forEachIndexed { index, name ->
                val isSelected = index == selectedIndex
                val isPlaying = index == playingIndex

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedIndex = index }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { selectedIndex = index },
                        colors = RadioButtonDefaults.colors(selectedColor = BluePrimary),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = name,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.W500 else FontWeight.W400,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            if (index == 5) {
                                onPickCustomFile()
                            } else {
                                playPreview(index)
                            }
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.settings_stop) else stringResource(R.string.settings_preview),
                            tint = if (isPlaying) BluePrimary else TextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            if (selectedIndex == 5) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (resolvedUri.isNotEmpty()) stringResource(R.string.settings_selected_prefix) + resolvedUri.substringAfterLast("/") else stringResource(R.string.settings_click_select_audio),
                    fontSize = 12.sp,
                    color = if (resolvedUri.isNotEmpty()) BluePrimary else TextTertiary,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        stopAndRelease()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CardBackground,
                        contentColor = TextSecondary,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray),
                ) {
                    Text(stringResource(R.string.settings_cancel), fontSize = 14.sp)
                }
                Button(
                    onClick = {
                        stopAndRelease()
                        val uri = if (selectedIndex == 5) resolvedUri else ""
                        onConfirm(selectedIndex, uri)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                    enabled = if (selectedIndex == 5) resolvedUri.isNotEmpty() else true,
                ) {
                    Text(stringResource(R.string.settings_confirm), color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}