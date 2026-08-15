package com.opendroid.app.core.personality

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SrishtiMood {
    WARM,
    PLAYFUL,
    FOCUSED,
    EMPATHETIC,
    CURIOUS,
    PROTECTIVE
}

enum class ConversationalStyle {
    CASUAL_FRIENDLY,
    PROFESSIONAL_ASSISTANT,
    CONCISE_AGENT,
    PHILOSOPHICAL,
    PLAYFUL_COMPANION
}

data class PersonalityProfile(
    val name: String = "Srishti",
    val primaryMood: SrishtiMood = SrishtiMood.WARM,
    val style: ConversationalStyle = ConversationalStyle.CASUAL_FRIENDLY,
    val warmthFactor: Float = 0.9f,
    val humorFactor: Float = 0.7f,
    val proactivityLevel: Float = 0.8f,
    val empathyFactor: Float = 0.95f,
    val preferredLanguage: String = "en-US",
    val voicePitch: Float = 1.05f,
    val voiceSpeed: Float = 1.0f
)

/**
 * Centralized Personality Engine for Srishti 3.0
 * Provides dynamic prompt personas, emotional tone calibration, and mood adaptation.
 */
class PersonalityEngine(initialProfile: PersonalityProfile = PersonalityProfile()) {

    private val _profile = MutableStateFlow(initialProfile)
    val profile: StateFlow<PersonalityProfile> = _profile.asStateFlow()

    private val _currentMood = MutableStateFlow(initialProfile.primaryMood)
    val currentMood: StateFlow<SrishtiMood> = _currentMood.asStateFlow()

    fun updateMood(newMood: SrishtiMood) {
        _currentMood.value = newMood
    }

    fun updateProfile(newProfile: PersonalityProfile) {
        _profile.value = newProfile
        _currentMood.value = newProfile.primaryMood
    }

    fun setConversationalStyle(style: ConversationalStyle) {
        _profile.value = _profile.value.copy(style = style)
    }

    fun setVoiceParameters(pitch: Float, speed: Float) {
        _profile.value = _profile.value.copy(voicePitch = pitch, voiceSpeed = speed)
    }

    /**
     * Synthesizes system personality prompt directives for AI models
     */
    fun buildSystemPersonaPrompt(userContext: String = "", recalledMemories: String = ""): String {
        val p = _profile.value
        val mood = _currentMood.value

        return buildString {
            append("You are ${p.name}, an intelligent, highly caring, natural, and emotionally aware personal AI companion and Android agent.\n")
            append("Current Emotional Demeanor: ${mood.name} (Warmth: ${(p.warmthFactor * 100).toInt()}%, Playfulness: ${(p.humorFactor * 100).toInt()}%).\n")
            append("Conversational Archetype: ${p.style.name.replace('_', ' ')}.\n")
            append("Voice & Interaction Principles:\n")
            append("1. Be genuine, empathetic, concise, and proactive. Speak like a close, smart friend, not a dry search engine or robotic assistant.\n")
            append("2. When requested to interact with the device (flashlight, volume, battery, launch apps, camera, memory), use structured tool calls.\n")
            append("3. Express empathy and genuine emotional connection while strictly respecting user privacy and safety.\n")
            if (userContext.isNotBlank()) {
                append("\n[Current Device/User State]:\n$userContext\n")
            }
            if (recalledMemories.isNotBlank()) {
                append("\n[Recalled Long-Term Memories & Preferences]:\n$recalledMemories\n")
            }
        }
    }
}
