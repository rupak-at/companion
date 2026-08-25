package com.ambientcompanion.domain.model

enum class TimePeriod { MORNING, DAY, EVENING, NIGHT }

enum class WeatherCondition { CLEAR, CLOUDY, RAIN, STORM, FOG, SNOW, UNKNOWN }

enum class TemperatureFeeling { VERY_COLD, COLD, COMFORTABLE, WARM, HOT }

enum class CompanionState {
    MORNING_CLEAR,
    MORNING_CLOUDY,
    MORNING_RAIN,
    DAY_CLEAR,
    DAY_CLOUDY,
    DAY_RAIN,
    DAY_HOT,
    EVENING_CLEAR,
    EVENING_CLOUDY,
    EVENING_RAIN,
    NIGHT_CLEAR,
    NIGHT_CLOUDY,
    NIGHT_RAIN,
    NIGHT_SLEEP,
    COLD,
    STORM,
    FOG,
    SNOW,
    CRITICAL_BATTERY,
    LOW_BATTERY,
    CHARGING,
    BATTERY_FULL,
    HEADPHONES,
    NETWORK_LOST,
    NETWORK_RESTORED,
    WEEKEND,
}

data class CompanionContext(
    val timePeriod: TimePeriod,
    val weather: WeatherCondition,
    val temperatureCelsius: Double?,
    val isDay: Boolean,
    val sunrise: Long? = null,
    val sunset: Long? = null,
)
