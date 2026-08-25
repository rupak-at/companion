package com.ambientcompanion.domain.wellbeing

import com.ambientcompanion.domain.attention.AttentionLevel

data class WellbeingReaction(val id: String, val thresholdMinutes: Int, val message: String?, val attention: AttentionLevel)

class WellbeingReactionEngine {
    fun scrollReaction(
        durationMs: Long,
        style: WellbeingReactionStyle,
        firstMinutes: Int = 30,
        strongMinutes: Int = 60,
    ): WellbeingReaction? {
        if (style == WellbeingReactionStyle.OFF) return null
        val minutes = durationMs / 60_000
        val threshold = listOf(90, strongMinutes, 45, firstMinutes).distinct().sortedDescending().firstOrNull { minutes >= it } ?: return null
        val message = when (style) {
            WellbeingReactionStyle.PLAYFUL -> when {
                threshold >= 90 -> "Are we trapped here? 😂"
                threshold >= strongMinutes -> "My eyes hurt watching this 😭"
                threshold >= 45 -> "That's a lot of scrolling."
                else -> "Still scrolling? 👀"
            }
            WellbeingReactionStyle.GENTLE -> if (threshold >= strongMinutes) "A short break might feel nice 👀" else "Maybe look away for a moment?"
            WellbeingReactionStyle.MINIMAL -> if (threshold == firstMinutes || threshold >= strongMinutes) "Time for a short pause?" else null
            WellbeingReactionStyle.OFF -> null
        }
        if (message == null) return null
        return WellbeingReaction("scroll_$threshold", threshold, message, if (threshold >= strongMinutes) AttentionLevel.NORMAL else AttentionLevel.SUBTLE)
    }

    fun appOpenReaction(count: Int, style: WellbeingReactionStyle): WellbeingReaction? {
        if (style == WellbeingReactionStyle.OFF || count < 5) return null
        val threshold = if (count >= 10) 10 else 5
        val message = when (style) {
            WellbeingReactionStyle.PLAYFUL -> if (threshold == 10) "We really meet again 👀" else "We meet again 👀"
            WellbeingReactionStyle.GENTLE -> "Back here again — be kind to your time."
            WellbeingReactionStyle.MINIMAL -> "Opened $count times today"
            WellbeingReactionStyle.OFF -> null
        }
        return WellbeingReaction("opens_$threshold", threshold, message, AttentionLevel.SUBTLE)
    }
}
