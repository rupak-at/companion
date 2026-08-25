package com.ambientcompanion.overlay

import org.junit.Assert.*
import org.junit.Test

class OverlayGestureCoordinatorTest {
    @Test fun `small movement remains a tap`() = assertFalse(OverlayGestureCoordinator().isDrag(4f, 5f, 8))
    @Test fun `movement beyond slop becomes drag`() = assertTrue(OverlayGestureCoordinator().isDrag(9f, 2f, 8))
    @Test fun `slow double tap becomes a new sequence`() {
        val coordinator = OverlayGestureCoordinator(360)
        assertEquals(1, coordinator.registerTap(1_000))
        assertEquals(1, coordinator.registerTap(1_500))
    }
    @Test fun `triple tap is recognized in one window`() {
        val coordinator = OverlayGestureCoordinator(360)
        coordinator.registerTap(1_000); coordinator.registerTap(1_150)
        assertEquals(3, coordinator.registerTap(1_300))
        assertEquals(3, coordinator.consumeTaps())
    }
}
