package com.ambientcompanion.domain.schedule

import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class SchedulePolicyTest {
    @Test fun `cross midnight quiet range works`() {
        assertTrue(SchedulePolicy.contains(LocalTime.of(23, 0), 22 * 60 + 30, 7 * 60))
        assertTrue(SchedulePolicy.contains(LocalTime.of(6, 59), 22 * 60 + 30, 7 * 60))
        assertFalse(SchedulePolicy.contains(LocalTime.of(12, 0), 22 * 60 + 30, 7 * 60))
    }
}
