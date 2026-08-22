package com.ambientcompanion.data.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,weather_code,is_day",
        @Query("daily") daily: String = "sunrise,sunset",
        @Query("timeformat") timeFormat: String = "unixtime",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 1,
    ): WeatherResponse
}

@Serializable
data class WeatherResponse(val current: CurrentWeatherDto, val daily: DailyWeatherDto)

@Serializable
data class CurrentWeatherDto(
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("is_day") val isDay: Int,
)

@Serializable
data class DailyWeatherDto(val sunrise: List<Long>, val sunset: List<Long>)
