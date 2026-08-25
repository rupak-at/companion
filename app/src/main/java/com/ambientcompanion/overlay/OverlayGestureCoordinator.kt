package com.ambientcompanion.overlay

import kotlin.math.abs

class OverlayGestureCoordinator(private val tapWindowMs: Long = 360L) {
    private var tapCount = 0
    private var lastTapAt: Long? = null

    fun isDrag(deltaX: Float, deltaY: Float, touchSlop: Int): Boolean =
        abs(deltaX) > touchSlop || abs(deltaY) > touchSlop

    fun registerTap(at: Long): Int {
        if (lastTapAt?.let { at - it > tapWindowMs } != false) tapCount = 0
        lastTapAt = at
        tapCount++
        return tapCount
    }

    fun consumeTaps(): Int = tapCount.also { tapCount = 0; lastTapAt = null }
}
