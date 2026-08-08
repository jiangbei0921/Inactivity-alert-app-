package com.sitbreak.app.ui.onboarding

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
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sitbreak.app.R
import com.sitbreak.app.ui.theme.BlueLight
import com.sitbreak.app.ui.theme.BluePrimary
import com.sitbreak.app.ui.theme.BorderGray
import com.sitbreak.app.ui.theme.PageBackground
import com.sitbreak.app.ui.theme.TextPrimary
import com.sitbreak.app.ui.theme.TextSecondary

private data class OnboardingPage(
    val icon: ImageVector,
    val titleRes: Int,
    val descRes: Int,
)

private val onboardingPages = listOf(
    OnboardingPage(Icons.Outlined.EventSeat, R.string.onboarding_p1_title, R.string.onboarding_p1_desc),
    OnboardingPage(Icons.Outlined.Notifications, R.string.onboarding_p2_title, R.string.onboarding_p2_desc),
    OnboardingPage(Icons.Outlined.Lock, R.string.onboarding_p3_title, R.string.onboarding_p3_desc),
)

/**
 * 首启引导（M4）。
 *
 * 设计取舍：不做「引导页里直接申请权限」——首启即弹权限框的通过率反而更低，
 * 这里只解释「为什么需要」，真正的申请交给主页上下文相关的横幅，用户理解成本更低。
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    when (uiState) {
        // 读取 DataStore 期间保持空白背景，避免老用户看到引导内容一闪而过
        OnboardingUiState.Loading -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PageBackground)
        )

        OnboardingUiState.Skip -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PageBackground)
            )
            LaunchedEffect(Unit) { onDone() }
        }

        OnboardingUiState.Show -> OnboardingContent(
            onFinish = { viewModel.complete(onDone) }
        )
    }
}

@Composable
private fun OnboardingContent(onFinish: () -> Unit) {
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val page = onboardingPages[pageIndex]
    val isLast = pageIndex == onboardingPages.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onFinish) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    fontSize = 14.sp,
                    color = TextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(BlueLight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = BluePrimary,
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = stringResource(page.titleRes),
            fontSize = 22.sp,
            fontWeight = FontWeight.W600,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(page.descRes),
            fontSize = 14.sp,
            fontWeight = FontWeight.W400,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onboardingPages.indices.forEach { index ->
                val active = index == pageIndex
                Box(
                    modifier = Modifier
                        .width(if (active) 20.dp else 8.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (active) BluePrimary else BorderGray)
                )
                if (index != onboardingPages.lastIndex) {
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (isLast) onFinish() else pageIndex += 1
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
        ) {
            Text(
                text = stringResource(
                    if (isLast) R.string.onboarding_start else R.string.onboarding_next
                ),
                fontSize = 16.sp,
                fontWeight = FontWeight.W600,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
