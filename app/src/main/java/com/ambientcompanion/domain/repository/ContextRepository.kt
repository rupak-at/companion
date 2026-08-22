package com.ambientcompanion.domain.repository

import com.ambientcompanion.data.location.ApproximateLocation
import com.ambientcompanion.data.location.LocationProvider
import com.ambientcompanion.data.preferences.AppPreferences
import com.ambientcompanion.data.weather.WeatherRepository
import com.ambientcompanion.domain.engine.ContextEngine
import com.ambientcompanion.domain.engine.TimeClassifier
import com.ambientcompanion.domain.model.CompanionContext
import com.ambientcompanion.domain.model.CompanionState
import com.ambientcompanion.domain.model.WeatherCondition

data class ContextSnapshot(
    val context: CompanionContext,
    val state: CompanionState,
    val weatherAvailable: Boolean,
    val updatedAt: Long,
)

class ContextRepository(
    private val preferences: AppPreferences,
    private val locationProvider: LocationProvider,
    private val weatherRepository: WeatherRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun refresh(force: Boolean = false): ContextSnapshot {
        val settings = preferences.currentSettings()
        val location = settings.manualLatitude?.let { latitude ->
            settings.manualLongitude?.let { longitude -> ApproximateLocation(latitude, longitude) }
        } ?: locationProvider.currentLocation()
        val weather = if (settings.weatherEnabled && location != null) weatherRepository.weather(location, force) else null
        val seconds = now() / 1_000
        val period = TimeClassifier.classify(seconds, sunriseEpochSeconds = weather?.sunrise, sunsetEpochSeconds = weather?.sunset)
        val context = CompanionContext(
            timePeriod = period,
            weather = weather?.condition ?: WeatherCondition.UNKNOWN,
            temperatureCelsius = weather?.temperatureCelsius,
            isDay = weather?.isDay ?: period != com.ambientcompanion.domain.model.TimePeriod.NIGHT,
            sunrise = weather?.sunrise,
            sunset = weather?.sunset,
        )
        return ContextSnapshot(context, ContextEngine.determineState(context), weather != null, now())
    }
}
