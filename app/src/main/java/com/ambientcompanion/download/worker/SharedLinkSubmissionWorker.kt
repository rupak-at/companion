package com.ambientcompanion.download.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.ambientcompanion.download.network.DownloadSubmissionClient
import com.ambientcompanion.download.network.SubmissionException
import java.io.IOException

class SharedLinkSubmissionWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val sourceUrl = inputData.getString(SOURCE_URL) ?: return Result.failure(errorData("Missing shared URL."))
        return try {
            val jobId = DownloadSubmissionClient(applicationContext).saveForLater(sourceUrl)
            Result.success(Data.Builder().putString(JOB_ID, jobId).build())
        } catch (error: IOException) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure(errorData(error.message))
        } catch (error: SubmissionException) {
            Result.failure(errorData(error.message))
        }
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
