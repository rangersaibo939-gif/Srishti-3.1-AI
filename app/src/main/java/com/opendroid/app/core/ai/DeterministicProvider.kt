package com.opendroid.app.core.ai

import com.opendroid.app.core.domain.StructuredModelDecision
import com.opendroid.app.core.domain.ToolCallRequest
import com.opendroid.app.core.vision.VisualFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Offline deterministic provider for common device commands and fallback chat. */
class DeterministicProvider : AIProvider {
    override val providerType: AIProviderType = AIProviderType.OFFLINE_DETERMINISTIC
    override val isAvailable: Boolean = true

    override suspend fun generateDecision(
        systemPrompt: String,
        userPrompt: String,
        imageFrame: VisualFrame?,
        conversationHistory: List<Pair<String, String>>
    ): Result<StructuredModelDecision> {
        val lower = userPrompt.trim().lowercase()

        fun tool(thought: String, name: String, args: Map<String, Any?> = emptyMap()) =
            Result.success(
                StructuredModelDecision.ToolCall(
                    thought = thought,
                    toolCall = ToolCallRequest(name = name, arguments = args)
                )
            )

        if (lower.contains("torch on") || lower.contains("flashlight on") || lower.contains("turn on flash") || lower.contains("turn on torch"))
            return tool("User requested to illuminate the flashlight offline.", "set_flashlight", mapOf("enabled" to true))

        if (lower.contains("torch off") || lower.contains("flashlight off") || lower.contains("turn off flash") || lower.contains("turn off torch"))
            return tool("User requested to turn off the flashlight offline.", "set_flashlight", mapOf("enabled" to false))

        if (lower.contains("battery") || lower.contains("power level") || lower.contains("charge percent"))
            return tool("User requested battery level query.", "get_battery_info")

        val volMatch = Regex("(?:set|change|put)\\s+(?:the\\s+)?volume\\s+(?:to\\s+)?([0-9]{1,3})").find(lower)
        if (volMatch != null) {
            val vol = (volMatch.groupValues[1].toIntOrNull() ?: 50).coerceIn(0, 100)
            return tool("User requested volume adjustment to $vol percent.", "set_media_volume", mapOf("volumePercent" to vol))
        }

        if (lower.contains("device info") || lower.contains("system status") || lower.contains("device telemetry"))
            return tool("User requested complete system telemetry.", "get_device_info")

        if (lower.startsWith("open ") || lower.startsWith("launch ")) {
            val target = lower.removePrefix("open ").removePrefix("launch ").trim()
            val pkg = when {
                target.contains("settings") -> "com.android.settings"
                target.contains("camera") -> "com.android.camera"
                target.contains("calculator") -> "com.android.calculator2"
                target.contains("clock") || target.contains("alarm") -> "com.android.deskclock"
                else -> target
            }
            return tool("User requested launching target application.", "launch_app", mapOf("packageName" to pkg))
        }

        val response = when {
            lower.contains("who are you") || lower.contains("what is your name") ->
                "I am Srishti, your personal AI companion and Android device agent. I'm right here with you."
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                "Hello! It's wonderful to hear from you. How can I help you today?"
            lower.contains("how are you") ->
                "I'm feeling energized and ready to help. How are you doing?"
            lower.contains("thank you") || lower.contains("thanks") ->
                "You're very welcome! I'm always glad to help."
            else -> "I heard you: \"$userPrompt\". I am currently operating in offline companion mode."
        }

        return Result.success(StructuredModelDecision.DirectResponse(thought = "Deterministic conversational response generated.", response = response))
    }

    override suspend fun streamResponse(
        systemPrompt: String,
        userPrompt: String,
        imageFrame: VisualFrame?
    ): Flow<String> = flow {
        val result = generateDecision(systemPrompt, userPrompt, imageFrame).getOrNull()
        if (result is StructuredModelDecision.DirectResponse) emit(result.response)
    }
}
