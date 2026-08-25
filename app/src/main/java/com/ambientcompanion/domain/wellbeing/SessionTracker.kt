package com.ambientcompanion.domain.wellbeing

class SessionTracker(private val idleThresholdMs: Long = 3 * 60_000L) {
    private var packageName: String? = null
    private var sessionStartedAt = 0L
    private var lastUpdateAt = 0L
    private var lastInteractionAt = 0L
    private var activeDurationMs = 0L
    private var screenOn = false
    private var state = SessionState.ENDED

    fun foreground(packageName: String?, screenOn: Boolean, now: Long) {
        advance(now)
        if (this.packageName != packageName) {
            this.packageName = packageName
            sessionStartedAt = now
            activeDurationMs = 0L
            lastInteractionAt = now
        }
        this.screenOn = screenOn
        state = if (packageName == null) SessionState.ENDED else if (screenOn) SessionState.ACTIVE else SessionState.PAUSED
        lastUpdateAt = now
    }

    fun interaction(now: Long) {
        advance(now)
        lastInteractionAt = now
        if (screenOn && packageName != null) state = SessionState.ACTIVE
        lastUpdateAt = now
    }

    fun screenOff(now: Long) {
        advance(now)
        screenOn = false
        state = SessionState.PAUSED
        lastUpdateAt = now
    }

    fun snapshot(now: Long): WellbeingContext {
        advance(now)
        val idle = (now - lastInteractionAt).coerceAtLeast(0L)
        state = when {
            packageName == null -> SessionState.ENDED
            !screenOn -> SessionState.PAUSED
            idle >= idleThresholdMs -> SessionState.IDLE
            else -> SessionState.ACTIVE
        }
        return WellbeingContext(
            currentAppPackage = packageName,
            currentSessionDurationMs = if (packageName == null) 0 else (now - sessionStartedAt).coerceAtLeast(0),
            activeSessionDurationMs = activeDurationMs,
            idleDurationMs = idle,
            sessionState = state,
        )
    }

    private fun advance(now: Long) {
        if (lastUpdateAt == 0L) { lastUpdateAt = now; return }
        val end = minOf(now, lastInteractionAt + idleThresholdMs)
        if (screenOn && packageName != null && end > lastUpdateAt) activeDurationMs += end - lastUpdateAt
        lastUpdateAt = now
    }
}
