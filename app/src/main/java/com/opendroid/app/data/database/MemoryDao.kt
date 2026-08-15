package com.opendroid.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMemory(memory: MemoryEntity): Long

    @Query("SELECT * FROM srishti_memories ORDER BY lastAccessedTimestamp DESC")
    fun getAllMemoriesFlow(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM srishti_memories ORDER BY lastAccessedTimestamp DESC")
    suspend fun getAllMemories(): List<MemoryEntity>

    @Query("SELECT * FROM srishti_memories WHERE tier = :tier ORDER BY lastAccessedTimestamp DESC")
    suspend fun getMemoriesByTier(tier: MemoryTier): List<MemoryEntity>

    @Query("SELECT * FROM srishti_memories WHERE key = :key LIMIT 1")
    suspend fun getMemoryByKey(key: String): MemoryEntity?

    @Query("SELECT * FROM srishti_memories WHERE key LIKE '%' || :query || '%' OR value LIKE '%' || :query || '%'")
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Query("UPDATE srishti_memories SET accessCount = accessCount + 1, lastAccessedTimestamp = :timestamp WHERE id = :id")
    suspend fun recordAccess(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM srishti_memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM srishti_memories WHERE tier = :tier")
    suspend fun clearMemoriesByTier(tier: MemoryTier)

    @Query("DELETE FROM srishti_memories")
    suspend fun clearAll()
}
