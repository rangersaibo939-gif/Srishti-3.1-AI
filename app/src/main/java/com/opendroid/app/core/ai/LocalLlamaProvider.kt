package com.opendroid.app.core.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.opendroid.app.core.domain.StructuredModelDecision
import com.opendroid.app.core.domain.ToolCall
import com.opendroid.app.core.inference.InferenceClient
import com.opendroid.app.core.logging.RedactedLogger
import com.opendroid.app.core.vision.VisualFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCancellableCoroutine

/**
 * On-Device Native llama.cpp Provider for Srishti 3.0
 * Connects over AIDL to the isolated :inference process running native GBNF grammar.
 */
class LocalLlamaProvider(private val inferenceClient: InferenceClient) : AIProvider {

    override val providerType: AIProviderType = AIProviderType.LOCAL_LLAMA

    override val isAvailable: Boolean
        get() = inferenceClient.isBound

    private val gson = Gson()

    override suspend fun generateDecision(
        systemPrompt: String,
        userPrompt: String,
        imageFrame: VisualFrame?,
        conversationHistory: List<Pair<String, String>>
    ): Result<StructuredModelDecision> {
        return suspendCancellableCoroutine { continuation ->
            val formattedPrompt = buildString {
                append("<|im_start|>system\n$systemPrompt<|im_end|>\n")
                conversationHistory.forEach { (role, msg) ->
                    append("<|im_start|>$role\n$msg<|im_end|>\n")
                }
                append("<|im_start|>user\n$userPrompt<|im_end|>\n")
                append("<|im_start|>assistant\n")
            }

            inferenceClient.runInference(
                prompt = formattedPrompt,
                grammar = TOOL_DECISION_GBNF,
                onToken = {},
                onComplete = { fullOutput ->
                    val decision = parseDecision(fullOutput)
                    continuation.resume(decision)
                },
                onError = { err ->
                    RedactedLogger.e(TAG, "Local inference error: $err")
                    continuation.resume(Result.failure(RuntimeException(err)))
                }
            )
        }
    }

    override suspend fun streamResponse(
        systemPrompt: String,
        userPrompt: String,
        imageFrame: VisualFrame?
    ): Flow<String> = flow {
        val result = generateDecision(systemPrompt, userPrompt, imageFrame).getOrNull()
        if (result is StructuredModelDecision.DirectResponse) {
            emit(result.content)
        }
    }

    private fun parseDecision(rawJson: String): Result<StructuredModelDecision> {
        return try {
            val clean = rawJson.trim()
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
            Result.success(
                StructuredModelDecision.DirectResponse(
                    thought = "Raw text fallback",
                    content = rawJson
                )
            )
        }
    }

    companion object {
        private const val TAG = "SrishtiLocalLlamaProvider"

        const val TOOL_DECISION_GBNF = """
root ::= "{" ws "\"type\"" ws ":" ws ("\"tool_call\"" ws "," ws "\"thought\"" ws ":" ws string ws "," ws "\"tool_name\"" ws ":" ws string ws "," ws "\"arguments\"" ws ":" ws object | "\"direct_response\"" ws "," ws "\"thought\"" ws ":" ws string ws "," ws "\"content\"" ws ":" ws string) ws "}"
object ::= "{" ws (string ws ":" ws value (ws "," ws string ws ":" ws value)*)? ws "}"
value ::= string | number | "true" | "false" | "null"
string ::= "\"" [^\"]* "\""
number ::= "-"? [0-9]+ ("." [0-9]+)?
ws ::= [ \t\n\r]*
"""
    }
}
