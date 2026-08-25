package com.ambientcompanion.overlay

import com.ambientcompanion.domain.screen.ContextConfidence
import com.ambientcompanion.domain.screen.ScreenBounds
import com.ambientcompanion.domain.screen.ScreenContext
import com.ambientcompanion.domain.screen.ScreenType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPositionControllerTest {
    private val controller = SmartPositionController()
    private val screen = ScreenBounds(0, 0, 1080, 1920)

    @Test fun `keyboard moves a companion out of lower screen`() {
        val result = controller.resolve(
            ScreenBounds(900, 1500, 1000, 1600),
            screen,
            ScreenContext(isKeyboardVisible = true, confidence = ContextConfidence.HIGH),
        )
        assertTrue(result.moved)
        assertTrue(result.y + 100 <= screen.height * 58 / 100)
    }

    @Test fun `dialog center is avoided while safe position remains stable`() {
        val context = ScreenContext(screenType = ScreenType.DIALOG, confidence = ContextConfidence.HIGH)
        assertTrue(controller.resolve(ScreenBounds(900, 900, 1000, 1000), screen, context).moved)
        assertFalse(controller.resolve(ScreenBounds(900, 120, 1000, 220), screen, context).moved)
    }
}
