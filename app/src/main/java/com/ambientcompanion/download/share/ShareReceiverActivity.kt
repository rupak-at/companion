package com.ambientcompanion.download.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
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
import com.ambientcompanion.download.network.DownloadSubmissionClient
import kotlinx.coroutines.launch

class ShareReceiverActivity : ComponentActivity() {
    private var link by mutableStateOf<ClassifiedLink?>(null)
    private var submissionState by mutableStateOf(SubmissionState.READY)
    private var submissionMessage by mutableStateOf<String?>(null)
    private val submissionClient by lazy { DownloadSubmissionClient(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readIntent(intent)
        setContent {
            DownloadConfirmation(
                link = link,
                state = submissionState,
                message = submissionMessage,
                onCancel = ::finish,
                onSave = ::saveForLater,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent) {
        val sharedText = intent.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)
        link = SharedUrlExtractor.extract(sharedText)?.let(LinkClassifier::classify)
        submissionState = SubmissionState.READY
        submissionMessage = null
    }

    private fun saveForLater() {
        val selectedLink = link ?: return
        submissionState = SubmissionState.SAVING
        submissionMessage = null
        lifecycleScope.launch {
            runCatching { submissionClient.saveForLater(selectedLink.normalizedUrl) }
                .onSuccess { jobId ->
                    submissionState = SubmissionState.SAVED
                    submissionMessage = "Saved for the local runner. Job ${jobId.take(8)}"
                }
                .onFailure { error ->
                    submissionState = SubmissionState.ERROR
                    submissionMessage = error.message ?: "The link could not be saved."
                }
        }
    }
}

private enum class SubmissionState { READY, SAVING, SAVED, ERROR }

@androidx.compose.runtime.Composable
private fun DownloadConfirmation(
    link: ClassifiedLink?,
    state: SubmissionState,
    message: String?,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
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
                            state == SubmissionState.SAVED -> "Saved for later"
                            supported -> "${link.providerName} link detected"
                            else -> "That link isn't supported yet."
                        },
                        color = ink,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    if (message != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(message, color = ink.copy(alpha = .7f), fontSize = 15.sp, textAlign = TextAlign.Center)
                    } else if (supported) {
                        Spacer(Modifier.height(8.dp))
                        Text("Save it now and download it later with the local runner?", color = ink.copy(alpha = .7f), fontSize = 15.sp, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(26.dp))
                    Button(
                        onClick = if (state == SubmissionState.SAVED) onCancel else onSave,
                        enabled = supported && state != SubmissionState.SAVING,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = aubergine),
                    ) {
                        Text(when (state) {
                            SubmissionState.READY -> "Save for later"
                            SubmissionState.SAVING -> "Saving…"
                            SubmissionState.SAVED -> "Done"
                            SubmissionState.ERROR -> "Try again"
                        })
                    }
                    if (state != SubmissionState.SAVED) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) {
                            Text("Cancel", color = ink)
                        }
                    }
                }
            }
        }
    }
}
