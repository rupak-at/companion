package com.ambientcompanion.download.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ambientcompanion.R
import com.ambientcompanion.download.network.DownloadSubmissionClient
import com.ambientcompanion.download.network.SubmissionException
import com.ambientcompanion.overlay.CompanionOverlayService
import java.io.IOException

class SharedLinkSubmissionWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val sourceUrl = inputData.getString(SOURCE_URL) ?: return Result.failure(errorData("Missing shared URL."))
        return try {
            val jobId = DownloadSubmissionClient(applicationContext).saveForLater(sourceUrl)
            reportStatus("Link saved for later ✓", success = true)
            Result.success(Data.Builder().putString(JOB_ID, jobId).build())
        } catch (error: IOException) {
            if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                reportStatus("Couldn't save the link. Check your connection.", success = false)
                Result.failure(errorData(error.message))
            }
        } catch (error: SubmissionException) {
            reportStatus("Couldn't save the link: ${error.message}", success = false)
            Result.failure(errorData(error.message))
        } catch (error: Exception) {
            reportStatus("Couldn't save the shared link.", success = false)
            Result.failure(errorData(error.message))
        }
    }

    private fun reportStatus(message: String, success: Boolean) {
        applicationContext.sendBroadcast(
            Intent(CompanionOverlayService.ACTION_EXTERNAL_MESSAGE)
                .setPackage(applicationContext.packageName)
                .putExtra(CompanionOverlayService.EXTRA_MESSAGE, message),
        )
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Saved links", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ambient)
            .setContentTitle(if (success) "Saved with Ambient" else "Ambient couldn't save the link")
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun errorData(message: String?): Data = Data.Builder()
        .putString(ERROR_MESSAGE, message ?: "The shared link could not be saved.")
        .build()

    companion object {
        const val SOURCE_URL = "source_url"
        const val JOB_ID = "job_id"
        const val ERROR_MESSAGE = "error_message"
        private const val MAX_ATTEMPTS = 5
        private const val CHANNEL_ID = "saved_links"
    }
}
