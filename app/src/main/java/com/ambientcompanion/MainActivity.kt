package com.ambientcompanion

import android.Manifest
import android.content.Intent
import android.content.ComponentName
import android.app.StatusBarManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ambientcompanion.data.preferences.UserSettings
import com.ambientcompanion.domain.repository.ContextSnapshot
import com.ambientcompanion.overlay.CompanionOverlayService
import com.ambientcompanion.quicksettings.CompanionTileService
import com.ambientcompanion.ui.AmbientApp
import com.ambientcompanion.ui.AppScreen
import kotlinx.coroutines.launch
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.ambientcompanion.domain.screen.ScreenContext
import com.ambientcompanion.domain.screen.AppProfile
import com.ambientcompanion.ui.InstalledAppEntry
import com.ambientcompanion.data.profile.AppCategoryResolver

class MainActivity : ComponentActivity() {
    private val app get() = application as AmbientApplication
    private var settingsState = androidx.compose.runtime.mutableStateOf(UserSettings())
    private var settingsLoadedState = androidx.compose.runtime.mutableStateOf(false)
    private var snapshotState = androidx.compose.runtime.mutableStateOf<ContextSnapshot?>(null)
    private var screenState = androidx.compose.runtime.mutableStateOf(AppScreen.HOME)
    private var accessibilityEnabledState = androidx.compose.runtime.mutableStateOf(false)
    private var screenContextState = androidx.compose.runtime.mutableStateOf(ScreenContext.EMPTY)
    private var pendingBluetoothSettings: UserSettings? = null

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshContext(true)
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingBluetoothSettings?.let { pending -> persistSettings(pending.copy(headphoneReactions = granted)) }
        pendingBluetoothSettings = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    app.preferences.settings.collect { settings ->
                        settingsState.value = settings
                        settingsLoadedState.value = true
                        sendBroadcast(Intent(CompanionOverlayService.ACTION_SETTINGS_UPDATED).setPackage(packageName))
                    }
                }
                launch { app.screenContextSource.state.collect { screenContextState.value = it } }
            }
        }
        refreshContext()
        val launchableApps = installedApps()
        setContent {
            AmbientApp(
                settings = settingsState.value,
                settingsLoaded = settingsLoadedState.value,
                snapshot = snapshotState.value,
                screenContext = screenContextState.value,
                installedApps = launchableApps,
                screen = screenState.value,
                hasAccessibilityPermission = accessibilityEnabledState.value,
                overlayRunning = CompanionOverlayService.isRunning,
                onNavigate = { screenState.value = it },
                onRequestLocation = { locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                onCompleteOnboarding = ::completeOnboarding,
                onToggleCompanion = ::toggleCompanion,
                onRefresh = { refreshContext(true) },
                onUpdateSettings = ::updateSettings,
                onResetPosition = {
                    lifecycleScope.launch { app.preferences.resetPosition() }
                    sendBroadcast(Intent(CompanionOverlayService.ACTION_RESET_POSITION).setPackage(packageName))
                },
                onAddQuickTile = ::requestQuickSettingsTile,
                onOpenAccessibilitySettings = ::openAccessibilityServiceSettings,
                onSaveAppProfile = { profile ->
                    app.appProfileRepository.save(profile)
                    sendBroadcast(Intent(CompanionOverlayService.ACTION_SETTINGS_UPDATED).setPackage(packageName))
                },
                onClearActivityData = {
                    app.wellbeingRepository.clear()
                    sendBroadcast(Intent(CompanionOverlayService.ACTION_CLEAR_ACTIVITY_DATA).setPackage(packageName))
                },
                onPreviewState = { state ->
                    sendBroadcast(Intent(CompanionOverlayService.ACTION_PREVIEW).setPackage(packageName).apply {
                        action = CompanionOverlayService.ACTION_PREVIEW
                        putExtra(CompanionOverlayService.EXTRA_STATE, state.name)
                    })
                },
            )
        }
    }

    private fun installedApps(): List<InstalledAppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val categoryResolver = AppCategoryResolver()
        return packageManager.queryIntentActivities(intent, 0).map { info ->
            val appPackage = info.activityInfo.packageName
            val category = categoryResolver.resolve(appPackage)
            InstalledAppEntry(
                packageName = appPackage,
                label = info.loadLabel(packageManager).toString(),
                profile = app.appProfileRepository.profileFor(appPackage, category),
            )
        }.distinctBy(InstalledAppEntry::packageName).sortedBy { it.label.lowercase() }
    }

    override fun onResume() {
        super.onResume()
        accessibilityEnabledState.value = isAssistiveServiceEnabled()
    }

    private fun isAssistiveServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val expected = ComponentName(this, CompanionOverlayService::class.java)
        return enabledServices.split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == expected }
    }

    private fun openAccessibilityServiceSettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun toggleCompanion(enabled: Boolean) {
        if (enabled && !isAssistiveServiceEnabled()) {
            openAccessibilityServiceSettings()
            return
        }
        updateSettings { it.copy(companionEnabled = enabled) }
    }

    private fun completeOnboarding() {
        lifecycleScope.launch {
            app.preferences.updateSettings { it.copy(onboardingComplete = true, companionEnabled = true) }
            sendBroadcast(Intent(CompanionOverlayService.ACTION_SETTINGS_UPDATED).setPackage(packageName))
        }
    }

    private fun refreshContext(force: Boolean = false) {
        lifecycleScope.launch {
            snapshotState.value = app.contextRepository.refresh(force)
            sendBroadcast(Intent(CompanionOverlayService.ACTION_CONTEXT_UPDATED).setPackage(packageName))
        }
    }

    private fun updateSettings(transform: (UserSettings) -> UserSettings) {
        val current = settingsState.value
        val next = transform(current)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !current.headphoneReactions && next.headphoneReactions &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingBluetoothSettings = next
            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }
        persistSettings(next)
    }

    private fun persistSettings(next: UserSettings) {
        lifecycleScope.launch {
            app.preferences.updateSettings { next }
            sendBroadcast(Intent(CompanionOverlayService.ACTION_SETTINGS_UPDATED).setPackage(packageName))
        }
    }

    private fun requestQuickSettingsTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, CompanionTileService::class.java),
                "Ambient Companion",
                android.graphics.drawable.Icon.createWithResource(this, com.ambientcompanion.R.drawable.ic_stat_ambient),
                mainExecutor,
            ) {}
        } else {
            startActivity(Intent("android.settings.QUICK_SETTINGS_SETTINGS"))
        }
    }

}
