package com.ambientcompanion.data.screen

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.ambientcompanion.domain.screen.SanitizedScreenSnapshot
import com.ambientcompanion.domain.screen.ScreenBounds
import com.ambientcompanion.domain.screen.ScreenOrientation

class ScreenSnapshotBuilder(private val maxNodes: Int = 500) {
    fun build(
        root: AccessibilityNodeInfo?,
        packageName: String?,
        screenWidth: Int,
        screenHeight: Int,
        keyboardVisible: Boolean,
        fullScreen: Boolean,
        secureWindow: Boolean,
    ): SanitizedScreenSnapshot {
        if (root == null) return SanitizedScreenSnapshot(
            packageName = packageName,
            isKeyboardVisible = keyboardVisible,
            isFullScreen = fullScreen,
            isSecureWindow = secureWindow,
            orientation = orientation(screenWidth, screenHeight),
        )
        val accumulator = Accumulator(screenWidth, screenHeight)
        visit(root, accumulator)
        return accumulator.snapshot(packageName, keyboardVisible, fullScreen, secureWindow)
    }

    private fun visit(node: AccessibilityNodeInfo, result: Accumulator) {
        if (result.nodeCount >= maxNodes) return
        result.add(node)
        for (index in 0 until node.childCount) {
            if (result.nodeCount >= maxNodes) break
            node.getChild(index)?.let { child -> visit(child, result) }
        }
    }

    private fun orientation(width: Int, height: Int) =
        if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT

    private class Accumulator(private val screenWidth: Int, private val screenHeight: Int) {
        var nodeCount = 0
        var editableCount = 0
        var buttonCount = 0
        var textNodeCount = 0
        var scrollableCount = 0
        var passwordCount = 0
        var pinCount = 0
        var focusedBounds: ScreenBounds? = null
        var hasFocusedInput = false
        var hasSubmit = false
        var hasSearch = false
        var hasMessageList = false
        var hasGrid = false
        var hasDialog = false
        var hasLargeMedia = false
        val importantBounds = mutableListOf<ScreenBounds>()

        fun add(node: AccessibilityNodeInfo) {
            nodeCount++
            val className = node.className?.toString()?.lowercase().orEmpty()
            val hint = node.hintText?.toString()?.lowercase().orEmpty()
            val description = node.contentDescription?.toString()?.lowercase().orEmpty()
            val transientLabels = "$hint $description"
            val editable = node.isEditable || className.contains("edittext")
            val button = node.isClickable && className.contains("button")
            if (editable) editableCount++
            if (button) buttonCount++
            if (!editable && (node.text?.isNotBlank() == true || description.isNotBlank())) textNodeCount++
            if (node.isScrollable) scrollableCount++
            if (node.isPassword) passwordCount++
            if (editable && node.inputType and PIN_MASK != 0) pinCount++
            if (node.isFocused && editable) {
                hasFocusedInput = true
                focusedBounds = node.bounds()
            }
            if (button && SUBMIT_WORDS.any(transientLabels::contains)) hasSubmit = true
            if (editable && SEARCH_WORDS.any(transientLabels::contains)) hasSearch = true
            if (className.contains("recyclerview") && editableCount > 0) hasMessageList = true
            if (className.contains("grid")) hasGrid = true
            if (className.contains("dialog")) hasDialog = true
            val bounds = node.bounds()
            val area = bounds.width.toLong() * bounds.height
            val screenArea = screenWidth.toLong() * screenHeight
            if ((className.contains("surfaceview") || className.contains("videoview")) && area > screenArea / 2) hasLargeMedia = true
            if ((node.isFocused && editable) || button) importantBounds += bounds
        }

        fun snapshot(packageName: String?, keyboard: Boolean, fullscreen: Boolean, secure: Boolean) =
            SanitizedScreenSnapshot(
                packageName = packageName,
                nodeCount = nodeCount,
                editableCount = editableCount,
                buttonCount = buttonCount,
                textNodeCount = textNodeCount,
                scrollableCount = scrollableCount,
                passwordFieldCount = passwordCount,
                pinFieldCount = pinCount,
                focusedInputBounds = focusedBounds,
                importantBounds = importantBounds.take(12),
                hasFocusedInput = hasFocusedInput,
                hasSubmitLikeControl = hasSubmit,
                hasSearchLikeInput = hasSearch,
                hasMessageListStructure = hasMessageList && editableCount > 0,
                hasSettingsStructure = packageName?.contains("settings", ignoreCase = true) == true,
                hasGridStructure = hasGrid,
                hasDialogLikeStructure = hasDialog,
                hasLargeMediaSurface = hasLargeMedia,
                isKeyboardVisible = keyboard,
                isFullScreen = fullscreen,
                isSecureWindow = secure,
                orientation = if (screenWidth > screenHeight) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT,
            )

        private fun AccessibilityNodeInfo.bounds(): ScreenBounds = Rect().also(::getBoundsInScreen).let {
            ScreenBounds(it.left, it.top, it.right, it.bottom)
        }

        companion object {
            private const val PIN_MASK = 0x2 or 0x10 or 0x20
            private val SUBMIT_WORDS = listOf("submit", "sign in", "log in", "continue", "next", "pay", "confirm")
            private val SEARCH_WORDS = listOf("search", "find")
        }
    }
}
