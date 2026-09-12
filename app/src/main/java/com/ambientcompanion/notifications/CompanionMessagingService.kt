package com.ambientcompanion.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ambientcompanion.R
import com.ambientcompanion.download.network.DownloadSubmissionClient
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CompanionMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { runCatching { DownloadSubmissionClient(applicationContext).registerDevice(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        showNotification(
            data["title"] ?: message.notification?.title ?: "Verification required",
            data["body"] ?: message.notification?.body ?: "Complete verification in your local browser.",
        )
    }

    private fun showNotification(title: String, body: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Download actions", NotificationManager.IMPORTANCE_HIGH))
        manager.notify(System.currentTimeMillis().toInt(), NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ambient)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build())
    }

    companion object {
        private const val CHANNEL_ID = "download_actions"
    }
}
