package com.ambientcompanion.domain.screen

import org.junit.Assert.*
import org.junit.Test

class ScreenEngineTest {
    private val classifier = DeterministicScreenClassifier()

    @Test fun `classifies common structural fixtures deterministically`() {
        assertEquals(ScreenType.ARTICLE, classifier.classify(SanitizedScreenSnapshot(nodeCount = 30, textNodeCount = 20, scrollableCount = 1)).type)
        assertEquals(ScreenType.FORM, classifier.classify(SanitizedScreenSnapshot(editableCount = 4, hasSubmitLikeControl = true)).type)
        assertEquals(ScreenType.LOGIN, classifier.classify(SanitizedScreenSnapshot(editableCount = 2, passwordFieldCount = 1)).type)
        assertEquals(ScreenType.CHAT, classifier.classify(SanitizedScreenSnapshot(editableCount = 1, hasMessageListStructure = true)).type)
        assertEquals(ScreenType.MEDIA, classifier.classify(SanitizedScreenSnapshot(hasLargeMediaSurface = true, isFullScreen = true)).type)
        assertEquals(ScreenType.LIST, classifier.classify(SanitizedScreenSnapshot(nodeCount = 8, scrollableCount = 1)).type)
        assertEquals(ScreenType.SETTINGS, classifier.classify(SanitizedScreenSnapshot(hasSettingsStructure = true)).type)
        assertEquals(ContextConfidence.LOW, classifier.classify(SanitizedScreenSnapshot()).confidence)
    }

    @Test fun `sensitive mode combines privacy signals and restricts actions`() {
        val sensitive = SensitiveScreenDetector().detect(
            SanitizedScreenSnapshot(passwordFieldCount = 1, hasSubmitLikeControl = true),
            AppCategory.FINANCE,
        )
        assertTrue(sensitive.isSensitive)
        assertTrue(SensitiveReason.PASSWORD_FIELD in sensitive.reasons)
        assertTrue(SensitiveReason.FINANCE_APP in sensitive.reasons)
        val actions = DefaultScreenActionResolver().resolve(ScreenContext(isSensitive = true, confidence = ContextConfidence.HIGH))
        assertEquals(setOf(ScreenAction.BACK, ScreenAction.HOME, ScreenAction.HIDE), actions)
        assertFalse(ScreenAction.SCREENSHOT in actions)
    }

    @Test fun `obstruction resolver moves away from focused input and preserves preferred side`() {
        val result = ObstructionResolver().resolve(
            current = ScreenBounds(900, 1400, 1000, 1500),
            screen = ScreenBounds(0, 0, 1080, 1920),
            zones = listOf(AvoidZone(ScreenBounds(700, 1300, 1080, 1600), 90, AvoidZoneSource.FOCUSED_INPUT)),
            preferRight = true,
        )
        assertTrue(result.moved)
        assertEquals(AvoidZoneSource.FOCUSED_INPUT, result.reason)
        assertFalse(ScreenBounds(result.x, result.y, result.x + 100, result.y + 100).intersects(ScreenBounds(700, 1300, 1080, 1600)))
    }
}
