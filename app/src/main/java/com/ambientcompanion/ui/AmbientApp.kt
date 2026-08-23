package com.ambientcompanion.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ambientcompanion.data.preferences.CompanionAppearance
import com.ambientcompanion.data.preferences.AppPreferences
import com.ambientcompanion.data.preferences.UserSettings
import com.ambientcompanion.domain.model.CompanionState
import com.ambientcompanion.domain.repository.ContextSnapshot
import kotlin.math.roundToInt

enum class AppScreen { HOME, CUSTOMIZE, SETTINGS, PREVIEW, DEBUG }

@Composable
fun AmbientApp(
    settings: UserSettings,
    settingsLoaded: Boolean,
    snapshot: ContextSnapshot?,
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
        } else when (screen) {
            AppScreen.HOME -> Home(settings, snapshot, overlayRunning, hasAccessibilityPermission, onToggleCompanion, onOpenAccessibilitySettings, onRefresh, onNavigate)
            AppScreen.CUSTOMIZE -> Customize(settings, onUpdateSettings, onNavigate)
            AppScreen.SETTINGS -> Settings(settings, onToggleCompanion, onUpdateSettings, onResetPosition, onAddQuickTile, onOpenAccessibilitySettings, onRefresh, onNavigate)
            AppScreen.PREVIEW -> Preview(onPreviewState, onNavigate)
            AppScreen.DEBUG -> Debug(onPreviewState, onNavigate)
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
        "In Accessibility, open Ambient Companion controls and turn on its main switch. Leave Shortcut off unless you want Android's optional quick toggle.",
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
    running: Boolean,
    accessibilityEnabled: Boolean,
    toggle: (Boolean) -> Unit,
    openAccessibilitySettings: () -> Unit,
    refresh: () -> Unit,
    navigate: (AppScreen) -> Unit,
) {
    val state = snapshot?.state ?: CompanionState.DAY_CLEAR
    PremiumBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(); Spacer(Modifier.height(34.dp)); Mascot(state, 154, settings.selectedEmoji.takeIf { settings.companionAppearance == CompanionAppearance.EMOJI }); Spacer(Modifier.height(18.dp))
            Text(messageFor(state), color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp)); Text(contextLine(snapshot), color = MutedInk, fontSize = 14.sp); Spacer(Modifier.height(30.dp))
            PremiumCard { SettingRow("Floating companion", if (running) "Here with you" else "Currently resting") { Switch(running, toggle, colors = premiumSwitchColors()) } }
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
                    Text(if (settings.companionAppearance == CompanionAppearance.EMOJI) settings.selectedEmoji else "✦", fontSize = 30.sp)
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
        item { SectionLabel("DISPLAY") }
        item { SizeControl(settings.companionSizeDp) { value -> update { it.copy(companionSizeDp = value) } } }
        item { OpacityControl(settings.idleOpacity) { value -> update { it.copy(idleOpacity = value) } } }
        item { ToggleCard("Snap to screen edge", "Turn off to leave it anywhere", settings.edgeSnapEnabled) { value -> update { it.copy(edgeSnapEnabled = value) } } }
    }
}

@Composable
private fun Settings(settings: UserSettings, toggleCompanion: (Boolean) -> Unit, update: ((UserSettings) -> UserSettings) -> Unit, reset: () -> Unit, addQuickTile: () -> Unit, openAccessibilitySettings: () -> Unit, refresh: () -> Unit, navigate: (AppScreen) -> Unit) {
    var taps by remember { mutableIntStateOf(0) }
    ScreenList("Settings", { navigate(AppScreen.HOME) }) {
        item { SectionLabel("EXPERIENCE") }
        item { ToggleCard("Companion", "Show above your apps", settings.companionEnabled, toggleCompanion) }
        item { ToggleCard("Messages", "Show dialogue when you tap", settings.messagesEnabled) { value -> update { it.copy(messagesEnabled = value) } } }
        item { ToggleCard("Automatic messages", "At most once each hour", settings.automaticMessages) { value -> update { it.copy(automaticMessages = value) } } }
        item { ToggleCard("Weather context", "Use local conditions when available", settings.weatherEnabled) { value -> update { it.copy(weatherEnabled = value) } } }
        item { ToggleCard("Reduced motion", "Prefer gentle fades", settings.reducedMotion) { value -> update { it.copy(reducedMotion = value) } } }
        item { ActionCard("Assistive controls", "Enable Back, Home, Recents and system actions") { openAccessibilitySettings() } }
        item { SectionLabel("COMPANION") }
        item { ActionCard("Appearance", if (settings.companionAppearance == CompanionAppearance.EMOJI) "Emoji · ${settings.selectedEmoji}" else "Ambient mascot") { update { it.copy(companionAppearance = if (it.companionAppearance == CompanionAppearance.AMBIENT) CompanionAppearance.EMOJI else CompanionAppearance.AMBIENT) } } }
        if (settings.companionAppearance == CompanionAppearance.EMOJI) {
            item { EmojiPicker(settings.selectedEmoji) { emoji -> update { it.copy(selectedEmoji = emoji) } } }
        }
        item { SizeControl(settings.companionSizeDp) { value -> update { it.copy(companionSizeDp = value) } } }
        item { OpacityControl(settings.idleOpacity) { value -> update { it.copy(idleOpacity = value) } } }
        item { ToggleCard("Snap to screen edge", "Turn off for free positioning", settings.edgeSnapEnabled) { value -> update { it.copy(edgeSnapEnabled = value) } } }
        item { ActionCard("Location", if (settings.manualLatitude == null) "Automatic · tap for Kathmandu" else "Kathmandu · tap for automatic") { update { if (it.manualLatitude == null) it.copy(manualLatitude = 27.7172, manualLongitude = 85.3240) else it.copy(manualLatitude = null, manualLongitude = null) }; refresh() } }
        item { ActionCard("Reset position", "Return to the right edge", reset) }
        item { ActionCard("Quick Settings button", "Add a system-area show/hide control", addQuickTile) }
        item { ActionCard("Refresh weather", "Update environmental context", refresh) }
        item { SectionLabel("ABOUT") }
        item { ActionCard("Ambient Companion", "Version 0.1.0") { taps++; if (taps >= 7) navigate(AppScreen.DEBUG) } }
    }
}

@Composable private fun Preview(preview: (CompanionState) -> Unit, navigate: (AppScreen) -> Unit) = ScreenList("State preview", { navigate(AppScreen.HOME) }) {
    items(CompanionState.entries) { state -> Surface(Modifier.fillMaxWidth().clickable { preview(state) }, color = Color.White.copy(alpha = .88f), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Mascot(state, 54); Column(Modifier.padding(start = 16.dp)) { Text(state.displayName(), color = Ink, fontWeight = FontWeight.SemiBold); Text("Tap to preview on the overlay", color = MutedInk, fontSize = 12.sp) } }
    } }
}

@Composable private fun Debug(preview: (CompanionState) -> Unit, navigate: (AppScreen) -> Unit) = ScreenList("Developer controls", { navigate(AppScreen.SETTINGS) }) {
    item { Text("Force any state without waiting for real time or weather.", color = MutedInk) }
    items(CompanionState.entries) { state -> ActionCard(state.displayName(), "Force state") { preview(state) } }
}

@Composable private fun ScreenList(title: String, back: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    PremiumBackground { LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("←  Back", color = Aubergine, modifier = Modifier.clickable(onClick = back).padding(vertical = 8.dp)) }; item { Text(title, color = Ink, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp)) }; content()
    } }
}
@Composable private fun PremiumBackground(content: @Composable BoxScope.() -> Unit) = Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Porcelain, Color(0xFFFFFDF9))))) { Canvas(Modifier.size(280.dp).align(Alignment.TopEnd).blur(48.dp)) { drawCircle(Color(0x22FFB86B), size.minDimension * .46f) }; content() }
@Composable private fun BrandMark() = Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(28.dp).background(Aubergine, CircleShape), contentAlignment = Alignment.Center) { Text("•ᴗ•", color = Amber, fontSize = 10.sp, fontWeight = FontWeight.Bold) }; Text("  AMBIENT", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp) }
@Composable private fun Mascot(state: CompanionState, size: Int, emoji: String? = null) { val colors = stateColors(state); if (emoji != null) { Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) { Text(emoji, fontSize = (size * .58f).sp) } } else { Box(Modifier.size(size.dp).shadow(22.dp, CircleShape, ambientColor = colors.last().copy(alpha = .3f)).background(Brush.radialGradient(colors), CircleShape), contentAlignment = Alignment.Center) { Text(faceFor(state), color = Ink, fontSize = (size * .17f).sp, fontWeight = FontWeight.Bold) } } }
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
    Spacer(Modifier.height(14.dp))
    emojiChoices.chunked(6).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            row.forEach { emoji ->
                Surface(
                    modifier = Modifier.size(44.dp).clickable { select(emoji) },
                    color = if (emoji == selected) Color(0xFFFFE8C4) else Color.Transparent,
                    shape = CircleShape,
                ) { Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 25.sp) } }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
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
