package com.ambientcompanion.reliability

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ambientcompanion.AmbientApplication
import com.ambientcompanion.overlay.CompanionOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val settings = (context.applicationContext as AmbientApplication).preferences.currentSettings()
            if (settings.startAfterReboot && settings.companionEnabled) CompanionOverlayService.requestSync()
            pending.finish()
        }
    }
}
