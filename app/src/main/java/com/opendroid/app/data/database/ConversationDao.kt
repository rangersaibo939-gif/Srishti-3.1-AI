package com.opendroid.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationEntity): Long

    @Query("SELECT * FROM srishti_conversations WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSessionFlow(sessionId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM srishti_conversations WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesForSession(sessionId: String, limit: Int = 20): List<ConversationEntity>

    @Query("SELECT DISTINCT sessionId FROM srishti_conversations ORDER BY timestamp DESC")
    fun getAllSessionIdsFlow(): Flow<List<String>>

    @Query("DELETE FROM srishti_conversations WHERE sessionId = :sessionId")
    suspend fun deleteSessionMessages(sessionId: String)

    @Query("DELETE FROM srishti_conversations")
    suspend fun clearAll()
}
