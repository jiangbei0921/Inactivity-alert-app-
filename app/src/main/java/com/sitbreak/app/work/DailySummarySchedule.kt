package com.sitbreak.app.work

import java.util.Calendar

/**
 * 每日小结的调度时间计算。
 *
 * 单独抽出来是为了让它不依赖任何 Android 类（Worker 基类会把 android.* 拖进测试类路径），
 * 从而能用最轻量的 JVM 单测覆盖「跨天 / 边界时刻」这类真正容易出错的分支。
 */
object DailySummarySchedule {

    /** 每日小结的触发时刻（本地时间，24 小时制）。 */
    const val TARGET_HOUR = 9

    /**
     * 距离下一个 [TARGET_HOUR] 点还有多少分钟。
     *
     * 关键约束：返回值必须为非负。WorkManager 收到负的 initialDelay 会立即执行，
     * 会导致用户刚装完 App 就收到一条内容为空的「昨日小结」。
     */
    fun initialDelayMinutes(now: Calendar = Calendar.getInstance()): Long {
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, TARGET_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        return (target.timeInMillis - now.timeInMillis) / 60_000L
    }

    /** 昨天 00:00（含）到今天 00:00（不含）的时间区间。 */
    fun yesterdayRange(now: Calendar = Calendar.getInstance()): Pair<Long, Long> {
        val calendar = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, -1)
        return calendar.timeInMillis to todayStart
    }
}
