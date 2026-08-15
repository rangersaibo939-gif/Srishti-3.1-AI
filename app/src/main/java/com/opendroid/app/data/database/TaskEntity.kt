package com.opendroid.app.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.opendroid.app.core.domain.TaskStatus

/**
 * Durable Task Entity surviving Android process death
 */
@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class TaskEntity(
    @PrimaryKey
    val id: String,
    val userPrompt: String,
    val status: TaskStatus,
    val provider: String,
    val model: String,
    val finalResponse: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
