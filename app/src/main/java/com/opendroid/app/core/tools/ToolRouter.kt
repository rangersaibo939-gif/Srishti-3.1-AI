package com.opendroid.app.core.tools

import android.content.Context
import com.opendroid.app.core.domain.RiskLevel
import com.opendroid.app.core.domain.ToolExecutionResult
import com.opendroid.app.core.logging.RedactedLogger
import com.opendroid.app.core.security.UUIDv5
import com.opendroid.app.core.task.EmergencyStopManager
import com.opendroid.app.core.task.IdempotencyEngine
import com.opendroid.app.data.database.TaskDao
import com.opendroid.app.data.database.TaskStepEntity
import com.opendroid.app.data.database.TaskStepStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class ToolRoutingOutcome(
    val success: Boolean,
    val toolName: String,
    val result: ToolExecutionResult?,
    val policyResult: PolicyEvaluationResult,
    val errorMessage: String? = null
)

/**
 * Tool Router for Srishti 3.0
 * Validates, policies, deduplicates via UUIDv5, and executes Android native tools.
 */
class ToolRouter(
    private val context: Context,
    private val taskDao: TaskDao,
    private val toolPolicy: ToolPolicy = ToolPolicy()
) {

    suspend fun routeAndExecute(
        taskId: String,
        stepIndex: Int,
        toolName: String,
        arguments: Map<String, Any?>,
        onConfirmationNeeded: (suspend (reason: String) -> Boolean)? = null
    ): ToolRoutingOutcome = withContext(Dispatchers.IO) {
        if (EmergencyStopManager.isStopRequested()) {
            return@withContext ToolRoutingOutcome(
                success = false,
                toolName = toolName,
                result = null,
                policyResult = PolicyEvaluationResult(false, false, "Emergency stop active", RiskLevel.BLOCKED),
                errorMessage = "Emergency stop was activated."
            )
        }

        val tool = ToolRegistry.getTool(toolName)
        if (tool == null) {
            RedactedLogger.w(TAG, "Unrecognized tool: $toolName")
            return@withContext ToolRoutingOutcome(
                success = false,
                toolName = toolName,
                result = null,
                policyResult = PolicyEvaluationResult(false, false, "Tool not registered", RiskLevel.BLOCKED),
                errorMessage = "Tool '$toolName' is not registered or supported."
            )
        }

        // Schema validation
        val validationResult = tool.validate(arguments)
        if (validationResult.isFailure) {
            val err = validationResult.exceptionOrNull()?.message ?: "Validation failed"
            RedactedLogger.e(TAG, "Argument validation failed for $toolName: $err")
            return@withContext ToolRoutingOutcome(
                success = false,
                toolName = toolName,
                result = null,
                policyResult = PolicyEvaluationResult(false, false, err, tool.riskTier),
                errorMessage = "Invalid parameters for '$toolName': $err"
            )
        }

        // Policy evaluation
        val policy = toolPolicy.evaluatePolicy(tool, arguments)
        if (!policy.isAllowed) {
            return@withContext ToolRoutingOutcome(
                success = false,
                toolName = toolName,
                result = null,
                policyResult = policy,
                errorMessage = "Tool execution blocked: ${policy.reason}"
            )
        }

        // User confirmation if required
        if (policy.requiresUserConfirmation && onConfirmationNeeded != null) {
            val approved = onConfirmationNeeded(policy.reason)
            if (!approved) {
                return@withContext ToolRoutingOutcome(
                    success = false,
                    toolName = toolName,
                    result = null,
                    policyResult = policy,
                    errorMessage = "User declined permission for $toolName."
                )
            }
        }

        // Idempotency tracking
        val canonicalArgs = IdempotencyEngine.canonicalizeJson(arguments)
        val idempotencyKey = UUIDv5.generateStepKey(taskId, stepIndex, toolName, canonicalArgs)
        val stepEntity = TaskStepEntity(
            taskId = taskId,
            stepIndex = stepIndex,
            toolName = toolName,
            argumentsJson = canonicalArgs,
            idempotencyKey = idempotencyKey,
            status = TaskStepStatus.EXECUTING
        )
        taskDao.insertStep(stepEntity)

        // Execution with timeout
        val execResult = withTimeoutOrNull(tool.timeoutMs) {
            tool.execute(context, arguments)
        } ?: ToolExecutionResult(
            success = false,
            data = emptyMap(),
            errorMessage = "Tool '$toolName' timed out after ${tool.timeoutMs}ms"
        )

        // Post-execution verification
        if (execResult.success) {
            val verified = tool.verify(context, arguments, execResult)
            if (!verified) {
                RedactedLogger.w(TAG, "Tool verification warning: OS state may not match intended mutation")
            }
            taskDao.updateStepStatus(taskId, stepIndex, TaskStepStatus.COMPLETED, execResult.data.toString())
        } else {
            taskDao.updateStepStatus(taskId, stepIndex, TaskStepStatus.FAILED, execResult.errorMessage)
        }

        ToolRoutingOutcome(
            success = execResult.success,
            toolName = toolName,
            result = execResult,
            policyResult = policy,
            errorMessage = execResult.errorMessage
        )
    }

    companion object {
        private const val TAG = "SrishtiToolRouter"
    }
}
