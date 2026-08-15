package com.opendroid.app.core.tools

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.opendroid.app.core.domain.RiskLevel
import com.opendroid.app.core.domain.ToolDefinition
import com.opendroid.app.core.domain.ToolExecutionResult
import com.opendroid.app.core.domain.ToolParameter
import com.opendroid.app.core.logging.RedactedLogger

/**
 * Real Android CameraManager.setTorchMode implementation with hardware state tracking
 */
class FlashlightTool : AndroidTool {
    override val id: String = "tool_flashlight_v1"
    override val name: String = "set_flashlight"
    override val description: String = "Turn device camera flashlight (torch) ON or OFF."
    override val riskTier: RiskLevel = RiskLevel.SAFE
    override val timeoutMs: Long = 3000L

    companion object {
        private const val TAG = "FlashlightTool"
        // State tracker updated by TorchCallback
        @Volatile
        var currentTorchState: Boolean = false
            private set
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
                    name = "enabled",
                    type = "boolean",
                    description = "True to turn flashlight ON, False to turn OFF",
                    required = true
                )
            )
        )
    }

    override fun validate(arguments: Map<String, Any?>): Result<Unit> {
        if (!arguments.containsKey("enabled")) {
            return Result.failure(IllegalArgumentException("Missing required parameter: 'enabled' (boolean)."))
        }
        val enabled = arguments["enabled"]
        if (enabled !is Boolean) {
            return Result.failure(IllegalArgumentException("Parameter 'enabled' must be a boolean, received: ${enabled?.javaClass?.simpleName}"))
        }
        return Result.success(Unit)
    }

    override suspend fun execute(context: Context, arguments: Map<String, Any?>): ToolExecutionResult {
        val enabled = arguments["enabled"] as Boolean
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return ToolExecutionResult(success = false, error = "CameraManager unavailable on this device.")

        return try {
            val cameraIdList = cameraManager.cameraIdList
            var rearCameraWithFlash: String? = null

            for (camId in cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(camId)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    rearCameraWithFlash = camId
                    break
                }
            }

            val targetCamId = rearCameraWithFlash ?: cameraIdList.firstOrNull()
                ?: return ToolExecutionResult(success = false, error = "No camera with flash hardware detected.")

            cameraManager.setTorchMode(targetCamId, enabled)
            currentTorchState = enabled
            RedactedLogger.i(TAG, "CameraManager.setTorchMode executed: targetCamId=$targetCamId, enabled=$enabled")

            val verified = verify(context, arguments, ToolExecutionResult(success = true))

            ToolExecutionResult(
                success = true,
                output = mapOf("cameraId" to targetCamId, "state" to if (enabled) "ON" else "OFF"),
                verificationPassed = verified,
                verificationDetails = "CameraManager setTorchMode($targetCamId, $enabled) confirmed."
            )
        } catch (e: Exception) {
            RedactedLogger.e(TAG, "Failed to set torch mode", e)
            ToolExecutionResult(
                success = false,
                error = "CameraException: ${e.message}",
                verificationPassed = false
            )
        }
    }

    override suspend fun verify(context: Context, arguments: Map<String, Any?>, result: ToolExecutionResult): Boolean {
        val requestedState = arguments["enabled"] as? Boolean ?: return false
        return currentTorchState == requestedState
    }
}
