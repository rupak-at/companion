package com.ambientcompanion.screenshot

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat

class ScreenshotPermissionActivity : Activity() {
    private val projectionManager by lazy { getSystemService(MediaProjectionManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_REQUEST)
        } else {
            requestCapture()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            requestCapture()
        } else {
            finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun requestCapture() {
        startActivityForResult(projectionManager.createScreenCaptureIntent(), CAPTURE_REQUEST)
    }

    @Deprecated("Activity result callback required by the platform capture API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAPTURE_REQUEST && resultCode == RESULT_OK && data != null) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, ScreenshotCaptureService::class.java)
                    .putExtra(ScreenshotCaptureService.EXTRA_RESULT_CODE, resultCode)
                    .putExtra(ScreenshotCaptureService.EXTRA_RESULT_DATA, data),
            )
        }
        finish()
    }

    companion object {
        private const val CAPTURE_REQUEST = 4201
        private const val STORAGE_REQUEST = 4202
    }
}
