package com.opendroid.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.app.core.personality.SrishtiMood
import com.opendroid.app.ui.theme.AmberGlow
import com.opendroid.app.ui.theme.Charcoal700
import com.opendroid.app.ui.theme.Charcoal800
import com.opendroid.app.ui.theme.Charcoal900
import com.opendroid.app.ui.theme.CyanAccent
import com.opendroid.app.ui.theme.EmeraldGreen
import com.opendroid.app.ui.theme.PurpleGlow
import com.opendroid.app.ui.theme.RosePink
import com.opendroid.app.ui.theme.TextLight
import com.opendroid.app.ui.theme.TextMuted

@Composable
fun PersonalitySheet(
    currentMood: SrishtiMood,
    onSelectMood: (SrishtiMood) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Charcoal900)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Personality & Mood Calibration",
            color = CyanAccent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Adjust Srishti's emotional demeanor, warmth, and conversational style.",
            color = TextMuted,
            fontSize = 13.sp
        )

        val moods = listOf(
            Triple(SrishtiMood.WARM, "Warm & Caring", AmberGlow),
            Triple(SrishtiMood.PLAYFUL, "Playful & Fun", RosePink),
            Triple(SrishtiMood.FOCUSED, "Focused & Direct", CyanAccent),
            Triple(SrishtiMood.EMPATHETIC, "Deep Empathy", EmeraldGreen),
            Triple(SrishtiMood.CURIOUS, "Curious Explorer", PurpleGlow),
            Triple(SrishtiMood.PROTECTIVE, "Protective Agent", CyanAccent)
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            moods.chunked(2).forEach { rowMoods ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowMoods.forEach { (mood, label, color) ->
                        val isSelected = currentMood == mood
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) color.copy(alpha = 0.25f) else Charcoal800)
                                .clickable { onSelectMood(mood) }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = mood.name,
                                    color = if (isSelected) color else TextLight,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = label,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
