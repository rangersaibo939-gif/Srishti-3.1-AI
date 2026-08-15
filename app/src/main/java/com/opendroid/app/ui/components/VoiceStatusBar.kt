package com.opendroid.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.app.core.ai.AIProviderType
import com.opendroid.app.core.personality.SrishtiMood
import com.opendroid.app.core.voice.VoiceState
import com.opendroid.app.ui.theme.AmberGlow
import com.opendroid.app.ui.theme.Charcoal700
import com.opendroid.app.ui.theme.CyanAccent
import com.opendroid.app.ui.theme.EmeraldGreen
import com.opendroid.app.ui.theme.ErrorRed
import com.opendroid.app.ui.theme.PurpleGlow

@Composable
fun VoiceStatusBar(
    voiceState: VoiceState,
    providerType: AIProviderType,
    currentMood: SrishtiMood,
    isContinuousMode: Boolean = false,
    onTapContinuous: () -> Unit = {},
    onTapMood: () -> Unit = {},
    onTapProvider: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Voice State Chip
        val (stateColor, stateLabel) = when (voiceState) {
            VoiceState.IDLE -> if (isContinuousMode) Pair(EmeraldGreen, "Continuous Ready") else Pair(CyanAccent, "Ready")
            VoiceState.LISTENING -> Pair(EmeraldGreen, "Listening...")
            VoiceState.THINKING -> Pair(PurpleGlow, "Thinking...")
            VoiceState.SPEAKING -> Pair(AmberGlow, "Speaking...")
            VoiceState.INTERRUPTED -> Pair(AmberGlow, "Interrupted")
            VoiceState.ERROR -> Pair(ErrorRed, "Voice Error")
            VoiceState.OFFLINE -> Pair(Color.Gray, "Offline")
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Charcoal700.copy(alpha = 0.7f))
                .clickable { onTapContinuous() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(stateColor)
            )
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = stateColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stateLabel,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Provider Badge
        val (providerIcon, providerText) = when (providerType) {
            AIProviderType.GEMINI_CLOUD -> Pair(Icons.Default.Cloud, "Gemini 2.5")
            AIProviderType.LOCAL_LLAMA -> Pair(Icons.Default.Bolt, "Native Llama")
            AIProviderType.OFFLINE_DETERMINISTIC -> Pair(Icons.Default.Psychology, "Deterministic")
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Charcoal700.copy(alpha = 0.7f))
                .clickable { onTapProvider() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = providerIcon,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = providerText,
                color = CyanAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Mood Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(AmberGlow.copy(alpha = 0.2f))
                .clickable { onTapMood() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = currentMood.name,
                color = AmberGlow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
