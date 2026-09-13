package com.ambientcompanion.download.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.ambientcompanion.download.network.DownloadSubmissionClient
import com.ambientcompanion.download.network.SubmissionException
import com.ambientcompanion.overlay.CompanionOverlayService
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SharedLinkSubmissionWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val sourceUrl = inputData.getString(SOURCE_URL) ?: return Result.failure(errorData("Missing shared URL."))
        return try {
            val submission = DownloadSubmissionClient(applicationContext).saveForLater(sourceUrl)
            runCatching {
                withContext(Dispatchers.IO) {
                    val token = Tasks.await(FirebaseMessaging.getInstance().token)
                    DownloadSubmissionClient(applicationContext).registerDevice(token)
                }
            }.onFailure { error -> Log.w("CompanionDownloads", "Could not register device for CAPTCHA alerts", error) }
            val message = if (submission.alreadySaved) {
                "This link is already in your download queue."
            } else {
                "Link queued for download."
            }
            reportStatus(message)
            Result.success(Data.Builder().putString(JOB_ID, submission.jobId).build())
        } catch (error: IOException) {
            if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                reportStatus("Couldn't save the link. Check your connection.")
                Result.failure(errorData(error.message))
            }
        } catch (error: SubmissionException) {
            reportStatus("Couldn't save the link: ${error.message}")
            Result.failure(errorData(error.message))
        } catch (error: Exception) {
            reportStatus("Couldn't save the shared link.")
            Result.failure(errorData(error.message))
        }
    }

    private fun reportStatus(message: String) {
        applicationContext.sendBroadcast(
            Intent(CompanionOverlayService.ACTION_EXTERNAL_MESSAGE)
                .setPackage(applicationContext.packageName)
                .putExtra(CompanionOverlayService.EXTRA_MESSAGE, message),
        )
    }

    private fun errorData(message: String?): Data = Data.Builder()
        .putString(ERROR_MESSAGE, message ?: "The shared link could not be saved.")
        .build()

    companion object {
        const val SOURCE_URL = "source_url"
        const val JOB_ID = "job_id"
        const val ERROR_MESSAGE = "error_message"
        private const val MAX_ATTEMPTS = 5
    }
}
