package com.opendroid.app.core.tools

import android.content.Context
import android.media.AudioManager
import com.opendroid.app.core.domain.RiskLevel
import com.opendroid.app.core.domain.ToolDefinition
import com.opendroid.app.core.domain.ToolExecutionResult
import com.opendroid.app.core.domain.ToolParameter
import com.opendroid.app.core.logging.RedactedLogger
import kotlin.math.roundToInt

/**
 * Real Android AudioManager media volume tool with hearing safety bounds
 */
class MediaVolumeTool : AndroidTool {
    override val id: String = "tool_volume_v1"
    override val name: String = "set_media_volume"
    override val description: String = "Set device media playback volume percent (0-100)."
    override val riskTier: RiskLevel = RiskLevel.SAFE
    override val timeoutMs: Long = 3000L

    companion object {
        private const val TAG = "MediaVolumeTool"
    }

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            id = id,
            name = name,
            description = description,
            riskTier = riskTier,
            timeoutMs = timeoutMs,
            parameters = listOf(
                ToolParameter(
                    name = "volumePercent",
                    type = "number",
                    description = "Target volume percentage from 0 to 100",
                    required = true
                ),
                ToolParameter(
                    name = "showUi",
                    type = "boolean",
                    description = "Whether to show the system volume slider overlay",
                    required = false
                )
            )
        )
    }

    override fun validate(arguments: Map<String, Any?>): Result<Unit> {
        if (!arguments.containsKey("volumePercent")) {
            return Result.failure(IllegalArgumentException("Missing required parameter: 'volumePercent'."))
        }
        val vol = (arguments["volumePercent"] as? Number)?.toDouble()
            ?: return Result.failure(IllegalArgumentException("Parameter 'volumePercent' must be a numeric value."))

        if (vol < 0.0 || vol > 100.0) {
            return Result.failure(IllegalArgumentException("Parameter 'volumePercent' must be between 0 and 100 (received: $vol)."))
        }
        return Result.success(Unit)
    }

    override suspend fun execute(context: Context, arguments: Map<String, Any?>): ToolExecutionResult {
        val volPercent = (arguments["volumePercent"] as Number).toDouble()
        val showUi = (arguments["showUi"] as? Boolean) ?: false

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolExecutionResult(success = false, error = "AudioManager is unavailable.")

        return try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val minVolume = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            } else 0

            val targetIndex = (minVolume + (volPercent / 100.0) * (maxVolume - minVolume)).roundToInt()
                .coerceIn(minVolume, maxVolume)

            val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, flags)

            RedactedLogger.i(TAG, "AudioManager.setStreamVolume set to index: $targetIndex / $maxVolume ($volPercent%)")

            val currentActual = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val verified = currentActual == targetIndex

            ToolExecutionResult(
                success = true,
                output = mapOf(
                    "requestedPercent" to volPercent,
                    "targetIndex" to targetIndex,
                    "maxIndex" to maxVolume,
                    "actualIndex" to currentActual
                ),
                verificationPassed = verified,
                verificationDetails = "Volume stream index updated to $currentActual (verified: $verified)."
            )
        } catch (e: Exception) {
            RedactedLogger.e(TAG, "Failed to set stream volume", e)
            ToolExecutionResult(
                success = false,
                error = "AudioException: ${e.message}",
                verificationPassed = false
            )
        }
    }

    override suspend fun verify(context: Context, arguments: Map<String, Any?>, result: ToolExecutionResult): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volPercent = (arguments["volumePercent"] as? Number)?.toDouble() ?: return false
        val expectedIndex = ((volPercent / 100.0) * maxVolume).roundToInt()
        val actual = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (actual - expectedIndex) in -1..1 // Allow 1-step rounding tolerance on coarse hardware steps
    }
}
