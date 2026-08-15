package com.opendroid.app.core.task

import com.opendroid.app.core.security.UUIDv5
import com.opendroid.app.data.database.TaskStepEntity
import com.opendroid.app.data.repository.TaskRepository

/**
 * Ensures strict exactly-once side-effect mutation
 * Prevents re-running tools when recovering from process death or retries.
 */
class IdempotencyEngine(private val repository: TaskRepository) {

    /**
     * Checks if a step has already executed or is currently planned
     */
    suspend fun checkOrRegisterStep(
        taskId: String,
        stepIndex: Int,
        toolName: String,
        argumentsJson: String,
        riskTier: com.opendroid.app.core.domain.RiskLevel
    ): Pair<String, Boolean> {
        val idempotencyKey = UUIDv5.forStep(taskId, stepIndex, toolName, argumentsJson)

        val existing = repository.getStepByIdempotencyKey(idempotencyKey)
        if (existing != null) {
            // Already recorded in Room
            return Pair(idempotencyKey, false)
        }

        val stepEntity = TaskStepEntity(
            taskId = taskId,
            stepIndex = stepIndex,
            idempotencyKey = idempotencyKey,
            toolName = toolName,
            argumentsJson = argumentsJson,
            riskTier = riskTier,
            status = com.opendroid.app.core.domain.TaskStatus.PLANNED
        )

        val isNew = repository.recordStepIfNew(stepEntity)
        return Pair(idempotencyKey, isNew)
    }
}
