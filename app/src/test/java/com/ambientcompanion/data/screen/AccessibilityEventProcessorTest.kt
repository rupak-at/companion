package com.ambientcompanion.data.screen

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityEventProcessorTest {
    private val processor = AccessibilityEventProcessor()

    @Test fun `scroll is light while app and window changes are full`() {
        assertEquals(ContextRefreshLevel.LIGHT, processor.refreshLevel(AccessibilityEvent.TYPE_VIEW_SCROLLED))
        assertEquals(ContextRefreshLevel.MEDIUM, processor.refreshLevel(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED))
        assertEquals(ContextRefreshLevel.FULL, processor.refreshLevel(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED))
        assertNull(processor.refreshLevel(AccessibilityEvent.TYPE_ANNOUNCEMENT))
    }

    @Test fun `power saver increases inspection debounce`() {
        assertEquals(180L, processor.debounceMs(ContextRefreshLevel.FULL, false))
        assertEquals(750L, processor.debounceMs(ContextRefreshLevel.FULL, true))
    }
}
