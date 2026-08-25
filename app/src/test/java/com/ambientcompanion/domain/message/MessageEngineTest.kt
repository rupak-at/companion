package com.ambientcompanion.domain.message

import com.ambientcompanion.domain.context.Personality
import com.ambientcompanion.domain.model.CompanionState
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class MessageEngineTest {
    private val engine = MessageEngine(Random(1))
    @Test fun `message never immediately repeats`() {
        val first = engine.select(MessageRequest(CompanionState.DAY_CLEAR, Personality.PLAYFUL, MessagePackId.DEFAULT, millisSinceLastTap = 1_000))
        val second = engine.select(MessageRequest(CompanionState.DAY_CLEAR, Personality.PLAYFUL, MessagePackId.DEFAULT, first.id, millisSinceLastTap = 1_000))
        assertNotEquals(first.id, second.id)
    }
    @Test fun `messages fit bubble copy limit`() = assertTrue(engine.select(MessageRequest(CompanionState.DAY_CLEAR, Personality.CHEERFUL, MessagePackId.MOTIVATIONAL)).text.length <= 40)
    @Test fun `quiet personality has much longer interval`() = assertTrue(engine.automaticIntervalMs(Personality.QUIET) > engine.automaticIntervalMs(Personality.PLAYFUL) * 2)
}
