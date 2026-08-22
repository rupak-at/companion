package com.ambientcompanion.domain.engine

import com.ambientcompanion.domain.model.CompanionContext
import com.ambientcompanion.domain.model.CompanionState
import com.ambientcompanion.domain.model.TimePeriod
import com.ambientcompanion.domain.model.WeatherCondition
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

class ContextEngineTest {
    @Test fun `storm overrides rain and heat`() {
        assertEquals(
            CompanionState.STORM,
            state(TimePeriod.DAY, WeatherCondition.STORM, 36.0),
        )
    }

    @Test fun `rain keeps time-specific appearance`() {
        assertEquals(CompanionState.MORNING_RAIN, state(TimePeriod.MORNING, WeatherCondition.RAIN))
        assertEquals(CompanionState.NIGHT_RAIN, state(TimePeriod.NIGHT, WeatherCondition.RAIN))
    }

    @Test fun `temperature overrides ordinary weather`() {
        assertEquals(CompanionState.DAY_HOT, state(TimePeriod.EVENING, WeatherCondition.CLEAR, 32.0))
        assertEquals(CompanionState.COLD, state(TimePeriod.DAY, WeatherCondition.CLOUDY, 4.9))
    }

    @Test fun `unknown weather falls back to time`() {
        assertEquals(CompanionState.EVENING_CLEAR, state(TimePeriod.EVENING, WeatherCondition.UNKNOWN))
    }

    @Test fun `WMO codes collapse into V1 categories`() {
        assertEquals(WeatherCondition.CLEAR, WeatherClassifier.fromWmoCode(0))
        assertEquals(WeatherCondition.RAIN, WeatherClassifier.fromWmoCode(82))
        assertEquals(WeatherCondition.STORM, WeatherClassifier.fromWmoCode(95))
        assertEquals(WeatherCondition.UNKNOWN, WeatherClassifier.fromWmoCode(400))
    }

    @Test fun `fixed time boundaries match the specification`() {
        val zone = ZoneId.of("UTC")
        fun at(hour: Int) = LocalDateTime.of(2026, 8, 22, hour, 0).atZone(zone).toEpochSecond()
        assertEquals(TimePeriod.NIGHT, TimeClassifier.classify(at(4), zone))
        assertEquals(TimePeriod.MORNING, TimeClassifier.classify(at(5), zone))
        assertEquals(TimePeriod.DAY, TimeClassifier.classify(at(11), zone))
        assertEquals(TimePeriod.EVENING, TimeClassifier.classify(at(17), zone))
        assertEquals(TimePeriod.NIGHT, TimeClassifier.classify(at(21), zone))
    }

    @Test fun `sunrise and sunset override fixed boundaries`() {
        val zone = ZoneId.of("UTC")
        val base = LocalDateTime.of(2026, 8, 22, 0, 0).atZone(zone).toEpochSecond()
        val sunrise = base + 6 * 3_600
        val sunset = base + 18 * 3_600
        assertEquals(TimePeriod.NIGHT, TimeClassifier.classify(base + 5 * 3_600, zone, sunrise, sunset))
        assertEquals(TimePeriod.MORNING, TimeClassifier.classify(base + 7 * 3_600, zone, sunrise, sunset))
        assertEquals(TimePeriod.DAY, TimeClassifier.classify(base + 12 * 3_600, zone, sunrise, sunset))
        assertEquals(TimePeriod.EVENING, TimeClassifier.classify(base + 17 * 3_600, zone, sunrise, sunset))
        assertEquals(TimePeriod.NIGHT, TimeClassifier.classify(base + 20 * 3_600, zone, sunrise, sunset))
    }

    @Test fun `every supported condition resolves without exceeding V1 states`() {
        for (period in TimePeriod.entries) {
            for (weather in WeatherCondition.entries) {
                ContextEngine.determineState(CompanionContext(period, weather, 20.0, period != TimePeriod.NIGHT))
            }
        }
        assertEquals(18, CompanionState.entries.size)
    }

    @Test fun `message selector avoids an immediate repeat`() {
        val selector = MessageSelector(Random(2))
        val first = selector.select(CompanionState.DAY_CLEAR)
        val second = selector.select(CompanionState.DAY_CLEAR, first.id)
        assertNotEquals(first.id, second.id)
    }

    private fun state(
        period: TimePeriod,
        weather: WeatherCondition,
        temperature: Double? = 20.0,
    ) = ContextEngine.determineState(
        CompanionContext(period, weather, temperature, period != TimePeriod.NIGHT),
    )
}
