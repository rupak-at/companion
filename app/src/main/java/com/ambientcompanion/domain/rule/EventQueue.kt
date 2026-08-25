package com.ambientcompanion.domain.rule

enum class CompanionEventType { BATTERY_LOW, BATTERY_CRITICAL, CHARGING_STARTED, CHARGING_STOPPED, BATTERY_FULL, HEADPHONES_CONNECTED, NETWORK_LOST, NETWORK_RESTORED }
data class CompanionEvent(val type: CompanionEventType, val createdAt: Long, val priority: Int = 0, val ttlMs: Long = 30_000)

class EventQueue(private val capacity: Int = 3, private val now: () -> Long = System::currentTimeMillis) {
    private val events = mutableListOf<CompanionEvent>()
    fun offer(event: CompanionEvent) {
        purge()
        events.removeAll { it.type == event.type }
        events += event
        events.sortByDescending { it.priority }
        while (events.size > capacity) events.removeAt(events.lastIndex)
    }
    fun poll(): CompanionEvent? { purge(); return if (events.isEmpty()) null else events.removeAt(0) }
    fun snapshot(): List<CompanionEvent> { purge(); return events.toList() }
    private fun purge() { events.removeAll { now() - it.createdAt > it.ttlMs } }
}

class EventCooldowns(private val now: () -> Long = System::currentTimeMillis) {
    private val lastSeen = mutableMapOf<CompanionEventType, Long>()
    fun allow(type: CompanionEventType, cooldownMs: Long): Boolean {
        val current = now()
        val previous = lastSeen[type]
        if (previous != null && current - previous < cooldownMs) return false
        lastSeen[type] = current
        return true
    }
}
