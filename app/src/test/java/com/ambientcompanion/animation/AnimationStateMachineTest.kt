package com.ambientcompanion.animation

import com.ambientcompanion.renderer.AnimationId
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationStateMachineTest {
    @Test fun `drag is not interrupted and queues transition`() {
        val machine = AnimationStateMachine()
        machine.request(AnimationId.DRAG, AnimationPhase.DRAGGING)
        machine.request(AnimationId.BATTERY_LOW, AnimationPhase.TRANSITION)
        assertEquals(AnimationId.DRAG, machine.snapshot.animation)
        assertEquals(AnimationId.BATTERY_LOW, machine.snapshot.queued)
        assertEquals(AnimationId.BATTERY_LOW, machine.finish().animation)
    }

    @Test fun `paused state ignores animation requests`() {
        val machine = AnimationStateMachine()
        machine.pause()
        machine.request(AnimationId.TAP_HAPPY, AnimationPhase.INTERACTION)
        assertEquals(AnimationPhase.PAUSED, machine.snapshot.phase)
    }
}
