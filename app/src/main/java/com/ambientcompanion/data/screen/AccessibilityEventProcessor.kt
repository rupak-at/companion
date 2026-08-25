package com.ambientcompanion.data.screen

import android.view.accessibility.AccessibilityEvent

enum class ContextRefreshLevel { LIGHT, MEDIUM, FULL }

class AccessibilityEventProcessor {
    fun refreshLevel(eventType: Int): ContextRefreshLevel? = when (eventType) {
        AccessibilityEvent.TYPE_VIEW_SCROLLED,
        AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> ContextRefreshLevel.LIGHT
        AccessibilityEvent.TYPE_VIEW_FOCUSED,
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> ContextRefreshLevel.MEDIUM
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> ContextRefreshLevel.FULL
        else -> null
    }

    fun debounceMs(level: ContextRefreshLevel, powerSaver: Boolean): Long = when (level) {
        ContextRefreshLevel.LIGHT -> if (powerSaver) 1_000L else 350L
        ContextRefreshLevel.MEDIUM -> if (powerSaver) 1_500L else 500L
        ContextRefreshLevel.FULL -> if (powerSaver) 750L else 180L
    }
}
