package com.ambientcompanion.quicksettings

import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.ambientcompanion.AmbientApplication
import com.ambientcompanion.MainActivity
import com.ambientcompanion.overlay.CompanionOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CompanionTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (!Settings.canDrawOverlays(this)) {
            openAmbient()
            return
        }
        val enabled = !CompanionOverlayService.isRunning
        val service = Intent(this, CompanionOverlayService::class.java)
        if (enabled) ContextCompat.startForegroundService(this, service) else stopService(service)
        scope.launch {
            (application as AmbientApplication).preferences.updateSettings { it.copy(companionEnabled = enabled) }
            updateTile(enabled)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun updateTile(enabled: Boolean = CompanionOverlayService.isRunning) {
        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Ambient Companion"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = if (enabled) "Visible" else "Hidden"
            updateTile()
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openAmbient() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        unlockAndRun {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
            } else {
                startActivityAndCollapse(intent)
            }
        }
    }
}
