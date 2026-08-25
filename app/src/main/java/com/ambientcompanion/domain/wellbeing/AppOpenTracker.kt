package com.ambientcompanion.domain.wellbeing

import java.time.LocalDate

class AppOpenTracker {
    private var date: LocalDate? = null
    private var foregroundPackage: String? = null
    private val counts = mutableMapOf<String, Int>()

    fun foreground(packageName: String?, today: LocalDate): Int {
        resetIfNeeded(today)
        if (packageName != null && packageName != foregroundPackage) counts[packageName] = (counts[packageName] ?: 0) + 1
        foregroundPackage = packageName
        return packageName?.let { counts[it] } ?: 0
    }

    fun count(packageName: String?, today: LocalDate): Int {
        resetIfNeeded(today)
        return packageName?.let { counts[it] } ?: 0
    }

    fun snapshot(today: LocalDate): Map<String, Int> { resetIfNeeded(today); return counts.toMap() }
    fun restore(today: LocalDate, values: Map<String, Int>) { date = today; counts.clear(); counts.putAll(values.filterValues { it > 0 }) }
    fun clear() { date = null; foregroundPackage = null; counts.clear() }

    private fun resetIfNeeded(today: LocalDate) {
        if (date != today) { date = today; foregroundPackage = null; counts.clear() }
    }
}
