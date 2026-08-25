package com.ambientcompanion.data.wellbeing

import android.content.Context
import java.time.LocalDate

data class StoredWellbeingDay(
    val date: LocalDate,
    val appOpenCounts: Map<String, Int>,
    val activeMinutes: Map<String, Int>,
    val deliveredReactionIds: Set<String>,
)

class WellbeingRepository(context: Context) {
    private val store = context.getSharedPreferences("v3_local_activity", Context.MODE_PRIVATE)

    fun load(today: LocalDate): StoredWellbeingDay {
        if (store.getString(KEY_DATE, null) != today.toString()) {
            clear()
            store.edit().putString(KEY_DATE, today.toString()).apply()
        }
        return StoredWellbeingDay(
            today,
            decodeMap(store.getString(KEY_OPENS, null)),
            decodeMap(store.getString(KEY_ACTIVE_MINUTES, null)),
            store.getStringSet(KEY_REACTIONS, emptySet()).orEmpty(),
        )
    }

    fun saveOpens(today: LocalDate, values: Map<String, Int>) {
        ensureDate(today)
        store.edit().putString(KEY_OPENS, encodeMap(values)).apply()
    }

    fun saveActiveMinutes(today: LocalDate, values: Map<String, Int>) {
        ensureDate(today)
        store.edit().putString(KEY_ACTIVE_MINUTES, encodeMap(values)).apply()
    }

    fun markReaction(today: LocalDate, id: String) {
        ensureDate(today)
        store.edit().putStringSet(KEY_REACTIONS, store.getStringSet(KEY_REACTIONS, emptySet()).orEmpty() + id).apply()
    }

    fun clear() { store.edit().clear().apply() }

    private fun ensureDate(today: LocalDate) { if (store.getString(KEY_DATE, null) != today.toString()) load(today) }
    private fun encodeMap(values: Map<String, Int>) = values.filterValues { it > 0 }.entries.joinToString("\n") { "${it.key}|${it.value}" }
    private fun decodeMap(value: String?): Map<String, Int> = value.orEmpty().lineSequence().mapNotNull { line ->
        val separator = line.lastIndexOf('|')
        if (separator <= 0) null else line.substring(0, separator) to (line.substring(separator + 1).toIntOrNull() ?: return@mapNotNull null)
    }.toMap()

    companion object {
        private const val KEY_DATE = "date"
        private const val KEY_OPENS = "app_open_counts"
        private const val KEY_ACTIVE_MINUTES = "app_active_minutes"
        private const val KEY_REACTIONS = "delivered_reactions"
    }
}
