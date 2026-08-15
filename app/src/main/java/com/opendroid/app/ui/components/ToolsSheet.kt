package com.opendroid.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.app.core.domain.RiskLevel
import com.opendroid.app.core.tools.ToolRegistry
import com.opendroid.app.ui.theme.AmberGlow
import com.opendroid.app.ui.theme.Charcoal800
import com.opendroid.app.ui.theme.Charcoal900
import com.opendroid.app.ui.theme.CyanAccent
import com.opendroid.app.ui.theme.EmeraldGreen
import com.opendroid.app.ui.theme.ErrorRed
import com.opendroid.app.ui.theme.TextLight
import com.opendroid.app.ui.theme.TextMuted

@Composable
fun ToolsSheet(
    onTriggerToolPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val definitions = ToolRegistry.getAllDefinitions()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Charcoal900)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Android System Tools & Capabilities",
            color = CyanAccent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Hardware tools and system APIs registered under Srishti's risk policy.",
            color = TextMuted,
            fontSize = 13.sp
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(definitions) { def ->
                val tool = ToolRegistry.getTool(def.name)
                val risk = tool?.riskTier ?: RiskLevel.SAFE

                Card(
                    colors = CardDefaults.cardColors(containerColor = Charcoal800),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = def.name,
                                    color = TextLight,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            when (risk) {
                                                RiskLevel.SAFE -> EmeraldGreen.copy(alpha = 0.2f)
                                                RiskLevel.CONFIRM -> AmberGlow.copy(alpha = 0.2f)
                                                RiskLevel.HIGH_RISK -> ErrorRed.copy(alpha = 0.2f)
                                                RiskLevel.BLOCKED -> ErrorRed.copy(alpha = 0.4f)
                                            }
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = risk.name,
                                            color = when (risk) {
                                                RiskLevel.SAFE -> EmeraldGreen
                                                RiskLevel.CONFIRM -> AmberGlow
                                                RiskLevel.HIGH_RISK -> ErrorRed
                                                RiskLevel.BLOCKED -> ErrorRed
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                            }

                            Text(
                                text = def.description,
                                color = TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Button(
                            onClick = {
                                val testPrompt = when (def.name) {
                                    "set_flashlight" -> "Turn on the flashlight"
                                    "get_battery_info" -> "Check the battery level"
                                    "set_media_volume" -> "Set media volume to 60%"
                                    "get_device_info" -> "Show device telemetry"
                                    "launch_app" -> "Open settings"
                                    else -> "Run ${def.name}"
                                }
                                onTriggerToolPrompt(testPrompt)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(text = "Test", color = CyanAccent, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
