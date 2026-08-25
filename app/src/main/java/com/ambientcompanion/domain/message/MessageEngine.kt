package com.ambientcompanion.domain.message

import com.ambientcompanion.domain.context.Personality
import com.ambientcompanion.domain.model.CompanionState
import kotlin.random.Random

enum class MessagePackId { DEFAULT, MINIMAL, MOTIVATIONAL, CUTE, FUNNY }

data class MessageRequest(
    val state: CompanionState,
    val personality: Personality,
    val pack: MessagePackId,
    val lastMessageId: String? = null,
    val tapsToday: Int = 0,
    val millisSinceLastTap: Long? = null,
)

data class SelectedMessage(val id: String, val text: String)

class MessageEngine(private val random: Random = Random.Default) {
    fun select(request: MessageRequest): SelectedMessage {
        val contextual = when {
            request.millisSinceLastTap != null && request.millisSinceLastTap < 1_500 && request.tapsToday >= 3 -> listOf("Okay okay 😂", "That tickles!")
            request.millisSinceLastTap == null || request.millisSinceLastTap > 4 * 60 * 60_000L -> listOf("Hey 👋", "Nice to see you!")
            request.state.isRain -> listOf("Umbrella time ☔", "Stay dry!")
            request.state == CompanionState.DAY_HOT -> listOf("Water break?", "Stay hydrated 💧")
            request.state in setOf(CompanionState.COLD, CompanionState.SNOW) -> listOf("Stay warm 🧣", "Cozy weather!")
            request.state.isNight -> listOf("Rest well 🌙", "Still awake? 👀")
            else -> packMessages.getValue(request.pack)
        }
        val styled = contextual.map { style(it, request.personality) }.distinct()
        val choices = styled.filterNot { id(it) == request.lastMessageId }.ifEmpty { styled }
        val text = choices[random.nextInt(choices.size)].take(40)
        return SelectedMessage(id(text), text)
    }

    fun automaticIntervalMs(personality: Personality): Long = when (personality) {
        Personality.CHEERFUL -> 45 * 60_000L
        Personality.PLAYFUL -> 60 * 60_000L
        Personality.CALM -> 90 * 60_000L
        Personality.QUIET -> 4 * 60 * 60_000L
    }

    private fun style(text: String, personality: Personality) = when (personality) {
        Personality.CHEERFUL -> if (text.any { it in "✨☀️🌙☔💧🧣👋😂" }) text else "$text ✨"
        Personality.CALM -> text.replace(Regex("[✨☀️🌙☔💧🧣👋😂]"), "").trim()
        Personality.PLAYFUL -> text
        Personality.QUIET -> text.substringBefore('!').substringBefore('?').trim().ifBlank { "Hi." }
    }

    private fun id(text: String) = text.hashCode().toString()

    companion object {
        private val packMessages = mapOf(
            MessagePackId.DEFAULT to listOf("Hope your day's going well!", "Good to see you"),
            MessagePackId.MINIMAL to listOf("Hello.", "Take care."),
            MessagePackId.MOTIVATIONAL to listOf("You've got this!", "Keep going 💪"),
            MessagePackId.CUTE to listOf("Morning sunshine ☀️", "Tiny happy moment!"),
            MessagePackId.FUNNY to listOf("Still computing vibes", "I live here now"),
        )
        private val CompanionState.isRain get() = this in setOf(CompanionState.MORNING_RAIN, CompanionState.DAY_RAIN, CompanionState.EVENING_RAIN, CompanionState.NIGHT_RAIN)
        private val CompanionState.isNight get() = this in setOf(CompanionState.NIGHT_CLEAR, CompanionState.NIGHT_CLOUDY, CompanionState.NIGHT_RAIN, CompanionState.NIGHT_SLEEP)
    }
}
