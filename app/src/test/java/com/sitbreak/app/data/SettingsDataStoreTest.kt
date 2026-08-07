package com.sitbreak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

import com.sitbreak.app.TimerSettingsDataStore
import com.sitbreak.app.ReminderSettingsDataStore

class SettingsDataStoreTest {

    @Test
    fun `default values are consistent`() {
        assertEquals(45, TimerSettingsDataStore.DEFAULT_SITTING_INTERVAL)
        assertEquals(20, TimerSettingsDataStore.DEFAULT_MICRO_BREAK_INTERVAL)
        assertEquals(9, TimerSettingsDataStore.DEFAULT_WORK_START_HOUR)
        assertEquals(18, TimerSettingsDataStore.DEFAULT_WORK_END_HOUR)
    }

    @Test
    fun `default fullscreen blacklist is not empty`() {
        val blacklist = ReminderSettingsDataStore.DEFAULT_FULLSCREEN_BLACKLIST
        assertTrue(blacklist.isNotEmpty())
        assertTrue(blacklist.contains("com.tencent.tmgp.sgame"))
    }

    @Test
    fun `default fullscreen blacklist contains common game and video apps`() {
        val blacklist = ReminderSettingsDataStore.DEFAULT_FULLSCREEN_BLACKLIST
        val packages = blacklist.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        assertTrue(packages.size > 10)
        assertTrue("com.tencent.tmgp.sgame" in packages)
        assertTrue("com.bilibili.app.in" in packages)
        assertTrue("com.ss.android.ugc.aweme" in packages)
    }
}