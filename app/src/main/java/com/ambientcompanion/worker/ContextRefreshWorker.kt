package com.ambientcompanion.worker

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ambientcompanion.AmbientApplication

class ContextRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        (applicationContext as AmbientApplication).contextRepository.refresh()
        applicationContext.sendBroadcast(
            Intent(com.ambientcompanion.overlay.CompanionOverlayService.ACTION_CONTEXT_UPDATED)
                .setPackage(applicationContext.packageName),
        )
        Result.success()
    }.getOrElse { Result.retry() }

    companion object { const val WORK_NAME = "ambient_context_refresh" }
}
