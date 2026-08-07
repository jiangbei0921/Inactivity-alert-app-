package com.sitbreak.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHelperTest {

    @Test
    fun `notification IDs are unique`() {
        val ids = setOf(
            NotificationHelper.NOTIFICATION_ID_SITTING,
            NotificationHelper.NOTIFICATION_ID_MICRO_BREAK,
            NotificationHelper.NOTIFICATION_ID_SERVICE,
            NotificationHelper.NOTIFICATION_ID_WATER,
            NotificationHelper.NOTIFICATION_ID_EYE,
        )
        assertEquals(5, ids.size)
    }

    @Test
    fun `channel IDs are unique`() {
        val channels = setOf(
            NotificationHelper.CHANNEL_SITTING_REMINDER,
            NotificationHelper.CHANNEL_MICRO_BREAK,
            NotificationHelper.CHANNEL_SERVICE,
        )
        assertEquals(3, channels.size)
    }

    @Test
    fun `action constants are unique`() {
        val actions = setOf(
            NotificationHelper.ACTION_STAND_UP,
            NotificationHelper.ACTION_SNOOZE,
            NotificationHelper.ACTION_PAUSE_TIMER,
            NotificationHelper.ACTION_RESUME_TIMER,
        )
        assertEquals(4, actions.size)
    }

    @Test
    fun `action constants have correct package prefix`() {
        val prefix = "com.sitbreak.app.ACTION_"
        assertTrue(NotificationHelper.ACTION_STAND_UP.startsWith(prefix))
        assertTrue(NotificationHelper.ACTION_SNOOZE.startsWith(prefix))
        assertTrue(NotificationHelper.ACTION_PAUSE_TIMER.startsWith(prefix))
        assertTrue(NotificationHelper.ACTION_RESUME_TIMER.startsWith(prefix))
    }

    @Test
    fun `notification ID range is positive`() {
        assertTrue(NotificationHelper.NOTIFICATION_ID_SITTING > 0)
        assertTrue(NotificationHelper.NOTIFICATION_ID_MICRO_BREAK > 0)
        assertTrue(NotificationHelper.NOTIFICATION_ID_SERVICE > 0)
        assertTrue(NotificationHelper.NOTIFICATION_ID_WATER > 0)
        assertTrue(NotificationHelper.NOTIFICATION_ID_EYE > 0)
    }
}