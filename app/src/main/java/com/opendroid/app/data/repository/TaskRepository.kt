package com.opendroid.app.data.repository

import com.opendroid.app.core.domain.TaskStatus
import com.opendroid.app.data.database.TaskDao
import com.opendroid.app.data.database.TaskEntity
import com.opendroid.app.data.database.TaskStepEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository layer managing Room SQLite IO dispatching
 */
class TaskRepository(private val taskDao: TaskDao) {

    val allTasksFlow: Flow<List<TaskEntity>> = taskDao.getAllTasksFlow()

    suspend fun insertTask(task: TaskEntity) = withContext(Dispatchers.IO) {
        taskDao.insertTask(task)
    }

    suspend fun updateTaskStatus(
        taskId: String,
        status: TaskStatus,
        error: String? = null,
        finalResponse: String? = null
    ) = withContext(Dispatchers.IO) {
        taskDao.updateTaskStatus(taskId, status, error, finalResponse)
    }

    suspend fun getTask(taskId: String): TaskEntity? = withContext(Dispatchers.IO) {
        taskDao.getTaskById(taskId)
    }

    suspend fun recordStepIfNew(step: TaskStepEntity): Boolean = withContext(Dispatchers.IO) {
        val rowId = taskDao.insertStep(step)
        rowId != -1L // Returns true if newly inserted, false if already exists (idempotent duplicate)
    }

    suspend fun getStepByIdempotencyKey(key: String): TaskStepEntity? = withContext(Dispatchers.IO) {
        taskDao.getStepByIdempotencyKey(key)
    }

    suspend fun updateStepStatus(
        idempotencyKey: String,
        status: TaskStatus,
        resultJson: String? = null,
        verified: Boolean = false
    ) = withContext(Dispatchers.IO) {
        taskDao.updateStepStatus(idempotencyKey, status, resultJson, verified)
    }

    suspend fun getStepsForTask(taskId: String): List<TaskStepEntity> = withContext(Dispatchers.IO) {
        taskDao.getStepsForTask(taskId)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        taskDao.clearAll()
    }
}
