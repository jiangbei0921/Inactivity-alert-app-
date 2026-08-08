package com.sitbreak.app

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * 计时状态机的 Flow 行为测试。
 *
 * [TimerStateHolder] 是前台服务与 UI 之间唯一的状态真源，
 * 一旦丢发射或发重复值，UI 上就会出现「按钮点了没反应」这类难复现的问题，
 * 因此用 Turbine 对 StateFlow 的发射序列做断言。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerStateHolderTest {

    @Before
    fun resetState() {
        TimerStateHolder.setState(TimerState.Idle)
        TimerStateHolder.lastStandVerified = null
    }

    @Test
    fun `initial state is idle`() = runTest {
        TimerStateHolder.state.test {
            assertEquals(TimerState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state flow emits full timer lifecycle in order`() = runTest {
        TimerStateHolder.state.test {
            assertEquals(TimerState.Idle, awaitItem())

            TimerStateHolder.setState(TimerState.Running)
            assertEquals(TimerState.Running, awaitItem())

            TimerStateHolder.setState(TimerState.Paused)
            assertEquals(TimerState.Paused, awaitItem())

            TimerStateHolder.setState(TimerState.Running)
            assertEquals(TimerState.Running, awaitItem())

            TimerStateHolder.setState(TimerState.Reminder)
            assertEquals(TimerState.Reminder, awaitItem())

            TimerStateHolder.setState(TimerState.Completed)
            assertEquals(TimerState.Completed, awaitItem())

            TimerStateHolder.setState(TimerState.Idle)
            assertEquals(TimerState.Idle, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state flow conflates duplicated value`() = runTest {
        TimerStateHolder.state.test {
            assertEquals(TimerState.Idle, awaitItem())

            TimerStateHolder.setState(TimerState.Running)
            assertEquals(TimerState.Running, awaitItem())

            // StateFlow 去重：重复设置同一状态不应再次触发 UI 重组
            TimerStateHolder.setState(TimerState.Running)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `get state stays in sync with flow value`() {
        TimerStateHolder.setState(TimerState.Reminder)
        assertEquals(TimerState.Reminder, TimerStateHolder.state.value)
    }

    @Test
    fun `last stand verified defaults to null meaning unknown`() {
        assertNull(TimerStateHolder.lastStandVerified)

        TimerStateHolder.lastStandVerified = true
        assertEquals(true, TimerStateHolder.lastStandVerified)

        TimerStateHolder.lastStandVerified = false
        assertEquals(false, TimerStateHolder.lastStandVerified)
    }
}
