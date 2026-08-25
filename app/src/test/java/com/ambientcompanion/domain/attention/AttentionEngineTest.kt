package com.ambientcompanion.domain.attention

import org.junit.Assert.*
import org.junit.Test

class AttentionEngineTest {
    private val engine = AttentionEngine()

    @Test fun `keyboard and fullscreen class events remain silent`() {
        assertFalse(engine.decide(AttentionInput(AttentionLevel.SILENT)).allowMessage)
    }

    @Test fun `sensitive and quiet contexts suppress normal messages`() {
        val sensitive = engine.decide(AttentionInput(AttentionLevel.NORMAL, sensitive = true))
        assertFalse(sensitive.allowMessage)
        assertTrue("sensitive screen" in sensitive.suppressionReasons)
        assertFalse(engine.decide(AttentionInput(AttentionLevel.NORMAL, quietHours = true)).allowMessage)
    }

    @Test fun `critical event bypasses ordinary cooldown but not sensitive privacy`() {
        assertTrue(engine.decide(AttentionInput(AttentionLevel.IMPORTANT, recentMessage = true)).allowMessage)
        assertFalse(engine.decide(AttentionInput(AttentionLevel.IMPORTANT, sensitive = true)).allowMessage)
    }
}
