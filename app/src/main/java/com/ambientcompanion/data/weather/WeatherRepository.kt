package com.ambientcompanion.data.weather

import com.ambientcompanion.data.location.ApproximateLocation
import com.ambientcompanion.data.preferences.AppPreferences
import com.ambientcompanion.data.preferences.CachedWeather
import com.ambientcompanion.domain.engine.WeatherClassifier

class WeatherRepository(
    private val api: WeatherApi,
    private val preferences: AppPreferences,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun weather(location: ApproximateLocation, force: Boolean = false): CachedWeather? {
        val cached = preferences.cachedWeather()
        if (!force && cached != null && clock() - cached.fetchedAt < REFRESH_INTERVAL_MS) return cached
        return runCatching {
            api.forecast(location.latitude, location.longitude).let { response ->
                CachedWeather(
                    condition = WeatherClassifier.fromWmoCode(response.current.weatherCode),
                    temperatureCelsius = response.current.temperature,
                    isDay = response.current.isDay == 1,
                    sunrise = response.daily.sunrise.firstOrNull(),
                    sunset = response.daily.sunset.firstOrNull(),
                    fetchedAt = clock(),
                ).also { preferences.cacheWeather(it) }
            }
        }.getOrElse {
            cached?.takeIf { clock() - it.fetchedAt < MAX_CACHE_AGE_MS }
        }
    }

    companion object {
        const val REFRESH_INTERVAL_MS = 60 * 60 * 1000L
        const val MAX_CACHE_AGE_MS = 3 * 60 * 60 * 1000L
    }
}
