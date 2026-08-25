package com.ambientcompanion.domain.screen

interface ScreenClassifier {
    fun classify(snapshot: SanitizedScreenSnapshot): ScreenClassification
}

class DeterministicScreenClassifier : ScreenClassifier {
    override fun classify(snapshot: SanitizedScreenSnapshot): ScreenClassification = when {
        snapshot.isHomeScreen -> result(ScreenType.HOME, ContextConfidence.HIGH, "launcher window")
        snapshot.passwordFieldCount > 0 && snapshot.editableCount >= 2 -> result(
            ScreenType.LOGIN, ContextConfidence.HIGH, "password field", "multiple editable fields",
        )
        snapshot.passwordFieldCount > 0 || snapshot.pinFieldCount > 0 -> result(
            ScreenType.LOGIN, ContextConfidence.MEDIUM, "authentication input",
        )
        snapshot.hasDialogLikeStructure -> result(ScreenType.DIALOG, ContextConfidence.HIGH, "dialog-like window")
        snapshot.hasLargeMediaSurface && snapshot.isFullScreen -> result(
            ScreenType.MEDIA, ContextConfidence.HIGH, "large media surface", "fullscreen window",
        )
        snapshot.hasMessageListStructure && snapshot.editableCount > 0 -> result(
            ScreenType.CHAT, ContextConfidence.HIGH, "message-list structure", "editable input",
        )
        snapshot.hasSearchLikeInput && snapshot.hasFocusedInput -> result(
            ScreenType.SEARCH, ContextConfidence.HIGH, "focused search input",
        )
        snapshot.editableCount >= 2 && snapshot.hasSubmitLikeControl -> result(
            ScreenType.FORM, ContextConfidence.HIGH, "${snapshot.editableCount} editable fields", "submit-like control",
        )
        snapshot.editableCount >= 2 -> result(ScreenType.FORM, ContextConfidence.MEDIUM, "multiple editable fields")
        snapshot.hasSettingsStructure -> result(ScreenType.SETTINGS, ContextConfidence.MEDIUM, "settings-like controls")
        snapshot.hasGridStructure && snapshot.nodeCount >= 4 -> result(ScreenType.GRID, ContextConfidence.MEDIUM, "grid structure")
        snapshot.scrollableCount > 0 && snapshot.textNodeCount >= 8 && snapshot.editableCount <= 1 -> result(
            ScreenType.ARTICLE, ContextConfidence.HIGH, "many text nodes", "scrollable container",
        )
        snapshot.scrollableCount > 0 && snapshot.nodeCount >= 4 -> result(
            ScreenType.LIST, ContextConfidence.MEDIUM, "scrollable content",
        )
        snapshot.hasLargeMediaSurface -> result(ScreenType.MEDIA, ContextConfidence.MEDIUM, "large media surface")
        else -> result(ScreenType.UNKNOWN, ContextConfidence.LOW, "insufficient structural signals")
    }

    private fun result(type: ScreenType, confidence: ContextConfidence, vararg reasons: String) =
        ScreenClassification(type, confidence, reasons.toList())
}
