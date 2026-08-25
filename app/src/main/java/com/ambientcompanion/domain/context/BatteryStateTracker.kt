package com.ambientcompanion.domain.context

class BatteryStateTracker {
    private var lowLatched = false
    fun update(percent: Int, full: Boolean): BatteryState {
        if (full || percent >= 100) { lowLatched = false; return BatteryState.FULL }
        if (percent <= 20) lowLatched = true
        if (percent >= 25) lowLatched = false
        return when {
            percent <= 10 -> BatteryState.CRITICAL
            lowLatched -> BatteryState.LOW
            percent >= 80 -> BatteryState.HIGH
            else -> BatteryState.NORMAL
        }
    }
}
