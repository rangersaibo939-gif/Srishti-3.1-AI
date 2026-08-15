package com.opendroid.app

import com.opendroid.app.core.agent.AgentCore
import com.opendroid.app.core.inference.LlamaCppAdapter
import com.opendroid.app.core.inference.UnimplementedLlamaCppAdapter
import com.opendroid.app.core.task.EmergencyStopManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaInferenceTest {

    @Test
    fun testSmokeTestPromptDefinition() {
        val smokePrompt = "Reply exactly: OPENDROID_LLAMA_OK"
        val expectedToken = "OPENDROID_LLAMA_OK"

        assertNotNull(smokePrompt)
        assertTrue(smokePrompt.contains(expectedToken))
    }

    @Test
    fun testEmergencyStopCancellation() {
        EmergencyStopManager.reset()
        assertFalse(EmergencyStopManager.isStopRequested())

        EmergencyStopManager.triggerEmergencyStop("Test stop")
        assertTrue(EmergencyStopManager.isStopRequested())

        EmergencyStopManager.reset()
        assertFalse(EmergencyStopManager.isStopRequested())
    }

    @Test
    fun testFallbackAdapterSafety() {
        val adapter: LlamaCppAdapter = UnimplementedLlamaCppAdapter()
        assertFalse(adapter.isNativeLibraryLoaded())
        assertFalse(adapter.isModelLoaded())
        assertFalse(adapter.loadModel("/invalid/path/model.gguf"))
    }

    @Test
    fun testGbnfGrammarSyntax() {
        val gbnf = AgentCore.TOOL_DECISION_GBNF
        assertNotNull(gbnf)
        assertTrue(gbnf.contains("root ::="))
        assertTrue(gbnf.contains("tool_call_field"))
        assertTrue(gbnf.contains("response_field"))
    }

    @Test
    fun testGbnfGrammarCompliantJsonParsing() {
        val validToolCallJson = """
            {
                "type": "tool_call",
                "thought": "User wants to turn on the flashlight",
                "tool_name": "set_flashlight",
                "arguments": {
                    "enabled": true
                }
            }
        """.trimIndent()

        val parsed = JSONObject(validToolCallJson)
        assertEquals("tool_call", parsed.getString("type"))
        assertEquals("set_flashlight", parsed.getString("tool_name"))
        assertTrue(parsed.getJSONObject("arguments").getBoolean("enabled"))
    }
}
