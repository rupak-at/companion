package com.ambientcompanion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ambientcompanion.data.preferences.CompanionAppearance
import com.ambientcompanion.data.preferences.CompanionArtwork
import com.ambientcompanion.data.preferences.AppPreferences
import com.ambientcompanion.data.preferences.UserSettings
import com.ambientcompanion.domain.model.CompanionState
import com.ambientcompanion.domain.repository.ContextSnapshot
import com.ambientcompanion.R
import com.ambientcompanion.domain.context.Personality
import com.ambientcompanion.domain.context.ResourceMode
import com.ambientcompanion.domain.behavior.QuickAction
import com.ambientcompanion.domain.schedule.OutsideHoursBehavior
import com.ambientcompanion.data.preferences.SettingsMigration
import java.time.DayOfWeek
import com.ambientcompanion.overlay.CompanionOverlayService
import com.ambientcompanion.domain.context.AmbientContext
import com.ambientcompanion.domain.context.AudioOutputType
import com.ambientcompanion.domain.context.CompanionPreferences
import com.ambientcompanion.domain.context.DeviceContext
import com.ambientcompanion.domain.context.NetworkState
import com.ambientcompanion.domain.model.CompanionContext
import com.ambientcompanion.domain.model.TimePeriod
import com.ambientcompanion.domain.model.WeatherCondition
import com.ambientcompanion.domain.rule.RuleEngine
import com.ambientcompanion.domain.message.MessageEngine
import com.ambientcompanion.domain.message.MessagePackId
import com.ambientcompanion.domain.message.MessageRequest
import com.ambientcompanion.domain.screen.AppProfile
import com.ambientcompanion.domain.screen.CompanionDisplayMode
import com.ambientcompanion.domain.screen.ScreenContext
import com.ambientcompanion.domain.wellbeing.WellbeingReactionStyle
import kotlin.math.roundToInt

enum class AppScreen { HOME, CUSTOMIZE, SETTINGS, SCREEN_AWARENESS, WELLBEING, PRIVACY, APP_PROFILES, PREVIEW, DEBUG }
data class InstalledAppEntry(val packageName: String, val label: String, val profile: AppProfile)

@Composable
fun AmbientApp(
    settings: UserSettings,
    settingsLoaded: Boolean,
    snapshot: ContextSnapshot?,
    screenContext: ScreenContext,
    installedApps: List<InstalledAppEntry>,
    screen: AppScreen,
    hasAccessibilityPermission: Boolean,
    overlayRunning: Boolean,
    onNavigate: (AppScreen) -> Unit,
    onRequestLocation: () -> Unit,
    onCompleteOnboarding: () -> Unit,
    onToggleCompanion: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onUpdateSettings: ((UserSettings) -> UserSettings) -> Unit,
    onResetPosition: () -> Unit,
    onAddQuickTile: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onSaveAppProfile: (AppProfile) -> Unit,
    onClearActivityData: () -> Unit,
    onPreviewState: (CompanionState) -> Unit,
) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = Aubergine, surface = Porcelain)) {
        if (!settingsLoaded) {
            LaunchScreen()
        } else if (!settings.onboardingComplete) {
            Onboarding(
                onRequestLocation,
                onOpenAccessibilitySettings,
                hasAccessibilityPermission,
                onCompleteOnboarding,
            )
        } else if (settings.schemaVersion < SettingsMigration.CURRENT_SCHEMA_VERSION) {
            WhatsNew { onUpdateSettings { SettingsMigration.migrate(it) } }
        } else when (screen) {
            AppScreen.HOME -> Home(settings, snapshot, screenContext, overlayRunning, hasAccessibilityPermission, onToggleCompanion, onOpenAccessibilitySettings, onRefresh, onUpdateSettings, onNavigate)
            AppScreen.CUSTOMIZE -> Customize(settings, onUpdateSettings, onNavigate)
            AppScreen.SETTINGS -> Settings(settings, onToggleCompanion, onUpdateSettings, onResetPosition, onAddQuickTile, onOpenAccessibilitySettings, onRefresh, onNavigate)
            AppScreen.SCREEN_AWARENESS -> ScreenAwareness(settings, hasAccessibilityPermission, onUpdateSettings, onOpenAccessibilitySettings, onNavigate)
            AppScreen.WELLBEING -> WellbeingSettings(settings, onUpdateSettings, onNavigate)
            AppScreen.PRIVACY -> PrivacySettings(settings, onUpdateSettings, onClearActivityData, onNavigate)
            AppScreen.APP_PROFILES -> AppProfiles(settings, installedApps, onUpdateSettings, onSaveAppProfile, onNavigate)
            AppScreen.PREVIEW -> Preview(settings, onPreviewState, onNavigate)
            AppScreen.DEBUG -> Debug(settings, snapshot, onPreviewState, onNavigate)
        }
    }
}

@Composable
private fun WhatsNew(continueToApp: () -> Unit) {
    PremiumBackground {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Mascot(CompanionState.WEEKEND, 148)
            Spacer(Modifier.height(28.dp))
            Text("What’s new in V3", color = Ink, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))
            listOf("Optional screen awareness", "Smart obstruction avoidance", "Private contextual actions", "Per-app behavior", "Gentle wellbeing reminders").forEach {
                Text("✓  $it", color = MutedInk, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            }
            Spacer(Modifier.height(28.dp))
            PrimaryButton("Continue", continueToApp)
        }
    }
}

@Composable
private fun LaunchScreen() {
    PremiumBackground {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrandMark()
            Spacer(Modifier.height(18.dp))
            Text("Loading your companion…", color = MutedInk, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Onboarding(
    requestLocation: () -> Unit,
    requestAccessibility: () -> Unit,
    accessibilityAllowed: Boolean,
    complete: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf("Meet Ambient", "Moves with your world", "Weather, gently", "Helpful controls", "You're all set")
    val copy = listOf(
        "A tiny companion designed to make ordinary moments feel a little warmer.",
        "Morning energy, evening calm, and a sleepy face when the day winds down.",
        "Approximate location helps Ambient notice rain, warmth, and daylight. Your location history is never stored.",
        "In Accessibility, open Ambient Companion controls and turn on its main switch. Optional Screen Awareness derives structure like typing or scrolling locally; raw content is never stored.",
        "Your companion is ready to say hello.",
    )
    PremiumBackground {
        Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("0${step + 1}  /  05", color = AmberDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(34.dp)); Mascot(if (step == 4) CompanionState.MORNING_CLEAR else CompanionState.DAY_CLEAR, 148)
            Spacer(Modifier.height(34.dp)); Text(titles[step], color = Ink, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp)); Text(copy[step], color = MutedInk, fontSize = 16.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(36.dp))
            when (step) {
                2 -> {
                    PrimaryButton("Use approximate location") { requestLocation(); step++ }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { step++ }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) { Text("Choose a city later", color = Ink) }
                }
                3 -> PrimaryButton(if (accessibilityAllowed) "Continue" else "Find Ambient Companion controls") { if (accessibilityAllowed) step++ else requestAccessibility() }
                4 -> PrimaryButton("Meet my companion", complete)
                else -> PrimaryButton("Continue") { step++ }
            }
        }
    }
}

@Composable
private fun Home(
    settings: UserSettings,
    snapshot: ContextSnapshot?,
    screenContext: ScreenContext,
    running: Boolean,
    accessibilityEnabled: Boolean,
    toggle: (Boolean) -> Unit,
    openAccessibilitySettings: () -> Unit,
    refresh: () -> Unit,
    update: ((UserSettings) -> UserSettings) -> Unit,
    navigate: (AppScreen) -> Unit,
) {
    val state = snapshot?.state ?: CompanionState.DAY_CLEAR
    PremiumBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(); Spacer(Modifier.height(34.dp)); Mascot(state, 154, settings.selectedEmoji.takeIf { settings.companionAppearance == CompanionAppearance.EMOJI }, settings.selectedArtwork.takeIf { settings.companionAppearance == CompanionAppearance.ARTWORK }); Spacer(Modifier.height(18.dp))
            Text(messageFor(state), color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp)); Text(contextLine(snapshot), color = MutedInk, fontSize = 14.sp); Spacer(Modifier.height(30.dp))
            PremiumCard { SettingRow("Floating companion", if (running) "Here with you" else "Currently resting") { Switch(running, toggle, colors = premiumSwitchColors()) } }
            Spacer(Modifier.height(14.dp))
            PremiumCard {
                SettingRow(
                    "Screen awareness",
                    if (!settings.screenAwarenessEnabled) "Off · V1/V2 behavior continues" else if (screenContext.packageName == null) "Waiting for screen context" else "${screenContext.screenType.name.lowercase().replaceFirstChar(Char::uppercase)} · ${screenContext.appCategory.name.lowercase().replaceFirstChar(Char::uppercase)}",
                ) {
                    Switch(settings.screenAwarenessEnabled, { enabled ->
                        update { it.copy(screenAwarenessEnabled = enabled) }
                        if (enabled && !accessibilityEnabled) openAccessibilitySettings()
                    }, colors = premiumSwitchColors())
                }
            }
            Spacer(Modifier.height(14.dp))
            if (!accessibilityEnabled) {
                ActionCard(
                    "Enable Ambient Companion controls",
                    "Open Accessibility, select this service, then turn on its main switch",
                    openAccessibilitySettings,
                )
                Spacer(Modifier.height(14.dp))
            }
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { navigate(AppScreen.CUSTOMIZE) },
                color = Aubergine,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 8.dp,
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (settings.companionAppearance == CompanionAppearance.ARTWORK) {
                        Image(painterResource(settings.selectedArtwork.drawableRes()), settings.selectedArtwork.label, Modifier.size(40.dp), contentScale = ContentScale.Fit)
                    } else {
                        Text(if (settings.companionAppearance == CompanionAppearance.EMOJI) settings.selectedEmoji else "✦", fontSize = 30.sp)
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text("Customize companion", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text("Change mascot, emoji, size and fade", color = Color.White.copy(alpha = .72f), fontSize = 13.sp)
                    }
                    Text("›", color = Color.White, fontSize = 26.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallAction("Refresh", Modifier.weight(1f), refresh); SmallAction("Preview", Modifier.weight(1f)) { navigate(AppScreen.PREVIEW) }; SmallAction("Settings", Modifier.weight(1f)) { navigate(AppScreen.SETTINGS) }
            }
            Spacer(Modifier.height(22.dp)); Text(if (settings.weatherEnabled) "Weather context on · Private by design" else "Time-only mode · Private by design", color = MutedInk, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Customize(
    settings: UserSettings,
    update: ((UserSettings) -> UserSettings) -> Unit,
    navigate: (AppScreen) -> Unit,
) {
    ScreenList("Customize", { navigate(AppScreen.HOME) }) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Mascot(
                    CompanionState.DAY_CLEAR,
                    138,
                    settings.selectedEmoji.takeIf { settings.companionAppearance == CompanionAppearance.EMOJI },
                    settings.selectedArtwork.takeIf { settings.companionAppearance == CompanionAppearance.ARTWORK },
                )
                Spacer(Modifier.height(8.dp))
                Text("Your floating companion", color = MutedInk, fontSize = 13.sp)
            }
        }
        item { SectionLabel("CHOOSE A LOOK") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppearanceChoice(
                    title = "Ambient",
                    symbol = "•ᴗ•",
                    selected = settings.companionAppearance == CompanionAppearance.AMBIENT,
                    modifier = Modifier.weight(1f),
                ) { update { it.copy(companionAppearance = CompanionAppearance.AMBIENT) } }
                AppearanceChoice(
                    title = "Artwork",
                    symbol = "▣",
                    selected = settings.companionAppearance == CompanionAppearance.ARTWORK,
                    modifier = Modifier.weight(1f),
                ) { update { it.copy(companionAppearance = CompanionAppearance.ARTWORK) } }
                AppearanceChoice(
                    title = "Emoji",
                    symbol = settings.selectedEmoji,
                    selected = settings.companionAppearance == CompanionAppearance.EMOJI,
                    modifier = Modifier.weight(1f),
                ) { update { it.copy(companionAppearance = CompanionAppearance.EMOJI) } }
            }
        }
        if (settings.companionAppearance == CompanionAppearance.EMOJI) {
            item { EmojiPicker(settings.selectedEmoji) { emoji -> update { it.copy(selectedEmoji = emoji) } } }
        }
        if (settings.companionAppearance == CompanionAppearance.ARTWORK) {
            item { ArtworkPicker(settings.selectedArtwork) { artwork -> update { it.copy(selectedArtwork = artwork) } } }
        }
        item { SectionLabel("DISPLAY") }
        item { SizeControl(settings.companionSizeDp) { value -> update { it.copy(companionSizeDp = value) } } }
        item { OpacityControl(settings.idleOpacity) { value -> update { it.copy(idleOpacity = value) } } }
        item { ToggleCard("Snap to screen edge", if (settings.screenAwarenessEnabled) "Turn off to leave it anywhere" else "Inactive while screen awareness is off", settings.edgeSnapEnabled) { value -> update { it.copy(edgeSnapEnabled = value) } } }
    }
}

@Composable
private fun Settings(settings: UserSettings, toggleCompanion: (Boolean) -> Unit, update: ((UserSettings) -> UserSettings) -> Unit, reset: () -> Unit, addQuickTile: () -> Unit, openAccessibilitySettings: () -> Unit, refresh: () -> Unit, navigate: (AppScreen) -> Unit) {
    var taps by remember { mutableIntStateOf(0) }
    ScreenList("Settings", { navigate(AppScreen.HOME) }) {
        item { SectionLabel("V3 AWARENESS") }
        item { ActionCard("Screen awareness", if (settings.screenAwarenessEnabled) "On · screen structure stays local" else "Off · V1/V2 remains available") { navigate(AppScreen.SCREEN_AWARENESS) } }
        item { ActionCard("Digital wellbeing", if (settings.wellbeingTrackingEnabled) "Active time and scrolling" else "Off") { navigate(AppScreen.WELLBEING) } }
        item { ActionCard("Per-app profiles", "Normal, Small, Quiet, Peek, Hidden or Privacy") { navigate(AppScreen.APP_PROFILES) } }
        item { ActionCard("Privacy", "Sensitive mode, exclusions and local data") { navigate(AppScreen.PRIVACY) } }
        item { SectionLabel("EXPERIENCE") }
        item { ToggleCard("Companion", "Show above your apps", settings.companionEnabled, toggleCompanion) }
        item { ToggleCard("Messages", "Show dialogue when you tap", settings.messagesEnabled) { value -> update { it.copy(messagesEnabled = value) } } }
        item { ToggleCard("Automatic messages", "At most once each hour", settings.automaticMessages) { value -> update { it.copy(automaticMessages = value) } } }
        item { ToggleCard("Weather context", "Use local conditions when available", settings.weatherEnabled) { value -> update { it.copy(weatherEnabled = value) } } }
        item { ToggleCard("Reduced motion", "Prefer gentle fades", settings.reducedMotion) { value -> update { it.copy(reducedMotion = value) } } }
        item { ActionCard("Personality", settings.personality.name.lowercase().replaceFirstChar(Char::uppercase)) { update { it.copy(personality = Personality.entries[(it.personality.ordinal + 1) % Personality.entries.size]) } } }
        item { ActionCard("Message pack", settings.messagePack.replaceFirstChar(Char::uppercase)) { update { val packs = listOf("default", "minimal", "motivational", "cute", "funny"); it.copy(messagePack = packs[(packs.indexOf(it.messagePack) + 1).mod(packs.size)]) } } }
        item { ActionCard("Theme", settings.theme.replaceFirstChar(Char::uppercase)) { update { val themes = listOf("default", "night glow", "warm sunset", "cloud", "mono"); it.copy(theme = themes[(themes.indexOf(it.theme) + 1).mod(themes.size)]) } } }
        item { ActionCard("Resource mode", settings.resourceMode.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)) { update { it.copy(resourceMode = ResourceMode.entries[(it.resourceMode.ordinal + 1) % ResourceMode.entries.size]) } } }
        item { SectionLabel("DEVICE CONTEXT") }
        item { ToggleCard("Battery reactions", "Low, critical and full states", settings.batteryReactions) { value -> update { it.copy(batteryReactions = value) } } }
        item { ToggleCard("Charging reactions", "React when power connects", settings.chargingReactions) { value -> update { it.copy(chargingReactions = value) } } }
        item { ToggleCard("Headphone reactions", "Wired, USB and Bluetooth output", settings.headphoneReactions) { value -> update { it.copy(headphoneReactions = value) } } }
        item { ToggleCard("Connectivity reactions", "Off by default to stay quiet", settings.connectivityReactions) { value -> update { it.copy(connectivityReactions = value) } } }
        item { ToggleCard("Weekend reactions", settings.weekendDays.joinToString { it.name.take(3) }, settings.weekendReactions) { value -> update { it.copy(weekendReactions = value) } } }
        item { SectionLabel("SCHEDULE") }
        item { ToggleCard("Quiet hours", "10:30 PM–7:00 AM", settings.quietHoursEnabled) { value -> update { it.copy(quietHoursEnabled = value) } } }
        if (settings.quietHoursEnabled) item { TimeRangeCard("Quiet range", settings.quietStartMinutes, settings.quietEndMinutes) { start, end -> update { it.copy(quietStartMinutes = start, quietEndMinutes = end) } } }
        item { ToggleCard("Active hours", "7:00 AM–11:00 PM", settings.activeHoursEnabled) { value -> update { it.copy(activeHoursEnabled = value) } } }
        if (settings.activeHoursEnabled) {
            item { TimeRangeCard("Active range", settings.activeStartMinutes, settings.activeEndMinutes) { start, end -> update { it.copy(activeStartMinutes = start, activeEndMinutes = end) } } }
            item { ActionCard("Outside active hours", settings.outsideHoursBehavior.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)) { update { it.copy(outsideHoursBehavior = OutsideHoursBehavior.entries[(it.outsideHoursBehavior.ordinal + 1) % OutsideHoursBehavior.entries.size]) } } }
        }
        item { WeekendPicker(settings.weekendDays) { days -> update { it.copy(weekendDays = days) } } }
        item { SectionLabel("ACTIONS") }
        item { QuickActionPicker(settings.quickActions) { actions -> update { it.copy(quickActions = actions) } } }
        item { ToggleCard("Start after reboot", "Off by default; requires enabled companion controls", settings.startAfterReboot) { value -> update { it.copy(startAfterReboot = value) } } }
        item { ActionCard("Assistive controls", "Enable Back, Home, Recents and system actions") { openAccessibilitySettings() } }
        item { SectionLabel("COMPANION") }
        item { ActionCard("Appearance", appearanceLabel(settings)) { update { it.copy(companionAppearance = CompanionAppearance.entries[(it.companionAppearance.ordinal + 1) % CompanionAppearance.entries.size]) } } }
        if (settings.companionAppearance == CompanionAppearance.EMOJI) {
            item { EmojiPicker(settings.selectedEmoji) { emoji -> update { it.copy(selectedEmoji = emoji) } } }
        }
        if (settings.companionAppearance == CompanionAppearance.ARTWORK) {
            item { ArtworkPicker(settings.selectedArtwork) { artwork -> update { it.copy(selectedArtwork = artwork) } } }
        }
        item { SizeControl(settings.companionSizeDp) { value -> update { it.copy(companionSizeDp = value) } } }
        item { OpacityControl(settings.idleOpacity) { value -> update { it.copy(idleOpacity = value) } } }
        item { ToggleCard("Snap to screen edge", if (settings.screenAwarenessEnabled) "Turn off for free positioning" else "Inactive while screen awareness is off", settings.edgeSnapEnabled) { value -> update { it.copy(edgeSnapEnabled = value) } } }
        item { ActionCard("Location", if (settings.manualLatitude == null) "Automatic · tap for Kathmandu" else "Kathmandu · tap for automatic") { update { if (it.manualLatitude == null) it.copy(manualLatitude = 27.7172, manualLongitude = 85.3240) else it.copy(manualLatitude = null, manualLongitude = null) }; refresh() } }
        item { ActionCard("Reset position", "Return to the right edge", reset) }
        item { ActionCard("Quick Settings button", "Add a system-area show/hide control", addQuickTile) }
        item { ActionCard("Refresh weather", "Update environmental context", refresh) }
        item { SectionLabel("ABOUT") }
        item { ActionCard("Ambient Companion", "Version 0.3.0") { taps++; if (taps >= 7) navigate(AppScreen.DEBUG) } }
    }
}

@Composable
private fun ScreenAwareness(
    settings: UserSettings,
    accessibilityEnabled: Boolean,
    update: ((UserSettings) -> UserSettings) -> Unit,
    openAccessibilitySettings: () -> Unit,
    navigate: (AppScreen) -> Unit,
) = ScreenList("Screen Awareness", { navigate(AppScreen.SETTINGS) }) {
    item {
        PremiumCard {
            Text("Understands structure, not content", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text("Ambient can derive whether you’re typing, scrolling, viewing a form, or watching fullscreen media. Raw accessibility text is processed ephemerally and never stored or sent to the cloud.", color = MutedInk, lineHeight = 20.sp)
        }
    }
    item { ToggleCard("Enabled", if (accessibilityEnabled) "Accessibility service connected" else "Enable the service in Android settings", settings.screenAwarenessEnabled) { enabled ->
        update { it.copy(screenAwarenessEnabled = enabled) }
        if (enabled && !accessibilityEnabled) openAccessibilitySettings()
    } }
    item { ToggleCard("Smart repositioning", "Moves silently around keyboards, fields and dialogs", settings.smartRepositioningEnabled) { value -> update { it.copy(smartRepositioningEnabled = value) } } }
    item { ToggleCard("Context actions", "Only run after you choose them", settings.contextActionsEnabled) { value -> update { it.copy(contextActionsEnabled = value) } } }
    item { ToggleCard("Sensitive Screen Mode", "Restricts actions and messages on private screens", settings.sensitiveScreenModeEnabled) { value -> update { it.copy(sensitiveScreenModeEnabled = value) } } }
    item { ActionCard("Per-app profiles", "Choose Normal, Small, Quiet, Peek, Hidden or Privacy") { navigate(AppScreen.APP_PROFILES) } }
    if (!accessibilityEnabled) item { ActionCard("Open Android Accessibility", "V1/V2 features keep working if you decline", openAccessibilitySettings) }
}

@Composable
private fun WellbeingSettings(
    settings: UserSettings,
    update: ((UserSettings) -> UserSettings) -> Unit,
    navigate: (AppScreen) -> Unit,
) = ScreenList("Digital Wellbeing", { navigate(AppScreen.SETTINGS) }) {
    item { Text("Local counters distinguish recent interaction from simply leaving an app open. Reminders never block an app.", color = MutedInk, lineHeight = 20.sp) }
    item { ToggleCard("Active-session tracking", "Pauses after three idle minutes", settings.wellbeingTrackingEnabled) { value -> update { it.copy(wellbeingTrackingEnabled = value) } } }
    item { ToggleCard("Long-scroll reminders", "Optional, cooldown-controlled nudges", settings.longScrollRemindersEnabled) { value -> update { it.copy(longScrollRemindersEnabled = value) } } }
    item { ToggleCard("App-open reactions", "A light reaction after repeated opens", settings.appOpenReactionsEnabled) { value -> update { it.copy(appOpenReactionsEnabled = value) } } }
    item { ActionCard("First reminder", "${settings.firstScrollReminderMinutes} minutes") { update { it.copy(firstScrollReminderMinutes = if (it.firstScrollReminderMinutes >= 60) 15 else it.firstScrollReminderMinutes + 15) } } }
    item { ActionCard("Strong reminder", "${settings.strongScrollReminderMinutes} minutes") { update { it.copy(strongScrollReminderMinutes = if (it.strongScrollReminderMinutes >= 120) 30 else it.strongScrollReminderMinutes + 15) } } }
    item { ActionCard("Reaction style", settings.wellbeingReactionStyle.name.lowercase().replaceFirstChar(Char::uppercase)) { update { it.copy(wellbeingReactionStyle = WellbeingReactionStyle.entries[(it.wellbeingReactionStyle.ordinal + 1) % WellbeingReactionStyle.entries.size]) } } }
    item { ToggleCard("Daily totals", "Stored only on this device", settings.dailyTotalsEnabled) { value -> update { it.copy(dailyTotalsEnabled = value) } } }
    item { ToggleCard("Break suggestions", "A user-started two-minute pause", settings.breakSuggestionsEnabled) { value -> update { it.copy(breakSuggestionsEnabled = value) } } }
    item { ActionCard("Excluded apps", "Choose apps that wellbeing should ignore") { navigate(AppScreen.APP_PROFILES) } }
}

@Composable
private fun PrivacySettings(
    settings: UserSettings,
    update: ((UserSettings) -> UserSettings) -> Unit,
    clearActivityData: () -> Unit,
    navigate: (AppScreen) -> Unit,
) = ScreenList("Privacy", { navigate(AppScreen.SETTINGS) }) {
    item { ToggleCard("Sensitive Screen Mode", "Passwords, authentication and finance use Privacy mode", settings.sensitiveScreenModeEnabled) { value -> update { it.copy(sensitiveScreenModeEnabled = value) } } }
    item {
        PremiumCard {
            Text("Privacy dashboard", color = Ink, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            PrivacyLine("Screen Awareness", if (settings.screenAwarenessEnabled) "ON" else "OFF")
            PrivacyLine("Wellbeing Tracking", if (settings.wellbeingTrackingEnabled) "ON" else "OFF")
            PrivacyLine("Notification Awareness", "OFF")
            PrivacyLine("Raw screen storage", "NEVER")
            PrivacyLine("Cloud screen processing", "OFF")
        }
    }
    item { ActionCard("Never inspect apps", "${settings.excludedScreenApps.size} excluded") { navigate(AppScreen.APP_PROFILES) } }
    item { ActionCard("Clear V3 activity data", "Clears daily counts, sessions, scroll totals and reaction state", clearActivityData) }
    item { Text("This does not change companion position, themes, schedules, or general preferences.", color = MutedInk, fontSize = 12.sp) }
}

@Composable
private fun AppProfiles(
    settings: UserSettings,
    installedApps: List<InstalledAppEntry>,
    update: ((UserSettings) -> UserSettings) -> Unit,
    saveProfile: (AppProfile) -> Unit,
    navigate: (AppScreen) -> Unit,
) {
    var localProfiles by remember { mutableStateOf(emptyMap<String, AppProfile>()) }
    ScreenList("Per-app profiles", { navigate(AppScreen.SETTINGS) }) {
        item { Text("Your override always wins. Finance defaults to Privacy, video to Edge Peek, games to Hidden, messaging to Small, and system apps to Quiet.", color = MutedInk, lineHeight = 20.sp) }
        items(installedApps, key = InstalledAppEntry::packageName) { entry ->
            val profile = localProfiles[entry.packageName] ?: entry.profile
            AppPolicyCard(
                entry = entry,
                profile = profile,
                inspect = entry.packageName !in settings.excludedScreenApps,
                wellbeing = entry.packageName !in settings.excludedWellbeingApps,
                cycleMode = {
                    val next = profile.copy(displayMode = CompanionDisplayMode.entries[(profile.displayMode.ordinal + 1) % CompanionDisplayMode.entries.size])
                    localProfiles = localProfiles + (entry.packageName to next)
                    saveProfile(next)
                },
                setMessages = { enabled ->
                    val next = profile.copy(allowMessages = enabled)
                    localProfiles = localProfiles + (entry.packageName to next)
                    saveProfile(next)
                },
                setActions = { enabled ->
                    val next = profile.copy(allowContextActions = enabled)
                    localProfiles = localProfiles + (entry.packageName to next)
                    saveProfile(next)
                },
                setInspect = { enabled -> update { it.copy(excludedScreenApps = if (enabled) it.excludedScreenApps - entry.packageName else it.excludedScreenApps + entry.packageName) } },
                setWellbeing = { enabled -> update { it.copy(excludedWellbeingApps = if (enabled) it.excludedWellbeingApps - entry.packageName else it.excludedWellbeingApps + entry.packageName) } },
            )
        }
    }
}

@Composable private fun PrivacyLine(label: String, value: String) = Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    Text(label, color = MutedInk, modifier = Modifier.weight(1f))
    Text(value, color = if (value == "NEVER" || value == "OFF") AmberDeep else Aubergine, fontWeight = FontWeight.Bold, fontSize = 12.sp)
}

@Composable private fun AppPolicyCard(
    entry: InstalledAppEntry,
    profile: AppProfile,
    inspect: Boolean,
    wellbeing: Boolean,
    cycleMode: () -> Unit,
    setMessages: (Boolean) -> Unit,
    setActions: (Boolean) -> Unit,
    setInspect: (Boolean) -> Unit,
    setWellbeing: (Boolean) -> Unit,
) = PremiumCard {
    SettingRow(entry.label, entry.packageName) { Text("›", color = Aubergine, fontSize = 22.sp, modifier = Modifier.clickable(onClick = cycleMode)) }
    Spacer(Modifier.height(8.dp))
    SettingRow("Companion mode", profile.displayMode.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)) { Text("Change", color = Aubergine, fontSize = 12.sp, modifier = Modifier.clickable(onClick = cycleMode).padding(8.dp)) }
    SettingRow("Messages", if (profile.allowMessages) "Allowed" else "Quiet") { Switch(profile.allowMessages, setMessages, colors = premiumSwitchColors()) }
    SettingRow("Screen actions", if (profile.allowContextActions) "Allowed" else "Hidden") { Switch(profile.allowContextActions, setActions, colors = premiumSwitchColors()) }
    SettingRow("Screen inspection", if (inspect) "Allowed" else "Never inspect") { Switch(inspect, setInspect, colors = premiumSwitchColors()) }
    SettingRow("Wellbeing", if (wellbeing) "Included" else "Excluded") { Switch(wellbeing, setWellbeing, colors = premiumSwitchColors()) }
}

@Composable private fun Preview(settings: UserSettings, preview: (CompanionState) -> Unit, navigate: (AppScreen) -> Unit) {
    var period by remember { mutableStateOf(TimePeriod.DAY) }
    var weather by remember { mutableStateOf(WeatherCondition.CLEAR) }
    var temperature by remember { mutableFloatStateOf(20f) }
    var battery by remember { mutableIntStateOf(74) }
    var charging by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf(true) }
    var headphones by remember { mutableStateOf(false) }
    var weekend by remember { mutableStateOf(false) }
    var quiet by remember { mutableStateOf(false) }
    var personality by remember { mutableStateOf(settings.personality) }
    var theme by remember { mutableStateOf(settings.theme) }
    val environment = CompanionContext(period, weather, temperature.toDouble(), period != TimePeriod.NIGHT)
    val ambient = AmbientContext(environment, DeviceContext(
        batteryPercent = battery, isCharging = charging, isBatteryFull = battery == 100,
        networkState = if (online) NetworkState.ONLINE else NetworkState.OFFLINE,
        isHeadphonesConnected = headphones,
        audioOutputType = if (headphones) AudioOutputType.BLUETOOTH else AudioOutputType.SPEAKER,
        dayOfWeek = if (weekend) DayOfWeek.SATURDAY else DayOfWeek.MONDAY, isWeekend = weekend,
    ), CompanionPreferences(personality = personality, quietHoursActive = quiet))
    val resolved = RuleEngine().resolve(ambient)
    val pack = runCatching { MessagePackId.valueOf(settings.messagePack.uppercase()) }.getOrDefault(MessagePackId.DEFAULT)
    val message = MessageEngine().select(MessageRequest(resolved.behavior.visualState, personality, pack)).text
    ScreenList("V2 preview", { navigate(AppScreen.HOME) }) {
        item { PremiumCard { Row(verticalAlignment = Alignment.CenterVertically) { Mascot(resolved.behavior.visualState, 86); Column(Modifier.padding(start = 14.dp)) { Text(resolved.winningRuleId.uppercase(), color = Ink, fontWeight = FontWeight.SemiBold); Text("Temporary: ${if (headphones) "HEADPHONES_CONNECTED" else "none"}", color = MutedInk); Text("Accessory: ${resolved.behavior.accessory ?: if (headphones) "HEADPHONES" else "none"}", color = MutedInk); Text("Message: “$message”", color = MutedInk) } } } }
        item { ActionCard("Time", period.name) { period = TimePeriod.entries[(period.ordinal + 1) % TimePeriod.entries.size] } }
        item { ActionCard("Weather", weather.name) { weather = WeatherCondition.entries[(weather.ordinal + 1) % WeatherCondition.entries.size] } }
        item { PremiumCard { SettingRow("Temperature", "${temperature.roundToInt()}°C") { Text("${temperature.roundToInt()}°", color = Aubergine) }; Slider(temperature, { temperature = it }, valueRange = -15f..45f) } }
        item { PremiumCard { SettingRow("Battery", "$battery%") { Text("$battery%", color = Aubergine) }; Slider(battery.toFloat(), { battery = it.roundToInt() }, valueRange = 0f..100f) } }
        item { ToggleCard("Charging", "Simulate power connection", charging) { charging = it } }
        item { ToggleCard("Network", if (online) "Online" else "Offline", online) { online = it } }
        item { ToggleCard("Headphones", "Simulate audio output", headphones) { headphones = it } }
        item { ToggleCard("Weekend", "Use configured weekend mood", weekend) { weekend = it } }
        item { ToggleCard("Quiet hours", "Suppress automatic messages", quiet) { quiet = it } }
        item { ActionCard("Personality", personality.name) { personality = com.ambientcompanion.domain.context.Personality.entries[(personality.ordinal + 1) % com.ambientcompanion.domain.context.Personality.entries.size] } }
        item { ActionCard("Theme", theme) { val themes = listOf("default", "night glow", "warm sunset", "cloud", "mono"); theme = themes[(themes.indexOf(theme) + 1).mod(themes.size)] } }
        item { SectionLabel("DIRECT VISUAL PREVIEW") }
        items(CompanionState.entries) { state -> Surface(Modifier.fillMaxWidth().clickable { preview(state) }, color = Color.White.copy(alpha = .88f), shape = RoundedCornerShape(24.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Mascot(state, 54); Column(Modifier.padding(start = 16.dp)) { Text(state.displayName(), color = Ink, fontWeight = FontWeight.SemiBold); Text("Tap to preview on the overlay", color = MutedInk, fontSize = 12.sp) } } } }
    }
}

@Composable private fun Debug(settings: UserSettings, snapshot: ContextSnapshot?, preview: (CompanionState) -> Unit, navigate: (AppScreen) -> Unit) = ScreenList("Rule debugger", { navigate(AppScreen.SETTINGS) }) {
    val debug = CompanionOverlayService.debugSnapshot()
    item { PremiumCard { Text("Current AmbientContext", color = Ink, fontWeight = FontWeight.SemiBold); Text("Environment: ${snapshot?.context?.weather ?: "unknown"} · ${snapshot?.context?.timePeriod ?: "unknown"}", color = MutedInk); Text("Renderer: ${debug?.renderer ?: settings.companionAppearance} · Resource: ${debug?.resourceMode ?: settings.resourceMode}", color = MutedInk); Text("Personality: ${settings.personality} · Pack: ${settings.messagePack}", color = MutedInk) } }
    item { PremiumCard { Text("Rule resolution", color = Ink, fontWeight = FontWeight.SemiBold); Text("Winner: ${debug?.winningRule ?: "overlay unavailable"}", color = MutedInk); Text("Active: ${debug?.activeRules?.joinToString().orEmpty().ifBlank { "none" }}", color = MutedInk); Text("Queue: ${debug?.queuedEvents?.joinToString().orEmpty().ifBlank { "empty" }}", color = MutedInk); Text("Animation: ${debug?.animation ?: "paused"} · Accessory: ${debug?.accessory ?: "none"}", color = MutedInk) } }
    item { PremiumCard {
        val screen = debug?.screenContext ?: ScreenContext.EMPTY
        Text("Screen context", color = Ink, fontWeight = FontWeight.SemiBold)
        Text("Package: ${screen.packageName ?: "unavailable"}", color = MutedInk)
        Text("${screen.appCategory} · ${screen.screenType} · ${screen.confidence}", color = MutedInk)
        Text("Keyboard: ${screen.isKeyboardVisible} · Focused: ${screen.hasFocusedInput} · Scrollable: ${screen.isScrollable}", color = MutedInk)
        Text("Fullscreen: ${screen.isFullScreen} · Sensitive: ${screen.isSensitive}", color = MutedInk)
        Text("Avoid bounds: ${screen.importantBounds.size} · Actions: ${screen.availableActions.joinToString()}", color = MutedInk)
    } }
    item { PremiumCard {
        val wellbeing = debug?.wellbeingContext ?: com.ambientcompanion.domain.wellbeing.WellbeingContext.EMPTY
        Text("Wellbeing context", color = Ink, fontWeight = FontWeight.SemiBold)
        Text("Session: ${wellbeing.sessionState} · Foreground: ${wellbeing.currentSessionDurationMs / 60_000} min", color = MutedInk)
        Text("Active: ${wellbeing.activeSessionDurationMs / 60_000} min · Idle: ${wellbeing.idleDurationMs / 1_000}s", color = MutedInk)
        Text("Scroll: ${wellbeing.continuousScrollDurationMs / 60_000} min · ${wellbeing.scrollEventCount} events", color = MutedInk)
        Text("Opens today: ${wellbeing.appOpenCountToday} · Active today: ${wellbeing.appActiveMinutesToday} min", color = MutedInk)
        Text("Last reaction: ${debug?.lastWellbeingReaction ?: "none"}", color = MutedInk)
        Text(debug?.attentionExplanation ?: "Attention engine unavailable", color = AmberDeep)
    } }
    item { Text("Force any state without waiting for real conditions.", color = MutedInk) }
    items(CompanionState.entries) { state -> ActionCard(state.displayName(), "Force state") { preview(state) } }
}

@Composable private fun ScreenList(title: String, back: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    PremiumBackground { LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("←  Back", color = Aubergine, modifier = Modifier.clickable(onClick = back).padding(vertical = 8.dp)) }; item { Text(title, color = Ink, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp)) }; content()
    } }
}
@Composable private fun PremiumBackground(content: @Composable BoxScope.() -> Unit) = Box(
    Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Porcelain, Color(0xFFFFFDF9))),
    ),
) {
    Box(
        Modifier.size(280.dp).align(Alignment.TopEnd).background(
            Brush.radialGradient(
                colors = listOf(Color(0x22FFB86B), Color.Transparent),
                radius = 280f,
            ),
        ),
    )
    content()
}
@Composable private fun BrandMark() = Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(28.dp).background(Aubergine, CircleShape), contentAlignment = Alignment.Center) { Text("•ᴗ•", color = Amber, fontSize = 10.sp, fontWeight = FontWeight.Bold) }; Text("  AMBIENT", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp) }
@Composable private fun Mascot(state: CompanionState, size: Int, emoji: String? = null, artwork: CompanionArtwork? = null) {
    when {
        emoji != null -> Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) { Text(emoji, fontSize = (size * .58f).sp) }
        artwork != null -> Image(painterResource(artwork.drawableRes()), artwork.label, Modifier.size(size.dp), contentScale = ContentScale.Fit)
        else -> Image(painterResource(R.drawable.companion_mascot), "Ambient Companion", Modifier.size(size.dp), contentScale = ContentScale.Fit)
    }
}
@Composable private fun PremiumCard(content: @Composable ColumnScope.() -> Unit) = Surface(color = Color.White.copy(alpha = .9f), shape = RoundedCornerShape(28.dp), shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(22.dp), content = content) }
@Composable private fun SettingRow(title: String, subtitle: String, control: @Composable () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = Ink, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MutedInk, fontSize = 13.sp) }; control() }
@Composable private fun ToggleCard(title: String, subtitle: String, checked: Boolean, change: (Boolean) -> Unit) = PremiumCard { SettingRow(title, subtitle) { Switch(checked, change, colors = premiumSwitchColors()) } }
@Composable private fun AppearanceChoice(title: String, symbol: String, selected: Boolean, modifier: Modifier, select: () -> Unit) = Surface(
    modifier = modifier.height(116.dp).clickable(onClick = select),
    color = if (selected) Color(0xFFFFE8C4) else Color.White.copy(alpha = .88f),
    shape = RoundedCornerShape(24.dp),
    border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, AmberDeep) else null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(symbol, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
    }
}
@Composable private fun EmojiPicker(selected: String, select: (String) -> Unit) = PremiumCard {
    Text("Choose an emoji", color = Ink, fontWeight = FontWeight.SemiBold)
    Text("Swipe sideways to see more", color = MutedInk, fontSize = 12.sp)
    Spacer(Modifier.height(14.dp))
    androidx.compose.foundation.lazy.grid.LazyHorizontalGrid(
        rows = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().height(144.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(emojiChoices.size, key = { emojiChoices[it] }) { index ->
            val emoji = emojiChoices[index]
            Surface(
                modifier = Modifier.size(44.dp).clickable { select(emoji) },
                color = if (emoji == selected) Color(0xFFFFE8C4) else Color.Transparent,
                shape = CircleShape,
            ) { Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 25.sp) } }
        }
    }
}
@Composable private fun ArtworkPicker(selected: CompanionArtwork, select: (CompanionArtwork) -> Unit) = PremiumCard {
    Text("Choose artwork", color = Ink, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(14.dp))
    CompanionArtwork.entries.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { artwork ->
                Surface(
                    modifier = Modifier.weight(1f).height(118.dp).clickable { select(artwork) },
                    color = if (artwork == selected) Color(0xFFFFE8C4) else Porcelain,
                    shape = RoundedCornerShape(20.dp),
                    border = if (artwork == selected) androidx.compose.foundation.BorderStroke(2.dp, AmberDeep) else null,
                ) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Image(painterResource(artwork.thumbnailRes()), artwork.label, Modifier.size(72.dp), contentScale = ContentScale.Fit)
                        Text(artwork.label, color = Ink, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
    }
}

private fun appearanceLabel(settings: UserSettings): String = when (settings.companionAppearance) {
    CompanionAppearance.AMBIENT -> "Ambient mascot"
    CompanionAppearance.ARTWORK -> "Artwork · ${settings.selectedArtwork.label}"
    CompanionAppearance.EMOJI -> "Emoji · ${settings.selectedEmoji}"
}
@Composable private fun OpacityControl(value: Float, update: (Float) -> Unit) = PremiumCard {
    SettingRow("Inactive opacity", "Fades to ${(value * 100).roundToInt()}% after 2.5 seconds") { Text("${(value * 100).roundToInt()}%", color = Aubergine, fontWeight = FontWeight.Bold) }
    Slider(value = value, onValueChange = update, valueRange = .35f..1f, steps = 12, colors = SliderDefaults.colors(thumbColor = Aubergine, activeTrackColor = Aubergine, inactiveTrackColor = Color(0xFFE4DCE1)))
}
@Composable private fun SizeControl(value: Int, update: (Int) -> Unit) = PremiumCard {
    val label = when {
        value <= 60 -> "Very small"
        value <= 84 -> "Small"
        value <= 112 -> "Medium"
        value <= 140 -> "Large"
        else -> "Very large"
    }
    SettingRow("Floating icon size", "$label · ${value}dp") { Text("${value}dp", color = Aubergine, fontWeight = FontWeight.Bold) }
    Slider(
        value = value.toFloat(),
        onValueChange = { update(it.roundToInt()) },
        valueRange = AppPreferences.MIN_COMPANION_SIZE_DP.toFloat()..AppPreferences.MAX_COMPANION_SIZE_DP.toFloat(),
        steps = 23,
        colors = SliderDefaults.colors(thumbColor = Aubergine, activeTrackColor = Aubergine, inactiveTrackColor = Color(0xFFE4DCE1)),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Very small", color = MutedInk, fontSize = 11.sp)
        Text("Very large", color = MutedInk, fontSize = 11.sp)
    }
}
@Composable private fun QuickActionPicker(selected: List<QuickAction>, update: (List<QuickAction>) -> Unit) = PremiumCard {
    Text("Quick actions · ${selected.size}/4", color = Ink, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    QuickAction.entries.forEach { action ->
        val checked = action in selected
        SettingRow(action.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase), if (!checked && selected.size >= 4) "Remove one action first" else "") {
            Switch(checked, { enabled ->
                if (enabled && selected.size < 4) update(selected + action)
                else if (!enabled) update(selected - action)
            }, enabled = checked || selected.size < 4, colors = premiumSwitchColors())
        }
    }
}
@Composable private fun TimeRangeCard(title: String, start: Int, end: Int, update: (Int, Int) -> Unit) = PremiumCard {
    fun label(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)
    SettingRow(title, "${label(start)}–${label(end)}") { Text(label(start), color = Aubergine, fontWeight = FontWeight.Bold) }
    Slider(start.toFloat(), { update((it / 15).roundToInt() * 15, end) }, valueRange = 0f..1425f, steps = 94)
    Slider(end.toFloat(), { update(start, (it / 15).roundToInt() * 15) }, valueRange = 0f..1425f, steps = 94)
}
@Composable private fun WeekendPicker(selected: Set<DayOfWeek>, update: (Set<DayOfWeek>) -> Unit) = PremiumCard {
    Text("Weekend days", color = Ink, fontWeight = FontWeight.SemiBold)
    DayOfWeek.entries.forEach { day ->
        SettingRow(day.name.lowercase().replaceFirstChar(Char::uppercase), "") {
            Switch(day in selected, { enabled -> update(if (enabled) selected + day else selected - day) }, colors = premiumSwitchColors())
        }
    }
}
@Composable private fun ActionCard(title: String, subtitle: String, action: () -> Unit) = Surface(Modifier.fillMaxWidth().clickable(onClick = action), color = Color.White.copy(alpha = .88f), shape = RoundedCornerShape(24.dp)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = Ink, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MutedInk, fontSize = 13.sp) }; Text("›", color = Aubergine, fontSize = 24.sp) } }
@Composable private fun SectionLabel(text: String) = Text(text, color = AmberDeep, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(top = 12.dp))
@Composable private fun PrimaryButton(label: String, action: () -> Unit) = Button(onClick = action, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Aubergine)) { Text(label, fontWeight = FontWeight.SemiBold) }
@Composable private fun SmallAction(label: String, modifier: Modifier, action: () -> Unit) = Surface(modifier.clickable(onClick = action), color = Color.White.copy(alpha = .8f), shape = RoundedCornerShape(18.dp)) { Text(label, color = Ink, textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 16.dp)) }
@Composable private fun premiumSwitchColors() = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Aubergine, uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFD8CFD4), uncheckedBorderColor = Color.Transparent)

private fun contextLine(snapshot: ContextSnapshot?): String { if (snapshot == null) return "Finding your current moment…"; val temp = snapshot.context.temperatureCelsius?.roundToInt()?.let { " · $it°C" }.orEmpty(); return snapshot.context.weather.name.lowercase().replaceFirstChar(Char::uppercase) + temp }
private fun messageFor(state: CompanionState) = when (state) { CompanionState.DAY_HOT -> "Water break? 💧"; CompanionState.COLD, CompanionState.SNOW -> "Stay warm 🧣"; CompanionState.STORM -> "Stay cozy and safe ⚡"; CompanionState.FOG -> "Take it slow out there"; CompanionState.MORNING_RAIN, CompanionState.DAY_RAIN, CompanionState.EVENING_RAIN, CompanionState.NIGHT_RAIN -> "Umbrella time ☔"; CompanionState.NIGHT_CLEAR, CompanionState.NIGHT_CLOUDY, CompanionState.NIGHT_SLEEP -> "Rest well 🌙"; CompanionState.EVENING_CLEAR, CompanionState.EVENING_CLOUDY -> "Good evening ✨"; CompanionState.MORNING_CLEAR, CompanionState.MORNING_CLOUDY -> "Good morning ☀️"; else -> "Hope your day's going well!" }
private fun stateColors(state: CompanionState) = when (state) { CompanionState.NIGHT_CLEAR, CompanionState.NIGHT_CLOUDY, CompanionState.NIGHT_RAIN, CompanionState.NIGHT_SLEEP -> listOf(Color(0xFFD9D1FF), Color(0xFF8068B2)); CompanionState.MORNING_RAIN, CompanionState.DAY_RAIN, CompanionState.EVENING_RAIN, CompanionState.STORM -> listOf(Color(0xFFC9E7F2), Color(0xFF6E93A8)); CompanionState.COLD, CompanionState.SNOW, CompanionState.FOG -> listOf(Color(0xFFE9F5F6), Color(0xFF9FC7CA)); CompanionState.EVENING_CLEAR, CompanionState.EVENING_CLOUDY -> listOf(Color(0xFFFFD4BC), Color(0xFFC98187)); else -> listOf(Color(0xFFFFE8B7), Amber) }
private fun faceFor(state: CompanionState) = when (state) { CompanionState.NIGHT_SLEEP -> "− ᴗ −"; CompanionState.STORM -> "• ﹏ •"; CompanionState.DAY_HOT -> "• _ •"; else -> "• ᴗ •" }
private fun CompanionState.displayName() = name.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
private val emojiChoices = listOf(
    // Happy and cheerful
    "🤠", "😀", "😃", "😄", "😁", "😆",
    "😂", "🤣", "😊", "😇", "🙂", "🙃",
    "😉", "😌", "😍", "🥰", "😘", "😗",
    "😙", "😚", "🤗", "🤩", "🥳", "😎",

    // Playful and silly
    "😋", "😛", "😜", "🤪", "😝", "🤑",
    "🤭", "🫢", "🫣", "🤫", "🤔", "🫡",
    "🤐", "🤨", "😐", "😑", "😶", "🫥",
    "😏", "😒", "🙄", "😬", "🤥", "🤓",

    // Sleepy, emotional, and worried
    "😴", "🤤", "😪", "😮‍💨", "😔", "😞",
    "😟", "😕", "🙁", "☹️", "😣", "😖",
    "😫", "😩", "🥺", "😢", "😭", "😤",
    "😠", "😡", "🤬", "🤯", "😳", "🥹",

    // Surprised and unwell
    "😮", "😯", "😲", "😱", "😨", "😰",
    "😥", "😓", "🤢", "🤮", "🤧", "😷",
    "🤒", "🤕", "🥴", "😵", "😵‍💫", "🫨",
    "🥶", "🥵", "😶‍🌫️", "😈", "👿", "💀",

    // Characters and creatures
    "😅", "🥸", "🤡", "👻", "👽", "🤖",
    "💩", "😺", "😸", "😹", "😻", "😼",
    "🙈", "🙉", "🙊", "🐶", "🐱", "🐼",
)
private val Porcelain = Color(0xFFFFF8F2); private val Ink = Color(0xFF302832); private val MutedInk = Color(0xFF786F77); private val Aubergine = Color(0xFF68486D); private val Amber = Color(0xFFFFBF67); private val AmberDeep = Color(0xFFAA6D2A)
