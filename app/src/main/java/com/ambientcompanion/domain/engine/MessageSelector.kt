package com.ambientcompanion.domain.engine

import com.ambientcompanion.domain.model.CompanionState
import kotlin.random.Random

data class CompanionMessage(val id: String, val text: String)

class MessageSelector(private val random: Random = Random.Default) {
    fun select(state: CompanionState, lastMessageId: String? = null): CompanionMessage {
        val candidates = map.getValue(state)
        val choices = candidates.filterNot { it.id == lastMessageId }.ifEmpty { candidates }
        return choices[random.nextInt(choices.size)]
    }

    companion object {
        private fun messages(vararg values: String) = values.mapIndexed { index, text ->
            CompanionMessage("${text.hashCode()}-$index", text)
        }

        private val morning = messages("Good morning ☀️", "Have a great day!", "You've got this ✨")
        private val day = messages("Hope your day's going well!", "Keep going 💪", "Beautiful day ☀️")
        private val evening = messages("Good evening ✨", "Slow down a little 🌆", "Nice evening, huh?")
        private val night = messages("Rest well 🌙", "Still awake? 👀", "Sleepy time 😴")
        private val rain = messages("Umbrella time ☔", "Stay dry!", "Rainy mood 🌧️")
        private val map = buildMap {
            put(CompanionState.MORNING_CLEAR, morning)
            put(CompanionState.MORNING_CLOUDY, morning)
            put(CompanionState.MORNING_RAIN, rain)
            put(CompanionState.DAY_CLEAR, day)
            put(CompanionState.DAY_CLOUDY, day)
            put(CompanionState.DAY_RAIN, rain)
            put(CompanionState.DAY_HOT, messages("Stay hydrated 💧", "Water break?"))
            put(CompanionState.EVENING_CLEAR, evening)
            put(CompanionState.EVENING_CLOUDY, evening)
            put(CompanionState.EVENING_RAIN, rain)
            put(CompanionState.NIGHT_CLEAR, night)
            put(CompanionState.NIGHT_CLOUDY, night)
            put(CompanionState.NIGHT_RAIN, rain)
            put(CompanionState.NIGHT_SLEEP, night)
            put(CompanionState.COLD, messages("Stay warm 🧣", "Brrr... ❄️", "Cozy weather!"))
            put(CompanionState.STORM, messages("Stormy out there ⚡", "Stay cozy and safe"))
            put(CompanionState.FOG, messages("A misty little world", "Take it slow out there"))
            put(CompanionState.SNOW, messages("Snow day ❄️", "Bundle up!"))
        }
    }
}
