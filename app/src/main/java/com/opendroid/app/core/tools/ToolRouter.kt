package com.opendroid.app.core.tools

import android.content.Context
import com.google.gson.Gson
import com.opendroid.app.core.domain.RiskLevel
import com.opendroid.app.core.domain.TaskStatus
import com.opendroid.app.core.domain.ToolExecutionResult
import com.opendroid.app.core.logging.RedactedLogger
import com.opendroid.app.core.security.UUIDv5
import com.opendroid.app.core.task.EmergencyStopManager
import com.opendroid.app.core.task.IdempotencyEngine
import com.opendroid.app.data.database.TaskDao
import com.opendroid.app.data.database.TaskStepEntity
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

/** Safe, durable gateway between Srishti's reasoning layer and Android tools. */
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
        if (EmergencyStopManager.isStopRequested()) return@withContext blocked(toolName, "Emergency stop was activated.")

        val tool = ToolRegistry.getTool(toolName)
            ?: return@withContext blocked(toolName, "Tool '$toolName' is not registered or supported.")
        val validation = tool.validate(arguments)
        if (validation.isFailure) {
            val reason = validation.exceptionOrNull()?.message ?: "Validation failed"
            return@withContext ToolRoutingOutcome(false, toolName, null,
                PolicyEvaluationResult(false, false, reason, tool.riskTier),
                "Invalid parameters for '$toolName': $reason")
        }

        val policy = toolPolicy.evaluatePolicy(tool, arguments)
        if (!policy.isAllowed) return@withContext ToolRoutingOutcome(false, toolName, null, policy, "Tool execution blocked: ${policy.reason}")
        if (policy.requiresUserConfirmation) {
            val approved = onConfirmationNeeded?.invoke(policy.reason) ?: false
            if (!approved) return@withContext ToolRoutingOutcome(false, toolName, null, policy, "User declined permission for $toolName.")
        }

        val canonicalArgs = IdempotencyEngine.canonicalizeJson(arguments)
        val idempotencyKey = UUIDv5.generateStepKey(taskId, stepIndex, toolName, canonicalArgs)
        val step = TaskStepEntity(
            taskId = taskId,
            stepIndex = stepIndex,
            toolName = toolName,
            argumentsJson = canonicalArgs,
            idempotencyKey = idempotencyKey,
            riskTier = tool.riskTier,
            status = TaskStatus.EXECUTING
        )
        if (taskDao.insertStep(step) == -1L) {
            val existing = taskDao.getStepByIdempotencyKey(idempotencyKey)
            return@withContext ToolRoutingOutcome(existing?.status == TaskStatus.COMPLETED, toolName, null, policy, "Duplicate action suppressed by idempotency key.")
        }

        val execResult = withTimeoutOrNull(tool.timeoutMs) { tool.execute(context, arguments) }
            ?: ToolExecutionResult(success = false, error = "Tool '$toolName' timed out after ${tool.timeoutMs}ms")
        val verified = execResult.success && tool.verify(context, arguments, execResult)
        val finalStatus = if (execResult.success && verified) TaskStatus.COMPLETED else TaskStatus.FAILED
        taskDao.updateStepStatus(idempotencyKey, finalStatus, Gson().toJson(execResult), verified)
        if (!verified && execResult.success) RedactedLogger.w(TAG, "Tool verification failed for $toolName")

        ToolRoutingOutcome(
            success = execResult.success && verified,
            toolName = toolName,
            result = execResult,
            policyResult = policy,
            errorMessage = if (execResult.success && verified) null else (execResult.error ?: "Post-execution verification failed.")
        )
    }

    private fun blocked(toolName: String, message: String) = ToolRoutingOutcome(false, toolName, null,
        PolicyEvaluationResult(false, false, message, RiskLevel.BLOCKED), message)

    companion object { private const val TAG = "SrishtiToolRouter" }
}
