package com.opendroid.app.core.tools

import com.opendroid.app.core.domain.ToolDefinition

/**
 * Phase 1 Safe Tool Registry
 * Only explicitly registered Android native tools can be executed.
 */
object ToolRegistry {

    private val tools = mutableMapOf<String, AndroidTool>()

    init {
        register(FlashlightTool())
        register(MediaVolumeTool())
        register(BatteryInfoTool())
        register(AppLaunchTool())
        register(DeviceInfoTool())
    }

    fun register(tool: AndroidTool) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): AndroidTool? {
        return tools[name]
    }

    fun getAllDefinitions(): List<ToolDefinition> {
        return tools.values.map { it.getDefinition() }
    }

    fun isRegistered(name: String): Boolean {
        return tools.containsKey(name)
    }
}
