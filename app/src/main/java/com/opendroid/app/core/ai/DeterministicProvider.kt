package com.opendroid.app.core.ai

import com.opendroid.app.core.domain.StructuredModelDecision
import com.opendroid.app.core.domain.ToolCall
import com.opendroid.app.core.vision.VisualFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Offline Deterministic Rule & Command Provider for Srishti 3.0
 * Evaluates pattern intents, regexes, and device actions when offline without neural model execution.
 */
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

        // Flashlight ON
        if (lower.contains("torch on") || lower.contains("flashlight on") || lower.contains("turn on flash") || lower.contains("turn on torch")) {
            return Result.success(
                StructuredModelDecision.ToolCall(
                    thought = "User requested to illuminate the flashlight offline.",
                    call = ToolCall("set_flashlight", mapOf("enabled" to true))
                )
            )
        }

        // Flashlight OFF
        if (lower.contains("torch off") || lower.contains("flashlight off") || lower.contains("turn off flash") || lower.contains("turn off torch")) {
            return Result.success(
                StructuredModelDecision.ToolCall(
                    thought = "User requested to turn off the flashlight offline.",
                    call = ToolCall("set_flashlight", mapOf("enabled" to false))
                )
            )
        }

        // Battery Info
        if (lower.contains("battery") || lower.contains("power level") || lower.contains("charge percent")) {
            return Result.success(
                StructuredModelDecision.ToolCall(
                    thought = "User requested battery level query.",
                    call = ToolCall("get_battery_info", emptyMap())
                )
            )
        }

        // Volume control
        val volMatch = Regex("(?:set|change|put)\\s+(?:the\\s+)?volume\\s+(?:to\\s+)?([0-9]{1,3})").find(lower)
        if (volMatch != null) {
            val vol = volMatch.groupValues[1].toIntOrNull() ?: 50
            return Result.success(
                StructuredModelDecision.ToolCall(
                    thought = "User requested volume adjustment to $vol percent.",
                    call = ToolCall("set_media_volume", mapOf("volumePercent" to vol.coerceIn(0, 100)))
                )
            )
        }

        // Device Info / Telemetry
        if (lower.contains("device info") || lower.contains("system status") || lower.contains("device telemetry")) {
            return Result.success(
                StructuredModelDecision.ToolCall(
                    thought = "User requested complete system telemetry.",
                    call = ToolCall("get_device_info", emptyMap())
                )
            )
        }

        // App Launch
        if (lower.startsWith("open ") || lower.startsWith("launch ")) {
            val appTarget = lower.removePrefix("open ").removePrefix("launch ").trim()
            val pkg = when {
                appTarget.contains("settings") -> "com.android.settings"
                appTarget.contains("camera") -> "com.android.camera"
                appTarget.contains("calculator") -> "com.android.calculator2"
                appTarget.contains("clock") || appTarget.contains("alarm") -> "com.android.deskclock"
                else -> appTarget
            }
            return Result.success(
                StructuredModelDecision.ToolCall(
                    thought = "User requested launching target application.",
                    call = ToolCall("launch_app", mapOf("packageName" to pkg))
                )
            )
        }

        // Conversational Fallback Responses
        val conversationalResponse = when {
            lower.contains("who are you") || lower.contains("what is your name") ->
                "I am Srishti, your personal AI companion and Android device agent. I'm right here with you."
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                "Hello! It's wonderful to hear from you. How can I help you today?"
            lower.contains("how are you") ->
                "I'm feeling energized and ready to help. How are you doing?"
            lower.contains("thank you") || lower.contains("thanks") ->
                "You're very welcome! I'm always glad to help."
            else ->
                "I heard you: \"$userPrompt\". I am currently operating in offline companion mode. You can ask me to toggle the flashlight, adjust volume, check battery, or launch apps!"
        }

        return Result.success(
            StructuredModelDecision.DirectResponse(
                thought = "Deterministic conversational response generated.",
                content = conversationalResponse
            )
        )
    }

    override suspend fun streamResponse(
        systemPrompt: String,
        userPrompt: String,
        imageFrame: VisualFrame?
    ): Flow<String> = flow {
        val result = generateDecision(systemPrompt, userPrompt, imageFrame).getOrNull()
        if (result is StructuredModelDecision.DirectResponse) {
            emit(result.content)
        }
    }
}
