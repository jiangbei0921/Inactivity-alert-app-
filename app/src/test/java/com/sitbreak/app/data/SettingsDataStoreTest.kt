package com.sitbreak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDataStoreTest {

    @Test
    fun `default values are consistent`() {
        assertEquals(45, SettingsDataStore.DEFAULT_SITTING_INTERVAL)
        assertEquals(20, SettingsDataStore.DEFAULT_MICRO_BREAK_INTERVAL)
        assertEquals(9, SettingsDataStore.DEFAULT_WORK_START_HOUR)
        assertEquals(18, SettingsDataStore.DEFAULT_WORK_END_HOUR)
    }

    @Test
    fun `default fullscreen blacklist is not empty`() {
        val blacklist = SettingsDataStore.DEFAULT_FULLSCREEN_BLACKLIST
        assertTrue(blacklist.isNotEmpty())
        assertTrue(blacklist.contains("com.tencent.tmgp.sgame"))
    }

    @Test
    fun `default fullscreen blacklist contains common game and video apps`() {
        val blacklist = SettingsDataStore.DEFAULT_FULLSCREEN_BLACKLIST
        val packages = blacklist.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        assertTrue(packages.size > 10)
        assertTrue("com.tencent.tmgp.sgame" in packages)
        assertTrue("com.bilibili.app.in" in packages)
        assertTrue("com.ss.android.ugc.aweme" in packages)
    }
}