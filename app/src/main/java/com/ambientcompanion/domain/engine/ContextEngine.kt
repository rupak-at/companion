package com.ambientcompanion.domain.engine

import com.ambientcompanion.domain.model.CompanionContext
import com.ambientcompanion.domain.model.CompanionState
import com.ambientcompanion.domain.model.TemperatureFeeling
import com.ambientcompanion.domain.model.TimePeriod
import com.ambientcompanion.domain.model.WeatherCondition
import java.time.Instant
import java.time.ZoneId

object TimeClassifier {
    fun classify(
        epochSeconds: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        sunriseEpochSeconds: Long? = null,
        sunsetEpochSeconds: Long? = null,
    ): TimePeriod {
        if (sunriseEpochSeconds != null && sunsetEpochSeconds != null) {
            val eveningStart = sunsetEpochSeconds - 2 * 60 * 60
            return when {
                epochSeconds < sunriseEpochSeconds -> TimePeriod.NIGHT
                Instant.ofEpochSecond(epochSeconds).atZone(zoneId).hour < 11 -> TimePeriod.MORNING
                epochSeconds < eveningStart -> TimePeriod.DAY
                epochSeconds <= sunsetEpochSeconds + 60 * 60 -> TimePeriod.EVENING
                else -> TimePeriod.NIGHT
            }
        }
        return when (Instant.ofEpochSecond(epochSeconds).atZone(zoneId).hour) {
            in 5..10 -> TimePeriod.MORNING
            in 11..16 -> TimePeriod.DAY
            in 17..20 -> TimePeriod.EVENING
            else -> TimePeriod.NIGHT
        }
    }
}

object TemperatureClassifier {
    fun classify(celsius: Double): TemperatureFeeling = when {
        celsius < 5 -> TemperatureFeeling.VERY_COLD
        celsius < 15 -> TemperatureFeeling.COLD
        celsius <= 25 -> TemperatureFeeling.COMFORTABLE
        celsius <= 31 -> TemperatureFeeling.WARM
        else -> TemperatureFeeling.HOT
    }
}

object WeatherClassifier {
    fun fromWmoCode(code: Int): WeatherCondition = when (code) {
        0 -> WeatherCondition.CLEAR
        1, 2, 3 -> WeatherCondition.CLOUDY
        45, 48 -> WeatherCondition.FOG
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherCondition.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOW
        95, 96, 99 -> WeatherCondition.STORM
        else -> WeatherCondition.UNKNOWN
    }
}

object ContextEngine {
    fun determineState(context: CompanionContext): CompanionState {
        when (context.weather) {
            WeatherCondition.STORM -> return CompanionState.STORM
            WeatherCondition.RAIN -> return rainState(context.timePeriod)
            WeatherCondition.FOG -> return CompanionState.FOG
            WeatherCondition.SNOW -> return CompanionState.SNOW
            else -> Unit
        }

        context.temperatureCelsius?.let {
            when (TemperatureClassifier.classify(it)) {
                TemperatureFeeling.VERY_COLD, TemperatureFeeling.COLD -> return CompanionState.COLD
                TemperatureFeeling.HOT -> return CompanionState.DAY_HOT
                else -> Unit
            }
        }

        return normalState(context.timePeriod, context.weather)
    }

    private fun rainState(period: TimePeriod) = when (period) {
        TimePeriod.MORNING -> CompanionState.MORNING_RAIN
        TimePeriod.DAY -> CompanionState.DAY_RAIN
        TimePeriod.EVENING -> CompanionState.EVENING_RAIN
        TimePeriod.NIGHT -> CompanionState.NIGHT_RAIN
    }

    private fun normalState(period: TimePeriod, weather: WeatherCondition): CompanionState {
        val cloudy = weather == WeatherCondition.CLOUDY
        return when (period) {
            TimePeriod.MORNING -> if (cloudy) CompanionState.MORNING_CLOUDY else CompanionState.MORNING_CLEAR
            TimePeriod.DAY -> if (cloudy) CompanionState.DAY_CLOUDY else CompanionState.DAY_CLEAR
            TimePeriod.EVENING -> if (cloudy) CompanionState.EVENING_CLOUDY else CompanionState.EVENING_CLEAR
            TimePeriod.NIGHT -> if (cloudy) CompanionState.NIGHT_CLOUDY else CompanionState.NIGHT_CLEAR
        }
    }
}
