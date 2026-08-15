package com.opendroid.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.app.core.ai.AIProviderType
import com.opendroid.app.core.export.ProjectExporter
import com.opendroid.app.core.upgrade.SystemHealthReport
import com.opendroid.app.ui.theme.AmberGlow
import com.opendroid.app.ui.theme.Charcoal700
import com.opendroid.app.ui.theme.Charcoal800
import com.opendroid.app.ui.theme.Charcoal900
import com.opendroid.app.ui.theme.CyanAccent
import com.opendroid.app.ui.theme.EmeraldGreen
import com.opendroid.app.ui.theme.ErrorRed
import com.opendroid.app.ui.theme.TextLight
import com.opendroid.app.ui.theme.TextMuted

@Composable
fun SettingsSheet(
    activeProvider: AIProviderType,
    onSelectProvider: (AIProviderType) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearSession: () -> Unit,
    healthReport: SystemHealthReport,
    exportProgress: ProjectExporter.ExportProgress = ProjectExporter.ExportProgress(),
    onExportProject: () -> Unit = {},
    onShareZip: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var apiKeyInput by remember { mutableStateOf("") }
    var saveSuccess by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Charcoal900)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Srishti Configuration & Settings",
            color = CyanAccent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        // AI Provider Selector
        Text(
            text = "Active AI Provider Pipeline",
            color = TextLight,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        val providers = listOf(
            Pair(AIProviderType.GEMINI_CLOUD, "Gemini Cloud (Online Multi-modal)"),
            Pair(AIProviderType.LOCAL_LLAMA, "Native llama.cpp (:inference process)"),
            Pair(AIProviderType.OFFLINE_DETERMINISTIC, "Deterministic Rule Engine (Offline)")
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            providers.forEach { (type, label) ->
                val isSelected = activeProvider == type
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) CyanAccent.copy(alpha = 0.2f) else Charcoal800)
                        .clickable { onSelectProvider(type) }
                        .padding(14.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) CyanAccent else TextLight,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Gemini API Key Input (Keystore Encrypted)
        Text(
            text = "Gemini API Key (Stored in Hardware Keystore)",
            color = TextLight,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = {
                apiKeyInput = it
                saveSuccess = false
            },
            placeholder = { Text("Paste AI Studio / Gemini API Key...", color = TextMuted) },
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanAccent,
                unfocusedBorderColor = Charcoal800,
                focusedTextColor = TextLight,
                unfocusedTextColor = TextLight
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (apiKeyInput.isNotBlank()) {
                        onSaveApiKey(apiKeyInput)
                        saveSuccess = true
                        apiKeyInput = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Save Key", color = Charcoal900, fontWeight = FontWeight.Bold)
            }

            if (saveSuccess) {
                Text(text = "✓ Key saved securely", color = EmeraldGreen, fontSize = 12.sp)
            }
        }

        // Project Export Section
        Text(
            text = "Developer / Project Export",
            color = TextLight,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Charcoal800),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Export Project Archive",
                    color = CyanAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Generate a sanitized ZIP archive containing all project source files, build scripts, AIDL interfaces, and configs. Secrets and private keys are strictly stripped.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                if (exportProgress.isExporting) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = exportProgress.statusMessage,
                                color = CyanAccent,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${exportProgress.progressPercentage}%",
                                color = CyanAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { exportProgress.progressPercentage / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = CyanAccent,
                            trackColor = Charcoal700
                        )
                        if (exportProgress.totalFileCount > 0) {
                            Text(
                                text = "Files: ${exportProgress.currentFileCount} / ${exportProgress.totalFileCount}",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (exportProgress.exportedZipFile != null && !exportProgress.isExporting) {
                    Text(
                        text = "✓ ${exportProgress.statusMessage}",
                        color = EmeraldGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Archive: ${exportProgress.exportedZipFile.name}",
                        color = TextLight,
                        fontSize = 11.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onExportProject,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Re-Export ZIP", color = Charcoal900, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Button(
                            onClick = onShareZip,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Share / Save ZIP", color = Charcoal900, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                } else if (!exportProgress.isExporting) {
                    Button(
                        onClick = onExportProject,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("EXPORT PROJECT ZIP", color = Charcoal900, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // System Health Status Card
        Text(
            text = "System Health Diagnostics",
            color = TextLight,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Charcoal800),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HealthRow("Microphone (Voice)", healthReport.isMicPermissionGranted)
                HealthRow("Camera (Vision)", healthReport.isCameraPermissionGranted)
                HealthRow("Native llama.cpp library", healthReport.isNativeLlamaLibraryLoaded)
                HealthRow("Room Database", healthReport.isDatabaseFunctional)
                HealthRow("Hardware Keystore", healthReport.isKeystoreSecure)
            }
        }

        Button(
            onClick = onClearSession,
            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = "Clear Current Session Dialogue", color = ErrorRed)
        }
    }
}

@Composable
private fun HealthRow(label: String, isHealthy: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextMuted, fontSize = 13.sp)
        Text(
            text = if (isHealthy) "✓ OK" else "⚠ Inactive",
            color = if (isHealthy) EmeraldGreen else AmberGlow,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

