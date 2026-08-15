package com.opendroid.app.core.risk

import com.opendroid.app.core.domain.RiskLevel
import com.opendroid.app.core.domain.ToolCallRequest
import com.opendroid.app.core.tools.ToolRegistry

data class RiskEvaluationResult(
    val tier: RiskLevel,
    val allowedToExecute: Boolean,
    val requiresUserConfirmation: Boolean,
    val requiresBiometricAuth: Boolean,
    val reason: String
)

/**
 * Pure Deterministic 4-Tier Risk Engine
 * The LLM CANNOT override this risk evaluation.
 * Evaluates tools before any Android OS mutation occurs.
 */
object RiskEngine {

    private val HIGH_RISK_PACKAGES = setOf(
        "com.android.settings",
        "com.google.android.apps.walletnfcrel",
        "com.bank.app"
    )

    fun evaluate(toolCall: ToolCallRequest): RiskEvaluationResult {
        val name = toolCall.name
        val args = toolCall.arguments
        val tool = ToolRegistry.getTool(name)

        // 1. BLOCKED check: Unknown or unwhitelisted tools
        if (tool == null) {
            return RiskEvaluationResult(
                tier = RiskLevel.BLOCKED,
                allowedToExecute = false,
                requiresUserConfirmation = false,
                requiresBiometricAuth = false,
                reason = "Operation BLOCKED: Tool '$name' is not registered in Phase 1 Safe Registry."
            )
        }

        // Explicit forbidden capabilities (Rule: No arbitrary automation, shell, whatsapp, financial)
        if (name.contains("shell") || name.contains("accessibility") || name.contains("whatsapp") || name.contains("purchase")) {
            return RiskEvaluationResult(
                tier = RiskLevel.BLOCKED,
                allowedToExecute = false,
                requiresUserConfirmation = false,
                requiresBiometricAuth = false,
                reason = "Operation BLOCKED: System policy strictly forbids arbitrary automation or financial primitives."
            )
        }

        // 2. Specific tool checks
        return when (name) {
            "open_installed_app" -> {
                val pkg = args["packageName"]?.toString() ?: ""
                if (HIGH_RISK_PACKAGES.contains(pkg)) {
                    RiskEvaluationResult(
                        tier = RiskLevel.HIGH_RISK,
                        allowedToExecute = false,
                        requiresUserConfirmation = true,
                        requiresBiometricAuth = true,
                        reason = "Opening privileged system settings or financial app '$pkg' requires Biometric Authentication."
                    )
                } else {
                    RiskEvaluationResult(
                        tier = RiskLevel.SAFE,
                        allowedToExecute = true,
                        requiresUserConfirmation = false,
                        requiresBiometricAuth = false,
                        reason = "Opening app '$pkg' is classified as SAFE under standard Android Intent dispatch."
                    )
                }
            }

            "set_media_volume" -> {
                val vol = (args["volumePercent"] as? Number)?.toInt() ?: 50
                if (vol > 85) {
                    RiskEvaluationResult(
                        tier = RiskLevel.CONFIRM,
                        allowedToExecute = false,
                        requiresUserConfirmation = true,
                        requiresBiometricAuth = false,
                        reason = "Setting media volume above 85% ($vol%) may cause hearing discomfort and requires explicit user confirmation."
                    )
                } else {
                    RiskEvaluationResult(
                        tier = RiskLevel.SAFE,
                        allowedToExecute = true,
                        requiresUserConfirmation = false,
                        requiresBiometricAuth = false,
                        reason = "Setting media volume to $vol% is SAFE."
                    )
                }
            }

            "set_flashlight" -> {
                RiskEvaluationResult(
                    tier = RiskLevel.SAFE,
                    allowedToExecute = true,
                    requiresUserConfirmation = false,
                    requiresBiometricAuth = false,
                    reason = "Controlling camera torch is SAFE."
                )
            }

            "get_battery_info" -> {
                RiskEvaluationResult(
                    tier = RiskLevel.SAFE,
                    allowedToExecute = true,
                    requiresUserConfirmation = false,
                    requiresBiometricAuth = false,
                    reason = "Querying battery status is read-only and SAFE."
                )
            }

            else -> {
                RiskEvaluationResult(
                    tier = RiskLevel.CONFIRM,
                    allowedToExecute = false,
                    requiresUserConfirmation = true,
                    requiresBiometricAuth = false,
                    reason = "Default fallback: User confirmation required for tool $name."
                )
            }
        }
    }
}
