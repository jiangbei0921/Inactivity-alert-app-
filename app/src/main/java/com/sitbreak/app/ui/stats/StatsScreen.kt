package com.sitbreak.app.ui.stats

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sitbreak.app.ui.components.StatCard
import com.sitbreak.app.ui.theme.BlueLight
import com.sitbreak.app.ui.theme.BluePrimary
import com.sitbreak.app.ui.theme.CardBackground
import com.sitbreak.app.ui.theme.DividerGray
import com.sitbreak.app.ui.theme.PageBackground
import com.sitbreak.app.ui.theme.TextPrimary
import com.sitbreak.app.ui.theme.TextSecondary

@Composable
fun StatsScreen(viewModel: StatsViewModel = viewModel()) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val weeklyAverage by viewModel.weeklyAverage.collectAsState()
    val dailyCounts by viewModel.dailyCounts.collectAsState()
    val totalCheckIns by viewModel.totalCheckIns.collectAsState()
    val longestStreak by viewModel.longestStreak.collectAsState()
    val yearlyCompletionRate by viewModel.yearlyCompletionRate.collectAsState()
    val monthlyStandCounts by viewModel.monthlyStandCounts.collectAsState()
    val bestMonth by viewModel.bestMonth.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "健康统计",
            fontSize = 17.sp,
            fontWeight = FontWeight.W600,
            color = TextPrimary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))

        SegmentedControl(
            items = listOf("本周", "本月", "本年"),
            selectedIndex = selectedTab,
            onSelectionChange = { viewModel.selectTab(it) },
        )

        Spacer(modifier = Modifier.height(20.dp))

        when (selectedTab) {
            0 -> WeeklyContent(weeklyAverage, dailyCounts, totalCheckIns, longestStreak)
            1 -> MonthlyContent(totalCheckIns, longestStreak)
            2 -> YearlyContent(yearlyCompletionRate, monthlyStandCounts, bestMonth)
        }
    }
}

@Composable
private fun SegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DividerGray)
            .padding(3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) CardBackground else Color.Transparent)
                        .then(
                            if (isSelected) Modifier.shadow(
                                elevation = 2.dp,
                                shape = RoundedCornerShape(12.dp),
                                ambientColor = Color.Black.copy(alpha = 0.08f),
                                spotColor = Color.Black.copy(alpha = 0.08f),
                            ) else Modifier
                        )
                        .clickable { onSelectionChange(index) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.W600 else FontWeight.W400,
                        color = if (isSelected) BluePrimary else TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyContent(
    weeklyAverage: Float,
    dailyCounts: List<StatsViewModel.DailyBarData>,
    totalCheckIns: Int,
    longestStreak: Int,
) {
    Column {
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
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${(weeklyAverage * 100).toInt()}%",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.W700,
                    color = TextPrimary,
                )
                Text(
                    text = "本周平均",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W400,
                    color = TextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "过去 7 天",
            fontSize = 14.sp,
            fontWeight = FontWeight.W600,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(8.dp))

        BarChart(
            data = dailyCounts,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = "累计站立",
                value = "$totalCheckIns",
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = "最长连续",
                value = "$longestStreak 天",
            )
        }
    }
}

@Composable
private fun MonthlyContent(
    totalCheckIns: Int,
    longestStreak: Int,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = "累计站立",
                value = "$totalCheckIns",
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = "最长连续",
                value = "$longestStreak 天",
            )
        }
    }
}

@Composable
private fun YearlyContent(
    yearlyCompletionRate: Float,
    monthlyStandCounts: List<StatsViewModel.MonthlyBarData>,
    bestMonth: String,
) {
    Column {
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
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${(yearlyCompletionRate * 100).toInt()}%",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.W700,
                    color = TextPrimary,
                )
                Text(
                    text = "本年完成率",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W400,
                    color = TextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "月度统计",
            fontSize = 14.sp,
            fontWeight = FontWeight.W600,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(8.dp))

        MonthlyBarChart(
            data = monthlyStandCounts,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

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
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "🏆 最佳月份",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bestMonth,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W700,
                    color = TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun BarChart(
    data: List<StatsViewModel.DailyBarData>,
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) return

    val maxY = data.maxOf { it.target.coerceAtLeast(1) }

    Canvas(modifier = modifier) {
        val barCount = data.size
        val barWidth = (size.width - 20.dp.toPx()) / barCount
        val gap = barWidth * 0.25f
        val barActualWidth = barWidth - gap
        val chartHeight = size.height - 30.dp.toPx()
        val bottomY = chartHeight + 5.dp.toPx()

        val countPaint = remember {
            android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#2563EB")
                textSize = 10.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
            }
        }
        val dayLabelPaint = remember {
            android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#6B7280")
                textSize = 11.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
            }
        }

        data.forEachIndexed { index, item ->
            val barHeight = if (maxY > 0) {
                (item.count.toFloat() / maxY) * chartHeight
            } else 0f

            val x = index * barWidth + gap / 2f
            val y = bottomY - barHeight

            val barColor = if (item.target > 0 && item.count >= item.target * 0.8f) {
                BluePrimary
            } else {
                BlueLight
            }

            drawRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barActualWidth, barHeight),
            )

            drawContext.canvas.nativeCanvas.drawText(
                item.count.toString(),
                x + barActualWidth / 2f,
                y - 4.dp.toPx(),
                countPaint
            )

            drawContext.canvas.nativeCanvas.drawText(
                item.dayLabel,
                x + barActualWidth / 2f,
                bottomY + 16.dp.toPx(),
                dayLabelPaint
            )
        }
    }
}

@Composable
private fun MonthlyBarChart(
    data: List<StatsViewModel.MonthlyBarData>,
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) return

    val maxY = data.maxOf { it.count.coerceAtLeast(1) }

    Canvas(modifier = modifier) {
        val barCount = data.size
        val barWidth = (size.width - 10.dp.toPx()) / barCount
        val gap = barWidth * 0.2f
        val barActualWidth = barWidth - gap
        val chartHeight = size.height - 30.dp.toPx()
        val bottomY = chartHeight + 5.dp.toPx()

        val monthLabelPaint = remember {
            android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#6B7280")
                textSize = 10.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
            }
        }

        data.forEachIndexed { index, item ->
            val barHeight = if (maxY > 0) {
                (item.count.toFloat() / maxY) * chartHeight
            } else 0f

            val x = index * barWidth + gap / 2f
            val y = bottomY - barHeight

            val barColor = if (item.target > 0 && item.count >= item.target * 0.8f) {
                BluePrimary
            } else {
                BlueLight
            }

            drawRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barActualWidth, barHeight),
            )

            drawContext.canvas.nativeCanvas.drawText(
                item.monthLabel,
                x + barActualWidth / 2f,
                bottomY + 16.dp.toPx(),
                monthLabelPaint
            )
        }
    }
}