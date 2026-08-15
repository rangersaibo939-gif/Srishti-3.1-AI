package com.opendroid.app.core.tools

import android.content.Context
import com.opendroid.app.core.domain.RiskLevel
import com.opendroid.app.core.domain.ToolDefinition
import com.opendroid.app.core.domain.ToolExecutionResult

/**
 * Common contract for all real Android OS native tools
 */
interface AndroidTool {
    val id: String
    val name: String
    val description: String
    val riskTier: RiskLevel
    val timeoutMs: Long

    fun getDefinition(): ToolDefinition

    /**
     * Pre-execution argument validation against JSON schema
     */
    fun validate(arguments: Map<String, Any?>): Result<Unit>

    /**
     * Executes the actual Android System API call
     */
    suspend fun execute(context: Context, arguments: Map<String, Any?>): ToolExecutionResult

    /**
     * Post-execution verification confirming OS state mutated as intended
     */
    suspend fun verify(context: Context, arguments: Map<String, Any?>, result: ToolExecutionResult): Boolean
}
