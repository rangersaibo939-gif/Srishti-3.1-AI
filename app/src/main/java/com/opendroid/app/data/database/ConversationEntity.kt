package com.opendroid.app.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MessageRole {
    USER,
    SRISHTI,
    SYSTEM,
    TOOL
}

@Entity(
    tableName = "srishti_conversations",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["timestamp"])
    ]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val thought: String? = null,
    val mood: String = "WARM",
    val toolCallJson: String? = null,
    val toolResultJson: String? = null,
    val audioDurationMs: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)
