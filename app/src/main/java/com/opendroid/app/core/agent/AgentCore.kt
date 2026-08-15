package com.opendroid.app.core.agent

import android.content.Context
import com.opendroid.app.core.domain.RiskLevel
import com.opendroid.app.core.domain.StructuredModelDecision
import com.opendroid.app.core.domain.TaskStatus
import com.opendroid.app.core.domain.ToolCallRequest
import com.opendroid.app.core.domain.ToolExecutionResult
import com.opendroid.app.core.inference.InferenceClient
import com.opendroid.app.core.logging.RedactedLogger
import com.opendroid.app.core.risk.RiskEngine
import com.opendroid.app.core.security.UUIDv5
import com.opendroid.app.core.task.EmergencyStopManager
import com.opendroid.app.core.tools.ToolRegistry
import com.opendroid.app.data.database.TaskEntity
import com.opendroid.app.data.database.TaskStepEntity
import com.opendroid.app.data.repository.TaskRepository
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.UUID

interface AgentExecutionCallback {
    fun onLog(message: String)
    fun onToken(token: String) {}
    fun onStateChange(status: TaskStatus)
    suspend fun onConfirmationRequired(
        toolCall: ToolCallRequest,
        reason: String
    ): Boolean
}

/**
 * Real In-Process AgentCore Coordinator
 *
 * Pipeline sequence:
 * USER_REQUEST
 * → CONTEXT
 * → INFERENCE (AIDL IPC to :inference process running native llama.cpp)
 * → STRUCTURED DECISION (GBNF schema parsed)
 * → TOOL REGISTRY
 * → SCHEMA VALIDATION
 * → RISK GATE
 * → EXECUTION
 * → VERIFICATION
 * → TASK PERSISTENCE
 * → FINAL RESPONSE
 */
class AgentCore(
    private val context: Context,
    private val repository: TaskRepository,
    private val inferenceClient: InferenceClient
) {
    private val gson = Gson()
    private val _currentStatus = MutableStateFlow(TaskStatus.CREATED)
    val currentStatus: StateFlow<TaskStatus> = _currentStatus

    companion object {
        private const val TAG = "AgentCore"
        const val DEFAULT_MODEL = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        const val DEFAULT_PROVIDER = "LOCAL_LLAMA_AIDL"

        // Production GBNF Grammar strictly enforcing structured JSON decision
        const val TOOL_DECISION_GBNF = """
root ::= "{" ws "\"type\":" ws ("\"tool_call\"" | "\"direct_response\"") "," ws "\"thought\":" ws string "," ws (tool_call_field | response_field) "}"
tool_call_field ::= "\"tool_name\":" ws string "," ws "\"arguments\":" ws object
response_field ::= "\"response\":" ws string
object ::= "{" ws (string ":" ws value ("," ws string ":" ws value)*)? "}"
value ::= string | number | "true" | "false" | "null" | object | array
array ::= "[" ws (value ("," ws value)*)? "]"
string ::= "\"" ([^"\\] | "\\" ["\\/bfnrt])* "\""
number ::= "-"? [0-9]+ ("." [0-9]+)?
ws ::= [ \t\n\r]*
"""
    }

    suspend fun executeTask(
        userPrompt: String,
        callbacks: AgentExecutionCallback? = null
    ): TaskEntity {
        fun log(msg: String) {
            val formatted = "[${System.currentTimeMillis()}] $msg"
            RedactedLogger.i(TAG, formatted)
            callbacks?.onLog(formatted)
        }

        if (EmergencyStopManager.isEmergencyStopActive()) {
            throw IllegalStateException("AgentCore halted: Emergency Stop is currently active.")
        }

        val taskId = "task_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val initialTask = TaskEntity(
            id = taskId,
            userPrompt = userPrompt,
            status = TaskStatus.CREATED,
            provider = DEFAULT_PROVIDER,
            model = DEFAULT_MODEL
        )

        // 1. Initial Room DB Write
        repository.insertTask(initialTask)
        _currentStatus.value = TaskStatus.CREATED
        callbacks?.onStateChange(TaskStatus.CREATED)
        log("Task [$taskId] persisted in Room database with state CREATED.")

        try {
            // 2. Context Bounding & Analyzing State
            repository.updateTaskStatus(taskId, TaskStatus.ANALYZING)
            _currentStatus.value = TaskStatus.ANALYZING
            callbacks?.onStateChange(TaskStatus.ANALYZING)
            log("AgentCore analyzing bounded context & preparing GBNF schema...")

            if (EmergencyStopManager.isEmergencyStopActive()) {
                throw CancellationException("Emergency stop triggered during context bounding.")
            }

            // 3. Native Inference via AIDL IPC to :inference process
            log("Querying real llama.cpp engine in :inference process over AIDL...")
            val serviceStatus = inferenceClient.getServiceStatus()
            log("Inference process status: $serviceStatus")

            val toolsPrompt = ToolRegistry.getAllTools().joinToString("\n") { tool ->
                "- ${tool.name}: ${tool.description} (params: ${tool.parameters.joinToString { "${it.name}:${it.type}" }})"
            }

            val prompt = """
You are OpenDroid, an on-device Android assistant.
Available Tools:
$toolsPrompt

User Request: $userPrompt

Respond strictly in JSON conforming to grammar:
{"type": "tool_call", "thought": "...", "tool_name": "...", "arguments": {...}} OR {"type": "direct_response", "thought": "...", "response": "..."}
""".trimIndent()

            val inferenceResult = inferenceClient.inferAsync(
                prompt = prompt,
                gbnfGrammar = TOOL_DECISION_GBNF,
                maxTokens = 256,
                onToken = { token ->
                    callbacks?.onToken(token)
                }
            )

            val rawOutput = inferenceResult.getOrElse { error ->
                log("Local inference IPC failed: ${error.message}")
                // Fallback is strictly rejected on production path if native engine is missing
                val failMsg = "Real llama.cpp inference failed: ${error.message}"
                repository.updateTaskStatus(taskId, TaskStatus.FAILED, error = failMsg)
                _currentStatus.value = TaskStatus.FAILED
                callbacks?.onStateChange(TaskStatus.FAILED)
                return repository.getTask(taskId) ?: initialTask
            }

            log("Received raw constrained response from llama.cpp: $rawOutput")

            val decision = parseStructuredResponse(rawOutput, userPrompt)
            log("Structured decision evaluated: ${decision.javaClass.simpleName}")

            when (decision) {
                is StructuredModelDecision.DirectResponse -> {
                    repository.updateTaskStatus(taskId, TaskStatus.COMPLETED, finalResponse = decision.response)
                    _currentStatus.value = TaskStatus.COMPLETED
                    callbacks?.onStateChange(TaskStatus.COMPLETED)
                    log("Task completed with direct text response.")
                    return repository.getTask(taskId) ?: initialTask
                }

                is StructuredModelDecision.BlockedOrInvalid -> {
                    repository.updateTaskStatus(taskId, TaskStatus.FAILED, error = decision.reason)
                    _currentStatus.value = TaskStatus.FAILED
                    callbacks?.onStateChange(TaskStatus.FAILED)
                    log("Task failed: ${decision.reason}")
                    return repository.getTask(taskId) ?: initialTask
                }

                is StructuredModelDecision.ToolCall -> {
                    val toolCall = decision.toolCall
                    log("Model requested tool '${toolCall.name}' with arguments: ${gson.toJson(toolCall.arguments)}")

                    // 4. Safe Tool Registry Check
                    val tool = ToolRegistry.getTool(toolCall.name)
                    if (tool == null) {
                        val errMsg = "Tool '${toolCall.name}' is not registered in Safe Registry."
                        repository.updateTaskStatus(taskId, TaskStatus.FAILED, error = errMsg)
                        _currentStatus.value = TaskStatus.FAILED
                        callbacks?.onStateChange(TaskStatus.FAILED)
                        log("REGISTRY ERROR: $errMsg")
                        return repository.getTask(taskId) ?: initialTask
                    }

                    // 5. Schema Validation
                    val validationResult = tool.validate(toolCall.arguments)
                    if (validationResult.isFailure) {
                        val errMsg = "Schema validation failed: ${validationResult.exceptionOrNull()?.message}"
                        repository.updateTaskStatus(taskId, TaskStatus.FAILED, error = errMsg)
                        _currentStatus.value = TaskStatus.FAILED
                        callbacks?.onStateChange(TaskStatus.FAILED)
                        log("SCHEMA FAILURE: $errMsg")
                        return repository.getTask(taskId) ?: initialTask
                    }

                    // 6. Deterministic Risk Engine Evaluation
                    val riskEval = RiskEngine.evaluate(toolCall)
                    log("Risk Engine evaluated [${riskEval.tier}]: ${riskEval.reason}")

                    if (riskEval.tier == RiskLevel.BLOCKED) {
                        val errMsg = "BLOCKED by Risk Engine: ${riskEval.reason}"
                        repository.updateTaskStatus(taskId, TaskStatus.FAILED, error = errMsg)
                        _currentStatus.value = TaskStatus.FAILED
                        callbacks?.onStateChange(TaskStatus.FAILED)
                        log("RISK GATE BLOCKED: $errMsg")
                        return repository.getTask(taskId) ?: initialTask
                    }

                    // 7. Idempotent Step Registration in Room (RFC 4122 UUIDv5)
                    val argsJson = gson.toJson(toolCall.arguments)
                    val idempotencyKey = UUIDv5.forStep(taskId, 0, toolCall.name, argsJson)

                    val stepEntity = TaskStepEntity(
                        taskId = taskId,
                        stepIndex = 0,
                        idempotencyKey = idempotencyKey,
                        toolName = toolCall.name,
                        argumentsJson = argsJson,
                        riskTier = riskEval.tier,
                        status = TaskStatus.PLANNED
                    )

                    val isNew = repository.recordStepIfNew(stepEntity)
                    if (!isNew) {
                        log("Duplicate idempotent step detected: $idempotencyKey. Skipping duplicate execution.")
                        return repository.getTask(taskId) ?: initialTask
                    }

                    repository.updateTaskStatus(taskId, TaskStatus.PLANNED)
                    _currentStatus.value = TaskStatus.PLANNED
                    callbacks?.onStateChange(TaskStatus.PLANNED)
                    log("Step planned with deterministic UUIDv5 key: $idempotencyKey")

                    // 8. User Confirmation Gate (if CONFIRM or HIGH_RISK)
                    if (riskEval.requiresUserConfirmation || riskEval.tier == RiskLevel.CONFIRM || riskEval.tier == RiskLevel.HIGH_RISK) {
                        repository.updateTaskStatus(taskId, TaskStatus.WAITING_CONFIRMATION)
                        _currentStatus.value = TaskStatus.WAITING_CONFIRMATION
                        callbacks?.onStateChange(TaskStatus.WAITING_CONFIRMATION)
                        log("Waiting for explicit user confirmation: ${riskEval.reason}")

                        val userApproved = callbacks?.onConfirmationRequired(toolCall, riskEval.reason) ?: true
                        if (!userApproved) {
                            val abortMsg = "Action rejected by user confirmation dialog."
                            repository.updateTaskStatus(taskId, TaskStatus.FAILED, error = abortMsg)
                            repository.updateStepStatus(idempotencyKey, TaskStatus.FAILED)
                            _currentStatus.value = TaskStatus.FAILED
                            callbacks?.onStateChange(TaskStatus.FAILED)
                            log("User declined action. Task aborted.")
                            return repository.getTask(taskId) ?: initialTask
                        }
                        log("User confirmation granted.")
                    }

                    // Check Emergency Stop before native mutation
                    if (EmergencyStopManager.isEmergencyStopActive()) {
                        throw CancellationException("Emergency stop active before native execution.")
                    }

                    // 9. Real Native Android Tool Execution
                    repository.updateTaskStatus(taskId, TaskStatus.EXECUTING)
                    repository.updateStepStatus(idempotencyKey, TaskStatus.EXECUTING)
                    _currentStatus.value = TaskStatus.EXECUTING
                    callbacks?.onStateChange(TaskStatus.EXECUTING)
                    log("Executing native Android tool [${tool.name}] via System Manager...")

                    val execResult: ToolExecutionResult = tool.execute(context, toolCall.arguments)

                    // 10. Post-Execution Verification State
                    repository.updateTaskStatus(taskId, TaskStatus.VERIFYING)
                    _currentStatus.value = TaskStatus.VERIFYING
                    callbacks?.onStateChange(TaskStatus.VERIFYING)
                    log("Verifying Android OS state change... Verified: ${execResult.verificationPassed}")

                    val resultJson = gson.toJson(execResult)
                    if (execResult.success && execResult.verificationPassed) {
                        repository.updateStepStatus(
                            idempotencyKey = idempotencyKey,
                            status = TaskStatus.COMPLETED,
                            resultJson = resultJson,
                            verified = true
                        )
                        val finalMsg = "Tool ${tool.name} executed and verified successfully. ${execResult.verificationDetails ?: ""}"
                        repository.updateTaskStatus(taskId, TaskStatus.COMPLETED, finalResponse = finalMsg)
                        _currentStatus.value = TaskStatus.COMPLETED
                        callbacks?.onStateChange(TaskStatus.COMPLETED)
                        log("Task [$taskId] successfully COMPLETED.")
                    } else {
                        val failMsg = execResult.error ?: "Post-execution verification failed."
                        repository.updateStepStatus(
                            idempotencyKey = idempotencyKey,
                            status = TaskStatus.FAILED,
                            resultJson = resultJson,
                            verified = false
                        )
                        repository.updateTaskStatus(taskId, TaskStatus.FAILED, error = failMsg)
                        _currentStatus.value = TaskStatus.FAILED
                        callbacks?.onStateChange(TaskStatus.FAILED)
                        log("Task [$taskId] FAILED: $failMsg")
                    }

                    return repository.getTask(taskId) ?: initialTask
                }
            }
        } catch (e: CancellationException) {
            log("Execution cancelled by Emergency Stop: ${e.message}")
            repository.updateTaskStatus(taskId, TaskStatus.FAILED, error = "Emergency Stop cancelled execution.")
            _currentStatus.value = TaskStatus.FAILED
            callbacks?.onStateChange(TaskStatus.FAILED)
            return repository.getTask(taskId) ?: initialTask
        } catch (e: Exception) {
            log("Unhandled exception in pipeline: ${e.message}")
            repository.updateTaskStatus(taskId, TaskStatus.FAILED, error = e.message)
            _currentStatus.value = TaskStatus.FAILED
            callbacks?.onStateChange(TaskStatus.FAILED)
            return repository.getTask(taskId) ?: initialTask
        }
    }

    /**
     * Parses real constrained JSON output produced by llama.cpp + GBNF
     */
    private fun parseStructuredResponse(rawJson: String, originalPrompt: String): StructuredModelDecision {
        return try {
            val json = JSONObject(rawJson.trim())
            val type = json.optString("type", "")
            val thought = json.optString("thought", "")

            if (type == "tool_call") {
                val toolName = json.getString("tool_name")
                val argsObj = json.optJSONObject("arguments") ?: JSONObject()
                val argsMap = mutableMapOf<String, Any?>()
                argsObj.keys().forEach { key ->
                    argsMap[key] = argsObj.get(key)
                }
                StructuredModelDecision.ToolCall(
                    thought = thought,
                    toolCall = ToolCallRequest(
                        id = "call_${System.currentTimeMillis()}",
                        name = toolName,
                        arguments = argsMap
                    )
                )
            } else if (type == "direct_response") {
                val resp = json.optString("response", "")
                StructuredModelDecision.DirectResponse(
                    thought = thought,
                    response = resp
                )
            } else {
                StructuredModelDecision.BlockedOrInvalid("Unknown decision type from model: $type")
            }
        } catch (e: Exception) {
            RedactedLogger.e(TAG, "Failed to parse model JSON: ${e.message}, raw: $rawJson")
            StructuredModelDecision.BlockedOrInvalid("Model output did not conform to JSON grammar: ${e.message}")
        }
    }
}
