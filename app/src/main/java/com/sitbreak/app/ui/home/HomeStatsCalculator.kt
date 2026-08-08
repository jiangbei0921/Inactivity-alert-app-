package com.sitbreak.app.ui.home

/**
 * 主页统计口径的纯函数实现。
 *
 * 之所以从 [HomeViewModel] 中抽离：ViewModel 依赖 Context / DataStore / Room，
 * 在 JVM 单元测试里构造成本高；而「完成率、活动时长」这类业务口径恰恰是最容易写错、
 * 也最需要回归保护的部分。抽成无副作用的纯函数后可 100% 被 JVM 单测覆盖。
 */
object HomeStatsCalculator {

    /** 单次站立按 3 分钟活动量折算。 */
    const val MINUTES_PER_STAND = 3f

    /**
     * 今日目标站立次数 = 工作时长(小时) * 60 / 提醒间隔(分钟)。
     *
     * @return 目标次数；当工作时段或间隔非法时返回 0，表示「无法计算目标」。
     */
    fun targetStandCount(workStartHour: Int, workEndHour: Int, intervalMinutes: Int): Int {
        if (intervalMinutes <= 0) return 0
        val workingHours = workEndHour - workStartHour
        if (workingHours <= 0) return 0
        return (workingHours * 60) / intervalMinutes
    }

    /**
     * 今日完成率，取值 [0f, 1f]。
     *
     * 无法计算目标（返回 0）时保持 [fallback]，避免把「未知」误显示成 0%。
     */
    fun completionRate(
        standCount: Int,
        workStartHour: Int,
        workEndHour: Int,
        intervalMinutes: Int,
        fallback: Float = 0f,
    ): Float {
        val target = targetStandCount(workStartHour, workEndHour, intervalMinutes)
        if (target <= 0) return fallback
        val safeCount = standCount.coerceAtLeast(0)
        return (safeCount.toFloat() / target).coerceIn(0f, 1f)
    }

    /** 今日活动时长（小时）。 */
    fun activeHours(standCount: Int): Float {
        val safeCount = standCount.coerceAtLeast(0)
        return safeCount * MINUTES_PER_STAND / 60f
    }
}
