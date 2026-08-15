package com.opendroid.app.core.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import com.opendroid.app.core.domain.RiskLevel
import com.opendroid.app.core.domain.ToolDefinition
import com.opendroid.app.core.domain.ToolExecutionResult
import com.opendroid.app.core.domain.ToolParameter
import com.opendroid.app.core.logging.RedactedLogger

/**
 * Real Android BatteryManager tool querying hardware battery level, charging status, and power saver mode
 */
class BatteryInfoTool : AndroidTool {
    override val id: String = "tool_battery_v1"
    override val name: String = "get_battery_info"
    override val description: String = "Query current device battery percentage, charging state, and power saver status."
    override val riskTier: RiskLevel = RiskLevel.SAFE
    override val timeoutMs: Long = 3000L

    companion object {
        private const val TAG = "BatteryInfoTool"
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
                    name = "includeHealth",
                    type = "boolean",
                    description = "Whether to include detailed battery health and temperature metrics",
                    required = false
                )
            )
        )
    }

    override fun validate(arguments: Map<String, Any?>): Result<Unit> {
        // No required parameters, optional includeHealth
        return Result.success(Unit)
    }

    override suspend fun execute(context: Context, arguments: Map<String, Any?>): ToolExecutionResult {
        return try {
            val batteryStatusIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )

            val level = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1

            val status = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isPowerSaveMode = powerManager?.isPowerSaveMode ?: false

            val output = mutableMapOf<String, Any?>(
                "percentage" to batteryPct,
                "isCharging" to isCharging,
                "isPowerSaveMode" to isPowerSaveMode
            )

            if (arguments["includeHealth"] == true) {
                val tempTenths = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                output["temperatureCelsius"] = tempTenths / 10.0
                output["health"] = when (batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
                    else -> "NORMAL"
                }
            }

            RedactedLogger.i(TAG, "Battery info retrieved: percentage=$batteryPct%, isCharging=$isCharging")

            ToolExecutionResult(
                success = true,
                output = output,
                verificationPassed = batteryPct >= 0,
                verificationDetails = "Battery state retrieved successfully via BatteryManager."
            )
        } catch (e: Exception) {
            RedactedLogger.e(TAG, "Failed to query BatteryManager", e)
            ToolExecutionResult(
                success = false,
                error = "BatteryException: ${e.message}",
                verificationPassed = false
            )
        }
    }

    override suspend fun verify(context: Context, arguments: Map<String, Any?>, result: ToolExecutionResult): Boolean {
        return result.success && (result.output?.get("percentage") as? Int ?: -1) >= 0
    }
}
