package com.sitbreak.app.ui.achievements

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.sitbreak.app.R
import com.sitbreak.app.ui.components.AppCard
import com.sitbreak.app.ui.theme.AccentOrange
import com.sitbreak.app.ui.theme.CardBackground
import com.sitbreak.app.ui.theme.DividerGray
import com.sitbreak.app.ui.theme.PageBackground
import com.sitbreak.app.ui.theme.SuccessGreen
import com.sitbreak.app.ui.theme.TextPrimary
import com.sitbreak.app.ui.theme.TextSecondary
import com.sitbreak.app.ui.theme.TextTertiary
import com.sitbreak.app.ui.theme.TintOrange

@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = hiltViewModel(),
) {
    val list by viewModel.ui.collectAsState()
    val unlocked by viewModel.unlockedCount.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground),
    ) {
        TopBar(onBack = onBack, unlocked = unlocked, total = list.size)

        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中…", fontSize = 13.sp, color = TextSecondary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list) { AchievementCard(it) }
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, unlocked: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(CardBackground)
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.achievements_title), fontSize = 18.sp, fontWeight = FontWeight.W700, color = TextPrimary)
            Text(
                stringResource(R.string.achievements_unlocked, unlocked, total),
                fontSize = 12.sp,
                color = TextSecondary,
            )
        }
        Icon(
            imageVector = Icons.Outlined.EmojiEvents,
            contentDescription = null,
            tint = AccentOrange,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun AchievementCard(item: AchievementUi) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (item.unlocked) TintOrange else DividerGray),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.achievement.icon,
                    contentDescription = null,
                    tint = if (item.unlocked) AccentOrange else TextTertiary,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = item.achievement.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.W600,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.achievement.description,
                fontSize = 11.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (item.unlocked) "已达成" else "${item.progress}/${item.goal}",
                fontSize = 11.sp,
                fontWeight = FontWeight.W500,
                color = if (item.unlocked) SuccessGreen else TextTertiary,
            )
        }
    }
}
