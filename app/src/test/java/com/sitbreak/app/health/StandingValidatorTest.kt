package com.sitbreak.app.health

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 站立验证器的「安全降级」测试。
 *
 * 该能力依赖 TYPE_STEP_COUNTER 传感器与 ACTIVITY_RECOGNITION 权限，
 * 但产品定位要求：设备不支持或用户拒绝授权时，必须静默降级为不可用，
 * 绝不能影响计时提醒主链路，也不能把「不可用」误判成「已验证站立」。
 */
class StandingValidatorTest {

    @After
    fun tearDown() {
        StandingValidator.stop()
    }

    @Test
    fun `degrades safely when device has no sensor service`() {
        val context = mockk<Context>()
        every { context.getSystemService(any<String>()) } returns null

        StandingValidator.start(context)

        assertFalse("无传感器服务时必须标记为不支持", StandingValidator.isSupported())
        assertFalse("不支持时不能判定为已站立", StandingValidator.standingLikely())
    }

    @Test
    fun `stop resets all runtime state`() {
        val context = mockk<Context>()
        every { context.getSystemService(any<String>()) } returns null
        StandingValidator.start(context)

        StandingValidator.stop()

        assertFalse(StandingValidator.isSupported())
        assertEquals(0, StandingValidator.stepsSinceStart())
        assertFalse(StandingValidator.standingLikely())
    }

    @Test
    fun `standing likely is false before any measurement`() {
        StandingValidator.stop()

        assertEquals(0, StandingValidator.stepsSinceStart())
        assertFalse(StandingValidator.standingLikely())
    }
}
