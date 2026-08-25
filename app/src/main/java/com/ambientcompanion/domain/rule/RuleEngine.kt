package com.ambientcompanion.domain.rule

import com.ambientcompanion.domain.behavior.CompanionBehavior
import com.ambientcompanion.domain.behavior.CompanionEffect
import com.ambientcompanion.domain.behavior.CompanionMood
import com.ambientcompanion.domain.context.AmbientContext
import com.ambientcompanion.domain.context.BatteryState
import com.ambientcompanion.domain.engine.ContextEngine
import com.ambientcompanion.domain.model.CompanionState
import com.ambientcompanion.renderer.AccessoryId
import com.ambientcompanion.renderer.AnimationId

interface ContextRule {
    val id: String
    val priority: Int
    fun matches(context: AmbientContext): Boolean
    fun result(context: AmbientContext): CompanionEffect.Persistent
}

class RuleEngine(private val rules: List<ContextRule> = defaultRules) {
    fun resolve(context: AmbientContext): ResolvedBehavior {
        val matches = rules.filter { it.matches(context) }.sortedByDescending { it.priority }
        val winner = matches.firstOrNull() ?: EnvironmentRule
        return ResolvedBehavior(winner.id, matches.map { it.id }, winner.result(context).behavior)
    }

    data class ResolvedBehavior(val winningRuleId: String, val activeRuleIds: List<String>, val behavior: CompanionBehavior)

    companion object {
        val defaultRules = listOf(CriticalBatteryRule, LowBatteryRule, ChargingRule, FullBatteryRule, StormRule, WeatherRule, QuietRule, WeekendRule, EnvironmentRule)
    }
}

object CriticalBatteryRule : ContextRule {
    override val id = "critical_battery"; override val priority = 100
    override fun matches(context: AmbientContext) = context.preferences.batteryReactions && context.device.batteryState == BatteryState.CRITICAL
    override fun result(context: AmbientContext) = CompanionEffect.Persistent(CompanionBehavior(
        CompanionState.CRITICAL_BATTERY, AnimationId.BATTERY_LOW, messagePoolId = "criticalBattery", mood = CompanionMood.WORRIED,
        automaticMessageAllowed = !context.preferences.quietHoursActive,
    ))
}

object LowBatteryRule : ContextRule {
    override val id = "low_battery"; override val priority = 95
    override fun matches(context: AmbientContext) = context.preferences.batteryReactions && context.device.batteryState == BatteryState.LOW
    override fun result(context: AmbientContext) = CompanionEffect.Persistent(CompanionBehavior(
        CompanionState.LOW_BATTERY, AnimationId.BATTERY_LOW, messagePoolId = "lowBattery", mood = CompanionMood.TIRED,
        automaticMessageAllowed = !context.preferences.quietHoursActive,
    ))
}

object ChargingRule : ContextRule {
    override val id = "charging"; override val priority = 90
    override fun matches(context: AmbientContext) = context.preferences.chargingReactions && context.device.isCharging
    override fun result(context: AmbientContext) = CompanionEffect.Persistent(CompanionBehavior(
        if (context.device.isBatteryFull) CompanionState.BATTERY_FULL else CompanionState.CHARGING,
        AnimationId.CHARGING, accessory = AccessoryId.CHARGING_SPARK,
        messagePoolId = if (context.device.isBatteryFull) "batteryFull" else "charging", mood = CompanionMood.HAPPY,
        automaticMessageAllowed = !context.preferences.quietHoursActive,
    ))
}

object FullBatteryRule : ContextRule {
    override val id = "battery_full"; override val priority = 92
    override fun matches(context: AmbientContext) = context.preferences.batteryReactions && context.device.batteryState == BatteryState.FULL
    override fun result(context: AmbientContext) = CompanionEffect.Persistent(CompanionBehavior(
        CompanionState.BATTERY_FULL, AnimationId.BATTERY_FULL, messagePoolId = "batteryFull", mood = CompanionMood.HAPPY,
        automaticMessageAllowed = !context.preferences.quietHoursActive,
    ))
}

object StormRule : ContextRule {
    override val id = "storm"; override val priority = 80
    override fun matches(context: AmbientContext) = context.environment.weather == com.ambientcompanion.domain.model.WeatherCondition.STORM
    override fun result(context: AmbientContext) = EnvironmentRule.result(context)
}

object WeatherRule : ContextRule {
    override val id = "weather"; override val priority = 70
    override fun matches(context: AmbientContext) = context.environment.weather in setOf(
        com.ambientcompanion.domain.model.WeatherCondition.RAIN, com.ambientcompanion.domain.model.WeatherCondition.SNOW,
        com.ambientcompanion.domain.model.WeatherCondition.FOG,
    )
    override fun result(context: AmbientContext) = EnvironmentRule.result(context)
}

object QuietRule : ContextRule {
    override val id = "quiet_hours"; override val priority = 50
    override fun matches(context: AmbientContext) = context.preferences.quietHoursActive || context.preferences.outsideActiveHours
    override fun result(context: AmbientContext) = CompanionEffect.Persistent(CompanionBehavior(
        CompanionState.NIGHT_SLEEP, AnimationId.SLEEP, accessory = AccessoryId.SLEEP_CAP,
        mood = CompanionMood.SLEEPY, automaticMessageAllowed = false,
    ))
}

object WeekendRule : ContextRule {
    override val id = "weekend"; override val priority = 30
    override fun matches(context: AmbientContext) = context.preferences.weekendReactions && context.device.isWeekend
    override fun result(context: AmbientContext) = CompanionEffect.Persistent(CompanionBehavior(
        CompanionState.WEEKEND, AnimationId.WEEKEND, messagePoolId = "weekend", mood = CompanionMood.PLAYFUL,
    ))
}

object EnvironmentRule : ContextRule {
    override val id = "environment"; override val priority = 20
    override fun matches(context: AmbientContext) = true
    override fun result(context: AmbientContext): CompanionEffect.Persistent {
        val state = ContextEngine.determineState(context.environment)
        val animation = when (state) {
            CompanionState.MORNING_RAIN, CompanionState.DAY_RAIN, CompanionState.EVENING_RAIN,
            CompanionState.NIGHT_RAIN, CompanionState.STORM -> AnimationId.RAIN
            CompanionState.COLD, CompanionState.SNOW -> AnimationId.COLD
            CompanionState.DAY_HOT -> AnimationId.HOT
            CompanionState.NIGHT_SLEEP -> AnimationId.SLEEP
            else -> AnimationId.IDLE
        }
        val accessory = when (state) {
            CompanionState.MORNING_RAIN, CompanionState.DAY_RAIN, CompanionState.EVENING_RAIN,
            CompanionState.NIGHT_RAIN -> AccessoryId.UMBRELLA
            CompanionState.COLD, CompanionState.SNOW -> AccessoryId.SCARF
            CompanionState.NIGHT_SLEEP -> AccessoryId.SLEEP_CAP
            else -> null
        }
        return CompanionEffect.Persistent(CompanionBehavior(
            state, idleAnimation = animation, messagePoolId = "environment", accessory = accessory,
            automaticMessageAllowed = !context.preferences.quietHoursActive,
        ))
    }
}
