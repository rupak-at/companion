package com.ambientcompanion

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = Aubergine,
                    onPrimary = Color.White,
                    surface = Porcelain,
                    onSurface = Ink,
                ),
            ) {
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Porcelain, Color(0xFFFFFDF9)))),
    ) {
        Canvas(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.TopEnd)
                .blur(48.dp),
        ) {
            drawCircle(Color(0x22FFB86B), radius = size.minDimension * 0.46f)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark()
            Spacer(Modifier.height(42.dp))
            Text(
                "A little life,\nright by your side.",
                color = Ink,
                fontSize = 38.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                letterSpacing = (-1).sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Quiet company that changes with your day.",
                color = MutedInk,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            MascotPreview()
            Spacer(Modifier.height(18.dp))
            Surface(color = Color.White.copy(alpha = 0.72f), shape = RoundedCornerShape(50)) {
                Text(
                    "Hey! I'm ready to float  👋",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(32.dp))

            if (!hasOverlayPermission) {
                PermissionCard(onPermissionClick)
            } else {
                Surface(
                    color = Color.White.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(28.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Floating companion", color = Ink, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (companionEnabled) "Visible above your apps" else "Currently hidden",
                                color = MutedInk,
                                fontSize = 14.sp,
                            )
                        }
                        Switch(
                            checked = companionEnabled,
                            onCheckedChange = onEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Aubergine,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFD8CFD4),
                                uncheckedBorderColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Private by design · Light on battery", color = MutedInk, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(Aubergine, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("•ᴗ•", color = Amber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "  AMBIENT",
            color = Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
    }
}

@Composable
private fun MascotPreview() {
    Box(
        modifier = Modifier
            .size(148.dp)
            .shadow(28.dp, CircleShape, ambientColor = Amber.copy(alpha = 0.35f))
            .background(
                Brush.radialGradient(listOf(Color(0xFFFFE8B7), Amber)),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 12.dp)
                .background(Color.White.copy(alpha = 0.22f), CircleShape),
        )
        Text("• ᴗ •", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PermissionCard(onPermissionClick: () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.9f),
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(22.dp)) {
            Text("One small permission", color = AmberDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Let your companion float", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Android needs your approval before Ambient can appear over your home screen and other apps.",
                color = MutedInk,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onPermissionClick,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Aubergine),
            ) {
                Text("Continue", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private val Porcelain = Color(0xFFFFF8F2)
private val Ink = Color(0xFF302832)
private val MutedInk = Color(0xFF786F77)
private val Aubergine = Color(0xFF68486D)
private val Amber = Color(0xFFFFBF67)
private val AmberDeep = Color(0xFFAA6D2A)
