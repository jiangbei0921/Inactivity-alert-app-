package com.sitbreak.app.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 每日小结的调度时间计算。
 *
 * 这段逻辑最容易踩的坑是「当天已过 9 点时算出负数延迟」，
 * WorkManager 收到负延迟会立刻执行，用户一装 App 就被推一条空的昨日小结。
 */
class DailySummaryScheduleTest {

    private fun calendarAt(hour: Int, minute: Int): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.MARCH)
            set(Calendar.DAY_OF_MONTH, 10)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    @Test
    fun `delay before target hour stays within the same day`() {
        // 07:00 -> 09:00 还有 120 分钟
        assertEquals(120L, DailySummarySchedule.initialDelayMinutes(calendarAt(7, 0)))
    }

    @Test
    fun `delay after target hour rolls over to next day`() {
        // 10:00 -> 次日 09:00 还有 23 小时
        assertEquals(23 * 60L, DailySummarySchedule.initialDelayMinutes(calendarAt(10, 0)))
    }

    @Test
    fun `delay exactly at target hour rolls over instead of firing immediately`() {
        assertEquals(24 * 60L, DailySummarySchedule.initialDelayMinutes(calendarAt(9, 0)))
    }

    @Test
    fun `delay is never negative and never exceeds one day`() {
        for (hour in 0..23) {
            val delay = DailySummarySchedule.initialDelayMinutes(calendarAt(hour, 30))
            assertTrue("hour=$hour 产生了负延迟 $delay", delay >= 0)
            assertTrue("hour=$hour 延迟超过一天 $delay", delay <= 24 * 60)
        }
    }

    @Test
    fun `caller calendar is not mutated`() {
        val now = calendarAt(7, 0)
        val before = now.timeInMillis
        DailySummarySchedule.initialDelayMinutes(now)
        DailySummarySchedule.yesterdayRange(now)
        assertEquals(before, now.timeInMillis)
    }

    @Test
    fun `yesterday range covers exactly one day and ends at today midnight`() {
        val now = calendarAt(14, 25)
        val (start, end) = DailySummarySchedule.yesterdayRange(now)

        assertTrue("区间必须左小右大", start < end)
        // 允许夏令时切换导致的 23h / 25h，只断言落在合理范围
        val hours = (end - start) / 3_600_000L
        assertTrue("区间应约为一天，实际 $hours 小时", hours in 23..25)

        val endCalendar = Calendar.getInstance().apply { timeInMillis = end }
        assertEquals(0, endCalendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, endCalendar.get(Calendar.MINUTE))
    }
}
