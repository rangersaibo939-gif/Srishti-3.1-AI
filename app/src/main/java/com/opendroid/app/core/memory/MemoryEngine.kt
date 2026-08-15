package com.opendroid.app.core.memory

import com.opendroid.app.data.database.MemoryCategory
import com.opendroid.app.data.database.MemoryDao
import com.opendroid.app.data.database.MemoryEntity
import com.opendroid.app.data.database.MemoryTier
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentLinkedDeque

data class ShortTermMemoryItem(
    val role: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Three-Tiered Memory Engine for Srishti 3.0:
 * 1. Short-Term Memory: Fast volatile rolling window buffer (last N turns)
 * 2. Session Memory: Ephemeral dialogue turn memory for current session
 * 3. Long-Term Memory: SQLite-persisted facts, learned preferences, routines, and emotional context
 */
class MemoryEngine(private val memoryDao: MemoryDao) {

    private val shortTermBuffer = ConcurrentLinkedDeque<ShortTermMemoryItem>()
    private val maxShortTermItems = 10

    fun addShortTermTurn(role: String, text: String) {
        shortTermBuffer.addLast(ShortTermMemoryItem(role, text))
        while (shortTermBuffer.size > maxShortTermItems) {
            shortTermBuffer.pollFirst()
        }
    }

    fun getShortTermContext(): List<ShortTermMemoryItem> {
        return shortTermBuffer.toList()
    }

    fun clearShortTermContext() {
        shortTermBuffer.clear()
    }

    fun getAllMemoriesFlow(): Flow<List<MemoryEntity>> {
        return memoryDao.getAllMemoriesFlow()
    }

    suspend fun recordLearnedMemory(
        key: String,
        value: String,
        category: MemoryCategory = MemoryCategory.PERSONAL_FACT,
        tier: MemoryTier = MemoryTier.LONG_TERM,
        confidence: Float = 1.0f
    ): Long {
        val existing = memoryDao.getMemoryByKey(key)
        val entity = existing?.copy(
            value = value,
            category = category,
            tier = tier,
            confidence = confidence,
            lastAccessedTimestamp = System.currentTimeMillis()
        ) ?: MemoryEntity(
            key = key,
            value = value,
            category = category,
            tier = tier,
            confidence = confidence
        )
        return memoryDao.insertOrUpdateMemory(entity)
    }

    /**
     * Retrieves relevant long-term memories matching tokens in user prompt
     */
    suspend fun retrieveRelevantContext(prompt: String): String {
        val tokens = prompt.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length > 3 }

        val allMemories = memoryDao.getAllMemories()
        if (allMemories.isEmpty()) return ""

        val matched = allMemories.filter { mem ->
            val memText = "${mem.key} ${mem.value}".lowercase()
            tokens.any { token -> memText.contains(token) }
        }.take(5)

        matched.forEach { memoryDao.recordAccess(it.id) }

        if (matched.isEmpty()) {
            // Default to top 3 most frequently accessed/relevant preferences
            val topPrefs = allMemories.sortedByDescending { it.accessCount }.take(3)
            return topPrefs.joinToString("\n") { "- ${it.key}: ${it.value}" }
        }

        return matched.joinToString("\n") { "- ${it.key}: ${it.value}" }
    }

    suspend fun deleteMemory(id: Long) {
        memoryDao.deleteMemoryById(id)
    }

    suspend fun clearAll() {
        shortTermBuffer.clear()
        memoryDao.clearAll()
    }
}
