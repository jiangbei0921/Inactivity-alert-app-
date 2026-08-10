package com.sitbreak.app.ui.home

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitbreak.app.R
import com.sitbreak.app.ui.components.rememberHapticClick
import com.sitbreak.app.ui.theme.AccentOrange
import com.sitbreak.app.ui.theme.BlueLight
import com.sitbreak.app.ui.theme.BluePrimary
import com.sitbreak.app.ui.theme.CardBackground
import com.sitbreak.app.ui.theme.RadiusButton
import com.sitbreak.app.ui.theme.SuccessGreen
import com.sitbreak.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay

/**
 * 全屏「连续天数达成」庆祝浮层（纯本地渲染，无网络依赖）。
 * 出现时触发一次触感反馈，3.5 秒后自动关闭，也可点击/按钮手动关闭。
 */
@Composable
fun StreakCelebrationOverlay(milestone: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(milestone) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(140, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(140)
            }
        }
        delay(3500)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        ConfettiCanvas(Modifier.fillMaxSize())
        AnimatedVisibility(
            visible = true,
            enter = scaleIn(initialScale = 0.6f) + fadeIn(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(BlueLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocalFireDepartment,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(56.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.streak_celebration_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.White,
                )
                Text(
                    text = stringResource(R.string.streak_days, milestone),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.W800,
                    color = AccentOrange,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = rememberHapticClick { onDismiss() },
                    shape = RadiusButton,
                    colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
                ) {
                    Text(stringResource(R.string.streak_continue), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.W500)
                }
            }
        }
    }
}

/**
 * 轻量彩带动画：挂载时从顶部洒落一次，约 2.4s 后自动停止（progress 收敛到 1 后不再重绘关键帧）。
 * 完全基于 Compose Canvas，不引入任何依赖，也不联网。
 */
@Composable
private fun ConfettiCanvas(modifier: Modifier = Modifier, durationMs: Int = 2400) {
    val particles = remember {
        val colors = listOf(AccentOrange, SuccessGreen, BluePrimary, Color(0xFFDC2626), Color(0xFF075985), Color(0xFF92400E))
        List(48) { i ->
            ConfettiParticle(
                x0 = 0.08f + 0.84f * ((i * 37) % 100) / 100f,
                delay = (i % 10) / 10f * 0.4f,
                color = colors[i % colors.size],
                size = 6f + (i % 4) * 3f,
                drift = ((i * 53) % 100) / 100f * 0.12f - 0.06f,
            )
        }
    }

    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = durationMs, easing = LinearEasing),
        label = "confetti",
    )

    Canvas(modifier = modifier) {
        particles.forEach { p ->
            val local = ((progress - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val x = (p.x0 + p.drift * local) * size.width
            val y = local * size.height * 0.9f
            val alpha = (1f - local).coerceIn(0.25f, 1f)
            drawCircle(color = p.color, radius = p.size, center = Offset(x, y), alpha = alpha)
        }
    }
}

private data class ConfettiParticle(
    val x0: Float,
    val delay: Float,
    val color: Color,
    val size: Float,
    val drift: Float,
)
