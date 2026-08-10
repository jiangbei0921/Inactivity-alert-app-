package com.sitbreak.app.ui.activity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.sitbreak.app.ui.components.AppCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitbreak.app.ui.theme.PageBackground
import com.sitbreak.app.ui.theme.TextPrimary
import com.sitbreak.app.ui.theme.TextSecondary
import com.sitbreak.app.ui.theme.Slate
import com.sitbreak.app.ui.theme.TintGreen
import com.sitbreak.app.ui.theme.TintOrange
import com.sitbreak.app.ui.theme.TintPurple
import com.sitbreak.app.ui.theme.TintRed
import com.sitbreak.app.ui.theme.TintTeal
import com.sitbreak.app.ui.theme.TintBlue

data class ActivityItem(
    val id: String,
    val name: String,
    val duration: String,
    val bgColor: Color,
    val steps: List<String>,
)

val activities = listOf(
    ActivityItem(
        id = "neck",
        name = "颈椎拉伸",
        duration = "30秒",
        bgColor = TintBlue,
        steps = listOf(
            "🔄 头部缓慢向左转，保持5秒",
            "🔄 头部缓慢向右转，保持5秒",
            "⬆️ 缓慢抬头望天，保持5秒",
            "⬇️ 缓慢低头看地，保持5秒",
            "🔄 重复上述动作2次",
        ),
    ),
    ActivityItem(
        id = "shoulder",
        name = "肩颈放松",
        duration = "45秒",
        bgColor = TintGreen,
        steps = listOf(
            "⬆️ 双肩同时向上耸起，保持3秒后放松",
            "🔄 双肩向前画圈5次",
            "🔄 双肩向后画圈5次",
            "✋ 左手摸右肩，右手轻推左肘，保持5秒",
            "✋ 右手摸左肩，左手轻推右肘，保持5秒",
        ),
    ),
    ActivityItem(
        id = "back",
        name = "腰背舒展",
        duration = "60秒",
        bgColor = TintOrange,
        steps = listOf(
            "🙆 双手交叉举过头顶，向上伸展，保持5秒",
            "↩️ 身体向左扭转，保持5秒",
            "↪️ 身体向右扭转，保持5秒",
            "🧘 双手扶腰，缓慢后仰，保持5秒",
            "🙇 缓慢弯腰，双手触地，保持5秒",
            "🔄 重复上述动作1次",
        ),
    ),
    ActivityItem(
        id = "legs",
        name = "下肢活动",
        duration = "45秒",
        bgColor = TintPurple,
        steps = listOf(
            "🦶 坐在椅子上，抬起左脚伸直，保持5秒",
            "🦶 抬起右脚伸直，保持5秒",
            "🔄 双脚脚踝顺时针旋转5圈",
            "🔄 双脚脚踝逆时针旋转5圈",
            "🚶 原地踏步20次",
        ),
    ),
    ActivityItem(
        id = "eye",
        name = "20-20-20护眼",
        duration = "60秒",
        bgColor = TintRed,
        steps = listOf(
            "👀 看向20英尺（约6米）外的物体",
            "👁️ 保持视线在远处物体上20秒",
            "🔄 眼球顺时针缓慢转动5圈",
            "🔄 眼球逆时针缓慢转动5圈",
            "😌 闭眼放松10秒",
            "👐 双手搓热，轻敷双眼10秒",
        ),
    ),
    ActivityItem(
        id = "breath",
        name = "深呼吸放松",
        duration = "30秒",
        bgColor = TintTeal,
        steps = listOf(
            "🫁 用鼻子缓慢吸气4秒，感受腹部鼓起",
            "🫁 屏住呼吸4秒",
            "😤 用嘴巴缓慢呼气6秒",
            "🫁 重复上述呼吸循环3次",
        ),
    ),
)

@Composable
fun HealthCenterScreen(
    onNavigateToDetail: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground),
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "活动中心",
            fontSize = 17.sp,
            fontWeight = FontWeight.W600,
            color = TextPrimary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(activities) { activity ->
                ActivityCard(
                    activity = activity,
                    onClick = { onNavigateToDetail(activity.id) },
                )
            }
        }
    }
}

@Composable
private fun ActivityCard(
    activity: ActivityItem,
    onClick: () -> Unit,
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        containerColor = activity.bgColor,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                ActivityFigure(activityId = activity.id)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = activity.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.W600,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = activity.duration,
                fontSize = 12.sp,
                fontWeight = FontWeight.W400,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ActivityFigure(activityId: String) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val strokeColor = Slate
        val strokeWidth = 3f

        when (activityId) {
            "neck" -> {
                drawCircle(
                    color = strokeColor,
                    radius = w * 0.12f,
                    center = Offset(cx, cy - h * 0.25f),
                    style = Stroke(strokeWidth),
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(cx, cy - h * 0.13f),
                    end = Offset(cx, cy + h * 0.1f),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(cx, cy - h * 0.05f),
                    end = Offset(cx - w * 0.2f, cy - h * 0.12f),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(cx, cy - h * 0.05f),
                    end = Offset(cx + w * 0.2f, cy - h * 0.12f),
                    strokeWidth = strokeWidth,
                )
            }
            "shoulder" -> {
                drawCircle(
                    color = strokeColor,
                    radius = w * 0.1f,
                    center = Offset(cx, cy - h * 0.28f),
                    style = Stroke(strokeWidth),
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(cx, cy - h * 0.18f),
                    end = Offset(cx, cy + h * 0.12f),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(cx, cy - h * 0.08f),
                    end = Offset(cx - w * 0.28f, cy - h * 0.02f),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(cx, cy - h * 0.08f),
                    end = Offset(cx + w * 0.28f, cy - h * 0.02f),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(cx, cy + h * 0.12f),
                    end = Offset(cx - w * 0.12f, cy + h * 0.28f),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(cx, cy + h * 0.12f),
                    end = Offset(cx + w * 0.12f, cy + h * 0.28f),
                    strokeWidth = strokeWidth,
                )
            }
            "back" -> {
                drawCircle(
                    color = strokeColor,
                    radius = w * 0.1f,
                    center = Offset(cx, cy - h * 0.3f),
                    style = Stroke(strokeWidth),
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(cx, cy - h * 0.2f),
                    end = Offset(cx, cy + h * 0.1f),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(cx, cy - h * 0.15f),
                    end = Offset(cx - w * 0.2f, cy - h * 0.05f),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(cx, cy - h * 0.15f),
                    end = Offset(cx + w * 0.2f, cy - h * 0.05f),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(cx, cy + h * 0.1f),
                    end = Offset(cx - w * 0.12f, cy + h * 0.28f),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(cx, cy + h * 0.1f),
                    end = Offset(cx + w * 0.12f, cy + h * 0.28f),
                    strokeWidth = strokeWidth,
                )
                val path = Path().apply {
                    moveTo(cx - w * 0.3f, cy - h * 0.35f)
                    lineTo(cx + w * 0.3f, cy - h * 0.35f)
                    lineTo(cx + w * 0.15f, cy - h * 0.3f)
                    lineTo(cx - w * 0.15f, cy - h * 0.3f)
                    close()
                }
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(strokeWidth),
                )
            }
            "legs" -> {
                drawCircle(
                    color = strokeColor,
                    radius = w * 0.1f,
                    center = Offset(cx, cy - h * 0.3f),
                    style = Stroke(strokeWidth),
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(cx, cy - h * 0.2f),
                    end = Offset(cx, cy + h * 0.05f),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(cx, cy - h * 0.15f),
                    end = Offset(cx - w * 0.18f, cy - h * 0.08f),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(cx, cy - h * 0.15f),
                    end = Offset(cx + w * 0.18f, cy - h * 0.08f),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(cx, cy + h * 0.05f),
                    end = Offset(cx - w * 0.15f, cy + h * 0.25f),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(cx, cy + h * 0.05f),
                    end = Offset(cx + w * 0.15f, cy + h * 0.25f),
                    strokeWidth = strokeWidth,
                )
            }
            "eye" -> {
                drawCircle(
                    color = strokeColor,
                    radius = w * 0.13f,
                    center = Offset(cx - w * 0.15f, cy - h * 0.05f),
                    style = Stroke(strokeWidth),
                )
                drawCircle(
                    color = strokeColor,
                    radius = w * 0.13f,
                    center = Offset(cx + w * 0.15f, cy - h * 0.05f),
                    style = Stroke(strokeWidth),
                )
                drawCircle(
                    color = strokeColor,
                    radius = w * 0.04f,
                    center = Offset(cx - w * 0.15f, cy - h * 0.05f),
                )
                drawCircle(
                    color = strokeColor,
                    radius = w * 0.04f,
                    center = Offset(cx + w * 0.15f, cy - h * 0.05f),
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(cx - w * 0.28f, cy - h * 0.05f),
                    end = Offset(cx - w * 0.02f, cy - h * 0.05f),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(cx + w * 0.02f, cy - h * 0.05f),
                    end = Offset(cx + w * 0.28f, cy - h * 0.05f),
                    strokeWidth = strokeWidth,
                )
            }
            "breath" -> {
                drawCircle(
                    color = strokeColor,
                    radius = w * 0.18f,
                    center = Offset(cx, cy + h * 0.05f),
                    style = Stroke(strokeWidth),
                )
                val path = Path().apply {
                    moveTo(cx - w * 0.15f, cy - h * 0.25f)
                    quadraticBezierTo(cx - w * 0.25f, cy - h * 0.05f, cx - w * 0.15f, cy + h * 0.05f)
                }
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(strokeWidth),
                )
                val path2 = Path().apply {
                    moveTo(cx + w * 0.15f, cy - h * 0.25f)
                    quadraticBezierTo(cx + w * 0.25f, cy - h * 0.05f, cx + w * 0.15f, cy + h * 0.05f)
                }
                drawPath(
                    path = path2,
                    color = strokeColor,
                    style = Stroke(strokeWidth),
                )
            }
        }
    }
}