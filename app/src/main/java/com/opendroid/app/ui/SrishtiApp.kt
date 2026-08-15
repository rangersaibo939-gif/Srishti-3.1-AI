package com.opendroid.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.app.core.voice.VoiceState
import com.opendroid.app.ui.components.AvatarVisualizer
import com.opendroid.app.ui.components.ConversationView
import com.opendroid.app.ui.components.MemoryDrawer
import com.opendroid.app.ui.components.PersonalitySheet
import com.opendroid.app.ui.components.SettingsSheet
import com.opendroid.app.ui.components.ToolsSheet
import com.opendroid.app.ui.components.VoiceStatusBar
import com.opendroid.app.ui.theme.AmberGlow
import com.opendroid.app.ui.theme.Charcoal800
import com.opendroid.app.ui.theme.Charcoal900
import com.opendroid.app.ui.theme.CyanAccent
import com.opendroid.app.ui.theme.EmeraldGreen
import com.opendroid.app.ui.theme.ErrorRed
import com.opendroid.app.ui.theme.TextLight
import com.opendroid.app.ui.theme.TextMuted
import kotlinx.coroutines.launch

enum class ActiveSheet {
    NONE,
    PERSONALITY,
    MEMORY,
    TOOLS,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SrishtiApp(viewModel: SrishtiViewModel) {
    val avatarState by viewModel.avatarState.collectAsState()
    val currentMood by viewModel.currentMood.collectAsState()
    val activeProvider by viewModel.activeProviderType.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val isContinuousMode by viewModel.isContinuousMode.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val memories by viewModel.memories.collectAsState()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()
    val healthReport by viewModel.healthReport.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var textInput by remember { mutableStateOf("") }
    var activeSheet by remember { mutableStateOf(ActiveSheet.NONE) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Charcoal900,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isContinuousMode) EmeraldGreen else CyanAccent)
                    )
                    Text(
                        text = "SRISHTI 3.0",
                        color = TextLight,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // Emergency Stop Button
                FloatingActionButton(
                    onClick = { viewModel.triggerEmergencyStop() },
                    containerColor = ErrorRed,
                    contentColor = TextLight,
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Emergency Stop",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Charcoal900)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Secondary action buttons (Personality, Memory, Tools, Settings)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { activeSheet = ActiveSheet.PERSONALITY }) {
                        Icon(Icons.Default.Face, contentDescription = "Personality", tint = AmberGlow)
                    }
                    IconButton(onClick = { activeSheet = ActiveSheet.MEMORY }) {
                        Icon(Icons.Default.Psychology, contentDescription = "Memory Vault", tint = CyanAccent)
                    }
                    IconButton(onClick = { activeSheet = ActiveSheet.TOOLS }) {
                        Icon(Icons.Default.Build, contentDescription = "Android Tools", tint = EmeraldGreen)
                    }
                    IconButton(onClick = { activeSheet = ActiveSheet.SETTINGS }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextMuted)
                    }
                }

                // Input bar & Voice trigger
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Talk with Srishti or command device...", color = TextMuted, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = Charcoal800,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            focusedContainerColor = Charcoal800,
                            unfocusedContainerColor = Charcoal800
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f),
                        maxLines = 3
                    )

                    if (textInput.isNotBlank()) {
                        IconButton(
                            onClick = {
                                viewModel.executeUserPrompt(textInput)
                                textInput = ""
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(CyanAccent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = Charcoal900
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                viewModel.toggleContinuousVoice()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (isContinuousMode) EmeraldGreen else Charcoal800)
                        ) {
                            Icon(
                                imageVector = if (isContinuousMode) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = if (isContinuousMode) "Stop Continuous" else "Start Continuous",
                                tint = if (isContinuousMode) Charcoal900 else CyanAccent
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status bar
            VoiceStatusBar(
                voiceState = voiceState,
                providerType = activeProvider,
                currentMood = currentMood,
                isContinuousMode = isContinuousMode,
                onTapContinuous = { viewModel.toggleContinuousVoice() },
                onTapMood = { activeSheet = ActiveSheet.PERSONALITY },
                onTapProvider = { activeSheet = ActiveSheet.SETTINGS }
            )

            // Animated Avatar
            AvatarVisualizer(
                avatarState = avatarState,
                modifier = Modifier.padding(vertical = 4.dp),
                onTapAvatar = {
                    viewModel.toggleContinuousVoice()
                }
            )

            // Prominent Continuous Voice Action Button
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (isContinuousMode) EmeraldGreen.copy(alpha = 0.2f) else Charcoal800)
                    .clickable { viewModel.toggleContinuousVoice() }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isContinuousMode) EmeraldGreen else CyanAccent)
                )
                Text(
                    text = if (isContinuousMode) "🔴 STOP CONTINUOUS" else "🎙 START CONTINUOUS",
                    color = if (isContinuousMode) EmeraldGreen else CyanAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            // Error banner if any
            if (!errorMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ErrorRed.copy(alpha = 0.2f))
                        .padding(8.dp)
                ) {
                    Text(text = "Notice: $errorMessage", color = ErrorRed, fontSize = 12.sp)
                }
            }

            // Conversation history & real-time cards
            ConversationView(
                messages = messages,
                pendingConfirmation = pendingConfirmation,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // Sheets
    if (activeSheet != ActiveSheet.NONE) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = ActiveSheet.NONE },
            sheetState = sheetState,
            containerColor = Charcoal900
        ) {
            when (activeSheet) {
                ActiveSheet.PERSONALITY -> PersonalitySheet(
                    currentMood = currentMood,
                    onSelectMood = {
                        viewModel.setMood(it)
                        scope.launch { sheetState.hide() }.invokeOnCompletion { activeSheet = ActiveSheet.NONE }
                    }
                )
                ActiveSheet.MEMORY -> MemoryDrawer(
                    memories = memories,
                    onDeleteMemory = { viewModel.deleteMemory(it) }
                )
                ActiveSheet.TOOLS -> ToolsSheet(
                    onTriggerToolPrompt = { prompt ->
                        viewModel.executeUserPrompt(prompt)
                        scope.launch { sheetState.hide() }.invokeOnCompletion { activeSheet = ActiveSheet.NONE }
                    }
                )
                ActiveSheet.SETTINGS -> SettingsSheet(
                    activeProvider = activeProvider,
                    onSelectProvider = { viewModel.setAIProvider(it) },
                    onSaveApiKey = { viewModel.saveApiKey(it) },
                    onClearSession = {
                        viewModel.clearSession()
                        scope.launch { sheetState.hide() }.invokeOnCompletion { activeSheet = ActiveSheet.NONE }
                    },
                    healthReport = healthReport,
                    exportProgress = exportProgress,
                    onExportProject = {
                        viewModel.exportProjectZip()
                    },
                    onShareZip = {
                        exportProgress.exportedZipFile?.let { file ->
                            val shareIntent = viewModel.getShareIntent(file)
                            if (shareIntent != null) {
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Srishti 3.0 Project Archive"))
                            }
                        }
                    }
                )
                ActiveSheet.NONE -> {}
            }
        }
    }
}
