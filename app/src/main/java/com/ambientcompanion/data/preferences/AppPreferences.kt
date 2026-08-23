package com.ambientcompanion.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ambientcompanion.domain.model.WeatherCondition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.dataStore by preferencesDataStore("ambient_preferences")

enum class CompanionSize(val dp: Int) { SMALL(68), MEDIUM(84), LARGE(104) }
enum class CompanionAppearance { AMBIENT, EMOJI }

data class UserSettings(
    val onboardingComplete: Boolean = false,
    val companionEnabled: Boolean = false,
    val messagesEnabled: Boolean = true,
    val automaticMessages: Boolean = true,
    val weatherEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val companionSize: CompanionSize = CompanionSize.MEDIUM,
    val companionAppearance: CompanionAppearance = CompanionAppearance.AMBIENT,
    val selectedEmoji: String = "😊",
    val idleOpacity: Float = 0.72f,
    val edgeSnapEnabled: Boolean = false,
    val manualLatitude: Double? = null,
    val manualLongitude: Double? = null,
)

data class CachedWeather(
    val condition: WeatherCondition,
    val temperatureCelsius: Double,
    val isDay: Boolean,
    val sunrise: Long?,
    val sunset: Long?,
    val fetchedAt: Long,
)

class AppPreferences(private val context: Context) {
    private val updateMutex = Mutex()
    val settings: Flow<UserSettings> = context.dataStore.data.map { values ->
        UserSettings(
            onboardingComplete = values[ONBOARDING_COMPLETE] ?: false,
            companionEnabled = values[COMPANION_ENABLED] ?: false,
            messagesEnabled = values[MESSAGES_ENABLED] ?: true,
            automaticMessages = values[AUTOMATIC_MESSAGES] ?: true,
            weatherEnabled = values[WEATHER_ENABLED] ?: true,
            reducedMotion = values[REDUCED_MOTION] ?: false,
            companionSize = CompanionSize.entries.getOrElse(values[COMPANION_SIZE] ?: 1) { CompanionSize.MEDIUM },
            companionAppearance = values[COMPANION_APPEARANCE]
                ?.let { name -> runCatching { CompanionAppearance.valueOf(name) }.getOrNull() }
                ?: CompanionAppearance.AMBIENT,
            selectedEmoji = values[SELECTED_EMOJI] ?: "😊",
            idleOpacity = (values[IDLE_OPACITY] ?: 0.72f).coerceIn(0.35f, 1f),
            edgeSnapEnabled = values[EDGE_SNAP_ENABLED] ?: false,
            manualLatitude = values[MANUAL_LATITUDE],
            manualLongitude = values[MANUAL_LONGITUDE],
        )
    }

    suspend fun currentSettings(): UserSettings = settings.first()

    suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
        updateMutex.withLock {
            val next = transform(currentSettings())
            context.dataStore.edit {
                it[ONBOARDING_COMPLETE] = next.onboardingComplete
                it[COMPANION_ENABLED] = next.companionEnabled
                it[MESSAGES_ENABLED] = next.messagesEnabled
                it[AUTOMATIC_MESSAGES] = next.automaticMessages
                it[WEATHER_ENABLED] = next.weatherEnabled
                it[REDUCED_MOTION] = next.reducedMotion
                it[COMPANION_SIZE] = next.companionSize.ordinal
                it[COMPANION_APPEARANCE] = next.companionAppearance.name
                it[SELECTED_EMOJI] = next.selectedEmoji
                it[IDLE_OPACITY] = next.idleOpacity.coerceIn(0.35f, 1f)
                it[EDGE_SNAP_ENABLED] = next.edgeSnapEnabled
                next.manualLatitude?.let { value -> it[MANUAL_LATITUDE] = value } ?: it.remove(MANUAL_LATITUDE)
                next.manualLongitude?.let { value -> it[MANUAL_LONGITUDE] = value } ?: it.remove(MANUAL_LONGITUDE)
            }
        }
    }

    suspend fun cachedWeather(): CachedWeather? = context.dataStore.data.first().let { values ->
        val condition = values[CACHE_CONDITION]?.let { name ->
            runCatching { WeatherCondition.valueOf(name) }.getOrNull()
        } ?: return@let null
        CachedWeather(
            condition = condition,
            temperatureCelsius = values[CACHE_TEMPERATURE] ?: return@let null,
            isDay = values[CACHE_IS_DAY] ?: true,
            sunrise = values[CACHE_SUNRISE],
            sunset = values[CACHE_SUNSET],
            fetchedAt = values[CACHE_FETCHED_AT] ?: return@let null,
        )
    }

    suspend fun cacheWeather(weather: CachedWeather) {
        context.dataStore.edit {
            it[CACHE_CONDITION] = weather.condition.name
            it[CACHE_TEMPERATURE] = weather.temperatureCelsius
            it[CACHE_IS_DAY] = weather.isDay
            weather.sunrise?.let { value -> it[CACHE_SUNRISE] = value }
            weather.sunset?.let { value -> it[CACHE_SUNSET] = value }
            it[CACHE_FETCHED_AT] = weather.fetchedAt
        }
    }

    suspend fun resetPosition() {
        context.getSharedPreferences("overlay_position", Context.MODE_PRIVATE).edit().clear().apply()
    }

    suspend fun saveLastMessage(id: String, timestamp: Long) {
        context.dataStore.edit { it[LAST_MESSAGE_ID] = id; it[LAST_MESSAGE_AT] = timestamp }
    }

    suspend fun lastMessage(): Pair<String?, Long> = context.dataStore.data.first().let {
        it[LAST_MESSAGE_ID] to (it[LAST_MESSAGE_AT] ?: 0L)
    }

    companion object {
        private val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        private val COMPANION_ENABLED = booleanPreferencesKey("companion_enabled")
        private val MESSAGES_ENABLED = booleanPreferencesKey("messages_enabled")
        private val AUTOMATIC_MESSAGES = booleanPreferencesKey("automatic_messages")
        private val WEATHER_ENABLED = booleanPreferencesKey("weather_enabled")
        private val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        private val COMPANION_SIZE = intPreferencesKey("companion_size")
        private val COMPANION_APPEARANCE = stringPreferencesKey("companion_appearance")
        private val SELECTED_EMOJI = stringPreferencesKey("selected_emoji")
        private val IDLE_OPACITY = floatPreferencesKey("idle_opacity")
        private val EDGE_SNAP_ENABLED = booleanPreferencesKey("edge_snap_enabled")
        private val MANUAL_LATITUDE = doublePreferencesKey("manual_latitude")
        private val MANUAL_LONGITUDE = doublePreferencesKey("manual_longitude")
        private val CACHE_CONDITION = stringPreferencesKey("cache_condition")
        private val CACHE_TEMPERATURE = doublePreferencesKey("cache_temperature")
        private val CACHE_IS_DAY = booleanPreferencesKey("cache_is_day")
        private val CACHE_SUNRISE = longPreferencesKey("cache_sunrise")
        private val CACHE_SUNSET = longPreferencesKey("cache_sunset")
        private val CACHE_FETCHED_AT = longPreferencesKey("cache_fetched_at")
        private val LAST_MESSAGE_ID = stringPreferencesKey("last_message_id")
        private val LAST_MESSAGE_AT = longPreferencesKey("last_message_at")
    }
}
