package com.opendroid.app.core.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.opendroid.app.core.domain.RiskLevel
import com.opendroid.app.core.domain.ToolDefinition
import com.opendroid.app.core.domain.ToolExecutionResult
import com.opendroid.app.core.domain.ToolParameter
import com.opendroid.app.core.logging.RedactedLogger

/**
 * Real Android PackageManager app launcher with Intent validation
 */
class AppLaunchTool : AndroidTool {
    override val id: String = "tool_launch_app_v1"
    override val name: String = "open_installed_app"
    override val description: String = "Launch an installed Android application by its package name."
    override val riskTier: RiskLevel = RiskLevel.SAFE
    override val timeoutMs: Long = 4000L

    companion object {
        private const val TAG = "AppLaunchTool"
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
                    name = "packageName",
                    type = "string",
                    description = "Fully qualified Android package name (e.g. com.google.android.calculator)",
                    required = true
                )
            )
        )
    }

    override fun validate(arguments: Map<String, Any?>): Result<Unit> {
        val pkg = arguments["packageName"] as? String
        if (pkg.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Missing required parameter: 'packageName'."))
        }
        // Strict package format regex: alphanumeric + dots
        if (!pkg.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$"))) {
            return Result.failure(IllegalArgumentException("Invalid Android package name format: '$pkg'."))
        }
        return Result.success(Unit)
    }

    override suspend fun execute(context: Context, arguments: Map<String, Any?>): ToolExecutionResult {
        val packageName = arguments["packageName"] as String
        val pm: PackageManager = context.packageManager

        return try {
            val launchIntent: Intent? = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                return ToolExecutionResult(
                    success = false,
                    error = "Package '$packageName' is not installed or has no launchable Activity.",
                    verificationPassed = false
                )
            }

            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)

            RedactedLogger.i(TAG, "Successfully started activity for package: $packageName")

            ToolExecutionResult(
                success = true,
                output = mapOf(
                    "packageName" to packageName,
                    "action" to (launchIntent.action ?: "MAIN"),
                    "launched" to true
                ),
                verificationPassed = true,
                verificationDetails = "Activity launch Intent dispatched to Android ActivityManager."
            )
        } catch (e: Exception) {
            RedactedLogger.e(TAG, "Failed to launch package: $packageName", e)
            ToolExecutionResult(
                success = false,
                error = "LaunchException: ${e.message}",
                verificationPassed = false
            )
        }
    }

    override suspend fun verify(context: Context, arguments: Map<String, Any?>, result: ToolExecutionResult): Boolean {
        val packageName = arguments["packageName"] as? String ?: return false
        val pm = context.packageManager
        return try {
            pm.getPackageInfo(packageName, 0)
            result.success
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
