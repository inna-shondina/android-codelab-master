package com.sap.codelab.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

internal class NotificationPreviewTest {

    @Test
    fun `preview is limited to 140 unicode code points`() {
        val text = "a".repeat(139) + "😀" + "ignored"

        val preview = text.takeCodePoints(140)

        assertEquals("a".repeat(139) + "😀", preview)
        assertEquals(140, preview.codePointCount(0, preview.length))
    }

    @Test
    fun `short preview is unchanged`() {
        assertEquals("Short memo", "Short memo".takeCodePoints(140))
    }
}
