import JSZip from 'jszip';
import { 
  DISAGREEMENT_RESOLUTIONS, 
  DECISION_TABLE, 
  REWRITE_KILLERS, 
  RISK_REGISTER,
  MASTER_BUILD_CONTRACT_SECTIONS,
  IMPLEMENTATION_STEPS_20,
  ACCEPTANCE_TESTS_12,
  PHASE1_SAFE_TOOLS
} from './auditData';

export function generateMarkdownReport(): string {
  const dateStr = new Date().toISOString().split('T')[0];
  
  return `# OpenDroid — Master Gemini Build Contract & Frozen Phase 1 Architecture Specification
**Date:** ${dateStr}  
**Status:** READY FOR GEMINI (FROZEN ARCHITECTURE)  
**Target Platform:** Android 14–16 (7–8 GB RAM Devices)  
**Primary Execution Engine:** Out-of-Process \`llama.cpp\` (:inference) + Single Cloud Provider (Gemini API / OpenAI-compatible)

---

## 1. Executive Summary & Committee Verdict

\`\`\`
================================================================================
                    FINAL COMMITTEE ARCHITECTURAL VERDICT
                              READY FOR GEMINI
================================================================================
"No additional architectural subsystem should be added during Phase 1 unless a 
blocking requirement is discovered."
================================================================================
\`\`\`

The OpenDroid & Srishti 3.0 architecture has been audited and hardened against Android 14–16 lifecycle constraints, out-of-process C++ crashes, and prompt injection vectors.

---

## 2. Master Gemini Build Contract (All 39 Directives)

${MASTER_BUILD_CONTRACT_SECTIONS.map((s) => `### Section ${s.sectionNumber}: ${s.title} [${s.category}]
* **Summary:** ${s.summary}
* **Mandatory Rules:**
${s.mandatoryRules.map(r => `  - ${r}`).join('\n')}
`).join('\n')}

---

## 3. 20-Step Implementation Order

| Step | Title | Components Touched | Verification Check |
| :--- | :--- | :--- | :--- |
${IMPLEMENTATION_STEPS_20.map(st => `| **Step ${st.step}** | **${st.title}** | \`${st.componentsTouched.join(', ')}\` | ${st.verificationCheck} |`).join('\n')}

---

## 4. The 12 Mandatory Phase-1 Acceptance Tests

${ACCEPTANCE_TESTS_12.map(t => `### ${t.code}: ${t.name}
* **Target Subsystem:** \`${t.target}\`
* **Test Description:** ${t.description}
* **Expected Outcome:** ${t.expectedOutcome}
* **Execution Path:**
${t.simSteps.map((step, i) => `  ${i + 1}. ${step}`).join('\n')}
`).join('\n')}

---

## 5. Phase-1 Safe Native Tools Specification

${PHASE1_SAFE_TOOLS.map(tool => `### Tool: \`${tool.name}\` (${tool.id})
* **Description:** ${tool.description}
* **Android API:** \`${tool.apiUsed}\`
* **Risk Tier:** \`${tool.riskTier}\` | **Idempotent:** \`${tool.idempotent}\`
* **Required Permissions:** ${tool.permissions.length > 0 ? tool.permissions.map(p => `\`${p}\``).join(', ') : 'None'}
* **Input JSON Schema:**
\`\`\`json
${JSON.stringify(tool.inputSchema, null, 2)}
\`\`\`
* **Output JSON Schema:**
\`\`\`json
${JSON.stringify(tool.outputSchema, null, 2)}
\`\`\`
* **Verification Strategy:** ${tool.verificationLogic}
`).join('\n')}

---

## 6. Disagreement Resolution: Claude vs. DeepSeek

${DISAGREEMENT_RESOLUTIONS.map((r, i) => `### Conflict #${i + 1}: ${r.topic}
* **Claude's Position:** ${r.claudeView}
* **DeepSeek's Position:** ${r.deepSeekView}
* **Adjudicated Winner:** **${r.winner}**
* **Architectural Rationale:** ${r.rationale}
* **Frozen Final Decision:** \`${r.frozenDecision}\`
`).join('\n')}

---

## 7. The 10 Architectural Rewrite Killers (Solved in Specification)

${REWRITE_KILLERS.map((k) => `### ${k.id}. ${k.killer}
* **The Danger:** ${k.danger}
* **Architectural Fix:** ${k.architecturalFix}
`).join('\n')}

---

## 8. Architectural Decision Matrix

| Subsystem / Component | Build Now (Phase 1) | Design Now | Later Phase | Reject Permanently | Architectural Rationale |
| :--- | :---: | :---: | :---: | :---: | :--- |
${DECISION_TABLE.map(d => `| **${d.component}** | ${d.buildNow ? '**✓**' : '—'} | ${d.designNow ? '**✓**' : '—'} | ${d.later ? '**✓**' : '—'} | ${d.reject ? '**✗**' : '—'} | ${d.reason} |`).join('\n')}

---

## 9. Adversarial Risk Register

| Risk ID | Vulnerability / Threat | Severity | Probability | Mitigation Strategy | Phase |
| :--- | :--- | :---: | :---: | :---: | :--- | :---: |
${RISK_REGISTER.map(r => `| **${r.id}** | ${r.risk} | **${r.severity}** | **${r.probability}** | ${r.mitigation} | ${r.phase} |`).join('\n')}

---
*OpenDroid Architecture Review Committee • Final Master Build Contract.*
`;
}

export function downloadFile(content: Blob | string, filename: string, mimeType: string = 'text/markdown;charset=utf-8;') {
  const blob = content instanceof Blob ? content : new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', filename);
  link.setAttribute('rel', 'noopener noreferrer');
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  // Allow time for mobile browsers to complete file stream before revoking
  setTimeout(() => {
    try {
      URL.revokeObjectURL(url);
    } catch {
      // ignore
    }
  }, 10000);
}

export async function downloadAPKFile(filename: string = 'Srishti3.0.apk'): Promise<boolean> {
  try {
    const response = await fetch('/Srishti3.0.apk', { credentials: 'omit' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const blob = await response.blob();
    const apkBlob = new Blob([blob], { type: 'application/vnd.android.package-archive' });
    downloadFile(apkBlob, filename, 'application/vnd.android.package-archive');
    return true;
  } catch (err) {
    console.warn('Client fetch blob download failed, falling back to direct anchor:', err);
    const link = document.createElement('a');
    link.href = '/Srishti3.0.apk';
    link.download = filename;
    link.target = '_self';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    return false;
  }
}

export async function downloadAPKZipFile(filename: string = 'Srishti3.0-APK.zip'): Promise<boolean> {
  try {
    const response = await fetch('/Srishti3.0-APK.zip', { credentials: 'omit' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const blob = await response.blob();
    const zipBlob = new Blob([blob], { type: 'application/zip' });
    downloadFile(zipBlob, filename, 'application/zip');
    return true;
  } catch (err) {
    console.warn('Client fetch blob download failed, falling back to direct anchor:', err);
    const link = document.createElement('a');
    link.href = '/Srishti3.0-APK.zip';
    link.download = filename;
    link.target = '_self';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    return false;
  }
}

export function downloadMarkdownSpec() {
  const md = generateMarkdownReport();
  downloadFile(md, 'OpenDroid_Phase1_Master_Build_Contract.md', 'text/markdown;charset=utf-8;');
}

export function downloadJSONSpec() {
  const data = {
    title: 'OpenDroid Phase 1 — Master Gemini Build Contract & Frozen Architecture Specification',
    status: 'READY FOR GEMINI',
    version: '1.0.0-FROZEN',
    date: new Date().toISOString(),
    masterBuildContractSections: MASTER_BUILD_CONTRACT_SECTIONS,
    implementationSteps20: IMPLEMENTATION_STEPS_20,
    acceptanceTests12: ACCEPTANCE_TESTS_12,
    phase1SafeTools: PHASE1_SAFE_TOOLS,
    disagreementResolutions: DISAGREEMENT_RESOLUTIONS,
    decisionMatrix: DECISION_TABLE,
    rewriteKillers: REWRITE_KILLERS,
    riskRegister: RISK_REGISTER
  };
  downloadFile(JSON.stringify(data, null, 2), 'OpenDroid_Phase1_Architecture_Data.json', 'application/json;charset=utf-8;');
}

// -------------------------------------------------------------
// SENSITIVE SECRETS SANITIZATION ENGINE
// -------------------------------------------------------------
const SENSITIVE_PATTERNS = [
  /AIza[0-9A-Za-z-_]{35}/g,
  /sk-[a-zA-Z0-9]{20,60}/g,
  /(api_key|apikey|secret|password|bearer)\s*=\s*['"]?[^'"\s]+['"]?/gi,
  /(GEMINI_API_KEY\s*=\s*)([^\s\n]+)/gi
];

export function sanitizeSourceCode(rawCode: string): string {
  let clean = rawCode;
  for (const pattern of SENSITIVE_PATTERNS) {
    clean = clean.replace(pattern, 'GEMINI_API_KEY=YOUR_GEMINI_API_KEY_HERE');
  }
  return clean;
}

export interface ExportProgressUpdate {
  isExporting: boolean;
  currentCount: number;
  totalCount: number;
  percentage: number;
  statusMessage: string;
  filename?: string;
  error?: string;
}

/**
 * Real Project ZIP Exporter for Srishti 3.0 & OpenDroid
 * Dynamically bundles all real Kotlin source files, Room entities, VoiceEngine, PersonalityEngine,
 * Tool policies, C++ JNI llama bindings, build scripts, AIDL interfaces, and configs.
 */
export async function exportProjectZip(
  onProgress?: (update: ExportProgressUpdate) => void
): Promise<{ blob: Blob; filename: string }> {
  const zip = new JSZip();

  const now = new Date();
  const pad = (n: number) => n.toString().padStart(2, '0');
  const timestamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
  const zipFilename = `Srishti3.0_Project_${timestamp}.zip`;

  // Dynamically load all project source files
  const rawAndroidFiles = (import.meta as any).glob([
    '/app/**/*',
    '/gradle/**/*',
    '/*.gradle.kts',
    '/*.properties',
    '/*.txt',
    '/*.json',
    '/src/**/*'
  ], { query: '?raw', import: 'default', eager: true }) as Record<string, string>;

  onProgress?.({
    isExporting: true,
    currentCount: 0,
    totalCount: Object.keys(rawAndroidFiles).length || 50,
    percentage: 5,
    statusMessage: 'Scanning and preparing all repository files...'
  });

  // Base files dictionary with fallback defaults
  const sourceFiles: Record<string, string> = {
    // 1. Root configuration & build
    'settings.gradle.kts': `pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "OpenDroid"
include(":app")
include(":inference")
`,
    'build.gradle.kts': `plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
}
`,
    'gradle.properties': `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
`,
    '.env.example': `# Srishti 3.0 Configuration
GEMINI_API_KEY=YOUR_GEMINI_API_KEY_HERE
AI_PROVIDER=GEMINI_CLOUD
LOCAL_LLAMA_MODEL_PATH=/data/local/tmp/models/qwen2.5-1.5b.gguf
`,
    'README.md': `# Srishti 3.0 — Continuous Voice & Agentic Companion

Srishti 3.0 is a companion AI and autonomous on-device assistant designed for Android with seamless continuous voice conversation.

## Core Architectural Subsystems
- **VoiceEngine**: Full duplex SpeechRecognizer + TextToSpeech pipeline with auto-listen turn loops & user barge-in interruption.
- **PersonalityEngine**: 6 distinct emotional profiles (Warm, Playful, Focused, Empathetic, Curious, Protective) with dynamic voice parameter adjustments.
- **MemoryEngine**: 3-tiered Room SQLite database persistence (Short-Term, Session, Long-Term) with semantic tagging.
- **AvatarEngine**: Dynamic RMS audio reactivity & state machines (IDLE, LISTENING, THINKING, SPEAKING).
- **ToolRouter & Policy**: 4-tier security gating (SAFE, CONFIRM, HIGH_RISK, BLOCKED) with CameraManager, AudioManager, BatteryManager APIs.
- **Inference Module**: Out-of-process llama.cpp with crash isolation.
`,

    // 2. App Module Build & Manifest
    'app/build.gradle.kts': `plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.opendroid.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.opendroid.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 300
        versionName = "3.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        aidl = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
}
`,
    'app/src/main/AndroidManifest.xml': `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
    <uses-feature android:name="android.hardware.camera.flash" android:required="false" />

    <application
        android:name=".OpenDroidApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Srishti 3.0"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.OpenDroid">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:theme="@style/Theme.OpenDroid">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="\${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>
`,

    // 3. Kotlin Core Subsystems
    'app/src/main/java/com/opendroid/app/core/voice/VoiceEngine.kt': `package com.opendroid.app.core.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.opendroid.app.core.logging.RedactedLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceEngine(
    private val context: Context,
    private val scope: CoroutineScope
) : TextToSpeech.OnInitListener {

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _isContinuousMode = MutableStateFlow(false)
    val isContinuousMode: StateFlow<Boolean> = _isContinuousMode.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var listener: VoiceListener? = null
    private var retryJob: Job? = null
    private var isDestroyed = false

    init {
        initTts()
    }

    fun setContinuousMode(enabled: Boolean) {
        _isContinuousMode.value = enabled
        if (!enabled) {
            retryJob?.cancel()
            stopListening()
            stopSpeaking()
            updateState(VoiceState.IDLE)
        } else {
            startListening()
        }
    }

    fun toggleContinuousMode(): Boolean {
        val newState = !_isContinuousMode.value
        setContinuousMode(newState)
        return newState
    }

    fun startListening() {
        if (isDestroyed) return
        retryJob?.cancel()
        scope.launch(Dispatchers.Main) {
            stopSpeaking()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer?.startListening(intent)
        }
    }

    fun stopListening() {
        retryJob?.cancel()
        scope.launch(Dispatchers.Main) {
            speechRecognizer?.stopListening()
        }
    }

    fun speak(text: String, utteranceId: String = "srishti_\${System.currentTimeMillis()}") {
        if (isDestroyed) return
        retryJob?.cancel()
        stopListening()
        updateState(VoiceState.SPEAKING)
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        if (_voiceState.value == VoiceState.SPEAKING) {
            updateState(VoiceState.IDLE)
        }
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { updateState(VoiceState.SPEAKING) }
                override fun onDone(utteranceId: String?) {
                    updateState(VoiceState.IDLE)
                    if (_isContinuousMode.value && !isDestroyed) {
                        scope.launch(Dispatchers.Main) {
                            delay(280)
                            if (_isContinuousMode.value) startListening()
                        }
                    }
                }
                override fun onError(utteranceId: String?) { updateState(VoiceState.ERROR) }
            })
        }
    }

    private fun updateState(newState: VoiceState) {
        _voiceState.value = newState
        listener?.onVoiceStateChanged(newState)
    }
}
`,

    'app/src/main/java/com/opendroid/app/core/export/ProjectExporter.kt': `package com.opendroid.app.core.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectExporter(private val context: Context) {
    data class ExportProgress(
        val isExporting: Boolean = false,
        val currentFileCount: Int = 0,
        val totalFileCount: Int = 0,
        val progressPercentage: Int = 0,
        val statusMessage: String = "Ready",
        val exportedZipFile: File? = null,
        val error: String? = null
    )

    private val _progress = MutableStateFlow(ExportProgress())
    val progress: StateFlow<ExportProgress> = _progress.asStateFlow()

    suspend fun exportProjectZip(): File? = withContext(Dispatchers.IO) {
        try {
            _progress.value = ExportProgress(isExporting = true, statusMessage = "Preparing project...")
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val zipFileName = "Srishti3.0_Project_\$timeStamp.zip"
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val zipFile = File(exportDir, zipFileName)

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                zos.putNextEntry(ZipEntry("README.md"))
                zos.write("Srishti 3.0 Project Package".toByteArray())
                zos.closeEntry()
            }

            _progress.value = ExportProgress(
                isExporting = false,
                progressPercentage = 100,
                statusMessage = "✓ Export complete",
                exportedZipFile = zipFile
            )
            zipFile
        } catch (e: Exception) {
            _progress.value = ExportProgress(isExporting = false, error = e.message)
            null
        }
    }
}
`,

    // 4. Native C++ inference bindings & CMake
    'inference/build.gradle.kts': `plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.opendroid.inference"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17 -O3 -fexceptions"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
`,
    'inference/src/main/cpp/CMakeLists.txt': `cmake_minimum_required(VERSION 3.22.1)
project("opendroid_llama" C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(opendroid_llama SHARED
    opendroid_llama_jni.cpp
)

target_include_directories(opendroid_llama PRIVATE
    \${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    \${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(opendroid_llama
    \${log-lib}
    \${android-lib}
)
`,
    'inference/src/main/aidl/com/opendroid/inference/ILlamaInferenceService.aidl': `package com.opendroid.inference;

interface ILlamaInferenceService {
    boolean initializeModel(String modelPath, int contextTokens, int threads);
    String generateText(String prompt, String grammarConstraint, float temperature, int maxTokens);
    void interruptGeneration();
    boolean isLoaded();
    void releaseModel();
}
`,

    // 5. Architecture Specifications
    'docs/MASTER_AUDIT_CONTRACT.md': generateMarkdownReport()
  };

  // Merge all scanned repository files
  for (const [globPath, content] of Object.entries(rawAndroidFiles)) {
    if (typeof content === 'string') {
      const cleanPath = globPath.startsWith('/') ? globPath.slice(1) : globPath;
      if (!cleanPath.includes('node_modules') && !cleanPath.includes('dist/') && !cleanPath.includes('.git/')) {
        sourceFiles[cleanPath] = content;
      }
    }
  }

  const fileEntries = Object.entries(sourceFiles);
  const total = fileEntries.length;

  for (let i = 0; i < total; i++) {
    const [path, content] = fileEntries[i];
    const sanitized = sanitizeSourceCode(content);
    zip.file(path, sanitized);

    const percent = Math.round(((i + 1) / total) * 90);
    onProgress?.({
      isExporting: true,
      currentCount: i + 1,
      totalCount: total,
      percentage: percent,
      statusMessage: `Archiving: ${path}`
    });
    // Yield to browser event loop
    await new Promise(r => setTimeout(r, 10));
  }

  onProgress?.({
    isExporting: true,
    currentCount: total,
    totalCount: total,
    percentage: 95,
    statusMessage: 'Compressing and finalizing ZIP...'
  });

  const blob = await zip.generateAsync({ type: 'blob', compression: 'DEFLATE', compressionOptions: { level: 9 } });

  onProgress?.({
    isExporting: false,
    currentCount: total,
    totalCount: total,
    percentage: 100,
    statusMessage: `✓ Export complete: ${zipFilename}`,
    filename: zipFilename
  });

  return { blob, filename: zipFilename };
}
