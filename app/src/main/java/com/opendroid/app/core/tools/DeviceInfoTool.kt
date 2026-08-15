package com.opendroid.app.core.tools

import android.content.Context
import android.os.Build
import com.opendroid.app.core.domain.RiskLevel
import com.opendroid.app.core.domain.ToolDefinition
import com.opendroid.app.core.domain.ToolExecutionResult
import com.opendroid.app.core.knowledge.KnowledgeEngine

class DeviceInfoTool : AndroidTool {
    override val id: String = "tool_device_info"
    override val name: String = "get_device_info"
    override val description: String = "Queries device telemetry including battery level, volume, connectivity, OS version, and model."
    override val riskTier: RiskLevel = RiskLevel.SAFE
    override val timeoutMs: Long = 3000L

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = description,
            parameters = emptyMap(),
            required = emptyList()
        )
    }

    override fun validate(arguments: Map<String, Any?>): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun execute(context: Context, arguments: Map<String, Any?>): ToolExecutionResult {
        val knowledge = KnowledgeEngine(context)
        val snapshot = knowledge.captureDeviceSnapshot()
        val data = mapOf(
            "batteryPercent" to snapshot.batteryPercent,
            "isCharging" to snapshot.isCharging,
            "mediaVolumePercent" to snapshot.mediaVolumePercent,
            "networkType" to snapshot.networkType,
            "localDateTime" to snapshot.localDateTime,
            "deviceModel" to snapshot.deviceModel,
            "androidVersion" to snapshot.androidVersion
        )
        return ToolExecutionResult(success = true, data = data)
    }

    override suspend fun verify(context: Context, arguments: Map<String, Any?>, result: ToolExecutionResult): Boolean {
        return result.success && result.data.containsKey("batteryPercent")
    }
}
