package com.ambientcompanion.domain.screen

data class ScreenBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    fun intersects(other: ScreenBounds): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top
}

enum class ScreenType { UNKNOWN, HOME, ARTICLE, LIST, CHAT, FORM, LOGIN, SEARCH, SETTINGS, MEDIA, DIALOG, GRID }
enum class ContextConfidence { HIGH, MEDIUM, LOW }
enum class AppCategory { BROWSER, MESSAGING, SOCIAL, VIDEO, MUSIC, READING, GAME, FINANCE, SHOPPING, SYSTEM, PRODUCTIVITY, OTHER }
enum class ScreenOrientation { PORTRAIT, LANDSCAPE }
enum class CompanionDisplayMode { NORMAL, SMALL, QUIET, EDGE_PEEK, HIDDEN, PRIVACY }
enum class ScreenAction {
    BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, HIDE, REFRESH, OPEN_APP, SCREENSHOT,
    SCROLL_TOP, SCROLL_BOTTOM, PREVIOUS_FIELD, NEXT_FIELD, HIDE_KEYBOARD, QUIET_30_MINUTES, EDGE_PEEK,
}

data class SanitizedScreenSnapshot(
    val packageName: String? = null,
    val nodeCount: Int = 0,
    val editableCount: Int = 0,
    val buttonCount: Int = 0,
    val textNodeCount: Int = 0,
    val scrollableCount: Int = 0,
    val passwordFieldCount: Int = 0,
    val pinFieldCount: Int = 0,
    val focusedInputBounds: ScreenBounds? = null,
    val importantBounds: List<ScreenBounds> = emptyList(),
    val hasFocusedInput: Boolean = false,
    val hasSubmitLikeControl: Boolean = false,
    val hasSearchLikeInput: Boolean = false,
    val hasMessageListStructure: Boolean = false,
    val hasSettingsStructure: Boolean = false,
    val hasGridStructure: Boolean = false,
    val hasDialogLikeStructure: Boolean = false,
    val hasLargeMediaSurface: Boolean = false,
    val isKeyboardVisible: Boolean = false,
    val isFullScreen: Boolean = false,
    val isHomeScreen: Boolean = false,
    val isSecureWindow: Boolean = false,
    val orientation: ScreenOrientation = ScreenOrientation.PORTRAIT,
)

data class ScreenClassification(
    val type: ScreenType,
    val confidence: ContextConfidence,
    val reasons: List<String>,
)

data class SensitiveContext(
    val isSensitive: Boolean,
    val reasons: Set<SensitiveReason> = emptySet(),
)

enum class SensitiveReason {
    PASSWORD_FIELD, PIN_FIELD, FINANCE_APP, USER_PROFILE, AUTHENTICATION_SCREEN,
    SECURE_WINDOW, UNKNOWN_SECURE_STATE,
}

data class ScreenContext(
    val packageName: String? = null,
    val appCategory: AppCategory = AppCategory.OTHER,
    val screenType: ScreenType = ScreenType.UNKNOWN,
    val isKeyboardVisible: Boolean = false,
    val hasFocusedInput: Boolean = false,
    val isScrollable: Boolean = false,
    val isFullScreen: Boolean = false,
    val isSensitive: Boolean = false,
    val orientation: ScreenOrientation = ScreenOrientation.PORTRAIT,
    val confidence: ContextConfidence = ContextConfidence.LOW,
    val availableActions: Set<ScreenAction> = emptySet(),
    val importantBounds: List<ScreenBounds> = emptyList(),
    val sensitiveReasons: Set<SensitiveReason> = emptySet(),
) {
    companion object { val EMPTY = ScreenContext() }
}

data class AppProfile(
    val packageName: String,
    val displayMode: CompanionDisplayMode,
    val allowMessages: Boolean = true,
    val allowContextActions: Boolean = true,
    val allowWellbeingReactions: Boolean = true,
    val sensitiveOverride: Boolean? = null,
    val categoryOverride: AppCategory? = null,
)
