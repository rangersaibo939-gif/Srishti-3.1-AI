package com.opendroid.app.core.tools

import com.opendroid.app.core.domain.RiskLevel

data class PolicyEvaluationResult(
    val isAllowed: Boolean,
    val requiresUserConfirmation: Boolean,
    val reason: String,
    val riskLevel: RiskLevel
)

/**
 * Tool Policy & Permission Evaluator for Srishti 3.0
 * Enforces risk boundaries, confirmation requirements for mutations, and prevents unauthorized actions.
 */
class ToolPolicy {

    fun evaluatePolicy(tool: AndroidTool, arguments: Map<String, Any?>): PolicyEvaluationResult {
        return when (tool.riskTier) {
            RiskLevel.SAFE -> {
                PolicyEvaluationResult(
                    isAllowed = true,
                    requiresUserConfirmation = false,
                    reason = "Read-only non-destructive system operation.",
                    riskLevel = RiskLevel.SAFE
                )
            }
            RiskLevel.CONFIRM -> {
                PolicyEvaluationResult(
                    isAllowed = true,
                    requiresUserConfirmation = true,
                    reason = "Action modifies device state (${tool.name}). Explicit confirmation required.",
                    riskLevel = RiskLevel.CONFIRM
                )
            }
            RiskLevel.HIGH_RISK -> {
                PolicyEvaluationResult(
                    isAllowed = true,
                    requiresUserConfirmation = true,
                    reason = "High-risk system or package operation (${tool.name}). User authorization required.",
                    riskLevel = RiskLevel.HIGH_RISK
                )
            }
            RiskLevel.BLOCKED -> {
                PolicyEvaluationResult(
                    isAllowed = false,
                    requiresUserConfirmation = false,
                    reason = "Action is blocked by safety policy.",
                    riskLevel = RiskLevel.BLOCKED
                )
            }
        }
    }
}
