package com.ambientcompanion

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.ambientcompanion.overlay.CompanionOverlayService

class MainActivity : ComponentActivity() {
    private var canDrawOverlay by mutableStateOf(false)
    private var companionEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AmbientHome(
                    hasOverlayPermission = canDrawOverlay,
                    companionEnabled = companionEnabled,
                    onPermissionClick = ::openOverlaySettings,
                    onEnabledChange = ::updateCompanionEnabled,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        canDrawOverlay = Settings.canDrawOverlays(this)
        companionEnabled = CompanionOverlayService.isRunning
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri(),
            ),
        )
    }

    private fun updateCompanionEnabled(enabled: Boolean) {
        if (enabled && !Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }

        val serviceIntent = Intent(this, CompanionOverlayService::class.java)
        if (enabled) {
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            stopService(serviceIntent)
        }
        companionEnabled = enabled
    }
}

@Composable
private fun AmbientHome(
    hasOverlayPermission: Boolean,
    companionEnabled: Boolean,
    onPermissionClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    Surface(color = Color(0xFFFFF8F2), modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Ambient Companion", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("A little life on your screen.", color = Color(0xFF6F6570))
            Spacer(Modifier.height(36.dp))
            MascotPreview()
            Spacer(Modifier.height(24.dp))
            Text("Hey! I'm ready to float 👋", fontSize = 18.sp)
            Spacer(Modifier.height(36.dp))

            if (!hasOverlayPermission) {
                PermissionCard(onPermissionClick)
            } else {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Floating companion", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (companionEnabled) "Visible above your apps" else "Currently hidden",
                                color = Color(0xFF6F6570),
                                fontSize = 14.sp,
                            )
                        }
                        Switch(checked = companionEnabled, onCheckedChange = onEnabledChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun MascotPreview() {
    Box(
        modifier = Modifier
            .background(Color(0xFFFFC46B), CircleShape)
            .padding(horizontal = 34.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("• ᴗ •", color = Color(0xFF3A2E38), fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PermissionCard(onPermissionClick: () -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Allow the floating companion", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Android needs your permission before the companion can appear over the home screen and other apps.",
                color = Color(0xFF6F6570),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onPermissionClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF76517B)),
            ) {
                Text("Open permission settings")
            }
        }
    }
}
