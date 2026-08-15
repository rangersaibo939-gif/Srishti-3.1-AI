package com.opendroid.app.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.opendroid.app.core.domain.RiskLevel
import com.opendroid.app.core.domain.TaskStatus

/**
 * Task Step Entity with unique idempotencyKey to prevent duplicate execution
 */
@Entity(
    tableName = "task_steps",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["idempotencyKey"], unique = true)
    ]
)
data class TaskStepEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: String,
    val stepIndex: Int,
    val idempotencyKey: String,
    val toolName: String,
    val argumentsJson: String,
    val riskTier: RiskLevel,
    val status: TaskStatus,
    val verified: Boolean = false,
    val resultJson: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
