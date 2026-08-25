package com.ambientcompanion.animation

import com.ambientcompanion.renderer.AnimationId

enum class AnimationPhase { IDLE, INTERACTION, DRAGGING, TRANSITION, AUTOMATIC, PAUSED }

data class AnimationSnapshot(
    val phase: AnimationPhase = AnimationPhase.IDLE,
    val animation: AnimationId = AnimationId.IDLE,
    val queued: AnimationId? = null,
)

class AnimationStateMachine {
    var snapshot = AnimationSnapshot()
        private set

    fun request(animation: AnimationId, phase: AnimationPhase): AnimationSnapshot {
        snapshot = when {
            snapshot.phase == AnimationPhase.PAUSED -> snapshot
            snapshot.phase == AnimationPhase.DRAGGING && phase != AnimationPhase.DRAGGING ->
                snapshot.copy(queued = animation)
            phase.priority >= snapshot.phase.priority -> AnimationSnapshot(phase, animation)
            else -> snapshot
        }
        return snapshot
    }

    fun finish(): AnimationSnapshot {
        val next = snapshot.queued
        snapshot = if (next == null) AnimationSnapshot() else
            AnimationSnapshot(AnimationPhase.TRANSITION, next)
        return snapshot
    }

    fun pause() { snapshot = AnimationSnapshot(AnimationPhase.PAUSED, snapshot.animation) }
    fun resume() { snapshot = AnimationSnapshot() }

    private val AnimationPhase.priority: Int get() = when (this) {
        AnimationPhase.PAUSED -> 5
        AnimationPhase.DRAGGING -> 4
        AnimationPhase.INTERACTION -> 3
        AnimationPhase.TRANSITION -> 2
        AnimationPhase.AUTOMATIC -> 1
        AnimationPhase.IDLE -> 0
    }
}
