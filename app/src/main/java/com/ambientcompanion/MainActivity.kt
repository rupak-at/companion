package com.ambientcompanion

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ambientcompanion.data.preferences.UserSettings
import com.ambientcompanion.domain.repository.ContextSnapshot
import com.ambientcompanion.overlay.CompanionOverlayService
import com.ambientcompanion.ui.AmbientApp
import com.ambientcompanion.ui.AppScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app get() = application as AmbientApplication
    private var settingsState = androidx.compose.runtime.mutableStateOf(UserSettings())
    private var snapshotState = androidx.compose.runtime.mutableStateOf<ContextSnapshot?>(null)
    private var screenState = androidx.compose.runtime.mutableStateOf(AppScreen.HOME)
    private var overlayPermissionState = androidx.compose.runtime.mutableStateOf(false)

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshContext(true)
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.preferences.settings.collect { settingsState.value = it }
            }
        }
        refreshContext()
        setContent {
            AmbientApp(
                settings = settingsState.value,
                snapshot = snapshotState.value,
                screen = screenState.value,
                hasOverlayPermission = overlayPermissionState.value,
                overlayRunning = CompanionOverlayService.isRunning,
                onNavigate = { screenState.value = it },
                onRequestLocation = { locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                onRequestOverlay = ::openOverlaySettings,
                onCompleteOnboarding = { updateSettings { it.copy(onboardingComplete = true) } },
                onToggleCompanion = ::toggleCompanion,
                onRefresh = { refreshContext(true) },
                onUpdateSettings = ::updateSettings,
                onResetPosition = { lifecycleScope.launch { app.preferences.resetPosition() } },
                onPreviewState = { state ->
                    startService(Intent(this, CompanionOverlayService::class.java).apply {
                        action = CompanionOverlayService.ACTION_PREVIEW
                        putExtra(CompanionOverlayService.EXTRA_STATE, state.name)
                    })
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        overlayPermissionState.value = Settings.canDrawOverlays(this)
    }

    private fun openOverlaySettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
    }

    private fun toggleCompanion(enabled: Boolean) {
        if (enabled && !Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val intent = Intent(this, CompanionOverlayService::class.java)
        if (enabled) ContextCompat.startForegroundService(this, intent) else stopService(intent)
        updateSettings { it.copy(companionEnabled = enabled) }
    }

    private fun refreshContext(force: Boolean = false) {
        lifecycleScope.launch {
            snapshotState.value = app.contextRepository.refresh(force)
            sendBroadcast(Intent(CompanionOverlayService.ACTION_CONTEXT_UPDATED).setPackage(packageName))
        }
    }

    private fun updateSettings(transform: (UserSettings) -> UserSettings) {
        lifecycleScope.launch { app.preferences.updateSettings(transform) }
    }
}
