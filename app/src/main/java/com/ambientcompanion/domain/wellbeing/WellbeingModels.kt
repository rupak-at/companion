package com.ambientcompanion.domain.wellbeing

enum class WellbeingReactionStyle { GENTLE, PLAYFUL, MINIMAL, OFF }

enum class SessionState { ACTIVE, IDLE, PAUSED, ENDED }

data class WellbeingContext(
    val currentAppPackage: String? = null,
    val currentSessionDurationMs: Long = 0,
    val activeSessionDurationMs: Long = 0,
    val continuousScrollDurationMs: Long = 0,
    val scrollEventCount: Int = 0,
    val appOpenCountToday: Int = 0,
    val appActiveMinutesToday: Int = 0,
    val idleDurationMs: Long = 0,
    val sessionState: SessionState = SessionState.ENDED,
) {
    companion object { val EMPTY = WellbeingContext() }
}
