package com.opendroid.app.core.knowledge

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceStateSnapshot(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val mediaVolumePercent: Int,
    val networkType: String,
    val localDateTime: String,
    val deviceModel: String,
    val androidVersion: String
)

/**
 * Knowledge Engine for Srishti 3.0
 * Collects dynamic device telemetry, system state, temporal context, and ambient awareness.
 */
class KnowledgeEngine(private val context: Context) {

    fun captureDeviceSnapshot(): DeviceStateSnapshot {
        // Battery
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100) / scale else 50
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        // Volume
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1
        val volumePercent = ((currentVol.toFloat() / maxVol.coerceAtLeast(1)) * 100).toInt()

        // Network
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNetwork)
        val networkType = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            else -> "Connected"
        }

        // Time
        val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.getDefault())
        val localDateTime = sdf.format(Date())

        return DeviceStateSnapshot(
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            mediaVolumePercent = volumePercent,
            networkType = networkType,
            localDateTime = localDateTime,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        )
    }

    fun formatContextString(): String {
        val snap = captureDeviceSnapshot()
        return buildString {
            append("• Time: ${snap.localDateTime}\n")
            append("• Device: ${snap.deviceModel} running ${snap.androidVersion}\n")
            append("• Battery: ${snap.batteryPercent}% (${if (snap.isCharging) "Charging" else "Discharging"})\n")
            append("• Media Volume: ${snap.mediaVolumePercent}%\n")
            append("• Network: ${snap.networkType}")
        }
    }
}
