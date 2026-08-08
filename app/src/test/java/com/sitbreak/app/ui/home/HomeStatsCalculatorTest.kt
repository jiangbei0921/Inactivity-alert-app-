package com.sitbreak.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 主页统计口径的边界回归测试。
 *
 * 覆盖重点是「除零 / 负值 / 上限截断 / 目标不可计算」这四类线上最容易崩或算错的场景。
 */
class HomeStatsCalculatorTest {

    private val delta = 0.0001f

    @Test
    fun `target stand count is working minutes divided by interval`() {
        // 9:00-18:00 共 9 小时 = 540 分钟，间隔 45 分钟 -> 12 次
        assertEquals(12, HomeStatsCalculator.targetStandCount(9, 18, 45))
    }

    @Test
    fun `target stand count returns zero when interval is not positive`() {
        assertEquals(0, HomeStatsCalculator.targetStandCount(9, 18, 0))
        assertEquals(0, HomeStatsCalculator.targetStandCount(9, 18, -30))
    }

    @Test
    fun `target stand count returns zero when work range is invalid`() {
        assertEquals(0, HomeStatsCalculator.targetStandCount(18, 9, 45))
        assertEquals(0, HomeStatsCalculator.targetStandCount(9, 9, 45))
    }

    @Test
    fun `completion rate is count over target`() {
        // 目标 12 次，已完成 6 次 -> 0.5
        assertEquals(0.5f, HomeStatsCalculator.completionRate(6, 9, 18, 45), delta)
    }

    @Test
    fun `completion rate is capped at one`() {
        assertEquals(1f, HomeStatsCalculator.completionRate(99, 9, 18, 45), delta)
    }

    @Test
    fun `completion rate never goes below zero`() {
        assertEquals(0f, HomeStatsCalculator.completionRate(-5, 9, 18, 45), delta)
    }

    @Test
    fun `completion rate keeps fallback when target is not computable`() {
        // 间隔非法：不应把「未知」写成 0%，而是保留旧值
        assertEquals(0.42f, HomeStatsCalculator.completionRate(6, 9, 18, 0, fallback = 0.42f), delta)
        assertEquals(0.42f, HomeStatsCalculator.completionRate(6, 18, 9, 45, fallback = 0.42f), delta)
    }

    @Test
    fun `completion rate does not divide by zero when interval exceeds work span`() {
        // 540 分钟工作时长 / 600 分钟间隔 = 0 次目标 -> 走 fallback，不能抛 ArithmeticException
        assertEquals(0f, HomeStatsCalculator.completionRate(1, 9, 18, 600), delta)
    }

    @Test
    fun `active hours converts stand count by three minutes each`() {
        assertEquals(0f, HomeStatsCalculator.activeHours(0), delta)
        assertEquals(0.05f, HomeStatsCalculator.activeHours(1), delta)
        assertEquals(1f, HomeStatsCalculator.activeHours(20), delta)
    }

    @Test
    fun `active hours clamps negative count`() {
        assertEquals(0f, HomeStatsCalculator.activeHours(-3), delta)
    }
}
