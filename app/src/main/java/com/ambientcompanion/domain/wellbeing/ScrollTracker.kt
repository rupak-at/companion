package com.ambientcompanion.domain.wellbeing

data class ScrollSnapshot(val durationMs: Long = 0, val eventCount: Int = 0)

class ScrollTracker(private val resetAfterMs: Long = 3 * 60_000L) {
    private var startedAt = 0L
    private var lastScrollAt = 0L
    private var eventCount = 0

    fun scroll(now: Long): ScrollSnapshot {
        if (lastScrollAt == 0L || now - lastScrollAt >= resetAfterMs) {
            startedAt = now
            eventCount = 0
        }
        lastScrollAt = now
        eventCount++
        return snapshot(now)
    }

    fun snapshot(now: Long): ScrollSnapshot {
        if (lastScrollAt == 0L || now - lastScrollAt >= resetAfterMs) return ScrollSnapshot()
        return ScrollSnapshot((now - startedAt).coerceAtLeast(0), eventCount)
    }

    fun reset() { startedAt = 0; lastScrollAt = 0; eventCount = 0 }
}
