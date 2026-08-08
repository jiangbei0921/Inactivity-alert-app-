package com.sitbreak.app.ui.stats

import android.content.Context
import com.sitbreak.app.data.CheckInDao
import com.sitbreak.app.data.CheckInRepository
import com.sitbreak.app.data.SettingsDataStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * StatsViewModel 测试。
 *
 * 覆盖 tab 切换，以及「每日目标站立次数」由工作时段与提醒间隔推算的核心逻辑
 * （target = (workEnd - workStart) * 60 / sittingInterval）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var repository: CheckInRepository
    private lateinit var context: Context

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        settingsDataStore = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        context = mockk()
        every { settingsDataStore.sittingIntervalMinutes } returns MutableStateFlow(45)
        every { settingsDataStore.workStartHour } returns MutableStateFlow(9)
        every { settingsDataStore.workEndHour } returns MutableStateFlow(18)
        every { repository.getDailyCountsForLast7Days(any(), any()) } returns emptyList<CheckInDao.DailyCount>()
        every { repository.getTotalCount() } returns 0
        every { repository.getMonthlyCountsForYear(any(), any()) } returns emptyList<CheckInDao.MonthlyCount>()
        every { repository.getAllDayCountsByType(any(), any()) } returns emptyList<CheckInDao.DailyCount>()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm() = StatsViewModel(
        appContext = context,
        repository = repository,
        settingsDataStore = settingsDataStore,
    )

    @Test
    fun `initial tab is weekly and daily target derived from working hours`() {
        val vm = createVm()
        assertEquals(0, vm.selectedTab.value)
        // target = (18 - 9) * 60 / 45 = 12
        assertEquals(12, vm.dailyTarget.value)
    }

    @Test
    fun `selectTab updates selected tab index`() {
        val vm = createVm()
        vm.selectTab(1)
        assertEquals(1, vm.selectedTab.value)
        vm.selectTab(2)
        assertEquals(2, vm.selectedTab.value)
        vm.selectTab(0)
        assertEquals(0, vm.selectedTab.value)
    }

    @Test
    fun `switching to monthly tab keeps daily target and loads monthly series`() {
        val vm = createVm()
        vm.selectTab(1)
        assertEquals(1, vm.selectedTab.value)
        assertEquals(12, vm.dailyTarget.value)
        // 12 个月柱状数据由 computeMonthlyStandCounts 产出
        assertEquals(12, vm.monthlyStandCounts.value.size)
    }
}
