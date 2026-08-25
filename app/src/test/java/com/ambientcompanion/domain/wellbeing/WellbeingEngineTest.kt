package com.ambientcompanion.domain.wellbeing

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WellbeingEngineTest {
    @Test fun `foreground time exceeds active time after idle threshold`() {
        val tracker = SessionTracker(idleThresholdMs = 180_000)
        tracker.foreground("social", true, 1_000)
        tracker.interaction(1_000)
        val context = tracker.snapshot(601_000)
        assertEquals(600_000, context.currentSessionDurationMs)
        assertEquals(180_000, context.activeSessionDurationMs)
        assertEquals(SessionState.IDLE, context.sessionState)
    }

    @Test fun `screen off pauses active time`() {
        val tracker = SessionTracker()
        tracker.foreground("reader", true, 1_000)
        tracker.interaction(1_000)
        tracker.screenOff(61_000)
        assertEquals(60_000, tracker.snapshot(601_000).activeSessionDurationMs)
        assertEquals(SessionState.PAUSED, tracker.snapshot(601_000).sessionState)
    }

    @Test fun `continuous scroll resets after three idle minutes`() {
        val tracker = ScrollTracker(180_000)
        tracker.scroll(1_000)
        assertEquals(59_000, tracker.scroll(60_000).durationMs)
        assertEquals(0, tracker.snapshot(240_000).durationMs)
        assertEquals(1, tracker.scroll(241_000).eventCount)
    }

    @Test fun `app opens reset at local day boundary and ignore duplicate foreground events`() {
        val tracker = AppOpenTracker()
        val day = LocalDate.of(2026, 8, 25)
        assertEquals(1, tracker.foreground("social", day))
        assertEquals(1, tracker.foreground("social", day))
        tracker.foreground("browser", day)
        assertEquals(2, tracker.foreground("social", day))
        assertEquals(0, tracker.count("social", day.plusDays(1)))
    }

    @Test fun `reaction engine uses configured thresholds and style`() {
        val engine = WellbeingReactionEngine()
        assertEquals("scroll_30", engine.scrollReaction(30 * 60_000L, WellbeingReactionStyle.PLAYFUL)?.id)
        assertEquals("scroll_60", engine.scrollReaction(60 * 60_000L, WellbeingReactionStyle.GENTLE)?.id)
        assertNull(engine.scrollReaction(120 * 60_000L, WellbeingReactionStyle.OFF))
    }
}
