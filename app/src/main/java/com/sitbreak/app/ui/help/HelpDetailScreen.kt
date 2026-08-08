package com.sitbreak.app.ui.help

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitbreak.app.ui.theme.BluePrimary
import com.sitbreak.app.ui.theme.CardBackground
import com.sitbreak.app.ui.theme.PageBackground
import com.sitbreak.app.ui.theme.TextPrimary
import com.sitbreak.app.ui.theme.TextSecondary

@Composable
fun HelpDetailScreen(
    helpId: String,
    onBack: () -> Unit,
) {
    val title = when (helpId) {
        "usage" -> "如何使用 站一站"
        "audience" -> "适用人群"
        "dangers" -> "久坐的危害"
        "contact" -> "联系我们"
        else -> "帮助"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground),
    ) {
        TopBar(title = title, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            when (helpId) {
                "usage" -> UsageContent()
                "audience" -> AudienceContent()
                "dangers" -> DangersContent()
                "contact" -> ContactContent()
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
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
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.W600,
            color = TextPrimary,
        )
    }
}

@Composable
@Suppress("DEPRECATION")
private fun UsageContent() {
    val steps = listOf(
        Triple(Icons.Outlined.PhoneAndroid, "步骤一", "打开 APP，点击主页「开始计时」按钮启动久坐计时"),
        Triple(Icons.Outlined.Timer, "步骤二", "在设置页设置提醒间隔（默认 45 分钟）"),
        Triple(Icons.Outlined.SelfImprovement, "步骤三", "收到提醒通知后，点击「我站起来了」记录打卡"),
        Triple(Icons.Outlined.Bolt, "步骤四", "在统计页查看健康数据和完成趋势"),
        Triple(Icons.Outlined.MenuBook, "步骤五", "自定义工作时段，避免非工作时间被打扰"),
    )

    steps.forEach { (icon, stepTitle, desc) ->
        DetailCard(
            icon = icon,
            iconBgColor = Color(0xFFDBEAFE),
            iconTint = BluePrimary,
            title = stepTitle,
            content = desc,
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
@Suppress("DEPRECATION")
private fun AudienceContent() {
    val audiences = listOf(
        Triple(Icons.Outlined.Monitor, "办公室白领", "长时间坐在电脑前处理文件"),
        Triple(Icons.Outlined.Psychology, "程序员 / 设计师", "专注工作容易忘记休息"),
        Triple(Icons.Outlined.SelfImprovement, "远程工作者", "居家办公缺少自然走动机会"),
        Triple(Icons.Outlined.School, "学生群体", "备考期间长时间伏案学习"),
        Triple(Icons.Outlined.SportsEsports, "游戏玩家", "长时间游戏忽略身体健康"),
        Triple(Icons.Outlined.MenuBook, "写作者 / 自由职业者", "创作时忘记时间"),
    )

    audiences.forEach { (icon, title, desc) ->
        DetailCard(
            icon = icon,
            iconBgColor = Color(0xFFDCFCE7),
            iconTint = Color(0xFF16A34A),
            title = title,
            content = desc,
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun DangersContent() {
    val dangers = listOf(
        Triple(Icons.Outlined.FavoriteBorder, "心血管风险", "久坐减缓血液循环，增加心脏病和中风风险"),
        Triple(Icons.Outlined.SelfImprovement, "脊椎损伤", "长期前倾姿势导致颈椎病、腰椎间盘突出"),
        Triple(Icons.Outlined.Bolt, "代谢下降", "久坐降低代谢率，容易引发肥胖和 2 型糖尿病"),
        Triple(Icons.Outlined.Favorite, "下肢静脉曲张", "腿部血液回流受阻，形成静脉曲张"),
        Triple(Icons.Outlined.Mood, "情绪影响", "缺乏运动导致多巴胺分泌减少，易产生焦虑抑郁"),
        Triple(Icons.Outlined.Visibility, "眼部疲劳", "长时间盯屏幕加重视疲劳和干眼症"),
    )

    dangers.forEach { (icon, title, desc) ->
        DetailCard(
            icon = icon,
            iconBgColor = Color(0xFFFFEDD5),
            iconTint = Color(0xFFEA580C),
            title = title,
            content = desc,
        )
        Spacer(modifier = Modifier.height(10.dp))
    }

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
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF9C3)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = Color(0xFFCA8A04),
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "世界卫生组织建议：每坐 30~60 分钟应起身活动 5~10 分钟",
                fontSize = 13.sp,
                color = Color(0xFF92400E),
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun ContactContent() {
    val context = LocalContext.current

    DetailCard(
        icon = Icons.Outlined.Email,
        iconBgColor = Color(0xFFFEE2E2),
        iconTint = Color(0xFFDC2626),
        title = "反馈邮箱",
        content = "2185428966@qq.com",
    )

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:2185428966@qq.com")
                putExtra(Intent.EXTRA_SUBJECT, "站一站 意见反馈")
            }
            context.startActivity(intent)
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
    ) {
        Icon(
            imageVector = Icons.Outlined.Email,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "发送意见反馈",
            fontSize = 14.sp,
            fontWeight = FontWeight.W500,
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "© 2026 站一站",
        fontSize = 12.sp,
        color = TextSecondary,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun DetailCard(
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    title: String,
    content: String,
) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = content,
                    fontSize = 13.sp,
                    color = Color(0xFF374151),
                    lineHeight = 22.sp,
                )
            }
        }
    }
}