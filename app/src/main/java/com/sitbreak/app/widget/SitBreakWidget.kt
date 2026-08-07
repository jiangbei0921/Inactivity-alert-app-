package com.sitbreak.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sitbreak.app.data.CheckInRepository
import com.sitbreak.app.data.TimerSettingsDataStore
import com.sitbreak.app.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import java.util.Calendar

class SitBreakWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val standCount = try {
            val repository = CheckInRepository(AppDatabase.getInstance(context).checkInDao())
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            val endOfDay = calendar.timeInMillis
            repository.getTodayStandCount(startOfDay, endOfDay)
        } catch (e: Exception) {
            0
        }

        val intervalMinutes = try {
            TimerSettingsDataStore(context).sittingIntervalMinutes.first()
        } catch (e: Exception) {
            45
        }

        provideContent {
            WidgetContent(standCount, intervalMinutes)
        }
    }

    @Composable
    private fun WidgetContent(standCount: Int, intervalMinutes: Int) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color.White))
                .padding(Dp(12f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                Text(
                    text = "站一站",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF2563EB)),
                        fontSize = Sp(14f),
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "今日站立 $standCount 次",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF6B7280)),
                        fontSize = Sp(12f)
                    )
                )
                Text(
                    text = "下次提醒约 $intervalMinutes 分钟后",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF9CA3AF)),
                        fontSize = Sp(11f)
                    )
                )
            }
        }
    }
}

class SitBreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SitBreakWidget()
}
