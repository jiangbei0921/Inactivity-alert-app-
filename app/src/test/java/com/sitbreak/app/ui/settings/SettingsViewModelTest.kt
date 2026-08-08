package com.sitbreak.app.ui.settings

import android.content.Context
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import com.sitbreak.app.data.SettingsDataStore
import com.sitbreak.app.notification.NotificationHelper

/**
 * SettingsViewModel 的边界与钳制逻辑测试。
 *
 * 设置项一旦越界（如久坐间隔超过 180 分钟、工作开始时间晚于结束时间），
 * 会让后台计时/提醒完全失效且难以排查，因此对这些 setter 的钳制行为做断言。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var context: Context

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        settingsDataStore = mockk(relaxed = true)
        notificationHelper = mockk(relaxed = true)
        context = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm() = SettingsViewModel(
        appContext = context,
        settingsDataStore = settingsDataStore,
        notificationHelper = notificationHelper,
    )

    @Test
    fun `setSittingInterval clamps to 1..180`() {
        val vm = createVm()
        vm.setSittingInterval(300)
        assertEquals(180, vm.sittingIntervalMinutes.value)
        vm.setSittingInterval(-5)
        assertEquals(1, vm.sittingIntervalMinutes.value)
        vm.setSittingInterval(45)
        assertEquals(45, vm.sittingIntervalMinutes.value)
        verify { settingsDataStore.setSittingInterval(any()) }
    }

    @Test
    fun `setWorkStartHour is rejected when not before workEndHour`() {
        val vm = createVm()
        // 默认 workEndHour = 18
        vm.setWorkStartHour(20)
        assertEquals(9, vm.workStartHour.value) // 越界被拒绝，保持默认
        vm.setWorkStartHour(12)
        assertEquals(12, vm.workStartHour.value) // 合法，接受
    }

    @Test
    fun `setWorkEndHour is rejected when not after workStartHour`() {
        val vm = createVm()
        // 默认 workStartHour = 9
        vm.setWorkEndHour(5)
        assertEquals(18, vm.workEndHour.value) // 越界被拒绝，保持默认
        vm.setWorkEndHour(20)
        assertEquals(20, vm.workEndHour.value) // 合法，接受
    }

    @Test
    fun `toggling a boolean setting updates state and persists`() {
        val vm = createVm()
        vm.setVibrationEnabled(false)
        assertEquals(false, vm.isVibrationEnabled.value)
        verify { settingsDataStore.setVibrationEnabled(false) }
        vm.setWaterReminderEnabled(false)
        assertEquals(false, vm.isWaterReminderEnabled.value)
        verify { settingsDataStore.setWaterReminderEnabled(false) }
    }
}
