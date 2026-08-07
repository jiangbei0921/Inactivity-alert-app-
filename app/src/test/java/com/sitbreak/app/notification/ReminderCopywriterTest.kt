package com.sitbreak.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderCopywriterTest {

    @Test
    fun `randomCopy returns valid copy for known style`() {
        val styles = listOf("health_care", "humorous", "programmer", "worker", "motivational")
        for (style in styles) {
            val copy = ReminderCopywriter.randomCopy(style)
            assertNotNull(copy)
            assertTrue(copy.isNotEmpty())
            val expectedList = ReminderCopywriter.styles[style]!!
            assertTrue("Copy '$copy' should be in ${style} list", copy in expectedList)
        }
    }

    @Test
    fun `randomCopy falls back to healthCare for unknown style`() {
        val copy = ReminderCopywriter.randomCopy("unknown_style")
        assertNotNull(copy)
        assertTrue(copy.isNotEmpty())
        assertTrue(copy in ReminderCopywriter.healthCare)
    }

    @Test
    fun `randomCopy falls back to healthCare for empty style`() {
        val copy = ReminderCopywriter.randomCopy("")
        assertNotNull(copy)
        assertTrue(copy.isNotEmpty())
        assertTrue(copy in ReminderCopywriter.healthCare)
    }

    @Test
    fun `all styles have at least one copy`() {
        for ((key, list) in ReminderCopywriter.styles) {
            assertTrue("Style '$key' should have at least one copy", list.isNotEmpty())
        }
    }

    @Test
    fun `all style names have corresponding styles`() {
        for (key in ReminderCopywriter.styleNames.keys) {
            assertTrue("Style '$key' should exist in styles map", key in ReminderCopywriter.styles)
        }
    }

    @Test
    fun `styleNames values are non-empty`() {
        for ((_, name) in ReminderCopywriter.styleNames) {
            assertTrue("Style name should not be empty", name.isNotEmpty())
        }
    }
}