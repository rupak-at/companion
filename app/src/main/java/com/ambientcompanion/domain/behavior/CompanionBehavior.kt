package com.ambientcompanion.domain.behavior

import com.ambientcompanion.domain.model.CompanionState
import com.ambientcompanion.renderer.AccessoryId
import com.ambientcompanion.renderer.AnimationId

enum class CompanionMood { HAPPY, CALM, PLAYFUL, TIRED, WORRIED, SLEEPY }

data class CompanionBehavior(
    val visualState: CompanionState,
    val idleAnimation: AnimationId = AnimationId.IDLE,
    val reaction: AnimationId? = null,
    val messagePoolId: String? = null,
    val accessory: AccessoryId? = null,
    val mood: CompanionMood = CompanionMood.CALM,
    val automaticMessageAllowed: Boolean = true,
)

sealed interface CompanionEffect {
    data class Persistent(val behavior: CompanionBehavior) : CompanionEffect
    data class Temporary(val behavior: CompanionBehavior, val durationMs: Long) : CompanionEffect
}
