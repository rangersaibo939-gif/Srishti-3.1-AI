package com.opendroid.app.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MemoryTier {
    SHORT_TERM,
    SESSION,
    LONG_TERM
}

enum class MemoryCategory {
    USER_PREFERENCE,
    PERSONAL_FACT,
    INTERACTION_STYLE,
    ROUTINE,
    EMOTIONAL_CONTEXT,
    SYSTEM_INSTRUCTION
}

@Entity(
    tableName = "srishti_memories",
    indices = [
        Index(value = ["key"], unique = true),
        Index(value = ["category"]),
        Index(value = ["tier"])
    ]
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val key: String,
    val value: String,
    val category: MemoryCategory,
    val tier: MemoryTier,
    val confidence: Float = 1.0f,
    val accessCount: Int = 0,
    val lastAccessedTimestamp: Long = System.currentTimeMillis(),
    val createdTimestamp: Long = System.currentTimeMillis()
)
