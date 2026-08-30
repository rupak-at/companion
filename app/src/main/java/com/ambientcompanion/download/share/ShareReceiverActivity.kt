package com.ambientcompanion.download.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ambientcompanion.download.classifier.ClassifiedLink
import com.ambientcompanion.download.classifier.LinkClassifier
import com.ambientcompanion.download.classifier.LinkType

class ShareReceiverActivity : ComponentActivity() {
    private var link by mutableStateOf<ClassifiedLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readIntent(intent)
        setContent { DownloadConfirmation(link = link, onCancel = ::finish, onDownload = { showBackendPending() }) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent) {
        val sharedText = intent.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)
        link = SharedUrlExtractor.extract(sharedText)?.let(LinkClassifier::classify)
    }

    private fun showBackendPending() {
        // Submission is intentionally disabled until the authenticated backend URL is configured.
        link = null
    }
}
@androidx.compose.runtime.Composable
private fun DownloadConfirmation(link: ClassifiedLink?, onCancel: () -> Unit, onDownload: () -> Unit) {
    val ink = Color(0xFF2F2630)
    val aubergine = Color(0xFF5C3B58)
    val supported = link != null && link.type != LinkType.UNKNOWN
    MaterialTheme {
        Column(
            Modifier.fillMaxSize().background(Color(0xFFF8F3EC)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(shape = RoundedCornerShape(28.dp), color = Color.White, shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🐦", fontSize = 64.sp)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        when {
                            link == null -> "I couldn't find a secure link."
                            supported -> "${link.providerName} link detected"
                            else -> "That link isn't supported yet."
                        },
                        color = ink,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    if (supported) {
                        Spacer(Modifier.height(8.dp))
                        Text("Want me to save this?", color = ink.copy(alpha = .7f), fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(26.dp))
                    Button(
                        onClick = onDownload,
                        enabled = supported,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = aubergine),
                    ) { Text("Download") }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) {
                        Text("Cancel", color = ink)
                    }
                }
            }
        }
    }
}
