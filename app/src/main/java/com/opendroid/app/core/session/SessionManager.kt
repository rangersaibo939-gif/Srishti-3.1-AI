package com.opendroid.app.core.session

import com.opendroid.app.data.database.ConversationDao
import com.opendroid.app.data.database.ConversationEntity
import com.opendroid.app.data.database.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Session Manager for Srishti 3.0
 * Manages active session lifecycle, persistent conversation logging, and turn history.
 */
class SessionManager(private val conversationDao: ConversationDao) {

    private val _currentSessionId = MutableStateFlow(UUID.randomUUID().toString())
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    fun startNewSession(): String {
        val newId = UUID.randomUUID().toString()
        _currentSessionId.value = newId
        return newId
    }

    fun switchSession(sessionId: String) {
        _currentSessionId.value = sessionId
    }

    fun getActiveMessagesFlow(): Flow<List<ConversationEntity>> {
        return conversationDao.getMessagesForSessionFlow(_currentSessionId.value)
    }

    suspend fun logTurn(
        role: MessageRole,
        content: String,
        thought: String? = null,
        mood: String = "WARM",
        toolCallJson: String? = null,
        toolResultJson: String? = null
    ): Long {
        val entity = ConversationEntity(
            sessionId = _currentSessionId.value,
            role = role,
            content = content,
            thought = thought,
            mood = mood,
            toolCallJson = toolCallJson,
            toolResultJson = toolResultJson
        )
        return conversationDao.insertMessage(entity)
    }

    suspend fun getRecentTurns(limit: Int = 10): List<ConversationEntity> {
        return conversationDao.getRecentMessagesForSession(_currentSessionId.value, limit).reversed()
    }

    suspend fun clearCurrentSession() {
        conversationDao.deleteSessionMessages(_currentSessionId.value)
    }
}
