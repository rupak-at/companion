package com.ambientcompanion.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ambientcompanion.AmbientApplication

class ContextRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        (applicationContext as AmbientApplication).contextRepository.refresh()
        Result.success()
    }.getOrElse { Result.retry() }

    companion object { const val WORK_NAME = "ambient_context_refresh" }
}
