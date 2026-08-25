package com.ambientcompanion.domain.context

import com.ambientcompanion.domain.model.CompanionContext
import java.time.DayOfWeek

enum class AudioOutputType { NONE, SPEAKER, WIRED, USB, BLUETOOTH, UNKNOWN }
enum class NetworkState { ONLINE, OFFLINE, UNKNOWN }
enum class BatteryState { CRITICAL, LOW, NORMAL, HIGH, FULL }

data class DeviceContext(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val isBatteryFull: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val networkState: NetworkState = NetworkState.UNKNOWN,
    val isWifiConnected: Boolean? = null,
    val isHeadphonesConnected: Boolean = false,
    val audioOutputType: AudioOutputType = AudioOutputType.UNKNOWN,
    val dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val isWeekend: Boolean = false,
) {
    val batteryState: BatteryState get() = when {
        isBatteryFull || batteryPercent >= 100 -> BatteryState.FULL
        batteryPercent <= 10 -> BatteryState.CRITICAL
        batteryPercent <= 20 -> BatteryState.LOW
        batteryPercent >= 80 -> BatteryState.HIGH
        else -> BatteryState.NORMAL
    }
}

enum class Personality { CHEERFUL, CALM, PLAYFUL, QUIET }
enum class ResourceMode { NORMAL, BATTERY_SAVER, MINIMAL }

data class CompanionPreferences(
    val personality: Personality = Personality.PLAYFUL,
    val quietHoursActive: Boolean = false,
    val outsideActiveHours: Boolean = false,
    val connectivityReactions: Boolean = false,
    val headphoneReactions: Boolean = true,
    val batteryReactions: Boolean = true,
    val chargingReactions: Boolean = true,
    val weekendReactions: Boolean = true,
    val resourceMode: ResourceMode = ResourceMode.NORMAL,
)

data class InteractionSummary(
    val tapsToday: Int = 0,
    val lastTapAt: Long? = null,
    val lastReactionId: String? = null,
    val lastAutomaticMessageAt: Long? = null,
    val lastMessageId: String? = null,
)

data class AmbientContext(
    val environment: CompanionContext,
    val device: DeviceContext,
    val preferences: CompanionPreferences = CompanionPreferences(),
    val recentInteractions: InteractionSummary = InteractionSummary(),
)
