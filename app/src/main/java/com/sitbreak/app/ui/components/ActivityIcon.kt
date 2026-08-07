package com.sitbreak.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sitbreak.app.ui.theme.BlueLight
import com.sitbreak.app.ui.theme.BluePrimary
import com.sitbreak.app.ui.theme.SuccessBg
import com.sitbreak.app.ui.theme.SuccessGreen
import com.sitbreak.app.ui.theme.WarningBg
import com.sitbreak.app.ui.theme.WarningYellow

enum class ActivityType {
    STAND_UP,
    SNOOZED,
    MICRO_BREAK
}

@Composable
fun ActivityIcon(
    type: ActivityType,
    modifier: Modifier = Modifier,
    size: Int = 28
) {
    val (bgColor, iconColor, icon) = when (type) {
        ActivityType.STAND_UP -> Triple(SuccessBg, SuccessGreen, Icons.Outlined.Check)
        ActivityType.SNOOZED -> Triple(WarningBg, WarningYellow, Icons.Outlined.Schedule)
        ActivityType.MICRO_BREAK -> Triple(BlueLight, BluePrimary, Icons.Outlined.SelfImprovement)
    }

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size((size * 0.55f).dp),
        )
    }
}