package com.opendroid.app.core.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.opendroid.app.core.domain.StructuredModelDecision
import com.opendroid.app.core.domain.ToolCall
import com.opendroid.app.core.logging.RedactedLogger
import com.opendroid.app.core.vision.VisualFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Cloud Gemini AI Provider for Srishti 3.0
 * Uses Gemini 2.5 Flash with structured JSON schema output and multimodal vision support.
 */
class GeminiProvider(
    private val apiKeyProvider: () -> String?,
    private val modelName: String = "gemini-2.5-flash"
) : AIProvider {

    override val providerType: AIProviderType = AIProviderType.GEMINI_CLOUD

    override val isAvailable: Boolean
        get() = !apiKeyProvider().isNullOrBlank()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    override suspend fun generateDecision(
        systemPrompt: String,
        userPrompt: String,
        imageFrame: VisualFrame?,
        conversationHistory: List<Pair<String, String>>
    ): Result<StructuredModelDecision> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured."))
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

            // Construct contents with system instruction & optional image
            val partsList = mutableListOf<Map<String, Any>>()

            if (imageFrame != null) {
                partsList.add(
                    mapOf(
                        "inline_data" to mapOf(
                            "mime_type" to imageFrame.mimeType,
                            "data" to imageFrame.base64Data
                        )
                    )
                )
            }

            partsList.add(mapOf("text" to userPrompt))

            val requestJson = mapOf(
                "system_instruction" to mapOf(
                    "parts" to listOf(
                        mapOf(
                            "text" to """$systemPrompt
You MUST respond with valid JSON matching EXACTLY one of these two structures:
1. For tool execution:
{"type":"tool_call","thought":"reasoning","tool_name":"name","arguments":{"key":"value"}}
2. For direct conversational responses:
{"type":"direct_response","thought":"reasoning","content":"natural conversational response"}
"""
                        )
                    )
                ),
                "contents" to listOf(
                    mapOf("role" to "user", "parts" to partsList)
                ),
                "generationConfig" to mapOf(
                    "response_mime_type" to "application/json",
                    "temperature" to 0.7
                )
            )

            val body = gson.toJson(requestJson).toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                RedactedLogger.e(TAG, "Gemini API HTTP ${response.code}: $responseBody")
                return@withContext Result.failure(RuntimeException("Gemini API error: HTTP ${response.code}"))
            }

            val jsonRoot = gson.fromJson(responseBody, JsonObject::class.java)
            val textOutput = jsonRoot
                ?.getAsJsonArray("candidates")?.get(0)?.asJsonObject
                ?.getAsJsonObject("content")
                ?.getAsJsonArray("parts")?.get(0)?.asJsonObject
                ?.get("text")?.asString ?: ""

            parseStructuredDecision(textOutput)
        } catch (e: Exception) {
            RedactedLogger.e(TAG, "Gemini API call failed: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun streamResponse(
        systemPrompt: String,
        userPrompt: String,
        imageFrame: VisualFrame?
    ): Flow<String> = flow {
        // Fallback or stream representation
        val decision = generateDecision(systemPrompt, userPrompt, imageFrame).getOrNull()
        if (decision is StructuredModelDecision.DirectResponse) {
            emit(decision.content)
        } else if (decision is StructuredModelDecision.ToolCall) {
            emit("Executing action: ${decision.call.name}...")
        }
    }.flowOn(Dispatchers.IO)

    private fun parseStructuredDecision(rawJson: String): Result<StructuredModelDecision> {
        return try {
            val clean = rawJson.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val obj = gson.fromJson(clean, JsonObject::class.java)
            val type = obj.get("type")?.asString ?: "direct_response"
            val thought = obj.get("thought")?.asString ?: ""

            if (type == "tool_call") {
                val toolName = obj.get("tool_name")?.asString ?: ""
                val argsObj = obj.getAsJsonObject("arguments")
                val argsMap = mutableMapOf<String, Any?>()
                argsObj?.entrySet()?.forEach { (k, v) ->
                    argsMap[k] = if (v.isJsonPrimitive && v.asJsonPrimitive.isBoolean) {
                        v.asBoolean
                    } else if (v.isJsonPrimitive && v.asJsonPrimitive.isNumber) {
                        v.asNumber.toInt()
                    } else {
                        v.asString
                    }
                }
                Result.success(
                    StructuredModelDecision.ToolCall(
                        thought = thought,
                        call = ToolCall(name = toolName, arguments = argsMap)
                    )
                )
            } else {
                val content = obj.get("content")?.asString ?: rawJson
                Result.success(
                    StructuredModelDecision.DirectResponse(
                        thought = thought,
                        content = content
                    )
                )
            }
        } catch (e: Exception) {
            RedactedLogger.w(TAG, "Falling back to raw text response: ${e.message}")
            Result.success(
                StructuredModelDecision.DirectResponse(
                    thought = "Raw text parsing fallback",
                    content = rawJson
                )
            )
        }
    }

    companion object {
        private const val TAG = "SrishtiGeminiProvider"
    }
}
