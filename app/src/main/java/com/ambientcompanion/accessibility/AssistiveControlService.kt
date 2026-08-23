package com.ambientcompanion.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

enum class AssistiveAction {
    BACK,
    HOME,
    RECENTS,
    NOTIFICATIONS,
    QUICK_SETTINGS,
    LOCK_SCREEN,
    POWER_DIALOG,
}

class AssistiveControlService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: AssistiveControlService? = null

        val isConnected: Boolean get() = instance != null

        fun perform(action: AssistiveAction): Boolean {
            val service = instance ?: return false
            val globalAction = when (action) {
                AssistiveAction.BACK -> GLOBAL_ACTION_BACK
                AssistiveAction.HOME -> GLOBAL_ACTION_HOME
                AssistiveAction.RECENTS -> GLOBAL_ACTION_RECENTS
                AssistiveAction.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
                AssistiveAction.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
                AssistiveAction.LOCK_SCREEN -> GLOBAL_ACTION_LOCK_SCREEN
                AssistiveAction.POWER_DIALOG -> GLOBAL_ACTION_POWER_DIALOG
            }
            return service.performGlobalAction(globalAction)
        }
    }
}
