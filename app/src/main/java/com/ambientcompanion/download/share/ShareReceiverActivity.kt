package com.ambientcompanion.download.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ambientcompanion.download.classifier.LinkClassifier
import com.ambientcompanion.download.classifier.LinkType
import com.ambientcompanion.download.worker.SharedLinkSubmissionWorker

class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enqueueSharedLink(intent)
        finish()
    }

    private fun enqueueSharedLink(intent: Intent) {
        val sharedText = intent.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
        val link = SharedUrlExtractor.extract(sharedText)?.let(LinkClassifier::classify)
        if (link == null || link.type == LinkType.UNKNOWN) {
            Toast.makeText(this, "Ambient couldn't find a supported link.", Toast.LENGTH_LONG).show()
            return
        }

        val request = OneTimeWorkRequestBuilder<SharedLinkSubmissionWorker>()
            .setInputData(Data.Builder().putString(SharedLinkSubmissionWorker.SOURCE_URL, link.normalizedUrl).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(applicationContext).enqueue(request)
        Toast.makeText(this, "${link.providerName} link queued for saving.", Toast.LENGTH_SHORT).show()
    }
}
