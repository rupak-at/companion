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

    @Test fun `full battery wins over storm`() {
        val base = context(weather = WeatherCondition.STORM)
        val result = RuleEngine().resolve(base.copy(device = base.device.copy(batteryPercent = 100, isBatteryFull = true)))
        assertEquals("battery_full", result.winningRuleId)
        assertEquals(CompanionState.BATTERY_FULL, result.behavior.visualState)
    }

    @Test fun `low battery wins during quiet hours but suppresses automatic copy`() {
        val result = RuleEngine().resolve(context(battery = 15, quiet = true))
        assertEquals("low_battery", result.winningRuleId)
        assertFalse(result.behavior.automaticMessageAllowed)
    }

    @Test fun `rain wins over weekend and supplies umbrella`() {
        val result = RuleEngine().resolve(context(weekend = true, weather = WeatherCondition.RAIN))
        assertEquals("weather", result.winningRuleId)
        assertEquals(com.ambientcompanion.renderer.AccessoryId.UMBRELLA, result.behavior.accessory)
    }

    @Test fun `weekend remains persistent while offline`() {
        val base = context(weekend = true, weather = WeatherCondition.CLEAR)
        val result = RuleEngine().resolve(base.copy(device = base.device.copy(networkState = NetworkState.OFFLINE)))
        assertEquals("weekend", result.winningRuleId)
    }

    @Test fun `event queue is bounded deduplicated and expires`() {
        var now = 1_000L
        val queue = EventQueue(3) { now }
        queue.offer(CompanionEvent(CompanionEventType.NETWORK_LOST, now))
        queue.offer(CompanionEvent(CompanionEventType.NETWORK_LOST, now + 1))
        assertEquals(1, queue.snapshot().size)
        now += 31_000
        assertTrue(queue.snapshot().isEmpty())
    }

    @Test fun `critical event moves ahead of lower priority events`() {
        val queue = EventQueue(3) { 1_000L }
        queue.offer(CompanionEvent(CompanionEventType.NETWORK_RESTORED, 1_000L, 40))
        queue.offer(CompanionEvent(CompanionEventType.BATTERY_CRITICAL, 1_000L, 100))
        assertEquals(CompanionEventType.BATTERY_CRITICAL, queue.poll()?.type)
    }

    @Test fun `cooldown allows first event and rejects repeats until elapsed`() {
        var now = 1_000L
        val cooldowns = EventCooldowns { now }
        assertTrue(cooldowns.allow(CompanionEventType.HEADPHONES_CONNECTED, 30_000L))
        assertFalse(cooldowns.allow(CompanionEventType.HEADPHONES_CONNECTED, 30_000L))
        now += 30_000L
        assertTrue(cooldowns.allow(CompanionEventType.HEADPHONES_CONNECTED, 30_000L))
    }
}
