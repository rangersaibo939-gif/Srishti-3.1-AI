package com.opendroid.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.opendroid.app.core.domain.TaskStatus
import kotlinx.coroutines.flow.Flow

data class TaskWithSteps(
    val task: TaskEntity,
    val steps: List<TaskStepEntity>
)

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, errorMessage = :error, finalResponse = :finalResponse, updatedAt = :updatedAt WHERE id = :taskId")
    suspend fun updateTaskStatus(
        taskId: String,
        status: TaskStatus,
        error: String? = null,
        finalResponse: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    suspend fun getAllTasks(): List<TaskEntity>

    // Steps & Idempotency queries
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStep(step: TaskStepEntity): Long

    @Query("SELECT * FROM task_steps WHERE idempotencyKey = :idempotencyKey LIMIT 1")
    suspend fun getStepByIdempotencyKey(idempotencyKey: String): TaskStepEntity?

    @Query("SELECT * FROM task_steps WHERE taskId = :taskId ORDER BY stepIndex ASC")
    suspend fun getStepsForTask(taskId: String): List<TaskStepEntity>

    @Query("UPDATE task_steps SET status = :status, verified = :verified, resultJson = :resultJson WHERE idempotencyKey = :idempotencyKey")
    suspend fun updateStepStatus(
        idempotencyKey: String,
        status: TaskStatus,
        resultJson: String? = null,
        verified: Boolean = false
    )

    @Query("DELETE FROM tasks")
    suspend fun clearAll()
}
