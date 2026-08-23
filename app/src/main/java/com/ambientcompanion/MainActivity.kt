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

class MainActivity : ComponentActivity() {
    private val app get() = application as AmbientApplication
    private var settingsState = androidx.compose.runtime.mutableStateOf(UserSettings())
    private var settingsLoadedState = androidx.compose.runtime.mutableStateOf(false)
    private var snapshotState = androidx.compose.runtime.mutableStateOf<ContextSnapshot?>(null)
    private var screenState = androidx.compose.runtime.mutableStateOf(AppScreen.HOME)
    private var accessibilityEnabledState = androidx.compose.runtime.mutableStateOf(false)

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshContext(true)
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.preferences.settings.collect { settings ->
                    settingsState.value = settings
                    settingsLoadedState.value = true
                    sendBroadcast(Intent(CompanionOverlayService.ACTION_SETTINGS_UPDATED).setPackage(packageName))
                }
            }
        }
        refreshContext()
        setContent {
            AmbientApp(
                settings = settingsState.value,
                settingsLoaded = settingsLoadedState.value,
                snapshot = snapshotState.value,
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
                onPreviewState = { state ->
                    sendBroadcast(Intent(CompanionOverlayService.ACTION_PREVIEW).setPackage(packageName).apply {
                        action = CompanionOverlayService.ACTION_PREVIEW
                        putExtra(CompanionOverlayService.EXTRA_STATE, state.name)
                    })
                },
            )
        }
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
        lifecycleScope.launch {
            app.preferences.updateSettings(transform)
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
