package com.opendroid.app.core.ai

import com.opendroid.app.core.domain.StructuredModelDecision
import com.opendroid.app.core.vision.VisualFrame
import kotlinx.coroutines.flow.Flow

enum class AIProviderType {
    GEMINI_CLOUD,
    LOCAL_LLAMA,
    OFFLINE_DETERMINISTIC
}

interface AIProvider {
    val providerType: AIProviderType
    val isAvailable: Boolean

    suspend fun generateDecision(
        systemPrompt: String,
        userPrompt: String,
        imageFrame: VisualFrame? = null,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): Result<StructuredModelDecision>

    suspend fun streamResponse(
        systemPrompt: String,
        userPrompt: String,
        imageFrame: VisualFrame? = null
    ): Flow<String>
}
