package com.ambientcompanion.domain.context

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryStateTrackerTest {
    @Test fun `low battery remains latched until twenty five percent`() {
        val tracker = BatteryStateTracker()
        assertEquals(BatteryState.NORMAL, tracker.update(21, false))
        assertEquals(BatteryState.LOW, tracker.update(20, false))
        assertEquals(BatteryState.LOW, tracker.update(24, false))
        assertEquals(BatteryState.NORMAL, tracker.update(25, false))
    }
    @Test fun `critical and full override low latch`() {
        val tracker = BatteryStateTracker()
        assertEquals(BatteryState.CRITICAL, tracker.update(8, false))
        assertEquals(BatteryState.FULL, tracker.update(100, true))
    }
}
