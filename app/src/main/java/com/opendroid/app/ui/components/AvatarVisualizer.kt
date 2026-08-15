package com.opendroid.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.app.core.avatar.AvatarVisualState
import com.opendroid.app.core.voice.VoiceState

@Composable
fun AvatarVisualizer(
    avatarState: AvatarVisualState,
    modifier: Modifier = Modifier,
    onTapAvatar: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (1800 / avatarState.pulseSpeed).toInt().coerceAtLeast(400),
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val waveRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveRotation"
    )

    val glowColor = Color(avatarState.glowColorHex)

    Box(
        modifier = modifier
            .size(200.dp)
            .clickable { onTapAvatar() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val baseRadius = (size.minDimension / 2.8f)

            // Outer reactive glow ring
            val outerRadius = baseRadius * pulseScale * (1f + avatarState.audioWaveformLevel * 0.4f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor.copy(alpha = 0.35f), Color.Transparent),
                    center = center,
                    radius = outerRadius * 1.5f
                ),
                radius = outerRadius * 1.3f,
                center = center
            )

            // Secondary orbital rings
            drawCircle(
                color = glowColor.copy(alpha = 0.25f),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            drawCircle(
                color = glowColor.copy(alpha = 0.6f),
                radius = baseRadius * pulseScale,
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )

            // Inner core sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        glowColor,
                        glowColor.copy(alpha = 0.8f)
                    ),
                    center = center,
                    radius = baseRadius * 0.7f
                ),
                radius = baseRadius * 0.7f,
                center = center
            )
        }

        // State indicator text inside avatar
        Text(
            text = when (avatarState.voiceState) {
                VoiceState.LISTENING -> "Listening"
                VoiceState.THINKING -> "Thinking"
                VoiceState.SPEAKING -> "Speaking"
                VoiceState.INTERRUPTED -> "Paused"
                VoiceState.OFFLINE -> "Offline"
                else -> avatarState.expressionLabel
            },
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 110.dp)
        )
    }
}
