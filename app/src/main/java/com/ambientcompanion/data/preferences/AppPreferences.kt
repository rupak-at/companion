package com.ambientcompanion.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ambientcompanion.domain.model.WeatherCondition
import com.ambientcompanion.domain.context.Personality
import com.ambientcompanion.domain.context.ResourceMode
import com.ambientcompanion.domain.schedule.OutsideHoursBehavior
import com.ambientcompanion.domain.behavior.QuickAction
import com.ambientcompanion.domain.wellbeing.WellbeingReactionStyle
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.dataStore by preferencesDataStore("ambient_preferences")

enum class CompanionSize(val dp: Int) { SMALL(68), MEDIUM(84), LARGE(104) }
enum class CompanionAppearance { AMBIENT, ARTWORK, EMOJI }
enum class CompanionArtwork(val label: String) {
    BIRD("Companion bird"),
    BOY_HOODIE("Boy in hoodie"),
    BULL_BLACK("Black bull"),
    FOX("Fox"),
    EAGLE_FLYING("Flying eagle"),
    EAGLE_AVIATOR("Aviator eagle"),
    GIRL_PORTRAIT("Girl portrait"),
    GIRL_YELLOW_DRESS("Yellow dress"),
    GIRL_MINT_HOODIE("Mint hoodie"),
    PANDA("Panda"),
}

data class UserSettings(
    val schemaVersion: Int = SettingsMigration.CURRENT_SCHEMA_VERSION,
    val onboardingComplete: Boolean = false,
    val companionEnabled: Boolean = false,
    val messagesEnabled: Boolean = true,
    val automaticMessages: Boolean = true,
    val weatherEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val companionSizeDp: Int = CompanionSize.MEDIUM.dp,
    val companionAppearance: CompanionAppearance = CompanionAppearance.AMBIENT,
    val selectedArtwork: CompanionArtwork = CompanionArtwork.BIRD,
    val rotateArtworkEnabled: Boolean = false,
    val selectedEmoji: String = "😊",
    val idleOpacity: Float = 0.72f,
    val edgeSnapEnabled: Boolean = false,
    val personality: Personality = Personality.PLAYFUL,
    val messagePack: String = "default",
    val theme: String = "default",
    val resourceMode: ResourceMode = ResourceMode.NORMAL,
    val batteryReactions: Boolean = true,
    val chargingReactions: Boolean = true,
    val connectivityReactions: Boolean = false,
    val headphoneReactions: Boolean = true,
    val weekendReactions: Boolean = true,
    val weekendDays: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
    val quietHoursEnabled: Boolean = false,
    val quietStartMinutes: Int = 22 * 60 + 30,
    val quietEndMinutes: Int = 7 * 60,
    val activeHoursEnabled: Boolean = false,
    val activeStartMinutes: Int = 7 * 60,
    val activeEndMinutes: Int = 23 * 60,
    val outsideHoursBehavior: OutsideHoursBehavior = OutsideHoursBehavior.SLEEP_IN_PLACE,
    val hiddenUntil: Long = 0L,
    val startAfterReboot: Boolean = false,
    val screenAwarenessEnabled: Boolean = false,
    val smartRepositioningEnabled: Boolean = true,
    val contextActionsEnabled: Boolean = true,
    val sensitiveScreenModeEnabled: Boolean = true,
    val wellbeingTrackingEnabled: Boolean = true,
    val longScrollRemindersEnabled: Boolean = true,
    val appOpenReactionsEnabled: Boolean = true,
    val firstScrollReminderMinutes: Int = 30,
    val strongScrollReminderMinutes: Int = 60,
    val wellbeingReactionStyle: WellbeingReactionStyle = WellbeingReactionStyle.PLAYFUL,
    val dailyTotalsEnabled: Boolean = false,
    val notificationAwarenessEnabled: Boolean = false,
    val breakSuggestionsEnabled: Boolean = true,
    val defaultBreakMinutes: Int = 2,
    val excludedScreenApps: Set<String> = emptySet(),
    val excludedWellbeingApps: Set<String> = emptySet(),
    val quickActions: List<QuickAction> = listOf(
        QuickAction.HOME, QuickAction.NOTIFICATIONS, QuickAction.SCREENSHOT, QuickAction.REFRESH,
    ),
    val manualLatitude: Double? = null,
    val manualLongitude: Double? = null,
)

data class CachedWeather(
    val condition: WeatherCondition,
    val temperatureCelsius: Double,
    val isDay: Boolean,
    val sunrise: Long?,
    val sunset: Long?,
    val fetchedAt: Long,
)

class AppPreferences(private val context: Context) {
    private val updateMutex = Mutex()
    val settings: Flow<UserSettings> = context.dataStore.data.map { values ->
        UserSettings(
            schemaVersion = values[SCHEMA_VERSION] ?: 1,
            onboardingComplete = values[ONBOARDING_COMPLETE] ?: false,
            companionEnabled = values[COMPANION_ENABLED] ?: false,
            messagesEnabled = values[MESSAGES_ENABLED] ?: true,
            automaticMessages = values[AUTOMATIC_MESSAGES] ?: true,
            weatherEnabled = values[WEATHER_ENABLED] ?: true,
            reducedMotion = values[REDUCED_MOTION] ?: false,
            companionSizeDp = (values[COMPANION_SIZE_DP]
                ?: CompanionSize.entries.getOrElse(values[COMPANION_SIZE] ?: 1) { CompanionSize.MEDIUM }.dp)
                .coerceIn(MIN_COMPANION_SIZE_DP, MAX_COMPANION_SIZE_DP),
            companionAppearance = values[COMPANION_APPEARANCE]
                ?.let { name -> runCatching { CompanionAppearance.valueOf(name) }.getOrNull() }
                ?: CompanionAppearance.AMBIENT,
            selectedArtwork = values[SELECTED_ARTWORK]
                ?.let { name -> runCatching { CompanionArtwork.valueOf(name) }.getOrNull() }
                ?: CompanionArtwork.BIRD,
            rotateArtworkEnabled = values[ROTATE_ARTWORK_ENABLED] ?: false,
            selectedEmoji = values[SELECTED_EMOJI] ?: "😊",
            idleOpacity = (values[IDLE_OPACITY] ?: 0.72f).coerceIn(0.35f, 1f),
            edgeSnapEnabled = values[EDGE_SNAP_ENABLED] ?: false,
            personality = values[PERSONALITY].enumOr(Personality.PLAYFUL),
            messagePack = values[MESSAGE_PACK] ?: "default",
            theme = values[THEME] ?: "default",
            resourceMode = values[RESOURCE_MODE].enumOr(ResourceMode.NORMAL),
            batteryReactions = values[BATTERY_REACTIONS] ?: true,
            chargingReactions = values[CHARGING_REACTIONS] ?: true,
            connectivityReactions = values[CONNECTIVITY_REACTIONS] ?: false,
            headphoneReactions = values[HEADPHONE_REACTIONS] ?: true,
            weekendReactions = values[WEEKEND_REACTIONS] ?: true,
            weekendDays = (values[WEEKEND_DAYS] ?: "SATURDAY,SUNDAY").split(',').mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.toSet(),
            quietHoursEnabled = values[QUIET_HOURS_ENABLED] ?: false,
            quietStartMinutes = values[QUIET_START] ?: 22 * 60 + 30,
            quietEndMinutes = values[QUIET_END] ?: 7 * 60,
            activeHoursEnabled = values[ACTIVE_HOURS_ENABLED] ?: false,
            activeStartMinutes = values[ACTIVE_START] ?: 7 * 60,
            activeEndMinutes = values[ACTIVE_END] ?: 23 * 60,
            outsideHoursBehavior = values[OUTSIDE_HOURS_BEHAVIOR].enumOr(OutsideHoursBehavior.SLEEP_IN_PLACE),
            hiddenUntil = values[HIDDEN_UNTIL] ?: 0L,
            startAfterReboot = values[START_AFTER_REBOOT] ?: false,
            screenAwarenessEnabled = values[SCREEN_AWARENESS] ?: false,
            smartRepositioningEnabled = values[SMART_REPOSITIONING] ?: true,
            contextActionsEnabled = values[CONTEXT_ACTIONS] ?: true,
            sensitiveScreenModeEnabled = values[SENSITIVE_SCREEN_MODE] ?: true,
            wellbeingTrackingEnabled = values[WELLBEING_TRACKING] ?: true,
            longScrollRemindersEnabled = values[LONG_SCROLL_REMINDERS] ?: true,
            appOpenReactionsEnabled = values[APP_OPEN_REACTIONS] ?: true,
            firstScrollReminderMinutes = (values[FIRST_SCROLL_REMINDER] ?: 30).coerceIn(5, 120),
            strongScrollReminderMinutes = (values[STRONG_SCROLL_REMINDER] ?: 60).coerceIn(10, 180),
            wellbeingReactionStyle = values[WELLBEING_REACTION_STYLE].enumOr(WellbeingReactionStyle.PLAYFUL),
            dailyTotalsEnabled = values[DAILY_TOTALS] ?: false,
            notificationAwarenessEnabled = values[NOTIFICATION_AWARENESS] ?: false,
            breakSuggestionsEnabled = values[BREAK_SUGGESTIONS] ?: true,
            defaultBreakMinutes = (values[DEFAULT_BREAK_MINUTES] ?: 2).coerceIn(1, 15),
            excludedScreenApps = values[EXCLUDED_SCREEN_APPS] ?: emptySet(),
            excludedWellbeingApps = values[EXCLUDED_WELLBEING_APPS] ?: emptySet(),
            quickActions = (values[QUICK_ACTIONS] ?: "HOME,NOTIFICATIONS,SCREENSHOT,REFRESH")
                .split(',').mapNotNull { runCatching { QuickAction.valueOf(it) }.getOrNull() }.distinct().take(4),
            manualLatitude = values[MANUAL_LATITUDE],
            manualLongitude = values[MANUAL_LONGITUDE],
        )
    }

    suspend fun currentSettings(): UserSettings = settings.first()

    suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
        updateMutex.withLock {
            val next = SettingsMigration.migrate(transform(currentSettings()))
            context.dataStore.edit {
                it[SCHEMA_VERSION] = next.schemaVersion
                it[ONBOARDING_COMPLETE] = next.onboardingComplete
                it[COMPANION_ENABLED] = next.companionEnabled
                it[MESSAGES_ENABLED] = next.messagesEnabled
                it[AUTOMATIC_MESSAGES] = next.automaticMessages
                it[WEATHER_ENABLED] = next.weatherEnabled
                it[REDUCED_MOTION] = next.reducedMotion
                it[COMPANION_SIZE_DP] = next.companionSizeDp.coerceIn(MIN_COMPANION_SIZE_DP, MAX_COMPANION_SIZE_DP)
                it[COMPANION_APPEARANCE] = next.companionAppearance.name
                it[SELECTED_ARTWORK] = next.selectedArtwork.name
                it[ROTATE_ARTWORK_ENABLED] = next.rotateArtworkEnabled
                it[SELECTED_EMOJI] = next.selectedEmoji
                it[IDLE_OPACITY] = next.idleOpacity.coerceIn(0.35f, 1f)
                it[EDGE_SNAP_ENABLED] = next.edgeSnapEnabled
                it[PERSONALITY] = next.personality.name
                it[MESSAGE_PACK] = next.messagePack
                it[THEME] = next.theme
                it[RESOURCE_MODE] = next.resourceMode.name
                it[BATTERY_REACTIONS] = next.batteryReactions
                it[CHARGING_REACTIONS] = next.chargingReactions
                it[CONNECTIVITY_REACTIONS] = next.connectivityReactions
                it[HEADPHONE_REACTIONS] = next.headphoneReactions
                it[WEEKEND_REACTIONS] = next.weekendReactions
                it[WEEKEND_DAYS] = next.weekendDays.joinToString(",") { day -> day.name }
                it[QUIET_HOURS_ENABLED] = next.quietHoursEnabled
                it[QUIET_START] = next.quietStartMinutes
                it[QUIET_END] = next.quietEndMinutes
                it[ACTIVE_HOURS_ENABLED] = next.activeHoursEnabled
                it[ACTIVE_START] = next.activeStartMinutes
                it[ACTIVE_END] = next.activeEndMinutes
                it[OUTSIDE_HOURS_BEHAVIOR] = next.outsideHoursBehavior.name
                it[HIDDEN_UNTIL] = next.hiddenUntil
                it[START_AFTER_REBOOT] = next.startAfterReboot
                it[SCREEN_AWARENESS] = next.screenAwarenessEnabled
                it[SMART_REPOSITIONING] = next.smartRepositioningEnabled
                it[CONTEXT_ACTIONS] = next.contextActionsEnabled
                it[SENSITIVE_SCREEN_MODE] = next.sensitiveScreenModeEnabled
                it[WELLBEING_TRACKING] = next.wellbeingTrackingEnabled
                it[LONG_SCROLL_REMINDERS] = next.longScrollRemindersEnabled
                it[APP_OPEN_REACTIONS] = next.appOpenReactionsEnabled
                it[FIRST_SCROLL_REMINDER] = next.firstScrollReminderMinutes.coerceIn(5, 120)
                it[STRONG_SCROLL_REMINDER] = next.strongScrollReminderMinutes.coerceIn(10, 180)
                it[WELLBEING_REACTION_STYLE] = next.wellbeingReactionStyle.name
                it[DAILY_TOTALS] = next.dailyTotalsEnabled
                it[NOTIFICATION_AWARENESS] = next.notificationAwarenessEnabled
                it[BREAK_SUGGESTIONS] = next.breakSuggestionsEnabled
                it[DEFAULT_BREAK_MINUTES] = next.defaultBreakMinutes.coerceIn(1, 15)
                it[EXCLUDED_SCREEN_APPS] = next.excludedScreenApps
                it[EXCLUDED_WELLBEING_APPS] = next.excludedWellbeingApps
                it[QUICK_ACTIONS] = next.quickActions.distinct().take(4).joinToString(",") { action -> action.name }
                next.manualLatitude?.let { value -> it[MANUAL_LATITUDE] = value } ?: it.remove(MANUAL_LATITUDE)
                next.manualLongitude?.let { value -> it[MANUAL_LONGITUDE] = value } ?: it.remove(MANUAL_LONGITUDE)
            }
        }
    }

    suspend fun cachedWeather(): CachedWeather? = context.dataStore.data.first().let { values ->
        val condition = values[CACHE_CONDITION]?.let { name ->
            runCatching { WeatherCondition.valueOf(name) }.getOrNull()
        } ?: return@let null
        CachedWeather(
            condition = condition,
            temperatureCelsius = values[CACHE_TEMPERATURE] ?: return@let null,
            isDay = values[CACHE_IS_DAY] ?: true,
            sunrise = values[CACHE_SUNRISE],
            sunset = values[CACHE_SUNSET],
            fetchedAt = values[CACHE_FETCHED_AT] ?: return@let null,
        )
    }

    suspend fun cacheWeather(weather: CachedWeather) {
        context.dataStore.edit {
            it[CACHE_CONDITION] = weather.condition.name
            it[CACHE_TEMPERATURE] = weather.temperatureCelsius
            it[CACHE_IS_DAY] = weather.isDay
            weather.sunrise?.let { value -> it[CACHE_SUNRISE] = value }
            weather.sunset?.let { value -> it[CACHE_SUNSET] = value }
            it[CACHE_FETCHED_AT] = weather.fetchedAt
        }
    }

    suspend fun resetPosition() {
        context.getSharedPreferences("overlay_position", Context.MODE_PRIVATE).edit().clear().apply()
    }

    suspend fun saveLastMessage(id: String, timestamp: Long) {
        context.dataStore.edit { it[LAST_MESSAGE_ID] = id; it[LAST_MESSAGE_AT] = timestamp }
    }

    suspend fun lastMessage(): Pair<String?, Long> = context.dataStore.data.first().let {
        it[LAST_MESSAGE_ID] to (it[LAST_MESSAGE_AT] ?: 0L)
    }

    data class TapRecord(val tapsToday: Int, val lastTapAt: Long?)

    suspend fun recordTap(timestamp: Long = System.currentTimeMillis()): TapRecord {
        val today = LocalDate.now().toString()
        val values = context.dataStore.data.first()
        val oldTimestamp = values[LAST_TAP_AT]
        val count = if (values[TAP_DATE] == today) (values[TAPS_TODAY] ?: 0) + 1 else 1
        context.dataStore.edit { it[TAP_DATE] = today; it[TAPS_TODAY] = count; it[LAST_TAP_AT] = timestamp }
        return TapRecord(count, oldTimestamp)
    }

    companion object {
        private val SCHEMA_VERSION = intPreferencesKey("settings_schema_version")
        private val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        private val COMPANION_ENABLED = booleanPreferencesKey("companion_enabled")
        private val MESSAGES_ENABLED = booleanPreferencesKey("messages_enabled")
        private val AUTOMATIC_MESSAGES = booleanPreferencesKey("automatic_messages")
        private val WEATHER_ENABLED = booleanPreferencesKey("weather_enabled")
        private val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        private val COMPANION_SIZE = intPreferencesKey("companion_size")
        private val COMPANION_SIZE_DP = intPreferencesKey("companion_size_dp")
        private val COMPANION_APPEARANCE = stringPreferencesKey("companion_appearance")
        private val SELECTED_ARTWORK = stringPreferencesKey("selected_artwork")
        private val ROTATE_ARTWORK_ENABLED = booleanPreferencesKey("rotate_artwork_enabled")
        private val SELECTED_EMOJI = stringPreferencesKey("selected_emoji")
        private val IDLE_OPACITY = floatPreferencesKey("idle_opacity")
        private val EDGE_SNAP_ENABLED = booleanPreferencesKey("edge_snap_enabled")
        private val PERSONALITY = stringPreferencesKey("personality")
        private val MESSAGE_PACK = stringPreferencesKey("message_pack")
        private val THEME = stringPreferencesKey("theme")
        private val RESOURCE_MODE = stringPreferencesKey("resource_mode")
        private val BATTERY_REACTIONS = booleanPreferencesKey("battery_reactions")
        private val CHARGING_REACTIONS = booleanPreferencesKey("charging_reactions")
        private val CONNECTIVITY_REACTIONS = booleanPreferencesKey("connectivity_reactions")
        private val HEADPHONE_REACTIONS = booleanPreferencesKey("headphone_reactions")
        private val WEEKEND_REACTIONS = booleanPreferencesKey("weekend_reactions")
        private val WEEKEND_DAYS = stringPreferencesKey("weekend_days")
        private val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        private val QUIET_START = intPreferencesKey("quiet_start_minutes")
        private val QUIET_END = intPreferencesKey("quiet_end_minutes")
        private val ACTIVE_HOURS_ENABLED = booleanPreferencesKey("active_hours_enabled")
        private val ACTIVE_START = intPreferencesKey("active_start_minutes")
        private val ACTIVE_END = intPreferencesKey("active_end_minutes")
        private val OUTSIDE_HOURS_BEHAVIOR = stringPreferencesKey("outside_hours_behavior")
        private val HIDDEN_UNTIL = longPreferencesKey("hidden_until")
        private val START_AFTER_REBOOT = booleanPreferencesKey("start_after_reboot")
        private val SCREEN_AWARENESS = booleanPreferencesKey("screen_awareness_enabled")
        private val SMART_REPOSITIONING = booleanPreferencesKey("smart_repositioning_enabled")
        private val CONTEXT_ACTIONS = booleanPreferencesKey("context_actions_enabled")
        private val SENSITIVE_SCREEN_MODE = booleanPreferencesKey("sensitive_screen_mode_enabled")
        private val WELLBEING_TRACKING = booleanPreferencesKey("wellbeing_tracking_enabled")
        private val LONG_SCROLL_REMINDERS = booleanPreferencesKey("long_scroll_reminders_enabled")
        private val APP_OPEN_REACTIONS = booleanPreferencesKey("app_open_reactions_enabled")
        private val FIRST_SCROLL_REMINDER = intPreferencesKey("first_scroll_reminder_minutes")
        private val STRONG_SCROLL_REMINDER = intPreferencesKey("strong_scroll_reminder_minutes")
        private val WELLBEING_REACTION_STYLE = stringPreferencesKey("wellbeing_reaction_style")
        private val DAILY_TOTALS = booleanPreferencesKey("daily_totals_enabled")
        private val NOTIFICATION_AWARENESS = booleanPreferencesKey("notification_awareness_enabled")
        private val BREAK_SUGGESTIONS = booleanPreferencesKey("break_suggestions_enabled")
        private val DEFAULT_BREAK_MINUTES = intPreferencesKey("default_break_minutes")
        private val EXCLUDED_SCREEN_APPS = stringSetPreferencesKey("excluded_screen_apps")
        private val EXCLUDED_WELLBEING_APPS = stringSetPreferencesKey("excluded_wellbeing_apps")
        private val QUICK_ACTIONS = stringPreferencesKey("quick_actions")
        private val MANUAL_LATITUDE = doublePreferencesKey("manual_latitude")
        private val MANUAL_LONGITUDE = doublePreferencesKey("manual_longitude")
        private val CACHE_CONDITION = stringPreferencesKey("cache_condition")
        private val CACHE_TEMPERATURE = doublePreferencesKey("cache_temperature")
        private val CACHE_IS_DAY = booleanPreferencesKey("cache_is_day")
        private val CACHE_SUNRISE = longPreferencesKey("cache_sunrise")
        private val CACHE_SUNSET = longPreferencesKey("cache_sunset")
        private val CACHE_FETCHED_AT = longPreferencesKey("cache_fetched_at")
        private val LAST_MESSAGE_ID = stringPreferencesKey("last_message_id")
        private val LAST_MESSAGE_AT = longPreferencesKey("last_message_at")
        private val TAP_DATE = stringPreferencesKey("tap_date")
        private val TAPS_TODAY = intPreferencesKey("taps_today")
        private val LAST_TAP_AT = longPreferencesKey("last_tap_at")
        const val MIN_COMPANION_SIZE_DP = 40
        const val MAX_COMPANION_SIZE_DP = 160
    }
}

private inline fun <reified T : Enum<T>> String?.enumOr(default: T): T =
    this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
