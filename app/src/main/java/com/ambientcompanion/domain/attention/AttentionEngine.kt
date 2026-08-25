package com.ambientcompanion.domain.attention

import com.ambientcompanion.domain.context.Personality
import com.ambientcompanion.domain.context.ResourceMode

enum class AttentionLevel { SILENT, SUBTLE, NORMAL, IMPORTANT }
data class AttentionDecision(
    val level: AttentionLevel,
    val allowMessage: Boolean,
    val allowAnimation: Boolean,
    val allowPositionChange: Boolean,
    val suppressionReasons: List<String> = emptyList(),
)
data class AttentionInput(
    val requestedLevel: AttentionLevel,
    val quietHours: Boolean = false,
    val sensitive: Boolean = false,
    val profileAllowsMessages: Boolean = true,
    val recentMessage: Boolean = false,
    val personality: Personality = Personality.PLAYFUL,
    val resourceMode: ResourceMode = ResourceMode.NORMAL,
    val screenOn: Boolean = true,
)

class AttentionEngine {
    fun decide(input: AttentionInput): AttentionDecision {
        val reasons = buildList {
            if (!input.screenOn) add("screen off")
            if (input.sensitive) add("sensitive screen")
            if (input.quietHours && input.requestedLevel != AttentionLevel.IMPORTANT) add("quiet hours")
            if (!input.profileAllowsMessages) add("app profile")
            if (input.recentMessage && input.requestedLevel != AttentionLevel.IMPORTANT) add("message cooldown")
            if (input.personality == Personality.QUIET && input.requestedLevel == AttentionLevel.SUBTLE) add("quiet personality")
        }
        val message = input.requestedLevel != AttentionLevel.SILENT && reasons.isEmpty()
        val animation = input.screenOn && !input.sensitive && input.resourceMode != ResourceMode.MINIMAL &&
            (input.requestedLevel != AttentionLevel.SUBTLE || input.personality != Personality.QUIET)
        return AttentionDecision(input.requestedLevel, message, animation, input.screenOn, reasons)
    }
}
