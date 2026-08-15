package com.opendroid.app.core.avatar

import com.opendroid.app.core.personality.SrishtiMood
import com.opendroid.app.core.voice.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AvatarVisualState(
    val voiceState: VoiceState = VoiceState.IDLE,
    val mood: SrishtiMood = SrishtiMood.WARM,
    val audioWaveformLevel: Float = 0f, // 0.0 to 1.0
    val breathingPhase: Float = 0f,     // 0.0 to 1.0
    val glowColorHex: Long = 0xFF38BDF8,
    val pulseSpeed: Float = 1.0f,
    val expressionLabel: String = "Serene"
)

/**
 * Avatar Engine for Srishti 3.0
 * Calculates animated visual states, reactive color halos, and breathing/speaking waveforms.
 */
class AvatarEngine {

    private val _avatarState = MutableStateFlow(AvatarVisualState())
    val avatarState: StateFlow<AvatarVisualState> = _avatarState.asStateFlow()

    fun updateVoiceAndAudio(voiceState: VoiceState, rmsLevel: Float) {
        val normalizedRms = ((rmsLevel + 2f) / 12f).coerceIn(0f, 1f)
        val current = _avatarState.value
        val (glowColor, pulseSpeed, label) = computeVisuals(voiceState, current.mood)

        _avatarState.value = current.copy(
            voiceState = voiceState,
            audioWaveformLevel = if (voiceState == VoiceState.SPEAKING || voiceState == VoiceState.LISTENING) normalizedRms else 0f,
            glowColorHex = glowColor,
            pulseSpeed = pulseSpeed,
            expressionLabel = label
        )
    }

    fun updateMood(mood: SrishtiMood) {
        val current = _avatarState.value
        val (glowColor, pulseSpeed, label) = computeVisuals(current.voiceState, mood)

        _avatarState.value = current.copy(
            mood = mood,
            glowColorHex = glowColor,
            pulseSpeed = pulseSpeed,
            expressionLabel = label
        )
    }

    private fun computeVisuals(voiceState: VoiceState, mood: SrishtiMood): Triple<Long, Float, String> {
        val baseColor = when (mood) {
            SrishtiMood.WARM -> 0xFFF59E0B       // Amber / Warm Gold
            SrishtiMood.PLAYFUL -> 0xFFEC4899    // Pink / Rose
            SrishtiMood.FOCUSED -> 0xFF38BDF8    // Cyan / Sky
            SrishtiMood.EMPATHETIC -> 0xFF10B981 // Emerald / Sage
            SrishtiMood.CURIOUS -> 0xFF8B5CF6    // Violet / Purple
            SrishtiMood.PROTECTIVE -> 0xFF6366F1 // Indigo / Cobalt
        }

        val (color, speed, label) = when (voiceState) {
            VoiceState.LISTENING -> Triple(0xFF10B981, 1.8f, "Listening Intently")
            VoiceState.THINKING -> Triple(0xFF8B5CF6, 2.5f, "Reflecting & Reasoning")
            VoiceState.SPEAKING -> Triple(baseColor, 1.5f, "Speaking with Care")
            VoiceState.INTERRUPTED -> Triple(0xFFF97316, 0.8f, "Attentive & Paused")
            VoiceState.ERROR -> Triple(0xFFEF4444, 0.5f, "Needs Attention")
            VoiceState.OFFLINE -> Triple(0xFF64748B, 0.5f, "Offline Mode Active")
            VoiceState.IDLE -> Triple(baseColor, 1.0f, mood.name.lowercase().replaceFirstChar { it.uppercase() })
        }

        return Triple(color, speed, label)
    }
}
