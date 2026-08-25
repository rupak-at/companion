package com.ambientcompanion.domain.rule

import com.ambientcompanion.domain.context.*
import com.ambientcompanion.domain.model.*
import java.time.DayOfWeek
import org.junit.Assert.*
import org.junit.Test

class RuleEngineTest {
    private fun context(battery: Int = 60, charging: Boolean = false, quiet: Boolean = false, weekend: Boolean = false, weather: WeatherCondition = WeatherCondition.RAIN) = AmbientContext(
        CompanionContext(TimePeriod.NIGHT, weather, 18.0, false),
        DeviceContext(battery, charging, false, false, NetworkState.ONLINE, true, false, AudioOutputType.SPEAKER, DayOfWeek.SATURDAY, weekend),
        CompanionPreferences(quietHoursActive = quiet),
    )

    @Test fun `critical battery wins over night rain`() = assertEquals("critical_battery", RuleEngine().resolve(context(battery = 8)).winningRuleId)
    @Test fun `charging wins over rain`() = assertEquals("charging", RuleEngine().resolve(context(charging = true)).winningRuleId)
    @Test fun `quiet hours suppress automatic message`() = assertFalse(RuleEngine().resolve(context(quiet = true)).behavior.automaticMessageAllowed)
    @Test fun `weekend wins over normal environment`() = assertEquals("weekend", RuleEngine().resolve(context(weekend = true, weather = WeatherCondition.CLEAR)).winningRuleId)

    @Test fun `event queue is bounded deduplicated and expires`() {
        var now = 1_000L
        val queue = EventQueue(3) { now }
        queue.offer(CompanionEvent(CompanionEventType.NETWORK_LOST, now))
        queue.offer(CompanionEvent(CompanionEventType.NETWORK_LOST, now + 1))
        assertEquals(1, queue.snapshot().size)
        now += 31_000
        assertTrue(queue.snapshot().isEmpty())
    }
}
