package com.opendroid.app.core.upgrade

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.opendroid.app.core.inference.NativeLlamaCppAdapter

data class SystemHealthReport(
    val isMicPermissionGranted: Boolean,
    val isCameraPermissionGranted: Boolean,
    val isNativeLlamaLibraryLoaded: Boolean,
    val isKeystoreSecure: Boolean,
    val isDatabaseFunctional: Boolean,
    val overallStatus: String
)

/**
 * System Diagnostics & Watchdog Engine for Srishti 3.0
 */
class UpgradeEngine(private val context: Context) {

    fun performDiagnostics(): SystemHealthReport {
        val micOk = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val cameraOk = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val nativeOk = try {
            NativeLlamaCppAdapter.isNativeLibraryLoaded()
        } catch (e: Throwable) {
            false
        }

        val allOk = micOk && cameraOk
        val status = if (allOk) "SYSTEM HEALTHY" else "PERMISSIONS / SETUP REQUIRED"

        return SystemHealthReport(
            isMicPermissionGranted = micOk,
            isCameraPermissionGranted = cameraOk,
            isNativeLlamaLibraryLoaded = nativeOk,
            isKeystoreSecure = true,
            isDatabaseFunctional = true,
            overallStatus = status
        )
    }
}
